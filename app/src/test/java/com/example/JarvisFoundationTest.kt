package com.example

import android.content.Context
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.agent.Phase2AgentCore
import com.example.core.ai.AiEngine
import com.example.core.ai.AiStreamEvent
import com.example.core.ai.EngineLoadState
import com.example.core.ai.GenerationBenchmark
import com.example.core.ai.LiteRtLmAiEngine
import com.example.core.cloud.Phase1CloudProviderRegistry
import com.example.core.hf.HuggingFaceApiClient
import com.example.core.hf.HfRepoDetail
import com.example.core.hf.HfRepoFile
import com.example.core.memory.RoomLocalMemory
import com.example.core.model.ArtifactState
import com.example.core.model.CompatibilityVerdict
import com.example.core.model.DownloadQueueManager
import com.example.core.model.DownloadQueueStatus
import com.example.core.model.DownloadServiceBridge
import com.example.core.model.EnqueueDownloadRequest
import com.example.core.model.EnqueueResult
import com.example.core.model.ModelArtifactManager
import com.example.core.model.ModelCompatibilityEngine
import com.example.core.model.ModelFormatRegistry
import com.example.core.model.ModelProviderType
import com.example.core.model.ModelSlot
import com.example.core.model.ModelStatus
import com.example.core.model.RoomModelManager
import com.example.core.model.RuntimeFormatId
import com.example.core.security.SecureCredentialStore
import com.example.core.tools.AndroidSystemTools
import com.example.core.tools.DeviceTelemetry
import com.example.core.voice.MicStatus
import com.example.core.voice.SpeechCompletionEvent
import com.example.core.voice.TranscriptCapturedEvent
import com.example.core.voice.TtsSanitizer
import com.example.core.voice.TtsVoiceInfo
import com.example.core.voice.VoiceConversationMode
import com.example.core.voice.VoiceEngine
import com.example.core.voice.VoiceEngineState
import com.example.core.voice.VoiceErrorKind
import com.example.core.voice.VoiceLanguageDetector
import com.example.core.voice.VoiceLanguageOption
import com.example.core.voice.VoiceLocaleCheck
import com.example.core.wakeword.ModularWakeWordEngine
import com.example.core.wakeword.WakeWordState
import com.example.data.local.db.JarvisDatabase
import com.example.data.local.db.MessageEntity
import com.example.data.local.db.MessageRole
import com.example.data.local.db.ModelEntryEntity
import com.example.data.repository.ConversationRepository
import com.example.data.repository.InferenceBackendPreference
import com.example.data.repository.JarvisSettings
import com.example.data.repository.SettingsRepository
import android.Manifest
import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.PowerManager
import com.example.core.ai.ThermalAndMemoryPolicy
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.core.agent.AgentRoutingMode
import com.example.core.agent.ExecutionRoute
import com.example.core.agent.IntentRouter
import com.example.core.agent.PlanStepStatus
import com.example.core.agent.PlanStepType
import com.example.core.agent.ToolRegistryFactory
import com.example.core.cloud.CloudInferenceConfig
import com.example.core.cloud.CloudProviderKind
import com.example.core.cloud.OkHttpCloudAiClient
import com.example.core.memory.MemoryKind
import com.example.core.tools.AccessibilityActionTool
import com.example.core.tools.AccessibilityBridge
import com.example.core.tools.AdjustMediaVolumeTool
import com.example.core.tools.AndroidTools
import com.example.core.tools.AuthorizedAccessibilityAction
import com.example.core.tools.ComposeMessageTool
import com.example.core.tools.ContactMatch
import com.example.core.tools.ControlMediaPlaybackTool
import com.example.core.tools.GoHomeScreenTool
import com.example.core.tools.InitiatePhoneCallTool
import com.example.core.tools.LaunchInstalledAppTool
import com.example.core.tools.LaunchableAppEntry
import com.example.core.tools.NotificationBridge
import com.example.core.tools.OpenAndroidSettingsTool
import com.example.core.tools.OpenAppScreenTool
import com.example.core.tools.ReadBatteryStatusTool
import com.example.core.tools.ReadDeviceInfoTool
import com.example.core.tools.ReadNotificationsTool
import com.example.core.tools.SearchContactsTool
import com.example.core.tools.SpecialAccessType
import com.example.core.tools.ToolCallRequest
import com.example.core.tools.ToolExecutionStatus
import com.example.core.tools.ToolPermissionValidator
import com.example.core.tools.ToolRegistry
import com.example.core.web.OkHttpWebResearchService
import com.example.core.web.WebSearchProviderKind
import com.example.ui.screens.chat.ChatViewModel
import com.example.ui.screens.voice.VoiceViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JarvisFoundationTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: JarvisDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, JarvisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `conversation repository persists messages with benchmarks and cascade deletes sessions`() = runTest {
        val repo = ConversationRepository(db.conversationDao())
        val convId = repo.createConversation("Alpha Diagnostics")
        repo.appendMessage(convId, MessageRole.USER, "Check core status", "Operator")
        repo.appendMessage(
            conversationId = convId,
            role = MessageRole.ASSISTANT,
            content = "All local subsystems nominal.",
            modelLabel = "Gemma 3 1B Instruct (Int4)",
            ttftMs = 142L,
            tokensPerSecond = 28.4f,
            outputTokens = 16,
            backendUsed = "GPU (OpenCL)"
        )

        val messages = repo.observeMessages(convId).first()
        assertEquals(2, messages.size)
        assertEquals("Check core status", messages[0].content)
        assertEquals(142L, messages[1].ttftMs)
        assertEquals(28.4f, messages[1].tokensPerSecond ?: 0f, 0.01f)
        assertEquals(16, messages[1].outputTokens)
        assertEquals("GPU (OpenCL)", messages[1].backendUsed)

        repo.renameConversation(convId, "Alpha Renamed")
        val sessions = repo.observeConversations().first()
        assertEquals("Alpha Renamed", sessions.first { it.id == convId }.title)

        repo.deleteConversation(convId)
        val remaining = repo.observeConversations().first()
        assertTrue(remaining.none { it.id == convId })
    }

    @Test
    fun `model manager seeds gemma 3 1b active slot and updates artifact states`() = runTest {
        val manager = RoomModelManager(db.modelDao())
        manager.ensureSeeded()

        val initialModels = manager.observeModels().first()
        assertTrue(initialModels.size >= 3)
        val active = manager.getActiveModelOnce()
        assertEquals(RoomModelManager.GEMMA_3_1B_ID, active?.id)
        assertEquals(ArtifactState.NOT_INSTALLED, active?.artifactState)
        assertEquals("litert-community/Gemma3-1B-IT", active?.sourceRepoId)

        manager.updateArtifactState(
            id = RoomModelManager.GEMMA_3_1B_ID,
            localFilePath = "/data/user/0/app/files/models/gemma3.litertlm",
            artifactState = ArtifactState.READY,
            status = ModelStatus.READY
        )
        val updatedActive = manager.getActiveModelOnce()
        assertEquals(ArtifactState.READY, updatedActive?.artifactState)
        assertEquals(ModelStatus.READY, updatedActive?.status)

        manager.setActiveModel("slot_gemini_cloud")
        assertEquals("slot_gemini_cloud", manager.getActiveModelOnce()?.id)

        manager.registerCustomSlot(
            displayName = "Custom Qwen3",
            providerType = ModelProviderType.LOCAL_ON_DEVICE,
            engineFamily = "LiteRT-LM (.litertlm)",
            parameterHint = "1.5B INT4",
            contextWindow = 8192
        )
        val updatedModels = manager.observeModels().first()
        assertTrue(updatedModels.any { it.displayName == "Custom Qwen3" && it.isCustom })
    }

    @Test
    fun `settings repository persists encrypted token and phase 2 preferences`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tmpFolder.root, "test_jarvis.preferences_pb") }
        )
        val settingsRepo = SettingsRepository(dataStore)

        val defaults = settingsRepo.settingsFlow.first()
        assertEquals(InferenceBackendPreference.AUTO_GPU_FALLBACK, defaults.inferenceBackend)
        assertEquals(4096, defaults.contextLengthTokens)
        assertEquals(512, defaults.maxOutputTokens)

        settingsRepo.setOrbAnimationEnabled(false)
        settingsRepo.setTtsSpeechRate(1.35f)
        settingsRepo.setTelemetryPollingSeconds(10)
        settingsRepo.setWakeWordStandbyEnabled(true)
        settingsRepo.setInferenceBackend(InferenceBackendPreference.CPU_ONLY)
        settingsRepo.setContextLengthTokens(8192)
        settingsRepo.setMaxOutputTokens(1024)
        settingsRepo.setSamplerTemperature(0.45f)
        settingsRepo.setHuggingFaceToken("  hf_test_token_12345  ")

        val current = settingsRepo.settingsFlow.first()
        assertFalse(current.orbAnimationEnabled)
        assertEquals(1.35f, current.ttsSpeechRate, 0.01f)
        assertEquals(10, current.telemetryPollingSeconds)
        assertTrue(current.wakeWordStandbyEnabled)
        assertEquals(InferenceBackendPreference.CPU_ONLY, current.inferenceBackend)
        assertEquals(8192, current.contextLengthTokens)
        assertEquals(1024, current.maxOutputTokens)
        assertEquals(0.45f, current.samplerTemperature, 0.01f)
        assertEquals("hf_test_token_12345", current.huggingFaceToken)
    }

    @Test
    fun `security credential store validates repo ids and blocks unsafe files`() {
        val store = SecureCredentialStore()
        val encrypted = store.encrypt("hf_secret_abc123")
        assertFalse(encrypted.contains("hf_secret_abc123"))
        assertEquals("hf_secret_abc123", store.decrypt(encrypted))
        assertEquals("hf_s••••c123", SecureCredentialStore.maskToken("hf_secret_abc123"))

        assertEquals(
            "litert-community/Gemma3-1B-IT",
            SecureCredentialStore.normalizeAndValidateRepoId("https://huggingface.co/litert-community/Gemma3-1B-IT").getOrThrow()
        )
        assertTrue(SecureCredentialStore.normalizeAndValidateRepoId("../etc/passwd").isFailure)
        assertTrue(SecureCredentialStore.normalizeAndValidateRepoId("onlyone").isFailure)
        assertTrue(SecureCredentialStore.normalizeAndValidateRepoId("owner/repo?bad=1").isFailure)

        assertTrue(SecureCredentialStore.isSafeModelFilename("gemma3-1b-it-int4.litertlm"))
        assertTrue(SecureCredentialStore.isSafeModelFilename("qwen2.5-0.5b-q4_k_m.gguf"))
        assertFalse(SecureCredentialStore.isSafeModelFilename("evil_script.py"))
        assertFalse(SecureCredentialStore.isSafeModelFilename("libhack.so"))
        assertFalse(SecureCredentialStore.isSafeModelFilename("payload.dex"))
        assertFalse(SecureCredentialStore.isSafeModelFilename("../escape.litertlm"))
    }

    @Test
    fun `compatibility engine inspects formats memory storage abi and safetensors restrictions`() {
        val engine = ModelCompatibilityEngine(ModelFormatRegistry())
        val telemetry = DeviceTelemetry(
            manufacturer = "Xiaomi",
            model = "2412DPC0AG",
            brand = "POCO",
            device = "rodin",
            hardware = "mt6899",
            socModel = "Dimensity 8400-Ultra",
            supportedAbis = "arm64-v8a",
            cpuCoreCount = 8,
            androidRelease = "15",
            sdkInt = 35,
            securityPatch = "2025-01-01",
            buildDisplay = "HyperOS 2",
            totalRamBytes = 12L * 1024L * 1024L * 1024L,
            availRamBytes = 6L * 1024L * 1024L * 1024L,
            lowMemory = false,
            totalStorageBytes = 256L * 1024L * 1024L * 1024L,
            freeStorageBytes = 64L * 1024L * 1024L * 1024L,
            batteryPercent = 85,
            batteryStatus = "Discharging",
            batteryPlugged = "Battery",
            batteryTempCelsius = 30f,
            batteryVoltageMv = 4100,
            batteryHealth = "Nominal",
            thermalStatusLabel = "Nominal",
            thermalCode = 0,
            isNetworkConnected = true,
            networkTransport = "Wi-Fi",
            isNetworkValidated = true,
            isNetworkMetered = false,
            downstreamBandwidthKbps = 100000,
            capturedAtMillis = 0L
        )

        val repo = HfRepoDetail(
            repoId = "litert-community/Gemma3-1B-IT",
            author = "litert-community",
            modelName = "Gemma3-1B-IT",
            downloads = 10000,
            likes = 500,
            isPrivate = false,
            isGated = false,
            gatedMode = null,
            gatedPrompt = null,
            license = "gemma",
            architecture = "Gemma 3",
            parameterCount = "1B",
            quantizationSummary = "INT4",
            contextLengthHint = 4096,
            supportedTasks = listOf("text-generation"),
            libraryName = "litert-lm",
            baseModel = null,
            tags = listOf("litert-lm"),
            files = emptyList()
        )

        // 1. Compatible LiteRT-LM artifact
        val litertFile = HfRepoFile("gemma3-1b-it-int4.litertlm", 612_368_384L, "abc", "https://huggingface.co/file")
        val litertReport = engine.inspect(repo, litertFile, telemetry, 20L * 1024L * 1024L * 1024L, hasUserToken = false)
        assertEquals(CompatibilityVerdict.READY_TO_INSTALL, litertReport.verdict)
        assertTrue(litertReport.canDownloadToDevice)
        assertTrue(litertReport.canRunInJarvisNow)

        // 2. Prepared GGUF adapter
        val ggufFile = HfRepoFile("qwen2.5-0.5b-instruct-q4_k_m.gguf", 400_000_000L, null, "https://huggingface.co/file.gguf")
        val ggufReport = engine.inspect(repo, ggufFile, telemetry, 20L * 1024L * 1024L * 1024L, hasUserToken = false)
        assertEquals(RuntimeFormatId.GGUF_LLAMA_CPP, ggufReport.formatId)
        assertEquals(CompatibilityVerdict.PREPARED_RUNTIME_ONLY, ggufReport.verdict)
        assertTrue(ggufReport.canDownloadToDevice)
        assertFalse(ggufReport.canRunInJarvisNow)

        // 3. Raw .safetensors strictly blocked
        val safetensorsFile = HfRepoFile("model.safetensors", 1_000_000_000L, null, "https://huggingface.co/model.safetensors")
        val safetensorsReport = engine.inspect(repo, safetensorsFile, telemetry, 20L * 1024L * 1024L * 1024L, hasUserToken = false)
        assertEquals(RuntimeFormatId.SAFETENSORS_RAW, safetensorsReport.formatId)
        assertEquals(CompatibilityVerdict.BLOCKED, safetensorsReport.verdict)
        assertFalse(safetensorsReport.canDownloadToDevice)
        assertFalse(safetensorsReport.canRunInJarvisNow)

        // 4. Gated repo without token is blocked
        val gatedRepo = repo.copy(isGated = true, gatedMode = "auto")
        val gatedBlocked = engine.inspect(gatedRepo, litertFile, telemetry, 20L * 1024L * 1024L * 1024L, hasUserToken = false)
        assertEquals(CompatibilityVerdict.BLOCKED, gatedBlocked.verdict)
        val gatedWithToken = engine.inspect(gatedRepo, litertFile, telemetry, 20L * 1024L * 1024L * 1024L, hasUserToken = true)
        assertEquals(CompatibilityVerdict.INSTALLABLE_WITH_WARNING, gatedWithToken.verdict)
    }

    @Test
    fun `huggingface api client queries real public repositories and parses metadata`() = runBlocking {
        val client = HuggingFaceApiClient()

        val gemmaResult = client.getModelDetail("litert-community/Gemma3-1B-IT")
        assertTrue("Expected real HF detail for Gemma3-1B-IT: ${gemmaResult.exceptionOrNull()}", gemmaResult.isSuccess)
        val gemma = gemmaResult.getOrThrow()
        assertEquals("litert-community/Gemma3-1B-IT", gemma.repoId)
        assertEquals("litert-community", gemma.author)
        assertTrue(gemma.isGated)
        assertEquals("gemma", gemma.license)
        assertTrue(gemma.files.any { it.fileName.endsWith(".litertlm") })
        assertTrue(gemma.files.any { it.sha256 != null && it.sizeBytes > 0L })

        val qwenResult = client.getModelDetail("Qwen/Qwen2.5-0.5B-Instruct")
        assertTrue("Expected real HF detail for Qwen2.5-0.5B-Instruct: ${qwenResult.exceptionOrNull()}", qwenResult.isSuccess)
        val qwen = qwenResult.getOrThrow()
        assertFalse(qwen.isGated)
        assertTrue(qwen.architecture.contains("Qwen", ignoreCase = true))

        val searchResult = client.searchModels(query = "gemma", author = "litert-community", limit = 10)
        assertTrue("Expected real HF search results: ${searchResult.exceptionOrNull()}", searchResult.isSuccess)
        assertTrue(searchResult.getOrThrow().any { it.repoId.contains("Gemma", ignoreCase = true) })
    }

    @Test
    fun `download queue manager handles dedup http range resume sha256 verification and model registration`() = runBlocking {
        val modelManager = RoomModelManager(db.modelDao()).apply { ensureSeeded() }
        val payload = ByteArray(16 * 1024) { (it % 239).toByte() }
        val expectedSha = sha256Hex(payload)
        val requestsServed = AtomicInteger(0)

        val server = ServerSocket(0)
        val port = server.localPort
        val serverThread = Thread {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                requestsServed.incrementAndGet()
                val reader = socket.getInputStream().bufferedReader()
                var rangeHeader: String? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        rangeHeader = line.substringAfter(':').trim()
                    }
                }
                val startOffset = rangeHeader
                    ?.removePrefix("bytes=")
                    ?.substringBefore('-')
                    ?.toIntOrNull()
                    ?.coerceIn(0, payload.size)
                    ?: 0
                val slice = payload.copyOfRange(startOffset, payload.size)
                val out = socket.getOutputStream()
                val statusLine = if (startOffset > 0) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n"
                val headers = buildString {
                    append(statusLine)
                    append("Accept-Ranges: bytes\r\n")
                    append("Content-Length: ${slice.size}\r\n")
                    if (startOffset > 0) {
                        append("Content-Range: bytes $startOffset-${payload.size - 1}/${payload.size}\r\n")
                    }
                    append("Connection: close\r\n\r\n")
                }
                out.write(headers.toByteArray())
                out.write(slice)
                out.flush()
                socket.close()
            }
        }.apply { isDaemon = true; start() }

        try {
            val queueManager = DownloadQueueManager(
                context = context,
                queueDao = db.downloadQueueDao(),
                modelManager = modelManager,
                storageSpaceProvider = { 10L * 1024L * 1024L * 1024L },
                serviceBridge = DownloadServiceBridge { _, _, _, _ -> }
            )

            val req = EnqueueDownloadRequest(
                repoId = "litert-community/TestModel",
                fileName = "test-q4.litertlm",
                displayName = "Test LiteRT Bundle",
                downloadUrl = "http://127.0.0.1:$port/test-q4.litertlm",
                expectedSha256 = expectedSha,
                totalBytes = payload.size.toLong(),
                runtimeFormat = RuntimeFormatId.LITERT_LM,
                architecture = "Gemma 3",
                quantization = "INT4",
                parameterCount = "1B",
                license = "apache-2.0"
            )

            // 1. Enqueue without auto-start -> verify duplicate prevention
            val enq1 = queueManager.enqueueAndStart(req, bearerToken = "", autoStart = false)
            assertTrue(enq1 is EnqueueResult.Enqueued)
            val taskId = (enq1 as EnqueueResult.Enqueued).item.id

            val dup = queueManager.enqueueAndStart(req, bearerToken = "", autoStart = false)
            assertTrue(dup is EnqueueResult.Duplicate)

            // 2. Seed partial .part file to test real HTTP 206 Range Resume + SHA-256 verification
            val taskEntity = db.downloadQueueDao().getTaskById(taskId)!!
            File("${taskEntity.destinationPath}.part").writeBytes(payload.copyOfRange(0, 4096))

            val execResult = queueManager.executeDownloadTask(taskId, bearerToken = "")
            assertTrue("Download & resume failed: ${execResult.exceptionOrNull()}", execResult.isSuccess)
            val savedPath = execResult.getOrThrow()
            assertEquals(payload.size.toLong(), File(savedPath).length())
            assertEquals(DownloadQueueStatus.COMPLETED, db.downloadQueueDao().getTaskById(taskId)?.status?.let { DownloadQueueStatus.valueOf(it) })

            val registeredSlot = modelManager.getModelById(taskEntity.modelSlotId)
            assertNotNull(registeredSlot)
            assertEquals(ArtifactState.READY, registeredSlot?.artifactState)
            assertEquals("Gemma 3", registeredSlot?.architecture)
        } finally {
            server.close()
            serverThread.interrupt()
        }
    }

    @Test
    fun `model artifact manager verifies sha256 handles corruption deletes artifacts and checks storage`() = runTest {
        val modelManager = RoomModelManager(db.modelDao()).apply { ensureSeeded() }
        val payload = ByteArray(4096) { (it % 251).toByte() }
        val expectedSha = sha256Hex(payload)
        val sourceFile = File(tmpFolder.root, "valid_model.litertlm").apply { writeBytes(payload) }

        val customSlot = ModelSlot(
            id = "slot_test_local",
            displayName = "Test LiteRT Bundle",
            providerType = ModelProviderType.LOCAL_ON_DEVICE,
            engineFamily = "LiteRT-LM",
            parameterHint = "1B INT4",
            contextWindow = 4096,
            status = ModelStatus.UNCONFIGURED,
            isActive = false,
            isCustom = false,
            downloadUrl = "https://example.invalid/model.litertlm",
            expectedSha256 = expectedSha,
            expectedSizeBytes = payload.size.toLong()
        )
        db.modelDao().upsertModel(
            ModelEntryEntity(
                id = customSlot.id,
                displayName = customSlot.displayName,
                providerType = customSlot.providerType.name,
                engineFamily = customSlot.engineFamily,
                parameterHint = customSlot.parameterHint,
                contextWindow = customSlot.contextWindow,
                status = customSlot.status.name,
                isActive = false,
                isCustom = false,
                downloadUrl = customSlot.downloadUrl,
                expectedSha256 = expectedSha,
                expectedSizeBytes = customSlot.expectedSizeBytes
            )
        )

        val lowSpaceManager = ModelArtifactManager(
            context = context,
            modelManager = modelManager,
            storageSpaceProvider = { 1024L }
        )
        val lowSpaceResult = lowSpaceManager.downloadArtifactInternal(
            slot = customSlot,
            url = customSlot.downloadUrl!!,
            bearerToken = ""
        )
        assertTrue(lowSpaceResult.isFailure)
        assertTrue(lowSpaceResult.exceptionOrNull()?.message?.contains("Insufficient storage") == true)

        val ampleSpaceManager = ModelArtifactManager(
            context = context,
            modelManager = modelManager,
            storageSpaceProvider = { 4L * 1024L * 1024L * 1024L }
        )
        val importResult = ampleSpaceManager.importFromFileInternal(customSlot, sourceFile)
        assertTrue(importResult.isSuccess)
        val installedPath = importResult.getOrThrow()
        assertTrue(File(installedPath).exists())

        val storedAfterImport = modelManager.getModelById(customSlot.id)
        assertEquals(ArtifactState.READY, storedAfterImport?.artifactState)
        assertEquals(installedPath, storedAfterImport?.localFilePath)

        File(installedPath).writeBytes(ByteArray(4096) { 0x5A.toByte() })
        val verifyCorrupt = ampleSpaceManager.verifyInstalledArtifact(storedAfterImport!!)
        assertTrue(verifyCorrupt.isFailure)
        assertFalse(File(installedPath).exists())
        val storedAfterCorrupt = modelManager.getModelById(customSlot.id)
        assertEquals(ArtifactState.CORRUPTED, storedAfterCorrupt?.artifactState)
        assertNull(storedAfterCorrupt?.localFilePath)

        ampleSpaceManager.deleteArtifact(storedAfterCorrupt!!)
        assertEquals(ArtifactState.NOT_INSTALLED, modelManager.getModelById(customSlot.id)?.artifactState)
    }

    @Test
    fun `agent core and chat viewmodel stream tokens persist benchmarks and support cancellation`() = runBlocking {
        val convRepo = ConversationRepository(db.conversationDao())
        val modelManager = RoomModelManager(db.modelDao()).apply { ensureSeeded() }
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tmpFolder.root, "chat_vm_settings.preferences_pb") }
        )
        val settingsRepo = SettingsRepository(dataStore)
        val fakeEngine = ControllableTestAiEngine()
        val wakeWord = ModularWakeWordEngine().apply {
            syncStandbyPreference(enabled = true, hasMicPermission = true, isAppInForeground = true, porcupineAccessKeyPresent = false)
        }
        val agentCore = Phase2AgentCore(
            aiEngine = fakeEngine,
            modelManager = modelManager,
            androidTools = AndroidSystemTools(context),
            cloudProviderRegistry = Phase1CloudProviderRegistry(),
            wakeWordEngine = wakeWord,
            localMemory = RoomLocalMemory(db.memoryDao())
        )

        assertEquals(WakeWordState.UNAVAILABLE_PENDING_MODEL_OR_KEY, wakeWord.state.value)
        assertFalse(wakeWord.isPhraseDetectionVerified)
        val snapshot = agentCore.runSystemTelemetrySnapshot()
        assertTrue(snapshot.content.contains("DEVICE TELEMETRY SNAPSHOT"))

        val initialAudit = agentCore.inspectEngineReadiness()
        assertTrue(initialAudit.content.contains("JARVIS SYSTEM READINESS"))

        modelManager.updateArtifactState(
            id = RoomModelManager.GEMMA_3_1B_ID,
            localFilePath = File(tmpFolder.root, "gemma.litertlm").apply { writeBytes(ByteArray(2048)) }.absolutePath,
            artifactState = ArtifactState.READY,
            status = ModelStatus.READY
        )

        val vm = ChatViewModel(
            conversationRepository = convRepo,
            modelManager = modelManager,
            agentCore = agentCore,
            aiEngine = fakeEngine,
            settingsRepository = settingsRepo,
            modelBenchmarkDao = db.modelBenchmarkDao()
        )
        var convId: String? = null
        repeat(40) {
            shadowOf(Looper.getMainLooper()).idle()
            convId = convRepo.observeConversations().first().firstOrNull()?.id
            if (convId != null) return@repeat
            Thread.sleep(15)
        }
        assertNotNull(convId)

        vm.sendMessage("Summarize Dimensity 8400")
        var msgs = emptyList<MessageEntity>()
        repeat(50) {
            shadowOf(Looper.getMainLooper()).idle()
            msgs = convRepo.getMessagesOnce(convId!!)
            if (msgs.size >= 2) return@repeat
            Thread.sleep(20)
        }
        assertEquals(2, msgs.size)
        assertEquals(MessageRole.USER, msgs[0].role)
        assertEquals(MessageRole.ASSISTANT, msgs[1].role)
        assertTrue(msgs[1].content.contains("MediaTek Dimensity"))
        assertEquals(110L, msgs[1].ttftMs)
        assertEquals(32.5f, msgs[1].tokensPerSecond ?: 0f, 0.01f)
        assertEquals("GPU (OpenCL)", msgs[1].backendUsed)

        val benchmarks = db.modelBenchmarkDao().observeBenchmarks().first()
        assertEquals(1, benchmarks.size)
        assertEquals(110L, benchmarks[0].ttftMs)

        fakeEngine.cancelOnSecondChunk = true
        vm.sendMessage("Generate long essay")
        repeat(50) {
            shadowOf(Looper.getMainLooper()).idle()
            msgs = convRepo.getMessagesOnce(convId!!)
            if (msgs.size >= 4) return@repeat
            Thread.sleep(20)
        }
        assertEquals(4, msgs.size)
        assertTrue(msgs[3].content.contains("[Generation stopped]"))
        dataStoreScope.cancel()
    }

    @Test
    fun `litertlm engine handles unloaded or missing artifact safely on host jvm`() = runTest {
        val engine = LiteRtLmAiEngine(context)
        val settings = JarvisSettings()
        val missingSlot = ModelSlot(
            id = RoomModelManager.GEMMA_3_1B_ID,
            displayName = "Gemma 3 1B Instruct (Int4)",
            providerType = ModelProviderType.LOCAL_ON_DEVICE,
            engineFamily = "LiteRT-LM",
            parameterHint = "1B INT4",
            contextWindow = 4096,
            status = ModelStatus.UNCONFIGURED,
            isActive = true,
            isCustom = false,
            localFilePath = null,
            artifactState = ArtifactState.NOT_INSTALLED
        )

        val loadResult = engine.loadModel(missingSlot, settings)
        assertTrue(loadResult.isFailure)
        assertTrue(engine.loadState.value is EngineLoadState.Error)

        val events = mutableListOf<AiStreamEvent>()
        engine.streamReply("conv-1", "Hello", emptyList(), settings).collect {
            events.add(it)
        }
        assertTrue(events.any { it is AiStreamEvent.Error })
        engine.release()
    }

    @Test
    fun `voice language detector and tts sanitizer handle arabic algerian french english and clean metadata`() {
        val dzArabic = VoiceLanguageDetector.detectLanguage("واش راك خويا كيفاش الخدمة اليوم", VoiceLanguageOption.AUTO)
        assertEquals(VoiceLanguageOption.ARABIC_ALGERIAN, dzArabic)

        val dzLatin = VoiceLanguageDetector.detectLanguage("wach rak labes saha", VoiceLanguageOption.AUTO)
        assertEquals(VoiceLanguageOption.ARABIC_ALGERIAN, dzLatin)

        val stdArabic = VoiceLanguageDetector.detectLanguage("مرحبا كيف حالك اليوم", VoiceLanguageOption.AUTO)
        assertEquals(VoiceLanguageOption.ARABIC, stdArabic)

        val french = VoiceLanguageDetector.detectLanguage("Bonjour comment allez-vous aujourd'hui ?", VoiceLanguageOption.AUTO)
        assertEquals(VoiceLanguageOption.FRENCH, french)

        val english = VoiceLanguageDetector.detectLanguage("Check device thermals and battery level", VoiceLanguageOption.AUTO)
        assertEquals(VoiceLanguageOption.ENGLISH, english)

        val rawResponse = """
            ### Diagnostic **Nominal**
            - Check [System Docs](https://example.com/docs)
            - See https://huggingface.co/litert-community/Gemma3-1B-IT
            ```kotlin
            val x = 42
            ```
            TTFT: 142 ms • 28.4 tok/s • GPU (OpenCL)
            [Generation stopped]
        """.trimIndent()
        val spoken = TtsSanitizer.sanitizeForSpeech(rawResponse)
        assertFalse(spoken.contains("**"))
        assertFalse(spoken.contains("https://"))
        assertFalse(spoken.contains("TTFT"))
        assertFalse(spoken.contains("tok/s"))
        assertFalse(spoken.contains("Generation stopped"))
        assertTrue(spoken.contains("Diagnostic Nominal"))
        assertTrue(spoken.contains("Check System Docs"))

        val chunks = TtsSanitizer.chunkForTts("Sentence one. Sentence two. Sentence three.", maxChunkChars = 20)
        assertTrue(chunks.size >= 2)
    }

    @Test
    fun `voice viewmodel handles arabic and french input auto tts interruption mic denial background guard and wake word`() = runBlocking {
        val convRepo = ConversationRepository(db.conversationDao())
        val modelManager = RoomModelManager(db.modelDao()).apply { ensureSeeded() }
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tmpFolder.root, "voice_vm_settings.preferences_pb") }
        )
        val settingsRepo = SettingsRepository(dataStore)
        val fakeAiEngine = ControllableTestAiEngine()
        val wakeWord = ModularWakeWordEngine()
        val agentCore = Phase2AgentCore(
            aiEngine = fakeAiEngine,
            modelManager = modelManager,
            androidTools = AndroidSystemTools(context),
            cloudProviderRegistry = Phase1CloudProviderRegistry(),
            wakeWordEngine = wakeWord,
            localMemory = RoomLocalMemory(db.memoryDao())
        )
        modelManager.updateArtifactState(
            id = RoomModelManager.GEMMA_3_1B_ID,
            localFilePath = File(tmpFolder.root, "gemma.litertlm").apply { writeBytes(ByteArray(1024)) }.absolutePath,
            artifactState = ArtifactState.READY,
            status = ModelStatus.READY
        )

        val fakeVoice = ControllableTestVoiceEngine()
        var serviceStartCount = 0
        var serviceStopCount = 0

        val vm = VoiceViewModel(
            voiceEngine = fakeVoice,
            wakeWordEngine = wakeWord,
            settingsRepository = settingsRepo,
            agentCore = agentCore,
            aiEngine = fakeAiEngine,
            modelManager = modelManager,
            conversationRepository = convRepo,
            modelBenchmarkDao = db.modelBenchmarkDao(),
            serviceStarter = { _, _ -> serviceStartCount++; true },
            serviceStopper = { serviceStopCount++ }
        )
        val uiSubJob = dataStoreScope.launch(Dispatchers.Unconfined) {
            vm.uiState.collect {}
        }

        // 1. Microphone permission denial blocks listening while text fallback still operates
        vm.onMicPermissionChanged(false)
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            if (vm.uiState.value.micStatus == MicStatus.PERMISSION_DENIED) return@repeat
            Thread.sleep(10)
        }
        assertEquals(MicStatus.PERMISSION_DENIED, vm.uiState.value.micStatus)
        vm.toggleListening()
        assertEquals(0, fakeVoice.startListeningCount)

        vm.submitTextFallback("Bonjour JARVIS donne moi le statut")
        repeat(40) {
            shadowOf(Looper.getMainLooper()).idle()
            if (fakeVoice.spokenUtterances.isNotEmpty() && !vm.uiState.value.isGeneratingReply) return@repeat
            Thread.sleep(15)
        }
        assertEquals(1, fakeVoice.spokenUtterances.size)
        assertEquals(VoiceLanguageOption.FRENCH, vm.uiState.value.voiceTurns.first().detectedLanguage)

        // 2. Grant permission, switch to Continuous + Algerian Arabic/Arabic, test STT -> TTS -> Auto Rearm
        vm.onMicPermissionChanged(true)
        vm.setConversationMode(VoiceConversationMode.CONTINUOUS)
        vm.setLanguageOption(VoiceLanguageOption.ARABIC_ALGERIAN)
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            if (vm.uiState.value.conversationMode == VoiceConversationMode.CONTINUOUS &&
                vm.uiState.value.selectedLanguage == VoiceLanguageOption.ARABIC_ALGERIAN
            ) return@repeat
            Thread.sleep(10)
        }

        vm.startContinuousSession()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, serviceStartCount)
        assertEquals(VoiceLanguageOption.ARABIC_ALGERIAN, fakeVoice.lastStartedLanguage)

        fakeVoice.emitTranscript("واش راك يا جارفيس", VoiceLanguageOption.ARABIC_ALGERIAN)
        repeat(40) {
            shadowOf(Looper.getMainLooper()).idle()
            if (fakeVoice.spokenUtterances.size >= 2 && !vm.uiState.value.isGeneratingReply) return@repeat
            Thread.sleep(15)
        }
        assertEquals(2, fakeVoice.spokenUtterances.size)
        assertEquals(VoiceLanguageOption.ARABIC_ALGERIAN, fakeVoice.lastSpokenLanguage)
        assertFalse(vm.uiState.value.isGeneratingReply)

        // 3. Natural speech completion re-arms listening in continuous mode (after 350ms delay)
        val listenCountBeforeDone = fakeVoice.startListeningCount
        fakeVoice.emitSpeechCompletion(interrupted = false)
        repeat(15) {
            shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
            if (fakeVoice.startListeningCount > listenCountBeforeDone) return@repeat
            Thread.sleep(10)
        }
        assertTrue(fakeVoice.startListeningCount > listenCountBeforeDone)

        // 4. Explicit interruption stops speaking and cancels continuous loop
        vm.interruptAllVoice()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(fakeVoice.stopSpeakingCount >= 1)
        assertFalse(vm.uiState.value.isContinuousSessionActive)

        // 5. Wake word modular interface processes PCM frames safely & honors background restriction
        vm.setWakeWordStandbyEnabled(true)
        repeat(30) {
            shadowOf(Looper.getMainLooper()).idle()
            if (wakeWord.state.value == WakeWordState.UNAVAILABLE_PENDING_MODEL_OR_KEY) return@repeat
            Thread.sleep(10)
        }
        assertEquals(WakeWordState.UNAVAILABLE_PENDING_MODEL_OR_KEY, wakeWord.state.value)
        assertFalse(wakeWord.isPhraseDetectionVerified)

        val pcmFrame = ShortArray(512) { idx -> if (idx % 2 == 0) 4000 else -4000 }
        wakeWord.processPcm16Frame(pcmFrame, pcmFrame.size)
        assertEquals(1L, wakeWord.audioMetrics.value.framesProcessed)
        assertTrue(wakeWord.audioMetrics.value.rmsDbfs > -30f)

        val stopsBeforeBg = serviceStopCount
        vm.onAppLifecycleChanged(inForeground = false)
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            if (wakeWord.state.value == WakeWordState.BACKGROUND_RESTRICTED) return@repeat
            Thread.sleep(10)
        }
        assertEquals(WakeWordState.BACKGROUND_RESTRICTED, wakeWord.state.value)
        assertTrue(serviceStopCount > stopsBeforeBg)

        uiSubJob.cancel()
        dataStoreScope.cancel()
    }

    private class ControllableTestVoiceEngine : VoiceEngine {
        private val _state = MutableStateFlow<VoiceEngineState>(VoiceEngineState.Idle)
        override val state: StateFlow<VoiceEngineState> = _state.asStateFlow()
        private val _lastTranscript = MutableStateFlow("")
        override val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()
        private val _isTtsReady = MutableStateFlow(true)
        override val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()
        private val _detectedLanguage = MutableStateFlow(VoiceLanguageOption.ENGLISH)
        override val detectedLanguage: StateFlow<VoiceLanguageOption> = _detectedLanguage.asStateFlow()
        private val _availableVoices = MutableStateFlow(
            listOf(TtsVoiceInfo("en-us-x-sfg-local", "en-US", "English (US)", false, 400))
        )
        override val availableVoices: StateFlow<List<TtsVoiceInfo>> = _availableVoices.asStateFlow()
        private val _localeChecks = MutableStateFlow<List<VoiceLocaleCheck>>(emptyList())
        override val localeChecks: StateFlow<List<VoiceLocaleCheck>> = _localeChecks.asStateFlow()
        private val _transcriptEvents = MutableSharedFlow<TranscriptCapturedEvent>(extraBufferCapacity = 8)
        override val transcriptEvents: SharedFlow<TranscriptCapturedEvent> = _transcriptEvents.asSharedFlow()
        private val _speechCompletionEvents = MutableSharedFlow<SpeechCompletionEvent>(extraBufferCapacity = 8)
        override val speechCompletionEvents: SharedFlow<SpeechCompletionEvent> = _speechCompletionEvents.asSharedFlow()

        var startListeningCount = 0
        var stopSpeakingCount = 0
        var lastStartedLanguage: VoiceLanguageOption? = null
        var lastSpokenLanguage: VoiceLanguageOption? = null
        val spokenUtterances = mutableListOf<String>()

        override fun isSpeechRecognitionAvailable(): Boolean = true
        override fun initializeTtsIfNeeded() = Unit

        override fun startListening(languageOption: VoiceLanguageOption) {
            startListeningCount++
            lastStartedLanguage = languageOption
            _state.value = VoiceEngineState.Listening(rmsDb = 5f, partialTranscript = "", isVoiceActive = true)
        }

        override fun stopListening() {
            _state.value = VoiceEngineState.Processing
        }

        override fun cancelListening() {
            _state.value = VoiceEngineState.Idle
        }

        override fun speak(
            text: String,
            speechRate: Float,
            languageOption: VoiceLanguageOption,
            preferredVoiceName: String
        ) {
            val clean = TtsSanitizer.sanitizeForSpeech(text)
            spokenUtterances.add(clean)
            lastSpokenLanguage = languageOption
            _state.value = VoiceEngineState.Speaking(clean)
        }

        override fun stopSpeaking() {
            stopSpeakingCount++
            _state.value = VoiceEngineState.Idle
        }

        override fun resetError() {
            _state.value = VoiceEngineState.Idle
        }

        override fun release() {
            _state.value = VoiceEngineState.Idle
        }

        fun emitTranscript(text: String, lang: VoiceLanguageOption) {
            _lastTranscript.value = text
            _detectedLanguage.value = lang
            _state.value = VoiceEngineState.Idle
            _transcriptEvents.tryEmit(TranscriptCapturedEvent(text, lang))
        }

        fun emitSpeechCompletion(interrupted: Boolean) {
            _state.value = VoiceEngineState.Idle
            _speechCompletionEvents.tryEmit(SpeechCompletionEvent(interrupted))
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class ControllableTestAiEngine : AiEngine {
        private val _loadState = MutableStateFlow<EngineLoadState>(EngineLoadState.Unloaded)
        override val loadState: StateFlow<EngineLoadState> = _loadState.asStateFlow()

        private val _isGenerating = MutableStateFlow(false)
        override val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

        private val _lastBenchmark = MutableStateFlow<GenerationBenchmark?>(null)
        override val lastBenchmark: StateFlow<GenerationBenchmark?> = _lastBenchmark.asStateFlow()

        private val _nativeMemoryMb = MutableStateFlow(12.5f)
        override val nativeMemoryMb: StateFlow<Float> = _nativeMemoryMb.asStateFlow()

        var cancelOnSecondChunk: Boolean = false
        private var cancelled = false

        override suspend fun loadModel(slot: ModelSlot, settings: JarvisSettings): Result<Unit> {
            _loadState.value = EngineLoadState.Ready(
                modelId = slot.id,
                modelName = slot.displayName,
                backendUsed = "GPU (OpenCL)",
                loadTimeMs = 250L
            )
            return Result.success(Unit)
        }

        override suspend fun unloadModel() {
            _loadState.value = EngineLoadState.Unloaded
        }

        override suspend fun reloadModel(slot: ModelSlot, settings: JarvisSettings): Result<Unit> =
            loadModel(slot, settings)

        override fun streamReply(
            conversationId: String,
            prompt: String,
            history: List<MessageEntity>,
            settings: JarvisSettings
        ): Flow<AiStreamEvent> = flow {
            cancelled = false
            _isGenerating.value = true
            emit(AiStreamEvent.Started("GPU (OpenCL)"))
            val chunks = listOf("MediaTek ", "Dimensity ", "8400-Ultra")
            val acc = StringBuilder()
            for ((index, chunk) in chunks.withIndex()) {
                acc.append(chunk)
                emit(AiStreamEvent.Delta(acc.toString(), chunk, index + 1))
                if (cancelOnSecondChunk && index == 1) {
                    cancelled = true
                }
                if (cancelled) {
                    val bench = GenerationBenchmark(
                        ttftMs = 95L,
                        tokensPerSecond = 24.0f,
                        outputTokens = index + 1,
                        prefillTokens = 10,
                        backendUsed = "GPU (OpenCL)",
                        nativeHeapMb = 64.0f
                    )
                    _lastBenchmark.value = bench
                    _isGenerating.value = false
                    emit(AiStreamEvent.Cancelled(acc.toString(), bench))
                    return@flow
                }
            }
            val bench = GenerationBenchmark(
                ttftMs = 110L,
                tokensPerSecond = 32.5f,
                outputTokens = 3,
                prefillTokens = 12,
                backendUsed = "GPU (OpenCL)",
                nativeHeapMb = 88.0f
            )
            _lastBenchmark.value = bench
            _isGenerating.value = false
            emit(AiStreamEvent.Completed(acc.toString(), bench))
        }

        override suspend fun cancelGeneration() {
            cancelled = true
        }

        override fun refreshMemoryUsage() {
            _nativeMemoryMb.value = 12.5f
        }

        override fun release() {
            _loadState.value = EngineLoadState.Unloaded
        }
    }

    @Test
    fun `phase 5 tool registry validates safety permissions and executes all device application and communication tools`() = runBlocking {
        val sysTools = AndroidSystemTools(context)
        val defaultRegistry = ToolRegistryFactory.createDefault(sysTools)
        assertEquals(13, defaultRegistry.listTools().size)

        val launchedIntents = mutableListOf<Intent>()
        var notificationAuthorized = false
        var accessibilityAuthorized = false
        var accessibilityReturnSuccess = true

        AccessibilityBridge.setTestOverride { accessibilityReturnSuccess }
        try {
            val validator = ToolPermissionValidator(
                notificationAuthChecker = { notificationAuthorized },
                accessibilityAuthChecker = { accessibilityAuthorized }
            )
            val testLauncher: (Context, Intent) -> Boolean = { _, intent ->
                launchedIntents.add(intent)
                true
            }
            val registry = ToolRegistry(
                tools = listOf(
                    LaunchInstalledAppTool(
                        appDiscoverer = {
                            listOf(LaunchableAppEntry("com.android.settings", "Settings", "SettingsActivity"))
                        },
                        intentLauncher = testLauncher
                    ),
                    OpenAppScreenTool(intentLauncher = testLauncher),
                    GoHomeScreenTool(intentLauncher = testLauncher, accessibilityRunner = { false }),
                    OpenAndroidSettingsTool(intentLauncher = testLauncher),
                    ReadBatteryStatusTool(sysTools),
                    ReadDeviceInfoTool(sysTools),
                    AdjustMediaVolumeTool(),
                    ControlMediaPlaybackTool(),
                    SearchContactsTool(
                        contactQueryProvider = { _, query ->
                            listOf(ContactMatch("Karim Support", "+213555010203")).filter {
                                it.displayName.contains(query, ignoreCase = true)
                            }
                        }
                    ),
                    InitiatePhoneCallTool(intentLauncher = testLauncher),
                    ComposeMessageTool(intentLauncher = testLauncher),
                    ReadNotificationsTool(),
                    AccessibilityActionTool()
                ),
                permissionValidator = validator
            )
            val baseSettings = JarvisSettings()

            // 1. Security: Shell metacharacters & unregistered tools strictly rejected
            val shellAttack = registry.dispatch(
                context,
                ToolCallRequest("open_android_settings", mapOf("page" to "wifi; rm -rf /")),
                baseSettings
            )
            assertEquals(ToolExecutionStatus.VALIDATION_ERROR, shellAttack.status)
            assertTrue(shellAttack.summary.contains("Blocked unsafe characters"))

            val unknownTool = registry.dispatch(
                context,
                ToolCallRequest("run_shell", mapOf("cmd" to "id")),
                baseSettings
            )
            assertEquals(ToolExecutionStatus.VALIDATION_ERROR, unknownTool.status)

            // 2. App control & settings tools
            val launchRes = registry.dispatch(
                context,
                ToolCallRequest("launch_installed_app", mapOf("app_query" to "Settings")),
                baseSettings
            )
            assertEquals(ToolExecutionStatus.SUCCESS, launchRes.status)
            assertEquals("com.android.settings", launchRes.verifiedFacts["packageName"])

            val screenRes = registry.dispatch(
                context,
                ToolCallRequest("open_app_screen", mapOf("screen" to "camera")),
                baseSettings
            )
            assertEquals(ToolExecutionStatus.SUCCESS, screenRes.status)

            val homeRes = registry.dispatch(
                context,
                ToolCallRequest("go_home_screen"),
                baseSettings
            )
            assertEquals(ToolExecutionStatus.SUCCESS, homeRes.status)

            val wifiRes = registry.dispatch(
                context,
                ToolCallRequest("open_android_settings", mapOf("page" to "wifi")),
                baseSettings
            )
            assertEquals(ToolExecutionStatus.SUCCESS, wifiRes.status)
            assertEquals(android.provider.Settings.ACTION_WIFI_SETTINGS, launchedIntents.last().action)

            val btRes = registry.dispatch(
                context,
                ToolCallRequest("open_android_settings", mapOf("page" to "bluetooth")),
                baseSettings
            )
            assertEquals(ToolExecutionStatus.SUCCESS, btRes.status)
            assertEquals(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS, launchedIntents.last().action)

            // 3. Device telemetry, battery, volume, media playback
            val batteryRes = registry.dispatch(context, ToolCallRequest("read_battery_status"), baseSettings)
            assertEquals(ToolExecutionStatus.SUCCESS, batteryRes.status)
            assertTrue(batteryRes.verifiedFacts.containsKey("batteryPercent"))

            val deviceRes = registry.dispatch(context, ToolCallRequest("read_device_info"), baseSettings)
            assertEquals(ToolExecutionStatus.SUCCESS, deviceRes.status)
            assertTrue(deviceRes.summary.contains("DEVICE TELEMETRY SNAPSHOT"))

            val volRes = registry.dispatch(
                context,
                ToolCallRequest("adjust_media_volume", mapOf("action" to "set", "percent" to "60")),
                baseSettings
            )
            assertEquals(ToolExecutionStatus.SUCCESS, volRes.status)
            assertTrue(volRes.verifiedFacts.containsKey("currentVolume"))

            val mediaRes = registry.dispatch(
                context,
                ToolCallRequest("control_media_playback", mapOf("command" to "play_pause")),
                baseSettings
            )
            assertEquals(ToolExecutionStatus.SUCCESS, mediaRes.status)

            // 4. Communication: READ_CONTACTS permission enforcement + dial/SMS confirmation gate
            val appShadow = shadowOf(ApplicationProvider.getApplicationContext<Application>())
            appShadow.denyPermissions(Manifest.permission.READ_CONTACTS)
            val contactsDenied = registry.dispatch(
                context,
                ToolCallRequest("search_contacts", mapOf("query" to "Karim")),
                baseSettings
            )
            assertEquals(ToolExecutionStatus.PERMISSION_DENIED, contactsDenied.status)
            assertEquals(Manifest.permission.READ_CONTACTS, contactsDenied.missingRuntimePermission)

            appShadow.grantPermissions(Manifest.permission.READ_CONTACTS)
            val contactsGranted = registry.dispatch(
                context,
                ToolCallRequest("search_contacts", mapOf("query" to "Karim")),
                baseSettings
            )
            assertEquals(ToolExecutionStatus.SUCCESS, contactsGranted.status)
            assertEquals("1", contactsGranted.verifiedFacts["matchCount"])

            val dialNeedsConfirm = registry.dispatch(
                context,
                ToolCallRequest("initiate_phone_call", mapOf("phone_number" to "+213555010203")),
                baseSettings,
                userConfirmed = false
            )
            assertEquals(ToolExecutionStatus.CONFIRMATION_REQUIRED, dialNeedsConfirm.status)

            val dialConfirmed = registry.dispatch(
                context,
                ToolCallRequest("initiate_phone_call", mapOf("phone_number" to "+213555010203")),
                baseSettings,
                userConfirmed = true
            )
            assertEquals(ToolExecutionStatus.SUCCESS, dialConfirmed.status)
            assertEquals(Intent.ACTION_DIAL, launchedIntents.last().action)

            val smsNeedsConfirm = registry.dispatch(
                context,
                ToolCallRequest("compose_message", mapOf("recipient" to "+213555010203", "message_body" to "Arriving in 10 min")),
                baseSettings,
                userConfirmed = false
            )
            assertEquals(ToolExecutionStatus.CONFIRMATION_REQUIRED, smsNeedsConfirm.status)

            val smsConfirmed = registry.dispatch(
                context,
                ToolCallRequest("compose_message", mapOf("recipient" to "+213555010203", "message_body" to "Arriving in 10 min")),
                baseSettings,
                userConfirmed = true
            )
            assertEquals(ToolExecutionStatus.SUCCESS, smsConfirmed.status)
            assertEquals(Intent.ACTION_SENDTO, launchedIntents.last().action)
            assertEquals("Arriving in 10 min", launchedIntents.last().getStringExtra("sms_body"))

            // 5. Notification privacy controls & listener authorization
            NotificationBridge.clearBuffer()
            NotificationBridge.syncPrivacyControls(enabled = false, maskEnabled = true)
            NotificationBridge.recordNotification("k1", "com.whatsapp", "WhatsApp", "Msg", "OTP 482910", 100L, context.packageName)
            assertTrue(NotificationBridge.notifications.value.isEmpty())

            val notifOptOut = registry.dispatch(context, ToolCallRequest("read_notifications"), baseSettings)
            assertEquals(ToolExecutionStatus.PERMISSION_DENIED, notifOptOut.status)

            val notifSettingsOn = baseSettings.copy(notificationReaderEnabled = true, notificationPrivacyMaskEnabled = true)
            val notifOsDenied = registry.dispatch(context, ToolCallRequest("read_notifications"), notifSettingsOn)
            assertEquals(ToolExecutionStatus.PERMISSION_DENIED, notifOsDenied.status)
            assertEquals(SpecialAccessType.NOTIFICATION_LISTENER, notifOsDenied.missingSpecialAccess)

            notificationAuthorized = true
            NotificationBridge.syncPrivacyControls(enabled = true, maskEnabled = true)
            NotificationBridge.recordNotification("k2", "com.whatsapp", "WhatsApp", "Bank Alert", "Code 482910 valid now", 200L, context.packageName)
            val notifRead = registry.dispatch(context, ToolCallRequest("read_notifications", mapOf("limit" to "5")), notifSettingsOn)
            assertEquals(ToolExecutionStatus.SUCCESS, notifRead.status)
            assertFalse(notifRead.summary.contains("482910"))
            assertTrue(notifRead.summary.contains("Masked"))
            NotificationBridge.clearBuffer()

            // 6. Accessibility authorization, confirmation gate & honest failure check
            val a11yOptOut = registry.dispatch(
                context,
                ToolCallRequest("accessibility_global_action", mapOf("action" to "recents")),
                baseSettings
            )
            assertEquals(ToolExecutionStatus.PERMISSION_DENIED, a11yOptOut.status)

            val a11ySettingsOn = baseSettings.copy(accessibilityAutomationEnabled = true)
            val a11yOsDenied = registry.dispatch(
                context,
                ToolCallRequest("accessibility_global_action", mapOf("action" to "recents")),
                a11ySettingsOn
            )
            assertEquals(ToolExecutionStatus.PERMISSION_DENIED, a11yOsDenied.status)
            assertEquals(SpecialAccessType.ACCESSIBILITY_SERVICE, a11yOsDenied.missingSpecialAccess)

            accessibilityAuthorized = true
            val a11yConfirm = registry.dispatch(
                context,
                ToolCallRequest("accessibility_global_action", mapOf("action" to "recents")),
                a11ySettingsOn,
                userConfirmed = false
            )
            assertEquals(ToolExecutionStatus.CONFIRMATION_REQUIRED, a11yConfirm.status)

            val a11ySuccess = registry.dispatch(
                context,
                ToolCallRequest("accessibility_global_action", mapOf("action" to "recents")),
                a11ySettingsOn,
                userConfirmed = true
            )
            assertEquals(ToolExecutionStatus.SUCCESS, a11ySuccess.status)

            accessibilityReturnSuccess = false
            val a11yFailed = registry.dispatch(
                context,
                ToolCallRequest("accessibility_global_action", mapOf("action" to "back")),
                a11ySettingsOn,
                userConfirmed = true
            )
            assertEquals(ToolExecutionStatus.FAILED, a11yFailed.status)

            // 7. AI & direct tool parsing + cancellation
            val parsedModel = ToolRegistry.parseModelToolCall(
                "Checking now. [TOOL_CALL]{\"tool\":\"read_battery_status\",\"args\":{}}[/TOOL_CALL]"
            )
            assertEquals("read_battery_status", parsedModel?.toolName)

            val parsedDirect = ToolRegistry.parseDirectToolCommand(
                "/tool compose_message recipient=\"+213555010203\" message_body=\"Hello JARVIS\""
            )
            assertEquals("compose_message", parsedDirect?.toolName)
            assertEquals("+213555010203", parsedDirect?.arguments?.get("recipient"))
            assertEquals("Hello JARVIS", parsedDirect?.arguments?.get("message_body"))
        } finally {
            AccessibilityBridge.setTestOverride(null)
            NotificationBridge.clearBuffer()
        }
    }

    @Test
    fun `chat viewmodel executes direct tool commands gates confirmation and handles cancellation`() = runBlocking {
        val convRepo = ConversationRepository(db.conversationDao())
        val modelManager = RoomModelManager(db.modelDao()).apply { ensureSeeded() }
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tmpFolder.root, "phase5_chat_vm.preferences_pb") }
        )
        val settingsRepo = SettingsRepository(dataStore)
        val agentCore = Phase2AgentCore(
            aiEngine = ControllableTestAiEngine(),
            modelManager = modelManager,
            androidTools = AndroidSystemTools(context),
            cloudProviderRegistry = Phase1CloudProviderRegistry(),
            wakeWordEngine = ModularWakeWordEngine(),
            localMemory = RoomLocalMemory(db.memoryDao()),
            appContext = context
        )
        val vm = ChatViewModel(
            conversationRepository = convRepo,
            modelManager = modelManager,
            agentCore = agentCore,
            aiEngine = ControllableTestAiEngine(),
            settingsRepository = settingsRepo
        )
        val sub = dataStoreScope.launch(Dispatchers.Unconfined) { vm.uiState.collect {} }

        var convId: String? = null
        repeat(30) {
            shadowOf(Looper.getMainLooper()).idle()
            convId = convRepo.observeConversations().first().firstOrNull()?.id
            if (convId != null) return@repeat
            Thread.sleep(15)
        }
        assertNotNull(convId)

        // Direct non-sensitive tool runs and records TOOL_SNAPSHOT
        vm.sendMessage("/tool read_battery_status")
        repeat(40) {
            shadowOf(Looper.getMainLooper()).idle()
            if (convRepo.getMessagesOnce(convId!!).any { it.role == MessageRole.TOOL_SNAPSHOT }) return@repeat
            Thread.sleep(15)
        }
        assertTrue(convRepo.getMessagesOnce(convId!!).any { it.role == MessageRole.TOOL_SNAPSHOT && it.content.contains("Battery:") })

        // Sensitive tool requires confirmation and supports cancellation
        vm.sendMessage("/tool initiate_phone_call phone_number=+213555010203")
        repeat(40) {
            shadowOf(Looper.getMainLooper()).idle()
            if (vm.uiState.value.pendingToolConfirmation != null) return@repeat
            Thread.sleep(15)
        }
        assertEquals("initiate_phone_call", vm.uiState.value.pendingToolConfirmation?.request?.toolName)

        vm.cancelPendingTool()
        repeat(30) {
            shadowOf(Looper.getMainLooper()).idle()
            if (convRepo.getMessagesOnce(convId!!).any { it.content.contains("cancelled by user") }) return@repeat
            Thread.sleep(15)
        }
        assertNull(vm.uiState.value.pendingToolConfirmation)
        assertTrue(convRepo.getMessagesOnce(convId!!).any { it.content.contains("cancelled by user") })

        sub.cancel()
        dataStoreScope.cancel()
    }

    @Test
    fun `phase 6 local memory supports approved preferences summaries search edit delete and blocks sensitive secrets without consent`() = runBlocking {
        val memory = RoomLocalMemory(db.memoryDao())

        // 1. Blocks sensitive API keys & passwords without explicit consent
        val blockedKey = memory.saveMemory(
            kind = MemoryKind.FACT,
            category = "Credentials",
            content = "My API key is sk-proj-abcdef1234567890xyz",
            containsSensitiveConsent = false
        )
        assertTrue(blockedKey.isFailure)
        assertTrue(blockedKey.exceptionOrNull() is SecurityException)

        val blockedPassword = memory.saveMemory(
            kind = MemoryKind.FACT,
            category = "Auth",
            content = "password=superSecret99",
            containsSensitiveConsent = false
        )
        assertTrue(blockedPassword.isFailure)

        // 2. Allows approved preferences & consented items
        val pref = memory.saveMemory(
            kind = MemoryKind.PREFERENCE,
            category = "Style",
            content = "Prefer concise Kotlin Compose answers tailored for Poco X7 Pro.",
            userApproved = true,
            containsSensitiveConsent = false
        ).getOrThrow()

        val fact = memory.saveMemory(
            kind = MemoryKind.FACT,
            category = "Hardware",
            content = "Device runs MediaTek Dimensity 8400-Ultra with 12GB RAM.",
            userApproved = true,
            containsSensitiveConsent = false
        ).getOrThrow()

        // 3. Search & context retrieval
        val searchHits = memory.searchMemories("Dimensity")
        assertEquals(1, searchHits.size)
        assertEquals(fact.id, searchHits[0].id)

        val contextHits = memory.retrieveRelevantContext("Explain Kotlin Compose performance on Dimensity", maxItems = 2)
        assertEquals(2, contextHits.size)
        assertEquals(pref.id, contextHits.first { it.kind == MemoryKind.PREFERENCE }.id)

        // 4. Edit & delete
        val editBlocked = memory.updateMemory(pref.id, "Style", "hf_abcdef1234567890", containsSensitiveConsent = false)
        assertTrue(editBlocked.isFailure)

        val editOk = memory.updateMemory(pref.id, "Style", "Prefer concise Jetpack Compose answers.", containsSensitiveConsent = false)
        assertTrue(editOk.isSuccess)
        assertTrue(memory.searchMemories("Jetpack").any { it.id == pref.id })

        memory.deleteMemory(fact.id)
        assertTrue(memory.searchMemories("Dimensity").isEmpty())

        // 5. Conversation summary generation & sensitive auto-filter
        val summary = memory.summarizeAndStoreConversation(
            conversationId = "conv-sum-1",
            title = "Architecture Review",
            messages = listOf(
                MessageEntity("m1", "conv-sum-1", MessageRole.USER, "Check thermal headroom", "Operator", 10L),
                MessageEntity("m2", "conv-sum-1", MessageRole.ASSISTANT, "Device temperature nominal at 34C.", "Gemma 3 1B", 20L)
            ),
            userApproved = true
        )
        assertNotNull(summary)
        assertEquals(MemoryKind.SUMMARY, summary?.kind)
        assertTrue(summary?.content?.contains("Architecture Review") == true)
    }

    @Test
    fun `phase 6 intent router switches local cloud hybrid respects consent and decomposes multi step tasks`() {
        val router = IntentRouter()
        val offlineSettings = JarvisSettings(
            routingMode = AgentRoutingMode.LOCAL_ONLY,
            cloudUploadConsentGranted = false,
            cloudApiKey = "",
            webResearchEnabled = false
        )

        val localWebDecision = router.routePrompt(
            prompt = "search the web for Android 16 release date",
            settings = offlineSettings,
            isLocalModelReady = true,
            isNetworkConnected = true
        )
        assertFalse(localWebDecision.useCloudModel)
        assertFalse(localWebDecision.needsWebResearch)
        assertTrue(localWebDecision.privacyBlockedReason?.contains("cannot access the internet") == true)

        // Cloud requested without consent -> strictly blocked
        val blockedConsent = router.routePrompt(
            prompt = "@cloud architect a distributed consensus protocol",
            settings = offlineSettings.copy(routingMode = AgentRoutingMode.AUTO_HYBRID),
            isLocalModelReady = true,
            isNetworkConnected = true
        )
        assertTrue(blockedConsent.useCloudModel)
        assertTrue(blockedConsent.privacyBlockedReason?.contains("consent is disabled") == true)

        // Cloud consented + key present -> allowed for CLOUD_ONLY or complex prompts
        val consentedSettings = JarvisSettings(
            routingMode = AgentRoutingMode.AUTO_HYBRID,
            cloudUploadConsentGranted = true,
            cloudApiKey = "sk-live-user-key-9999",
            webResearchEnabled = true
        )
        val simpleLocal = router.routePrompt("Hello JARVIS", consentedSettings, isLocalModelReady = true, isNetworkConnected = true)
        assertFalse(simpleLocal.useCloudModel)
        assertEquals(ExecutionRoute.LOCAL_LITERT_LM, simpleLocal.route)

        val complexCloud = router.routePrompt(
            "Compare and analyze detailed microservice tradeoff strategy and optimize database architecture",
            consentedSettings,
            isLocalModelReady = true,
            isNetworkConnected = true
        )
        assertTrue(complexCloud.useCloudModel)
        assertEquals(ExecutionRoute.CLOUD_API, complexCloud.route)

        // Explicit @local overrides CLOUD_ONLY
        val forcedLocalOverride = router.routePrompt(
            "@local summarize my notes",
            consentedSettings.copy(routingMode = AgentRoutingMode.CLOUD_ONLY),
            isLocalModelReady = true,
            isNetworkConnected = true
        )
        assertFalse(forcedLocalOverride.useCloudModel)

        // Multi-step decomposition
        val multiToolDecision = router.routePrompt(
            "Check battery status and open wifi settings",
            offlineSettings,
            isLocalModelReady = true,
            isNetworkConnected = false
        )
        assertEquals(2, multiToolDecision.detectedTools.size)
        val steps = router.decomposeTasks("Check battery status and open wifi settings", multiToolDecision)
        assertEquals(PlanStepType.MEMORY_LOOKUP, steps[0].type)
        assertEquals("Execute read_battery_status", steps[1].title)
        assertEquals("Execute open_android_settings", steps[2].title)
    }

    @Test
    fun `phase 6 cloud ai client streams sse tracks usage and handles http errors cleanly`() = runBlocking {
        val responseMode = AtomicInteger(0) // 0=SSE OK, 1=HTTP 500
        val server = ServerSocket(0)
        val port = server.localPort
        val serverThread = Thread {
            try {
                while (!server.isClosed) {
                    val socket = server.accept()
                    val reader = socket.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val out = socket.getOutputStream()
                    if (responseMode.get() == 0) {
                        val sseBody = buildString {
                            append("data: {\"choices\":[{\"delta\":{\"content\":\"Hybrid \"}}]}\n\n")
                            append("data: {\"choices\":[{\"delta\":{\"content\":\"Cloud Ready\"}}]}\n\n")
                            append("data: [DONE]\n\n")
                        }
                        val header = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n"
                        out.write(header.toByteArray())
                        out.write(sseBody.toByteArray())
                    } else {
                        val errJson = "{\"error\":{\"message\":\"Upstream gateway unavailable\"}}"
                        val header = "HTTP/1.1 500 Internal Server Error\r\nContent-Type: application/json\r\nContent-Length: ${errJson.toByteArray().size}\r\nConnection: close\r\n\r\n"
                        out.write(header.toByteArray())
                        out.write(errJson.toByteArray())
                    }
                    out.flush()
                    socket.close()
                }
            } catch (_: Exception) {
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        try {
            val client = OkHttpCloudAiClient()
            val config = CloudInferenceConfig(
                providerKind = CloudProviderKind.OPENAI_COMPATIBLE,
                endpointUrl = "http://127.0.0.1:$port/v1/chat/completions",
                modelId = "gpt-4o-mini",
                apiKey = "user-test-key",
                timeoutSeconds = 10
            )

            // 1. Successful SSE stream
            val events = mutableListOf<AiStreamEvent>()
            client.streamChat(config, "System", "Hello cloud", emptyList()).collect { events.add(it) }
            val completed = events.filterIsInstance<AiStreamEvent.Completed>().firstOrNull()
            assertNotNull(completed)
            assertEquals("Hybrid Cloud Ready", completed?.fullText)
            assertEquals(1, client.usageStats.value.totalRequests)
            assertEquals(1, client.usageStats.value.successfulRequests)
            assertEquals(0, client.usageStats.value.failedRequests)

            // 2. HTTP 500 error updates failed stats honestly
            responseMode.set(1)
            val errEvents = mutableListOf<AiStreamEvent>()
            client.streamChat(config, "System", "Retry", emptyList()).collect { errEvents.add(it) }
            val err = errEvents.filterIsInstance<AiStreamEvent.Error>().firstOrNull()
            assertNotNull(err)
            assertTrue(err!!.message.contains("Cloud HTTP 500"))
            assertEquals(2, client.usageStats.value.totalRequests)
            assertEquals(1, client.usageStats.value.failedRequests)
            assertNotNull(client.usageStats.value.lastErrorMessage)
        } finally {
            server.close()
        }
    }

    @Test
    fun `phase 6 hybrid agent handles offline online sse multi step tools and error recovery end to end`() = runBlocking {
        val convRepo = ConversationRepository(db.conversationDao())
        val modelManager = RoomModelManager(db.modelDao()).apply { ensureSeeded() }
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tmpFolder.root, "phase6_hybrid_vm.preferences_pb") }
        )
        val settingsRepo = SettingsRepository(dataStore)
        val localMemory = RoomLocalMemory(db.memoryDao())
        localMemory.saveMemory(
            kind = MemoryKind.PREFERENCE,
            category = "Platform",
            content = "Always mention Poco X7 Pro optimization context."
        )

        modelManager.updateArtifactState(
            id = RoomModelManager.GEMMA_3_1B_ID,
            localFilePath = File(tmpFolder.root, "gemma3_p6.litertlm").apply { writeBytes(ByteArray(1024)) }.absolutePath,
            artifactState = ArtifactState.READY,
            status = ModelStatus.READY
        )

        val serverMode = AtomicInteger(0) // 0=SSE 200, 1=503 fail
        val server = ServerSocket(0)
        val port = server.localPort
        val serverThread = Thread {
            try {
                while (!server.isClosed) {
                    val socket = server.accept()
                    val reader = socket.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val out = socket.getOutputStream()
                    if (serverMode.get() == 0) {
                        val sseBody = "data: {\"choices\":[{\"delta\":{\"content\":\"Cloud SSE Verification OK\"}}]}\n\ndata: [DONE]\n\n"
                        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".toByteArray())
                        out.write(sseBody.toByteArray())
                    } else {
                        val errJson = "{\"error\":{\"message\":\"Gateway down\"}}"
                        out.write("HTTP/1.1 503 Service Unavailable\r\nContent-Type: application/json\r\nContent-Length: ${errJson.toByteArray().size}\r\nConnection: close\r\n\r\n".toByteArray())
                        out.write(errJson.toByteArray())
                    }
                    out.flush()
                    socket.close()
                }
            } catch (_: Exception) {
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        val fakeAiEngine = ControllableTestAiEngine()
        val cloudClient = OkHttpCloudAiClient()
        val webService = OkHttpWebResearchService()
        val delegateTools = AndroidSystemTools(context)
        var networkOnline = true
        val testAndroidTools = object : AndroidTools by delegateTools {
            override suspend fun readDeviceTelemetry(): DeviceTelemetry =
                delegateTools.readDeviceTelemetry().copy(isNetworkConnected = networkOnline)
        }
        val agentCore = Phase2AgentCore(
            aiEngine = fakeAiEngine,
            modelManager = modelManager,
            androidTools = testAndroidTools,
            cloudProviderRegistry = Phase1CloudProviderRegistry(),
            wakeWordEngine = ModularWakeWordEngine(),
            localMemory = localMemory,
            appContext = context,
            cloudAiClient = cloudClient,
            webResearchService = webService,
            intentRouter = IntentRouter(),
            agentExecutionDao = db.agentExecutionDao()
        )
        val vm = ChatViewModel(
            conversationRepository = convRepo,
            modelManager = modelManager,
            agentCore = agentCore,
            aiEngine = fakeAiEngine,
            settingsRepository = settingsRepo,
            modelBenchmarkDao = db.modelBenchmarkDao(),
            localMemory = localMemory
        )
        val sub = dataStoreScope.launch(Dispatchers.Unconfined) { vm.uiState.collect {} }

        try {
            var convId: String? = null
            repeat(30) {
                shadowOf(Looper.getMainLooper()).idle()
                convId = convRepo.observeConversations().first().firstOrNull()?.id
                if (convId != null) return@repeat
                Thread.sleep(15)
            }
            assertNotNull(convId)

            // 1. Offline conversation (LOCAL_ONLY, 0 cloud calls)
            vm.setRoutingMode(AgentRoutingMode.LOCAL_ONLY)
            repeat(20) {
                shadowOf(Looper.getMainLooper()).idle()
                if (vm.uiState.value.settings.routingMode == AgentRoutingMode.LOCAL_ONLY) return@repeat
                Thread.sleep(10)
            }
            vm.sendMessage("Explain Poco X7 Pro memory budget")
            repeat(50) {
                shadowOf(Looper.getMainLooper()).idle()
                if (convRepo.getMessagesOnce(convId!!).any { it.role == MessageRole.ASSISTANT }) return@repeat
                Thread.sleep(15)
            }
            assertEquals(0, cloudClient.usageStats.value.totalRequests)
            assertTrue(convRepo.getMessagesOnce(convId!!).any { it.content.contains("Dimensity") })

            // 2. Multi-step natural language tool chain
            val countBeforeTools = convRepo.getMessagesOnce(convId!!).size
            vm.sendMessage("Check battery status and check device info")
            repeat(50) {
                shadowOf(Looper.getMainLooper()).idle()
                val msgs = convRepo.getMessagesOnce(convId!!)
                if (msgs.size >= countBeforeTools + 3) return@repeat
                Thread.sleep(15)
            }
            val afterTools = convRepo.getMessagesOnce(convId!!)
            assertTrue(afterTools.count { it.role == MessageRole.TOOL_SNAPSHOT } >= 2)
            assertTrue(db.agentExecutionDao().observeExecutions().first().isNotEmpty())

            // 3. Online Cloud SSE conversation
            settingsRepo.setCloudUploadConsentGranted(true)
            settingsRepo.setCloudProviderKind(CloudProviderKind.OPENAI_COMPATIBLE)
            settingsRepo.setCloudEndpointUrl("http://127.0.0.1:$port/v1/chat/completions")
            settingsRepo.setCloudModelId("gpt-4o-mini")
            settingsRepo.setCloudApiKey("test-cloud-key")
            vm.setRoutingMode(AgentRoutingMode.CLOUD_ONLY)
            repeat(30) {
                shadowOf(Looper.getMainLooper()).idle()
                val s = vm.uiState.value.settings
                if (s.routingMode == AgentRoutingMode.CLOUD_ONLY && s.cloudUploadConsentGranted && s.cloudApiKey == "test-cloud-key") {
                    return@repeat
                }
                Thread.sleep(15)
            }
            vm.sendMessage("Hello cloud")
            repeat(50) {
                shadowOf(Looper.getMainLooper()).idle()
                if (convRepo.getMessagesOnce(convId!!).any { it.content.contains("Cloud SSE Verification OK") }) return@repeat
                Thread.sleep(15)
            }
            assertTrue(convRepo.getMessagesOnce(convId!!).any { it.content.contains("Cloud SSE Verification OK") })
            assertEquals(1, cloudClient.usageStats.value.successfulRequests)

            // 4. Failed network + Web Research HTTP 503 recovers gracefully onto offline LiteRT-LM in AUTO_HYBRID
            serverMode.set(1)
            settingsRepo.setWebResearchEnabled(true)
            settingsRepo.setWebSearchProvider(WebSearchProviderKind.CUSTOM_JSON)
            settingsRepo.setWebSearchCustomUrl("http://127.0.0.1:$port/search")
            vm.setRoutingMode(AgentRoutingMode.AUTO_HYBRID)
            repeat(30) {
                shadowOf(Looper.getMainLooper()).idle()
                val s = vm.uiState.value.settings
                if (s.routingMode == AgentRoutingMode.AUTO_HYBRID && s.webResearchEnabled) return@repeat
                Thread.sleep(15)
            }
            val beforeRecoveryCount = convRepo.getMessagesOnce(convId!!).size
            vm.sendMessage("Search the web for Kotlin 2.2 benchmark details")
            repeat(80) {
                shadowOf(Looper.getMainLooper()).idle()
                val added = convRepo.getMessagesOnce(convId!!).drop(beforeRecoveryCount)
                if (added.any { it.role == MessageRole.ASSISTANT } && !vm.uiState.value.isGenerating) return@repeat
                Thread.sleep(20)
            }
            val recoveryMsgs = convRepo.getMessagesOnce(convId!!).drop(beforeRecoveryCount)
            assertTrue(recoveryMsgs.any { it.role == MessageRole.SYSTEM_NOTICE && it.content.contains("Web lookup failed") })
            assertTrue(recoveryMsgs.any { it.role == MessageRole.SYSTEM_NOTICE && it.content.contains("Falling back to offline LiteRT-LM") })
            assertTrue(recoveryMsgs.any { it.role == MessageRole.ASSISTANT && it.content.contains("Dimensity") })
        } finally {
            server.close()
            sub.cancel()
            dataStoreScope.cancel()
        }
    }

    @Test
    fun `phase 6 migration 3 to 4 upgrades memory_notes and creates agent_executions table`() {
        val dbFile = File(tmpFolder.root, "migration_v3_v4.db")
        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE memory_notes (
                                id TEXT NOT NULL PRIMARY KEY,
                                category TEXT NOT NULL,
                                content TEXT NOT NULL,
                                createdAt INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL("INSERT INTO memory_notes (id, category, content, createdAt) VALUES ('n1', 'Device', 'Poco X7 Pro', 1000)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val sqlite = helper.writableDatabase
        JarvisDatabase.MIGRATION_3_4.migrate(sqlite)

        sqlite.query("SELECT id, kind, isUserApproved, updatedAt FROM memory_notes WHERE id = 'n1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("FACT", c.getString(1))
            assertEquals(1, c.getInt(2))
            assertEquals(1000L, c.getLong(3))
        }
        sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='agent_executions'").use { c ->
            assertTrue(c.moveToFirst())
        }
        helper.close()
    }

    @Test
    fun `phase 7 thermal and memory policy enforces low ram guard and thermal token throttling`() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val lowMemInfo = ActivityManager.MemoryInfo().apply {
            totalMem = 12L * 1024L * 1024L * 1024L
            availMem = 256L * 1024L * 1024L
            lowMemory = true
        }
        shadowOf(am).setMemoryInfo(lowMemInfo)

        val blockedReport = ThermalAndMemoryPolicy.assessMemoryHeadroom(context, 612L * 1024L * 1024L)
        assertFalse(blockedReport.canLoadModel)
        assertTrue(blockedReport.isLowMemory)
        assertNotNull(blockedReport.reason)

        val healthyInfo = ActivityManager.MemoryInfo().apply {
            totalMem = 12L * 1024L * 1024L * 1024L
            availMem = 4096L * 1024L * 1024L
            lowMemory = false
        }
        shadowOf(am).setMemoryInfo(healthyInfo)

        val healthyReport = ThermalAndMemoryPolicy.assessMemoryHeadroom(context, 612L * 1024L * 1024L)
        assertTrue(healthyReport.canLoadModel)
        assertFalse(healthyReport.isLowMemory)

        val baseSettings = JarvisSettings(
            inferenceBackend = InferenceBackendPreference.AUTO_GPU_FALLBACK,
            maxOutputTokens = 512
        )
        val moderateAdapt = ThermalAndMemoryPolicy.applyThermalAdaptation(
            settings = baseSettings,
            thermalCode = PowerManager.THERMAL_STATUS_MODERATE,
            thermalLabel = "Moderate"
        )
        assertTrue(moderateAdapt.wasThrottled)
        assertEquals(384, moderateAdapt.effectiveSettings.maxOutputTokens)

        val criticalAdapt = ThermalAndMemoryPolicy.applyThermalAdaptation(
            settings = baseSettings,
            thermalCode = PowerManager.THERMAL_STATUS_CRITICAL,
            thermalLabel = "Critical"
        )
        assertTrue(criticalAdapt.wasThrottled)
        assertEquals(256, criticalAdapt.effectiveSettings.maxOutputTokens)
        assertEquals(InferenceBackendPreference.CPU_ONLY, criticalAdapt.effectiveSettings.inferenceBackend)
    }

    @Test
    fun `phase 7 migration 4 to 5 upgrades model_benchmarks with init memory size thermal and battery columns`() {
        val dbFile = File(tmpFolder.root, "migration_v4_v5.db")
        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE model_benchmarks (
                                id TEXT NOT NULL PRIMARY KEY,
                                modelId TEXT NOT NULL,
                                modelName TEXT NOT NULL,
                                backendUsed TEXT NOT NULL,
                                ttftMs INTEGER NOT NULL,
                                tokensPerSecond REAL NOT NULL,
                                outputTokens INTEGER NOT NULL,
                                prefillTokens INTEGER NOT NULL,
                                nativeHeapMb REAL NOT NULL,
                                timestamp INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO model_benchmarks
                            (id, modelId, modelName, backendUsed, ttftMs, tokensPerSecond, outputTokens, prefillTokens, nativeHeapMb, timestamp)
                            VALUES ('b1', 'slot_gemma3_1b_int4', 'Gemma 3 1B', 'GPU (OpenCL)', 120, 31.5, 48, 24, 412.5, 5000)
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val sqlite = helper.writableDatabase
        JarvisDatabase.MIGRATION_4_5.migrate(sqlite)

        sqlite.query(
            "SELECT initTimeMs, peakMemoryMb, modelSizeBytes, thermalStatus, batteryPercent, batteryTempCelsius FROM model_benchmarks WHERE id = 'b1'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0L, c.getLong(0))
            assertEquals(412.5f, c.getFloat(1), 0.1f)
            assertEquals(0L, c.getLong(2))
            assertEquals("Nominal", c.getString(3))
            assertEquals(-1, c.getInt(4))
            assertEquals(0f, c.getFloat(5), 0.01f)
        }
        helper.close()
    }

    @Test
    fun `phase 7 litertlm engine trimMemory handles background and critical pressure cleanly`() = runTest {
        val engine = LiteRtLmAiEngine(context)
        engine.trimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        engine.trimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertTrue(engine.loadState.value is EngineLoadState.Unloaded)
        engine.release()
    }

    @Test
    fun `phase 7 manifest disables allowBackup for security hardening`() {
        val appInfo = context.applicationInfo
        assertEquals(0, appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
