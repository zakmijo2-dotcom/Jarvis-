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
import com.example.core.wakeword.Phase1WakeWordEngine
import com.example.core.wakeword.WakeWordState
import com.example.data.local.db.JarvisDatabase
import com.example.data.local.db.MessageEntity
import com.example.data.local.db.MessageRole
import com.example.data.local.db.ModelEntryEntity
import com.example.data.repository.ConversationRepository
import com.example.data.repository.InferenceBackendPreference
import com.example.data.repository.JarvisSettings
import com.example.data.repository.SettingsRepository
import com.example.ui.screens.chat.ChatViewModel
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
        val wakeWord = Phase1WakeWordEngine().apply { syncStandbyPreference(true) }
        val agentCore = Phase2AgentCore(
            aiEngine = fakeEngine,
            modelManager = modelManager,
            androidTools = AndroidSystemTools(context),
            cloudProviderRegistry = Phase1CloudProviderRegistry(),
            wakeWordEngine = wakeWord,
            localMemory = RoomLocalMemory(db.memoryDao())
        )

        assertEquals(WakeWordState.STANDBY_CONFIGURED_PENDING_ENGINE, wakeWord.state.value)
        val snapshot = agentCore.runSystemTelemetrySnapshot()
        assertTrue(snapshot.content.contains("DEVICE TELEMETRY SNAPSHOT"))

        val initialAudit = agentCore.inspectEngineReadiness()
        assertTrue(initialAudit.content.contains("JARVIS PHASE 2 ENGINE READINESS"))

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
}
