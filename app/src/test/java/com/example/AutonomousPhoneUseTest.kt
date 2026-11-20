package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.core.agent.AgentRoutingMode
import com.example.core.agent.HybridAgentEvent
import com.example.core.agent.IntentRouter
import com.example.core.agent.Phase2AgentCore
import com.example.core.agent.PlanStepStatus
import com.example.core.agent.PlanStepType
import com.example.core.agent.ToolRegistryFactory
import com.example.core.ai.AiEngine
import com.example.core.ai.AiStreamEvent
import com.example.core.ai.EngineLoadState
import com.example.core.ai.GenerationBenchmark
import com.example.core.cloud.CloudAiClient
import com.example.core.cloud.CloudImageAttachment
import com.example.core.cloud.CloudInferenceConfig
import com.example.core.cloud.CloudProviderKind
import com.example.core.cloud.CloudUsageStats
import com.example.core.cloud.Phase1CloudProviderRegistry
import com.example.core.mcp.McpClient
import com.example.core.mcp.McpManager
import com.example.core.memory.RoomLocalMemory
import com.example.core.model.ArtifactState
import com.example.core.model.ModelSlot
import com.example.core.model.ModelStatus
import com.example.core.model.RoomModelManager
import com.example.core.overlay.ExecutionOverlayManager
import com.example.core.security.ActionRiskTier
import com.example.core.security.AppBlocklistPolicy
import com.example.core.security.ScreenRiskClassifier
import com.example.core.security.SecureCredentialStore
import com.example.core.tools.AccessibilityBridge
import com.example.core.tools.AndroidSystemTools
import com.example.core.tools.ScreenElementSummary
import com.example.core.tools.ToolCallRequest
import com.example.core.tools.ToolExecutionStatus
import com.example.core.vision.OcrResult
import com.example.core.vision.OcrTextBlock
import com.example.core.vision.OnDeviceOcrEngine
import com.example.core.vision.ScreenCaptureSource
import com.example.core.vision.ScreenJournal
import com.example.core.vision.ScreenPerceptionEngine
import com.example.core.vision.ScreenTreeCompressor
import com.example.core.wakeword.ModularWakeWordEngine
import com.example.core.web.OkHttpWebResearchService
import com.example.core.web.PublicHttpSafetyGuard
import com.example.core.web.PublicHttpValidationResult
import com.example.core.web.WebPageExtractor
import com.example.core.web.WebResearchConfig
import com.example.core.web.WebSearchProviderKind
import com.example.data.local.db.AgentCheckpointEntity
import com.example.data.local.db.JarvisDatabase
import com.example.data.local.db.MessageEntity
import com.example.data.local.db.ResearchHistoryEntity
import com.example.data.repository.JarvisSettings
import com.example.data.repository.SettingsRepository
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutonomousPhoneUseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: JarvisDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, JarvisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        AccessibilityBridge.clearAllTestOverrides()
    }

    @After
    fun tearDown() {
        AccessibilityBridge.clearAllTestOverrides()
        db.close()
    }

    @Test
    fun `room migration 7 to 8 creates checkpoints research history and mcp servers cleanly`() = runBlocking {
        val dbFile = File(tmpFolder.root, "jarvis_mig_v7_v8.db")
        val v7Callback = object : SupportSQLiteOpenHelper.Callback(7) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `conversations` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `conversation_id` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `modelLabel` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `ttftMs` INTEGER, `tokensPerSecond` REAL, `outputTokens` INTEGER, `backendUsed` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`conversation_id`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversation_id_timestamp` ON `messages` (`conversation_id`, `timestamp`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `model_entries` (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `providerType` TEXT NOT NULL, `engineFamily` TEXT NOT NULL, `parameterHint` TEXT NOT NULL, `contextWindow` INTEGER NOT NULL, `status` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `isCustom` INTEGER NOT NULL, `localFilePath` TEXT, `downloadUrl` TEXT, `expectedSha256` TEXT, `expectedSizeBytes` INTEGER NOT NULL, `artifactState` TEXT NOT NULL, `sourceRepoId` TEXT, `license` TEXT, `architecture` TEXT, `quantization` TEXT, `parameterCount` TEXT, `runtimeFormat` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `download_queue` (`id` TEXT NOT NULL, `repoId` TEXT NOT NULL, `fileName` TEXT NOT NULL, `displayName` TEXT NOT NULL, `downloadUrl` TEXT NOT NULL, `destinationPath` TEXT NOT NULL, `expectedSha256` TEXT, `totalBytes` INTEGER NOT NULL, `downloadedBytes` INTEGER NOT NULL, `speedBytesPerSec` INTEGER NOT NULL, `etaSeconds` INTEGER NOT NULL, `status` TEXT NOT NULL, `supportsResume` INTEGER NOT NULL, `errorMessage` TEXT, `modelSlotId` TEXT NOT NULL, `runtimeFormat` TEXT NOT NULL, `architecture` TEXT, `quantization` TEXT, `parameterCount` TEXT, `license` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `contextWindow` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `model_benchmarks` (`id` TEXT NOT NULL, `modelId` TEXT NOT NULL, `modelName` TEXT NOT NULL, `backendUsed` TEXT NOT NULL, `ttftMs` INTEGER NOT NULL, `tokensPerSecond` REAL NOT NULL, `outputTokens` INTEGER NOT NULL, `prefillTokens` INTEGER NOT NULL, `nativeHeapMb` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `initTimeMs` INTEGER NOT NULL, `peakMemoryMb` REAL NOT NULL, `modelSizeBytes` INTEGER NOT NULL, `thermalStatus` TEXT NOT NULL, `batteryPercent` INTEGER NOT NULL, `batteryTempCelsius` REAL NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_model_benchmarks_modelId_timestamp` ON `model_benchmarks` (`modelId`, `timestamp`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `memory_notes` (`id` TEXT NOT NULL, `category` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `kind` TEXT NOT NULL, `sourceConversationId` TEXT, `isUserApproved` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `importanceScore` INTEGER NOT NULL, `accessCount` INTEGER NOT NULL, `lastAccessedAt` INTEGER NOT NULL, `expiresAt` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_notes_kind_createdAt` ON `memory_notes` (`kind`, `createdAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `agent_executions` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `userPrompt` TEXT NOT NULL, `routeTaken` TEXT NOT NULL, `routeReason` TEXT NOT NULL, `status` TEXT NOT NULL, `stepsJson` TEXT NOT NULL, `resultSummary` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_executions_timestamp` ON `agent_executions` (`timestamp`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `agent_skills` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `triggerPattern` TEXT NOT NULL, `requiredPermissionsJson` TEXT NOT NULL, `parametersJson` TEXT NOT NULL, `stepsJson` TEXT NOT NULL, `validationRules` TEXT NOT NULL, `failureHandling` TEXT NOT NULL, `version` INTEGER NOT NULL, `provenance` TEXT NOT NULL, `riskLevel` TEXT NOT NULL, `usageCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_agent_skills_name` ON `agent_skills` (`name`)")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(v7Callback)
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        val migratedDb = Room.databaseBuilder(context, JarvisDatabase::class.java, dbFile.absolutePath)
            .addMigrations(JarvisDatabase.MIGRATION_7_8, JarvisDatabase.MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()
        try {
            val now = System.currentTimeMillis()
            val cp = AgentCheckpointEntity(
                id = "cp_1",
                conversationId = "conv_1",
                userGoal = "Open Settings and inspect Wi-Fi",
                currentStep = 2,
                maxSteps = 12,
                status = "PAUSED_FOR_CONFIRMATION",
                lastPackageName = "com.android.settings",
                lastStabilityHash = "abcd1234",
                stepsHistoryJson = """[{"index":0,"title":"Observe"}]""",
                lastObservationSummary = "On Wi-Fi screen",
                createdAt = now,
                updatedAt = now
            )
            migratedDb.agentCheckpointDao().upsertCheckpoint(cp)
            val resumable = migratedDb.agentCheckpointDao().getLatestResumableForConversation("conv_1")
            assertNotNull(resumable)
            assertEquals("cp_1", resumable?.id)

            migratedDb.researchHistoryDao().insertRecord(
                ResearchHistoryEntity(
                    id = "res_1",
                    query = "Android 16 Accessibility screenshot API",
                    provider = "Brave Search API",
                    urlsJson = """["https://developer.android.com"]""",
                    summarySnippet = "Supports takeScreenshot on display",
                    timestamp = now
                )
            )
            assertEquals(1, migratedDb.researchHistoryDao().getRecentOnce().size)

            migratedDb.mcpServerDao().upsertServer(
                com.example.data.local.db.McpServerEntity(
                    id = "mcp_1",
                    name = "LocalNotes",
                    endpointUrl = "https://mcp.example.com/rpc",
                    enabled = true,
                    requireWriteConfirmation = true
                )
            )
            assertEquals("mcp_1", migratedDb.mcpServerDao().getByName("localnotes")?.id)

            migratedDb.appKnowledgeDao().upsert(
                com.example.data.local.db.AppKnowledgeEntity(
                    id = "ak_1",
                    packageName = "com.android.settings",
                    appName = "Settings",
                    screenSignature = "SETTINGS:abcd",
                    screenType = "System Settings",
                    knownElementsJson = """["Network & internet"]""",
                    verifiedWorkflowHints = "Tap Network & internet for Wi-Fi",
                    successCount = 1,
                    failureCount = 0,
                    lastVerifiedAt = now
                )
            )
            assertEquals(1, migratedDb.appKnowledgeDao().getAllOnce().size)
        } finally {
            migratedDb.close()
        }
    }

    @Test
    fun `web research v2 brave serpapi ddg fallback extractor and ssrf guard enforce safety`() = runBlocking {
        // 1. PublicHttpSafetyGuard SSRF checks with offline deterministic DNS stubs
        val v4Public = InetAddress.getByAddress("example.com", byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34))
        val v4Private10 = InetAddress.getByAddress("internal.corp", byteArrayOf(10, 0, 0, 1))
        val v4Metadata = InetAddress.getByAddress("169.254.169.254", byteArrayOf(169.toByte(), 254.toByte(), 169.toByte(), 254.toByte()))
        val v4Loopback = InetAddress.getByAddress("127.0.0.1", byteArrayOf(127, 0, 0, 1))
        val v6Ula = InetAddress.getByAddress(
            "ula.internal",
            byteArrayOf(0xfc.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        )

        assertTrue(PublicHttpSafetyGuard.validateUrl("https://example.com/docs", allowLoopback = false) { arrayOf(v4Public) } is PublicHttpValidationResult.Safe)
        assertTrue(PublicHttpSafetyGuard.validateUrl("http://127.0.0.1:8080/admin", allowLoopback = false) { arrayOf(v4Loopback) } is PublicHttpValidationResult.Blocked)
        assertTrue(PublicHttpSafetyGuard.validateUrl("http://10.0.0.1/secret", allowLoopback = false) { arrayOf(v4Private10) } is PublicHttpValidationResult.Blocked)
        assertTrue(PublicHttpSafetyGuard.validateUrl("http://169.254.169.254/latest/meta-data", allowLoopback = false) { arrayOf(v4Metadata) } is PublicHttpValidationResult.Blocked)
        assertTrue(PublicHttpSafetyGuard.validateUrl("http://[fc00::1]/", allowLoopback = false) { arrayOf(v6Ula) } is PublicHttpValidationResult.Blocked)
        assertTrue(PublicHttpSafetyGuard.validateUrl("http://metadata.google.internal/", allowLoopback = false) is PublicHttpValidationResult.Blocked)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/brave") { ex ->
            val token = ex.requestHeaders.getFirst("X-Subscription-Token").orEmpty()
            assertEquals("brave-secret-123", token)
            val body = """{"web":{"results":[{"title":"Android Developers","url":"https://developer.android.com","description":"Official docs for AccessibilityService"}]}}"""
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, body.toByteArray().size.toLong())
            ex.responseBody.use { it.write(body.toByteArray()) }
        }
        server.createContext("/serp") { ex ->
            assertTrue(ex.requestURI.rawQuery.contains("api_key=serp-secret-456"))
            val body = """{"organic_results":[{"title":"Kotlin Coroutines","link":"https://kotlinlang.org","snippet":"Structured concurrency guide"}]}"""
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, body.toByteArray().size.toLong())
            ex.responseBody.use { it.write(body.toByteArray()) }
        }
        server.createContext("/ddg") { ex ->
            val html = """<html><body><a class="result__a" href="https://example.org/agent">Agent Architecture</a><a class="result__snippet">Closed-loop UI perception</a></body></html>"""
            ex.responseHeaders.add("Content-Type", "text/html")
            ex.sendResponseHeaders(200, html.toByteArray().size.toLong())
            ex.responseBody.use { it.write(html.toByteArray()) }
        }
        server.createContext("/article") { ex ->
            val html = """
                <html>
                  <head>
                    <title>JARVIS Engineering Guide &amp; Notes</title>
                    <script type="application/ld+json">{"headline":"JARVIS v1.0.1","description":"Non-root phone agent"}</script>
                  </head>
                  <body>
                    <nav>Ignore nav chrome</nav>
                    <article><h1>Overview</h1><p>Structured screen tree plus ML Kit OCR.</p></article>
                  </body>
                </html>
            """.trimIndent()
            ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            ex.sendResponseHeaders(200, html.toByteArray().size.toLong())
            ex.responseBody.use { it.write(html.toByteArray()) }
        }
        server.createContext("/ssrf_redirect") { ex ->
            ex.responseHeaders.add("Location", "http://10.0.0.1/internal")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }
        server.start()
        val port = server.address.port
        try {
            val webService = OkHttpWebResearchService(
                researchHistoryDao = db.researchHistoryDao(),
                braveBaseUrl = "http://127.0.0.1:$port/brave",
                serpApiBaseUrl = "http://127.0.0.1:$port/serp",
                duckDuckGoHtmlUrl = "http://127.0.0.1:$port/ddg"
            )

            val braveRes = webService.search(
                "Android AccessibilityService",
                WebResearchConfig(enabled = true, provider = WebSearchProviderKind.BRAVE_SEARCH, braveApiKey = "brave-secret-123")
            ).getOrThrow()
            assertEquals(1, braveRes.results.size)
            assertEquals("Android Developers", braveRes.results.first().title)

            val serpRes = webService.search(
                "Kotlin Coroutines",
                WebResearchConfig(enabled = true, provider = WebSearchProviderKind.SERPAPI, serpApiKey = "serp-secret-456")
            ).getOrThrow()
            assertEquals("Kotlin Coroutines", serpRes.results.first().title)

            val ddgRes = webService.search(
                "Closed-loop agent",
                WebResearchConfig(enabled = true, provider = WebSearchProviderKind.KEYLESS_FALLBACK)
            ).getOrThrow()
            assertEquals("Agent Architecture", ddgRes.results.first().title)
            assertEquals(3, db.researchHistoryDao().getRecentOnce().size)

            val extractor = WebPageExtractor(allowLoopbackForTests = true)
            val extract = extractor.fetchReadablePage("http://127.0.0.1:$port/article").getOrThrow()
            assertTrue(extract.title.contains("JARVIS Engineering Guide & Notes"))
            assertTrue(extract.jsonLdSummary.contains("Non-root phone agent"))
            assertTrue(extract.readableText.contains("Structured screen tree plus ML Kit OCR."))
            assertFalse(extract.readableText.contains("Ignore nav chrome"))

            val redirectBlock = extractor.fetchReadablePage("http://127.0.0.1:$port/ssrf_redirect")
            assertTrue(redirectBlock.isFailure)
            assertTrue(redirectBlock.exceptionOrNull() is SecurityException)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `mcp client and manager support initialize sse tools list and write confirmation gate`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/mcp") { ex ->
            val reqText = ex.requestBody.bufferedReader().readText()
            val reqJson = JSONObject(reqText)
            val method = reqJson.optString("method")
            val rpcId = reqJson.optInt("id", 0)
            when (method) {
                "initialize" -> {
                    ex.responseHeaders.add("Content-Type", "application/json")
                    ex.responseHeaders.add("Mcp-Session-Id", "sess-mcp-42")
                    val body = """{"jsonrpc":"2.0","id":$rpcId,"result":{"protocolVersion":"2025-03-26","serverInfo":{"name":"DemoMCP"}}}"""
                    ex.sendResponseHeaders(200, body.toByteArray().size.toLong())
                    ex.responseBody.use { it.write(body.toByteArray()) }
                }
                "notifications/initialized" -> {
                    ex.sendResponseHeaders(202, -1)
                    ex.close()
                }
                "tools/list" -> {
                    assertEquals("sess-mcp-42", ex.requestHeaders.getFirst("Mcp-Session-Id"))
                    assertEquals("Bearer mcp-token-xyz", ex.requestHeaders.getFirst("Authorization"))
                    ex.responseHeaders.add("Content-Type", "text/event-stream")
                    val sse = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":$rpcId,\"result\":{\"tools\":[{\"name\":\"get_status\",\"description\":\"Read service health\"},{\"name\":\"delete_item\",\"description\":\"Delete remote record\"}]}}\n\n"
                    ex.sendResponseHeaders(200, sse.toByteArray().size.toLong())
                    ex.responseBody.use { it.write(sse.toByteArray()) }
                }
                "tools/call" -> {
                    val params = reqJson.getJSONObject("params")
                    val tName = params.getString("name")
                    ex.responseHeaders.add("Content-Type", "application/json")
                    val body = """{"jsonrpc":"2.0","id":$rpcId,"result":{"isError":false,"content":[{"type":"text","text":"MCP executed $tName"}]}}"""
                    ex.sendResponseHeaders(200, body.toByteArray().size.toLong())
                    ex.responseBody.use { it.write(body.toByteArray()) }
                }
                else -> {
                    ex.sendResponseHeaders(400, -1)
                    ex.close()
                }
            }
        }
        server.start()
        val port = server.address.port
        try {
            val mcpManager = McpManager(
                context = context,
                mcpServerDao = db.mcpServerDao(),
                mcpClient = McpClient(),
                credentialStore = SecureCredentialStore()
            )
            val created = mcpManager.addOrUpdateServer(
                name = "DemoMCP",
                endpointUrl = "http://127.0.0.1:$port/mcp",
                bearerToken = "mcp-token-xyz",
                enabled = true,
                requireWriteConfirmation = true
            ).getOrThrow()

            val synced = mcpManager.syncServerTools(created.id).getOrThrow()
            assertEquals(2, synced.tools.size)
            assertFalse(synced.tools.first { it.name == "get_status" }.isPotentiallyMutating)
            assertTrue(synced.tools.first { it.name == "delete_item" }.isPotentiallyMutating)

            // Read tool runs immediately
            val readOut = mcpManager.invokeTool("DemoMCP", "get_status", "{}", userConfirmed = false).getOrThrow()
            assertEquals("MCP executed get_status", readOut.textContent)

            // Mutating tool requires confirmation when userConfirmed = false
            val mcpTool = com.example.core.tools.McpCallTool { mcpManager }
            val unconfirmed = mcpTool.execute(
                context = context,
                args = mapOf("tool_name" to "delete_item", "arguments_json" to """{"id":"99"}""", "server_name" to "DemoMCP"),
                settings = JarvisSettings(),
                userConfirmed = false
            )
            assertEquals(ToolExecutionStatus.CONFIRMATION_REQUIRED, unconfirmed.status)

            val confirmed = mcpTool.execute(
                context = context,
                args = mapOf("tool_name" to "delete_item", "arguments_json" to """{"id":"99"}""", "server_name" to "DemoMCP"),
                settings = JarvisSettings(),
                userConfirmed = true
            )
            assertEquals(ToolExecutionStatus.SUCCESS, confirmed.status)
            assertTrue(confirmed.summary.contains("MCP executed delete_item"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `vision compressor journal risk classifier blocklist and ocr hook enforce safety and determinism`() = runBlocking {
        // 1. ScreenTreeCompressor compaction, bounds centering, offscreen filter, password redaction, noise resistance
        val raw = listOf(
            ScreenElementSummary("Offscreen", "v_off", "View", isClickable = true, isEditable = false, isScrollable = false, bounds = "[-100,0][-10,50]"),
            ScreenElementSummary("ZeroSize", "v_zero", "View", isClickable = true, isEditable = false, isScrollable = false, bounds = "[10,10][10,10]"),
            ScreenElementSummary("[REDACTED_PASSWORD]", "et_pass", "EditText", isClickable = true, isEditable = true, isScrollable = false, bounds = "[100,200][500,300]"),
            ScreenElementSummary("12:45 88% Status", "tv_clock", "TextView", isClickable = false, isEditable = false, isScrollable = false, bounds = "[0,0][200,40]"),
            ScreenElementSummary("Continue", "btn_continue", "Button", isClickable = true, isEditable = false, isScrollable = false, bounds = "[200,800][600,920]")
        )
        val (nodesA, hashA) = ScreenTreeCompressor.compress(raw, "com.example.demo", screenWidth = 1080, screenHeight = 2400)
        assertEquals(3, nodesA.size)
        val btn = nodesA.first { it.label == "Continue" }
        assertEquals(400, btn.centerX)
        assertEquals(860, btn.centerY)
        assertTrue(nodesA.first { it.id == "et_pass" }.passwordProtected)

        val rawNextMinute = raw.map {
            if (it.viewId == "tv_clock") it.copy(label = "12:46 87% Status") else it
        }
        val (_, hashB) = ScreenTreeCompressor.compress(rawNextMinute, "com.example.demo", screenWidth = 1080, screenHeight = 2400)
        assertEquals(hashA, hashB)

        // 2. ScreenJournal stagnation & oscillation detection
        val journal = ScreenJournal()
        journal.record(1, "hash1", "pkg", "tap", stateChanged = false)
        journal.record(2, "hash1", "pkg", "tap", stateChanged = false)
        assertTrue(journal.isStalledOrOscillating("hash1"))

        journal.clear()
        journal.record(1, "hA", "pkg", "scroll_down", stateChanged = true)
        journal.record(2, "hB", "pkg", "scroll_up", stateChanged = true)
        journal.record(3, "hA", "pkg", "scroll_down", stateChanged = true)
        journal.record(4, "hB", "pkg", "scroll_up", stateChanged = true)
        assertTrue(journal.isStalledOrOscillating("hB"))

        // 3. AppBlocklistPolicy & ScreenRiskClassifier
        assertTrue(AppBlocklistPolicy.isPackageBlocked("com.x8bit.bitwarden"))
        assertTrue(AppBlocklistPolicy.isPackageBlocked("com.custom.vault", "com.custom.vault; org.other"))
        assertEquals(
            ActionRiskTier.BLOCKED,
            ScreenRiskClassifier.assessAction("com.paypal.android.p2pmobile", "Pay", null, "tap_ui_element").tier
        )
        assertEquals(
            ActionRiskTier.CONFIRM_REQUIRED,
            ScreenRiskClassifier.assessAction("com.shop.app", "Confirm Payment $20", null, "tap_ui_element").tier
        )
        assertEquals(
            ActionRiskTier.SAFE,
            ScreenRiskClassifier.assessAction("com.android.settings", "Wi-Fi", "Network & internet", "tap_ui_element").tier
        )

        // 4. OnDeviceOcrEngine + ScreenPerceptionEngine OCR fallback when tree nodes are empty
        val ocrEngine = OnDeviceOcrEngine().apply {
            testOcrOverride = { _ ->
                OcrResult(
                    fullText = "Approve Device Access",
                    blocks = listOf(OcrTextBlock("Approve Device Access", 100, 400, 700, 480)),
                    durationMs = 12L,
                    succeeded = true
                )
            }
        }
        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        AccessibilityBridge.setTestActivePackageOverride("com.demo.kiosk")
        AccessibilityBridge.setTestElementsOverride { emptyList() }
        AccessibilityBridge.setTestScreenshotOverride { bmp }
        val perception = ScreenPerceptionEngine(context, ocrEngine = ocrEngine)
        val obs = perception.observeScreen(includeScreenshot = false, includeOcr = true)
        assertFalse(obs.blockedByPolicy)
        assertEquals(ScreenCaptureSource.ACCESSIBILITY_API30, obs.captureSource)
        assertTrue(obs.ocrResult?.fullText?.contains("Approve Device Access") == true)
        assertTrue(obs.elements.isEmpty())
    }

    @Test
    fun `closed loop agent tests A through I verify ocr multi step stagnation error recovery cancel permission checkpoint steer and vision`() = runBlocking {
        val modelManager = RoomModelManager(db.modelDao()).apply { ensureSeeded() }
        modelManager.updateArtifactState(
            id = RoomModelManager.GEMMA_3_1B_ID,
            localFilePath = File(tmpFolder.root, "gemma3_test.litertlm").apply { writeBytes(ByteArray(512)) }.absolutePath,
            artifactState = ArtifactState.READY,
            status = ModelStatus.READY
        )
        val localMemory = RoomLocalMemory(db.memoryDao())
        val sysTools = AndroidSystemTools(context)
        val ocrEngine = OnDeviceOcrEngine().apply {
            testOcrOverride = {
                OcrResult(
                    fullText = "Canvas Button: Submit Diagnostics",
                    blocks = listOf(OcrTextBlock("Submit Diagnostics", 120, 600, 520, 680)),
                    durationMs = 8L,
                    succeeded = true
                )
            }
        }
        val overlayManager = ExecutionOverlayManager(context)
        val perceptionEngine = ScreenPerceptionEngine(context, ocrEngine, overlayManager)
        val mcpManager = McpManager(context, db.mcpServerDao(), McpClient(), SecureCredentialStore())
        val registry = ToolRegistryFactory.createDefault(
            androidTools = sysTools,
            localMemoryProvider = { localMemory },
            perceptionEngineProvider = { perceptionEngine },
            mcpManagerProvider = { mcpManager }
        )

        // Helper builders for fake Cloud + Local engines
        fun buildFakeCloud(onCall: (CloudInferenceConfig, String) -> String): CloudAiClient = object : CloudAiClient {
            private val _stats = MutableStateFlow(CloudUsageStats())
            override val usageStats: StateFlow<CloudUsageStats> = _stats.asStateFlow()
            override fun cancelActiveRequest() = Unit
            override fun resetUsageStats() = Unit
            override fun streamChat(
                config: CloudInferenceConfig,
                systemInstruction: String,
                prompt: String,
                history: List<MessageEntity>
            ): Flow<AiStreamEvent> = flow {
                emit(AiStreamEvent.Started("Cloud"))
                val out = onCall(config, prompt)
                emit(AiStreamEvent.Completed(out, GenerationBenchmark(10L, 45f, 12, 8, "Cloud", 0f)))
            }
        }

        val readyLocalEngine = object : AiEngine {
            private val _ls = MutableStateFlow<EngineLoadState>(EngineLoadState.Ready(RoomModelManager.GEMMA_3_1B_ID, "Gemma 3", "CPU", 10L))
            override val loadState: StateFlow<EngineLoadState> = _ls.asStateFlow()
            override val isGenerating: StateFlow<Boolean> = MutableStateFlow(false)
            override val lastBenchmark: StateFlow<GenerationBenchmark?> = MutableStateFlow(null)
            override val nativeMemoryMb: StateFlow<Float> = MutableStateFlow(32f)
            override suspend fun loadModel(slot: ModelSlot, settings: JarvisSettings) = Result.success(Unit)
            override suspend fun unloadModel() = Unit
            override suspend fun reloadModel(slot: ModelSlot, settings: JarvisSettings) = Result.success(Unit)
            override suspend fun cancelGeneration() = Unit
            override fun refreshMemoryUsage() = Unit
            override fun release() = Unit
            override fun streamReply(conversationId: String, prompt: String, history: List<MessageEntity>, settings: JarvisSettings) = flow<AiStreamEvent> {
                emit(AiStreamEvent.Started("CPU"))
                emit(AiStreamEvent.Completed("Local fallback summary", GenerationBenchmark(12L, 30f, 5, 5, "CPU", 32f)))
            }
        }

        val cloudSettings = JarvisSettings(
            routingMode = AgentRoutingMode.CLOUD_ONLY,
            cloudUploadConsentGranted = true,
            cloudProviderKind = CloudProviderKind.OPENAI_COMPATIBLE,
            cloudApiKey = "sk-test-key",
            accessibilityAutomationEnabled = true,
            screenshotVisionEnabled = true,
            floatingOverlayEnabled = false
        )

        // TEST A: Observation + sparse-node OCR fallback in CONTEXT_OBSERVE (complex prompt triggers CONTEXT_OBSERVE)
        val bmpA = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        AccessibilityBridge.setTestActivePackageOverride("com.demo.canvas")
        AccessibilityBridge.setTestElementsOverride { emptyList() }
        AccessibilityBridge.setTestScreenshotOverride { bmpA }
        var seenOcrInPrompt = false
        var seenAttachmentCount = 0
        val coreA = Phase2AgentCore(
            aiEngine = readyLocalEngine,
            modelManager = modelManager,
            androidTools = sysTools,
            cloudProviderRegistry = Phase1CloudProviderRegistry(),
            wakeWordEngine = ModularWakeWordEngine(),
            localMemory = localMemory,
            appContext = context,
            toolRegistry = registry,
            cloudAiClient = buildFakeCloud { cfg, p ->
                seenOcrInPrompt = p.contains("Submit Diagnostics")
                seenAttachmentCount = cfg.attachments.size
                "OCR inspected screen."
            },
            screenPerceptionEngine = perceptionEngine,
            executionOverlayManager = overlayManager,
            checkpointDao = db.agentCheckpointDao(),
            mcpManager = mcpManager
        )
        coreA.executeHybridTask(
            conversationId = "c_a",
            prompt = "Inspect my active screen step by step and summarize what needs attention cleanly",
            history = emptyList(),
            settings = cloudSettings,
            attachments = listOf(CloudImageAttachment("ZmFrZQ==", "image/jpeg", "user_photo"))
        ).collect {}
        assertTrue(seenOcrInPrompt)
        assertTrue(seenAttachmentCount >= 1)
        assertFalse(overlayManager.hudState.value.active)

        // TEST B: UI Action Confirmation Gate + Verification (`tap_coordinates` via `/tap_coordinates`)
        var tapCoordFired = false
        AccessibilityBridge.setTestElementsOverride {
            if (!tapCoordFired) listOf(ScreenElementSummary("Save", "btn_save", "Button", isClickable = true, isEditable = false, isScrollable = false, bounds = "[100,100][200,200]"))
            else listOf(ScreenElementSummary("Saved!", "tv_done", "TextView", isClickable = false, isEditable = false, isScrollable = false, bounds = "[100,100][200,200]"))
        }
        AccessibilityBridge.setTestCoordinateTapOverride { _, _ ->
            tapCoordFired = true
            true
        }
        val evB1 = mutableListOf<HybridAgentEvent>()
        coreA.executeHybridTask("c_b", "/tool tap_coordinates x=150 y=150", emptyList(), cloudSettings, confirmedStepIndex = null).collect { evB1.add(it) }
        assertTrue(evB1.any { it is HybridAgentEvent.ConfirmationRequired })
        assertFalse(tapCoordFired)

        val evB2 = mutableListOf<HybridAgentEvent>()
        coreA.executeHybridTask("c_b", "/tool tap_coordinates x=150 y=150", emptyList(), cloudSettings, confirmedStepIndex = 0).collect { evB2.add(it) }
        assertTrue(tapCoordFired)
        assertTrue(evB2.any { it is HybridAgentEvent.ToolSnapshotEmitted && it.content.contains("Tapped (150, 150)") })

        // TEST C & E: Multi-step closed-loop tool calls + Stagnation recovery signal (`ScreenJournal`)
        AccessibilityBridge.setTestElementsOverride {
            // Always returns identical static screen so consecutive non-changing actions trigger RECOVERY_SIGNAL!
            listOf(ScreenElementSummary("Feed", "tv_feed", "TextView", isClickable = false, isEditable = false, isScrollable = true, bounds = "[0,0][500,800]"))
        }
        AccessibilityBridge.setTestScrollOverride { true }
        val turnC = AtomicInteger(0)
        var recoverySignalSeen = false
        val coreC = Phase2AgentCore(
            aiEngine = readyLocalEngine,
            modelManager = modelManager,
            androidTools = sysTools,
            cloudProviderRegistry = Phase1CloudProviderRegistry(),
            wakeWordEngine = ModularWakeWordEngine(),
            localMemory = localMemory,
            appContext = context,
            toolRegistry = registry,
            cloudAiClient = buildFakeCloud { _, p ->
                when (turnC.incrementAndGet()) {
                    1 -> """[TOOL_CALL]{"tool":"scroll_screen","args":{"direction":"down"}}[/TOOL_CALL]"""
                    2 -> """[TOOL_CALL]{"tool":"scroll_screen","args":{"direction":"up"}}[/TOOL_CALL]"""
                    else -> {
                        recoverySignalSeen = p.contains("[RECOVERY_SIGNAL]")
                        "Concluded after noticing screen stalled."
                    }
                }
            },
            screenPerceptionEngine = perceptionEngine,
            executionOverlayManager = overlayManager,
            checkpointDao = db.agentCheckpointDao(),
            mcpManager = mcpManager
        )
        val evC = mutableListOf<HybridAgentEvent>()
        coreC.executeHybridTask(
            conversationId = "c_ce",
            prompt = "Check screen feed and explain why scrolling stops step by step across items",
            history = emptyList(),
            settings = cloudSettings,
            confirmedStepIndex = 2
        ).collect { evC.add(it) }
        assertTrue(recoverySignalSeen)
        assertTrue(evC.any { it is HybridAgentEvent.AiStream && it.event is AiStreamEvent.Completed })

        // TEST D: Web Research step failure triggers clean RECOVERED fallback notice
        val failingWebService = object : com.example.core.web.WebResearchService {
            override suspend fun search(query: String, config: WebResearchConfig) =
                Result.failure<com.example.core.web.WebResearchReport>(IllegalStateException("Network offline"))
        }
        val coreD = Phase2AgentCore(
            aiEngine = readyLocalEngine,
            modelManager = modelManager,
            androidTools = sysTools,
            cloudProviderRegistry = Phase1CloudProviderRegistry(),
            wakeWordEngine = ModularWakeWordEngine(),
            localMemory = localMemory,
            appContext = context,
            toolRegistry = registry,
            cloudAiClient = buildFakeCloud { _, _ -> "Offline explanation delivered." },
            webResearchService = failingWebService,
            checkpointDao = db.agentCheckpointDao()
        )
        val evD = mutableListOf<HybridAgentEvent>()
        coreD.executeHybridTask(
            conversationId = "c_d",
            prompt = "Search the web for latest Kotlin compiler performance",
            history = emptyList(),
            settings = cloudSettings.copy(webResearchEnabled = true)
        ).collect { evD.add(it) }
        val planD = evD.filterIsInstance<HybridAgentEvent.PlanUpdated>().last().plan
        assertEquals(PlanStepStatus.RECOVERED, planD.steps.first { it.type == PlanStepType.WEB_RESEARCH }.status)

        // TEST F: Error recovery when target UI node is missing (`tap_ui_element`)
        AccessibilityBridge.setTestTapOverride { false }
        val turnF = AtomicInteger(0)
        var missingNodeReported = false
        val coreF = Phase2AgentCore(
            aiEngine = readyLocalEngine,
            modelManager = modelManager,
            androidTools = sysTools,
            cloudProviderRegistry = Phase1CloudProviderRegistry(),
            wakeWordEngine = ModularWakeWordEngine(),
            localMemory = localMemory,
            appContext = context,
            toolRegistry = registry,
            cloudAiClient = buildFakeCloud { _, p ->
                if (turnF.incrementAndGet() == 1) {
                    """[TOOL_CALL]{"tool":"tap_ui_element","args":{"target":"NonExistentButton"}}[/TOOL_CALL]"""
                } else {
                    missingNodeReported = p.contains("status=FAILED")
                    "Could not find NonExistentButton on screen."
                }
            },
            checkpointDao = db.agentCheckpointDao()
        )
        coreF.executeHybridTask("c_f", "Tap missing button safely and report", emptyList(), cloudSettings, confirmedStepIndex = 1).collect {}
        assertTrue(missingNodeReported)

        // TEST G: Instant Cancellation via cancelAutonomousTask()
        val cancelGateG = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slowCloud = object : CloudAiClient {
            override val usageStats = MutableStateFlow(CloudUsageStats())
            override fun cancelActiveRequest() {
                cancelGateG.complete(Unit)
            }
            override fun resetUsageStats() = Unit
            override fun streamChat(config: CloudInferenceConfig, systemInstruction: String, prompt: String, history: List<MessageEntity>) = flow {
                emit(AiStreamEvent.Started("Cloud"))
                kotlinx.coroutines.withTimeoutOrNull(2000L) { cancelGateG.await() }
                if (cancelGateG.isCompleted) {
                    emit(AiStreamEvent.Cancelled("", null))
                } else {
                    emit(AiStreamEvent.Completed("Should not complete", GenerationBenchmark(1L, 1f, 1, 1, "Cloud", 0f)))
                }
            }
        }
        val coreG = Phase2AgentCore(
            aiEngine = readyLocalEngine,
            modelManager = modelManager,
            androidTools = sysTools,
            cloudProviderRegistry = Phase1CloudProviderRegistry(),
            wakeWordEngine = ModularWakeWordEngine(),
            localMemory = localMemory,
            appContext = context,
            toolRegistry = registry,
            cloudAiClient = slowCloud,
            agentExecutionDao = db.agentExecutionDao(),
            checkpointDao = db.agentCheckpointDao()
        )
        val jobG = launch {
            coreG.executeHybridTask("c_g", "Long running check", emptyList(), cloudSettings).collect {}
        }
        delay(80L)
        coreG.cancelAutonomousTask()
        jobG.join()
        val cancelledExec = db.agentExecutionDao().observeExecutions().first().firstOrNull { it.conversationId == "c_g" }
        assertEquals("CANCELLED", cancelledExec?.status)

        // TEST H: Permission Denial Honesty (Accessibility unauthorized)
        AccessibilityBridge.clearAllTestOverrides()
        val evH = mutableListOf<HybridAgentEvent>()
        coreF.executeHybridTask("c_h", "/tool capture_screen_ocr", emptyList(), cloudSettings).collect { evH.add(it) }
        assertTrue(evH.any { it is HybridAgentEvent.PermissionRequired })

        // TEST I: Checkpoint Save + Resume + Mid-run Steering Injection
        var sawResumedTag = false
        var sawSteeringUpdate = false
        val turnI = AtomicInteger(0)
        lateinit var coreI: Phase2AgentCore
        coreI = Phase2AgentCore(
            aiEngine = readyLocalEngine,
            modelManager = modelManager,
            androidTools = sysTools,
            cloudProviderRegistry = Phase1CloudProviderRegistry(),
            wakeWordEngine = ModularWakeWordEngine(),
            localMemory = localMemory,
            appContext = context,
            toolRegistry = registry,
            cloudAiClient = buildFakeCloud { _, p ->
                val t = turnI.incrementAndGet()
                if (t == 1) {
                    sawResumedTag = p.contains("[RESUMED_CHECKPOINT")
                    coreI.submitSteeringMessage("Focus only on battery stats")
                    """[TOOL_CALL]{"tool":"read_battery_status","args":{}}[/TOOL_CALL]"""
                } else {
                    sawSteeringUpdate = p.contains("[USER_STEERING_UPDATE] Focus only on battery stats")
                    "Resumed task completed cleanly."
                }
            },
            checkpointDao = db.agentCheckpointDao()
        )
        db.agentCheckpointDao().upsertCheckpoint(
            AgentCheckpointEntity(
                id = "cp_resume_1",
                conversationId = "c_i",
                userGoal = "Check hardware status",
                currentStep = 1,
                maxSteps = 12,
                status = "PAUSED_FOR_CONFIRMATION",
                lastPackageName = "com.android.settings",
                lastStabilityHash = "hash99",
                stepsHistoryJson = "[]",
                lastObservationSummary = "Paused before battery inspection",
                createdAt = 100L,
                updatedAt = 200L
            )
        )
        coreI.executeHybridTask(
            conversationId = "c_i",
            prompt = "",
            history = emptyList(),
            settings = cloudSettings,
            resumeCheckpointId = "cp_resume_1"
        ).collect {}
        assertTrue(sawResumedTag)
        assertTrue(sawSteeringUpdate)
    }

    @Test
    fun `settings repository persists dual ui mode vision overlay blocklist and encrypted search keys`() = runBlocking {
        val dsFile = File(tmpFolder.root, "settings_v101.preferences_pb")
        val ds = PreferenceDataStoreFactory.create(produceFile = { dsFile })
        val repo = SettingsRepository(ds, SecureCredentialStore())

        val initial = repo.settingsFlow.first()
        assertFalse(initial.uiAdvancedModeEnabled)
        assertFalse(initial.screenshotVisionEnabled)
        assertTrue(initial.floatingOverlayEnabled)

        repo.setUiAdvancedModeEnabled(true)
        repo.setScreenshotVisionEnabled(true)
        repo.setFloatingOverlayEnabled(false)
        repo.setBlockedPackagesCsv("com.custom.bank, org.authenticator")
        repo.setBraveSearchApiKey("brave-test-key")
        repo.setSerpApiKey("serp-test-key")

        val updated = repo.settingsFlow.first()
        assertTrue(updated.uiAdvancedModeEnabled)
        assertTrue(updated.screenshotVisionEnabled)
        assertFalse(updated.floatingOverlayEnabled)
        assertEquals("com.custom.bank, org.authenticator", updated.blockedPackagesCsv)
        assertEquals("brave-test-key", updated.braveSearchApiKey)
        assertEquals("serp-test-key", updated.serpApiKey)
    }
}
