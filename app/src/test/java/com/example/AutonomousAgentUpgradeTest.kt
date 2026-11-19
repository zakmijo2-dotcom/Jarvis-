package com.example

import androidx.compose.ui.graphics.Color
import com.example.core.agent.ExecutionRoute
import com.example.core.agent.GoalTaskPlanner
import com.example.core.agent.PlanStepType
import com.example.core.agent.RoutingDecision
import com.example.core.ai.ModelCapabilityDetector
import com.example.core.ai.ModelTier
import com.example.core.background.VirtualDisplayFeasibility
import com.example.core.background.VirtualDisplayFeasibilityProbe
import com.example.core.browser.BrowserAutomationEngine
import com.example.core.browser.BrowserHttpFetcher
import com.example.core.cloud.CloudProviderKind
import com.example.core.device.DeviceCapabilitySnapshot
import com.example.core.device.ShizukuAvailabilityState
import com.example.core.device.ShizukuStatusSnapshot
import com.example.core.memory.AppKnowledgeRepository
import com.example.core.vision.LocalizationSource
import com.example.core.vision.OcrResult
import com.example.core.vision.OcrTextBlock
import com.example.core.vision.ScreenCaptureSource
import com.example.core.vision.ScreenClassifier
import com.example.core.vision.ScreenElementNode
import com.example.core.vision.ScreenObservation
import com.example.core.vision.ScreenPerceptionEngine
import com.example.core.vision.ScreenType
import com.example.core.vision.VisualElementLocator
import com.example.data.local.db.AppKnowledgeDao
import com.example.data.local.db.AppKnowledgeEntity
import com.example.ui.components.parseSimpleMarkdown
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutonomousAgentUpgradeTest {

    // -------------------------------------------------------------------------
    // 1. GoalTaskPlanner — Multi-step goal decomposition & bounded replanning
    // -------------------------------------------------------------------------

    @Test
    fun goalTaskPlanner_decomposesCompositeAppWorkflowWithSummaryAndMemory() {
        val planner = GoalTaskPlanner()
        val decision = RoutingDecision(
            route = ExecutionRoute.CLOUD_API,
            reason = "Multi-step app goal",
            complexityScore = 7,
            needsWebResearch = false,
            detectedTools = emptyList(),
            useCloudModel = true
        )

        val plan = planner.buildGoalPlan(
            prompt = "Find a football video on YouTube, summarize it, and save the summary",
            decision = decision
        )

        assertTrue(plan.isMultiStepAppGoal)
        assertTrue(plan.wantsSummary)
        assertTrue(plan.wantsMemorySave)
        assertEquals("youtube", plan.targetAppQuery?.lowercase())
        assertTrue(plan.steps.any { it.type == PlanStepType.ANDROID_TOOL && it.title.contains("launch_installed_app") })
        assertTrue(plan.steps.any { it.type == PlanStepType.CONTEXT_OBSERVE })
        assertTrue(plan.steps.any { it.type == PlanStepType.RESULT_VERIFY })
        assertTrue(plan.plannedToolRequests.values.any { it.toolName == "launch_installed_app" })
    }

    @Test
    fun goalTaskPlanner_decomposesBrowserUrlGoalIntoBrowserOpenPipeline() {
        val planner = GoalTaskPlanner()
        val decision = RoutingDecision(
            route = ExecutionRoute.HYBRID_WEB_AND_AI,
            reason = "Browser goal",
            complexityScore = 5,
            needsWebResearch = true,
            detectedTools = emptyList(),
            useCloudModel = true
        )

        val plan = planner.buildGoalPlan(
            prompt = "Open https://example.org/docs and summarize the release notes",
            decision = decision
        )

        assertTrue(plan.wantsBrowserWorkflow)
        assertEquals("https://example.org/docs", plan.targetUrl)
        assertTrue(plan.plannedToolRequests.values.any { it.toolName == "browser_open" })
        assertTrue(plan.steps.any { it.type == PlanStepType.RESULT_VERIFY })
    }

    @Test
    fun goalTaskPlanner_boundsDynamicReplanningAfterMaxAttempts() {
        val planner = GoalTaskPlanner()

        val firstRecovery = planner.planRecoveryAfterFailure(
            failedToolName = "tap_ui_element",
            failureReason = "No matching element found for 'Search'",
            activePackage = "com.google.android.youtube",
            attemptCount = 0
        )
        assertTrue(firstRecovery.shouldReplan)
        assertEquals("capture_screen_ocr", firstRecovery.recoveryToolRequest?.toolName)

        val exhausted = planner.planRecoveryAfterFailure(
            failedToolName = "tap_ui_element",
            failureReason = "No matching element found",
            activePackage = "com.google.android.youtube",
            attemptCount = 3
        )
        assertFalse(exhausted.shouldReplan)
        assertTrue(exhausted.modelGuidanceNote.contains("REPLAN_EXHAUSTED"))
    }

    // -------------------------------------------------------------------------
    // 2. ScreenClassifier & VisualElementLocator — Accessibility + OCR fallback
    // -------------------------------------------------------------------------

    @Test
    fun screenClassifier_detectsPermissionDialogsLoadingAndBlockedScreens() {
        val dialogObs = ScreenObservation(
            capturedAtMs = 1000L,
            activePackageName = "com.google.android.permissioncontroller",
            screenWidth = 1080,
            screenHeight = 2400,
            elements = listOf(
                node(0, "Allow Jarvis to access your location?", clickable = false),
                node(1, "While using the app", clickable = true),
                node(2, "Don't allow", clickable = true)
            ),
            stabilityHash = "hash_perm"
        )
        val dialogClass = ScreenClassifier.classify(dialogObs)
        assertEquals(ScreenType.DIALOG_PERMISSION, dialogClass.screenType)
        assertTrue(dialogClass.hasPermissionOrSystemDialog)

        val loadingObs = ScreenObservation(
            capturedAtMs = 1000L,
            activePackageName = "com.example.news",
            screenWidth = 1080,
            screenHeight = 2400,
            elements = listOf(
                node(0, "Loading...", clickable = false, className = "android.widget.ProgressBar")
            ),
            stabilityHash = "hash_load"
        )
        val loadingClass = ScreenClassifier.classify(loadingObs)
        assertEquals(ScreenType.LOADING, loadingClass.screenType)
        assertTrue(loadingClass.isLoading)

        val blockedObs = loadingObs.copy(blockedByPolicy = true, blockReason = "Banking app blocked")
        assertEquals(ScreenType.SENSITIVE_BLOCKED, ScreenClassifier.classify(blockedObs).screenType)
    }

    @Test
    fun visualElementLocator_prefersAccessibilityAndFallsBackToOcrBounds() {
        val obs = ScreenObservation(
            capturedAtMs = 1000L,
            activePackageName = "com.example.canvasapp",
            screenWidth = 1080,
            screenHeight = 2400,
            elements = listOf(
                node(0, "Search videos", clickable = true, cx = 540, cy = 120)
            ),
            ocrResult = OcrResult(
                fullText = "Subscribe Now",
                blocks = listOf(
                    OcrTextBlock(text = "Subscribe Now", left = 700, top = 1100, right = 980, bottom = 1180)
                )
            ),
            stabilityHash = "hash_vis"
        )

        val a11yMatch = VisualElementLocator.locateElement(obs, "Search videos")
        assertNotNull(a11yMatch)
        assertEquals(LocalizationSource.ACCESSIBILITY_TREE, a11yMatch!!.source)
        assertEquals(540, a11yMatch.centerX)

        val ocrMatch = VisualElementLocator.locateElement(obs, "Subscribe")
        assertNotNull(ocrMatch)
        assertEquals(LocalizationSource.OCR_VISUAL_BLOCK, ocrMatch!!.source)
        assertEquals(840, ocrMatch.centerX)
        assertEquals(1140, ocrMatch.centerY)
    }

    // -------------------------------------------------------------------------
    // 3. ScreenPerceptionEngine — Context-aware cloud screenshot attachment
    // -------------------------------------------------------------------------

    @Test
    fun screenPerceptionEngine_skipsUnchangedFramesAndNonVisionModels() {
        val engine = ScreenPerceptionEngine(RuntimeEnvironment.getApplication())
        val denseElements = (0..7).map { idx -> node(idx, "Item $idx", clickable = true) }
        val obs = ScreenObservation(
            capturedAtMs = 1000L,
            activePackageName = "com.android.settings",
            screenWidth = 1080,
            screenHeight = 2400,
            elements = denseElements,
            screenshotBase64Jpeg = "ZmFrZV9qcGVn",
            captureSource = ScreenCaptureSource.TEST_STUB,
            stabilityHash = "stable_hash_1"
        )

        assertFalse(
            engine.shouldAttachScreenshotToCloud(
                observation = obs,
                previousSentHash = null,
                userPrompt = "Look at the screen",
                previousActionFailed = false,
                modelSupportsVision = false
            )
        )
        assertFalse(
            engine.shouldAttachScreenshotToCloud(
                observation = obs,
                previousSentHash = "stable_hash_1",
                userPrompt = "Look at the screen",
                previousActionFailed = false,
                modelSupportsVision = true
            )
        )
        assertTrue(
            engine.shouldAttachScreenshotToCloud(
                observation = obs,
                previousSentHash = "stable_hash_1",
                userPrompt = "Tap Wi-Fi",
                previousActionFailed = true,
                modelSupportsVision = true
            )
        )
    }

    // -------------------------------------------------------------------------
    // 4. BrowserAutomationEngine — Tabs, backstack, forms, links, SSRF guard
    // -------------------------------------------------------------------------

    @Test
    fun browserAutomationEngine_handlesMultiTabNavigationLinksFormsAndSsrfBlock() = runBlocking {
        val postedPayloads = mutableListOf<Pair<String, Map<String, String>>>()
        val fakeFetcher = object : BrowserHttpFetcher {
            override suspend fun get(url: String): Pair<Int, String> = when {
                url.contains("page2") -> 200 to """
                    <html><head><title>Second Page</title></head>
                    <body><p>Welcome to page two.</p>
                    <form action="/submit" method="POST">
                      <input type="text" name="query" value="" />
                    </form>
                    </body></html>
                """.trimIndent()
                else -> 200 to """
                    <html><head><title>Home Portal</title></head>
                    <body><h1>Portal</h1><a href="/page2">Go Next</a></body></html>
                """.trimIndent()
            }

            override suspend fun postForm(url: String, fields: Map<String, String>): Pair<Int, String> {
                postedPayloads += url to fields
                return 200 to "<html><head><title>Submitted</title></head><body>Form accepted</body></html>"
            }
        }

        val browser = BrowserAutomationEngine(fetcher = fakeFetcher)

        // Open tab 1
        val home = browser.openUrl("https://example.org").getOrThrow()
        assertEquals("Home Portal", home.title)
        assertEquals(1, home.links.size)

        // Click link -> navigates to /page2
        val page2 = browser.clickLink(textQuery = "Go Next").getOrThrow()
        assertEquals("Second Page", page2.title)
        assertEquals(1, page2.forms.size)

        // Submit POST form
        val submitted = browser.fillAndSubmitForm(0, mapOf("query" to "android agent")).getOrThrow()
        assertEquals("Submitted", submitted.title)
        assertEquals(1, postedPayloads.size)
        assertEquals("android agent", postedPayloads.first().second["query"])

        // Back navigation restores previous URL
        val back = browser.navigateBack().getOrThrow()
        assertEquals("Second Page", back.title)

        // Open second tab and switch back
        browser.openUrl("https://example.org", newTab = true).getOrThrow()
        assertEquals(2, browser.tabs.value.size)
        assertNotNull(browser.switchTab(1))
        assertTrue(browser.closeTab(2))
        assertEquals(1, browser.tabs.value.size)

        // SSRF protection blocks localhost & private LAN
        assertTrue(browser.openUrl("http://127.0.0.1:8080/admin").isFailure)
        assertTrue(browser.openUrl("http://192.168.1.1/router").isFailure)
    }

    // -------------------------------------------------------------------------
    // 5. ModelCapabilityDetector — Multi-provider capability & tier routing
    // -------------------------------------------------------------------------

    @Test
    fun modelCapabilityDetector_detectsVisionToolsAndTieredSelection() {
        val geminiCaps = ModelCapabilityDetector.detectCloudModel(
            provider = CloudProviderKind.GEMINI_REST,
            modelId = "gemini-2.5-flash"
        )
        assertTrue(geminiCaps.supportsTools)
        assertTrue(geminiCaps.supportsVision)
        assertEquals(ModelTier.FAST_SMALL, geminiCaps.tier)

        val tinyCaps = ModelCapabilityDetector.detectCloudModel(
            provider = CloudProviderKind.OPENROUTER,
            modelId = "tinyllama-1.1b"
        )
        assertFalse(tinyCaps.supportsTools)
        assertFalse(tinyCaps.supportsVision)

        val (_, strongTier) = ModelCapabilityDetector.selectTieredCloudModel(
            provider = CloudProviderKind.GEMINI_REST,
            configuredModelId = CloudProviderKind.GEMINI_REST.defaultModel,
            complexityScore = 8,
            needsVision = false
        )
        assertEquals(ModelTier.REASONING_STRONG, strongTier)
    }

    // -------------------------------------------------------------------------
    // 6. AppKnowledgeRepository — Verified screen learning + privacy filtering
    // -------------------------------------------------------------------------

    @Test
    fun appKnowledgeRepository_persistsVerifiedScreensAndRejectsSensitiveData() = runBlocking {
        val dao = InMemoryAppKnowledgeDao()
        val repo = AppKnowledgeRepository(dao)

        val youtubeObs = ScreenObservation(
            capturedAtMs = 2000L,
            activePackageName = "com.google.android.youtube",
            screenWidth = 1080,
            screenHeight = 2400,
            elements = listOf(
                node(0, "Search", clickable = true, id = "com.google.android.youtube:id/menu_item_1"),
                node(1, "Home", clickable = true)
            ),
            stabilityHash = "yt_home_123456"
        )

        val saved = repo.recordVerifiedObservation(
            observation = youtubeObs,
            appNameHint = "YouTube",
            workflowHint = "Tap Search icon at top right to open search input"
        )
        assertNotNull(saved)
        assertEquals("YouTube", saved!!.appName)
        assertEquals(1, saved.successCount)

        // Recording a sensitive secret hint must be rejected
        val rejectedSecret = repo.recordVerifiedObservation(
            observation = youtubeObs,
            workflowHint = "My password is supersecret123"
        )
        assertNull(rejectedSecret)

        // Blocked banking/password packages must never be persisted
        val blockedObs = youtubeObs.copy(
            activePackageName = "com.google.android.apps.walletnfcrel",
            blockedByPolicy = true
        )
        assertNull(repo.recordVerifiedObservation(blockedObs))
    }

    // -------------------------------------------------------------------------
    // 7. VirtualDisplayFeasibilityProbe — Honest Android 12–16 reporting
    // -------------------------------------------------------------------------

    @Test
    fun virtualDisplayProbe_reportsHonestConstraintsAcrossSdkAndBridges() {
        val noBridge = VirtualDisplayFeasibilityProbe.evaluate(
            capabilities = DeviceCapabilitySnapshot(),
            sdkInt = 35
        )
        assertEquals(VirtualDisplayFeasibility.MAIN_DISPLAY_FOREGROUND_REQUIRED, noBridge.feasibility)
        assertFalse(noBridge.privilegedShellAvailable)

        val shizukuApi35 = VirtualDisplayFeasibilityProbe.evaluate(
            capabilities = DeviceCapabilitySnapshot(
                shizuku = ShizukuStatusSnapshot(
                    availability = ShizukuAvailabilityState.READY,
                    isBinderAlive = true,
                    hasPermission = true
                )
            ),
            sdkInt = 35
        )
        assertEquals(VirtualDisplayFeasibility.SUPPORTED_WITH_SHELL_CONSTRAINTS, shizukuApi35.feasibility)
        assertTrue(shizukuApi35.privilegedShellAvailable)
    }

    // -------------------------------------------------------------------------
    // 8. JarvisMarkdownText parser — Headings, inline styles, lists
    // -------------------------------------------------------------------------

    @Test
    fun parseSimpleMarkdown_formatsHeadingsBoldCodeAndBulletsCleanly() {
        val rendered = parseSimpleMarkdown(
            raw = "# Status Report\n- **Battery**: `84%`\n> *Ready for tasks*",
            baseColor = Color.White,
            accentColor = Color.Cyan,
            codeBackground = Color.DarkGray
        )
        val text = rendered.text
        assertTrue(text.contains("Status Report"))
        assertTrue(text.contains("•  Battery:  84% "))
        assertTrue(text.contains("▎ Ready for tasks"))
        assertTrue(rendered.spanStyles.isNotEmpty())
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun node(
        index: Int,
        label: String,
        clickable: Boolean,
        className: String = "android.widget.Button",
        id: String? = null,
        cx: Int = 100,
        cy: Int = 100
    ) = ScreenElementNode(
        index = index,
        id = id,
        className = className,
        label = label,
        roleType = "Button",
        clickable = clickable,
        editable = false,
        scrollable = false,
        passwordProtected = false,
        boundsLeft = cx - 40,
        boundsTop = cy - 20,
        boundsRight = cx + 40,
        boundsBottom = cy + 20,
        centerX = cx,
        centerY = cy
    )

    private class InMemoryAppKnowledgeDao : AppKnowledgeDao {
        private val items = MutableStateFlow<List<AppKnowledgeEntity>>(emptyList())

        override fun observeAll(): Flow<List<AppKnowledgeEntity>> = items

        override suspend fun getAllOnce(): List<AppKnowledgeEntity> = items.value

        override suspend fun getByPackage(packageName: String): List<AppKnowledgeEntity> =
            items.value.filter { it.packageName.equals(packageName, ignoreCase = true) }

        override suspend fun findByPackageAndScreen(
            packageName: String,
            screenSignature: String
        ): AppKnowledgeEntity? = items.value.firstOrNull {
            it.packageName.equals(packageName, ignoreCase = true) && it.screenSignature == screenSignature
        }

        override suspend fun search(pattern: String): List<AppKnowledgeEntity> {
            val needle = pattern.replace("%", "").lowercase()
            return items.value.filter {
                it.appName.lowercase().contains(needle) ||
                    it.packageName.lowercase().contains(needle) ||
                    it.verifiedWorkflowHints.lowercase().contains(needle)
            }
        }

        override suspend fun upsert(entry: AppKnowledgeEntity) {
            items.value = items.value.filterNot { it.id == entry.id } + entry
        }

        override suspend fun deleteById(id: String) {
            items.value = items.value.filterNot { it.id == id }
        }

        override suspend fun clearAll() {
            items.value = emptyList()
        }
    }
}
