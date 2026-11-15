package com.example

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.core.agent.AgentRoutingMode
import com.example.core.agent.ExecutionRoute
import com.example.core.ai.BatteryThermalSnapshot
import com.example.core.ai.EngineLoadState
import com.example.core.ai.ModelRecommendationBadge
import com.example.core.ai.ModelRouter
import com.example.core.ai.PromptCompressor
import com.example.core.model.ArtifactState
import com.example.core.model.HardwareFitTier
import com.example.core.model.ModelCompatibilityEngine
import com.example.core.model.ModelProviderType
import com.example.core.model.ModelSlot
import com.example.core.model.ModelStatus
import com.example.core.model.RuntimeFormatId
import com.example.core.security.HttpSecuritySupport
import com.example.core.security.SecureCredentialStore
import com.example.core.security.SecurityLogRedactor
import com.example.core.tools.DeviceTelemetry
import com.example.data.local.db.MessageEntity
import com.example.data.local.db.MessageRole
import com.example.data.local.db.ModelBenchmarkEntity
import com.example.data.repository.JarvisSettings
import com.sun.net.httpserver.HttpServer
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiRouterAndSecurityTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun sampleLocalSlot(
        id: String = "slot_gemma",
        name: String = "Gemma 3 1B",
        sizeBytes: Long = 600L * 1024L * 1024L,
        format: RuntimeFormatId = RuntimeFormatId.LITERT_LM
    ) = ModelSlot(
        id = id,
        displayName = name,
        providerType = ModelProviderType.LOCAL_ON_DEVICE,
        engineFamily = "LiteRT-LM",
        parameterHint = "1B",
        contextWindow = 4096,
        status = ModelStatus.READY,
        isActive = true,
        isCustom = false,
        localFilePath = "/tmp/fake.litertlm",
        expectedSizeBytes = sizeBytes,
        artifactState = ArtifactState.READY,
        runtimeFormat = format
    )

    private fun sampleCloudSlot() = ModelSlot(
        id = "slot_gemini_cloud",
        displayName = "Gemini Cloud Core",
        providerType = ModelProviderType.CLOUD_API,
        engineFamily = "Cloud Provider API",
        parameterHint = "Cloud",
        contextWindow = 1048576,
        status = ModelStatus.UNCONFIGURED,
        isActive = false,
        isCustom = false,
        runtimeFormat = RuntimeFormatId.UNKNOWN
    )

    private fun sampleTelemetry(
        totalRamGb: Long = 8L,
        availRamGb: Long = 4L,
        connected: Boolean = true
    ) = DeviceTelemetry(
        manufacturer = "Google",
        model = "Pixel",
        brand = "google",
        device = "pixel",
        hardware = "tensor",
        socModel = "Tensor G3",
        supportedAbis = "arm64-v8a",
        cpuCoreCount = 8,
        androidRelease = "14",
        sdkInt = 34,
        securityPatch = "2025-01-01",
        buildDisplay = "AP2A",
        totalRamBytes = totalRamGb * 1024L * 1024L * 1024L,
        availRamBytes = availRamGb * 1024L * 1024L * 1024L,
        lowMemory = false,
        totalStorageBytes = 128L * 1024L * 1024L * 1024L,
        freeStorageBytes = 20L * 1024L * 1024L * 1024L,
        batteryPercent = 82,
        batteryStatus = "Discharging",
        batteryPlugged = "Battery",
        batteryTempCelsius = 31.0f,
        batteryVoltageMv = 3900,
        batteryHealth = "Nominal",
        thermalStatusLabel = "Nominal",
        thermalCode = 0,
        isNetworkConnected = connected,
        networkTransport = if (connected) "Wi-Fi" else "Offline",
        isNetworkValidated = connected,
        isNetworkMetered = false,
        downstreamBandwidthKbps = 50_000,
        capturedAtMillis = 1000L
    )

    @Test
    fun `model router ranks hardware fit accurately and caps context under thermal strain`() {
        val router = ModelRouter()
        val localSlot = sampleLocalSlot()
        val hugeSlot = sampleLocalSlot(
            id = "slot_huge",
            name = "Giant 14B",
            sizeBytes = 7_500L * 1024L * 1024L
        ).copy(isActive = false)
        val cloudSlot = sampleCloudSlot()

        val bench = ModelBenchmarkEntity(
            id = "b1",
            modelId = localSlot.id,
            modelName = localSlot.displayName,
            backendUsed = "GPU",
            ttftMs = 420L,
            tokensPerSecond = 21.5f,
            outputTokens = 120,
            prefillTokens = 64,
            nativeHeapMb = 680f,
            timestamp = 1000L
        )

        val settings = JarvisSettings(
            routingMode = AgentRoutingMode.AUTO_HYBRID,
            cloudUploadConsentGranted = true,
            cloudApiKey = "sk-test-1234",
            contextLengthTokens = 8192
        )

        // 1. Nominal thermal: selects local LiteRT-LM and reports real measured metrics
        val nominalPlan = router.routeTask(
            prompt = "Summarize my schedule today.",
            historyChars = 120,
            settings = settings,
            models = listOf(localSlot, hugeSlot, cloudSlot),
            loadState = EngineLoadState.Unloaded,
            telemetry = sampleTelemetry(),
            batterySnapshot = BatteryThermalSnapshot(batteryPercent = 82, thermalStatusCode = 0, thermalStatusLabel = "Nominal"),
            benchmarks = listOf(bench)
        )
        assertEquals(ExecutionRoute.LOCAL_LITERT_LM, nominalPlan.decision.route)
        assertEquals(localSlot.id, nominalPlan.preferredSlot?.id)
        val localRec = nominalPlan.recommendations.first { it.slotId == localSlot.id }
        val hugeRec = nominalPlan.recommendations.first { it.slotId == hugeSlot.id }
        assertEquals(HardwareFitTier.OPTIMAL, localRec.fitTier)
        assertNotNull(localRec.measuredMetrics)
        assertEquals(420L, localRec.measuredMetrics?.avgTtftMs)
        assertEquals(HardwareFitTier.INSUFFICIENT_RAM, hugeRec.fitTier)
        assertEquals(ModelRecommendationBadge.INSUFFICIENT_RAM, hugeRec.badge)
        // Cloud has no benchmark -> measuredMetrics stays null (never invented)
        assertNull(nominalPlan.recommendations.first { it.slotId == cloudSlot.id }.measuredMetrics)

        // 2. Severe thermal pressure offloads synthesis to consented cloud and caps local context
        val hotPlan = router.routeTask(
            prompt = "Explain JVM GC tradeoffs.",
            historyChars = 400,
            settings = settings,
            models = listOf(localSlot, hugeSlot, cloudSlot),
            loadState = EngineLoadState.Unloaded,
            telemetry = sampleTelemetry(),
            batterySnapshot = BatteryThermalSnapshot(batteryPercent = 12, isCharging = false, thermalStatusCode = 3, thermalStatusLabel = "Severe"),
            benchmarks = listOf(bench)
        )
        assertTrue(hotPlan.decision.useCloudModel)
        assertEquals(2048, hotPlan.recommendedContextTokens)
        assertNotNull(hotPlan.thermalBatteryNote)
    }

    @Test
    fun `model router blocks over-budget model loads before native allocation`() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(am).setMemoryInfo(
            ActivityManager.MemoryInfo().apply {
                totalMem = 4L * 1024L * 1024L * 1024L
                availMem = 1200L * 1024L * 1024L
                lowMemory = false
            }
        )
        val router = ModelRouter()
        val oversizedSlot = sampleLocalSlot(sizeBytes = 3_600L * 1024L * 1024L)
        val verdict = router.evaluateLoadMemoryBudget(
            context = context,
            incomingSlot = oversizedSlot,
            currentlyLoadedSizesBytes = emptyMap()
        )
        assertFalse(verdict.allowedToLoad)
        assertTrue(verdict.reason.contains("exceeds 85%"))
    }

    @Test
    fun `prompt compressor preserves recent turns verbatim and summarizes older turns within budget`() {
        val messages = (1..10).map { i ->
            MessageEntity(
                id = "m$i",
                conversationId = "c1",
                role = if (i % 2 == 1) MessageRole.USER else MessageRole.ASSISTANT,
                content = "Turn $i detailed engineering context " + "x".repeat(180),
                modelLabel = "Test",
                timestamp = i.toLong() * 1000L
            )
        }
        val compressed = PromptCompressor.compressHistory(messages, contextBudgetTokens = 1024)
        assertTrue(compressed.compactedTurnCount > 0)
        assertTrue(compressed.turns.size < messages.size)
        assertTrue(compressed.turns.first().content.contains("[COMPACTED PRIOR CONTEXT"))
        assertTrue(compressed.turns.last().content.startsWith("Turn 10"))
    }

    @Test
    fun `okhttp network interceptor strips Authorization on external CDN redirect while preserving loopback source`() {
        val originServer = HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        val cdnServer = HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        val originAuthHeaders = mutableListOf<String?>()
        val cdnAuthHeaders = mutableListOf<String?>()

        originServer.createContext("/") { ex ->
            originAuthHeaders += ex.requestHeaders.getFirst("Authorization")
            val cdnPort = cdnServer.address.port
            ex.responseHeaders.add("Location", "http://cdn.untrusted.example:$cdnPort/weights.litertlm")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }
        cdnServer.createContext("/") { ex ->
            cdnAuthHeaders += ex.requestHeaders.getFirst("Authorization")
            val payload = "OK_WEIGHTS".toByteArray()
            ex.sendResponseHeaders(200, payload.size.toLong())
            ex.responseBody.use { it.write(payload) }
        }

        originServer.start()
        cdnServer.start()

        try {
            val customDns = object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    listOf(InetAddress.getByName("127.0.0.1"))
            }
            val client = OkHttpClient.Builder()
                .dns(customDns)
                .followRedirects(true)
                .addNetworkInterceptor(HttpSecuritySupport.hfRedirectAuthSanitizer)
                .build()

            val request = Request.Builder()
                .url("http://localhost:${originServer.address.port}/resolve/main/model.litertlm")
                .header("Authorization", "Bearer hf_top_secret_token_999")
                .build()

            client.newCall(request).execute().use { res ->
                assertEquals(200, res.code)
                assertEquals("OK_WEIGHTS", res.body?.string())
            }

            assertEquals(listOf("Bearer hf_top_secret_token_999"), originAuthHeaders)
            assertEquals("Redirect to non-HF CDN host must NOT carry Authorization", listOf<String?>(null), cdnAuthHeaders)

            assertTrue(HttpSecuritySupport.isTrustedHfAuthHost("https://huggingface.co/model".toHttpUrl()))
            assertFalse(HttpSecuritySupport.isTrustedHfAuthHost("https://cdn-lfs.huggingface.co/blob".toHttpUrl()))
            assertFalse(HttpSecuritySupport.isTrustedHfAuthHost("https://cas-bridge.xethub.hf.co/blob".toHttpUrl()))
            assertFalse(HttpSecuritySupport.isTrustedHfAuthHost("http://huggingface.co/insecure".toHttpUrl()))
        } finally {
            originServer.stop(0)
            cdnServer.stop(0)
        }
    }

    @Test
    fun `security log redactor masks secrets pairing codes and records credential store errors`() {
        val raw = "Failed calling endpoint with Authorization: Bearer hf_AbCdEf1234567890, x-goog-api-key=AIzaSyTestKey12345, pair code 482910"
        val clean = SecurityLogRedactor.redact(raw)
        assertFalse(clean.contains("hf_AbCdEf1234567890"))
        assertFalse(clean.contains("AIzaSyTestKey12345"))
        assertFalse(clean.contains("482910"))
        assertTrue(clean.contains("Bearer [REDACTED]"))

        val store = SecureCredentialStore()
        // Tampered cipher string fails cleanly and records redacted error
        val decrypted = store.decrypt("enc_fb_v1:not-valid-base64!")
        assertEquals("", decrypted)
        assertNotNull(store.lastKeystoreError)
    }

    @Test
    fun `compatibility engine marks context-scaled ram and rejects unbundled runtime formats cleanly`() {
        val compat = ModelCompatibilityEngine()
        val repo = com.example.core.hf.HfRepoDetail(
            repoId = "user/test-litert",
            author = "user",
            modelName = "TestLite",
            downloads = 100,
            likes = 5,
            isPrivate = false,
            isGated = false,
            gatedMode = null,
            gatedPrompt = null,
            license = "apache-2.0",
            architecture = "Gemma 3",
            parameterCount = "1B",
            quantizationSummary = "INT4",
            contextLengthHint = 8192,
            supportedTasks = emptyList(),
            libraryName = "litert-lm",
            baseModel = null,
            tags = emptyList(),
            files = emptyList()
        )
        val file4k = com.example.core.hf.HfRepoFile("test.litertlm", 500_000_000L, null, "https://huggingface.co/test.litertlm")
        val r2k = compat.inspect(repo, file4k, sampleTelemetry(), 10L * 1024L * 1024L * 1024L, hasUserToken = false, contextLengthTokens = 2048)
        val r8k = compat.inspect(repo, file4k, sampleTelemetry(), 10L * 1024L * 1024L * 1024L, hasUserToken = false, contextLengthTokens = 8192)
        assertTrue(r8k.estimatedMemoryBytes > r2k.estimatedMemoryBytes)
        assertEquals(HardwareFitTier.OPTIMAL, r8k.hardwareFit)
    }
}
