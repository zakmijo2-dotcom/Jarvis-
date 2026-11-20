package com.example

import com.example.core.security.SafeNetwork
import com.example.core.security.SafePublicDns
import com.example.core.web.PublicHttpSafetyGuard
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class SecurityHardeningTest {

    // -------------------------------------------------------------------------
    // PART 0 — Shared SafeNetwork & IPv6 SSRF Address Classification
    // -------------------------------------------------------------------------

    @Test
    fun publicHttpSafetyGuard_blocksIpv6TransitionAndSpecialRanges() {
        // NAT64 well-known 64:ff9b::127.0.0.1 and 64:ff9b::169.254.169.254
        val nat64Loopback = InetAddress.getByAddress(
            byteArrayOf(0x00, 0x64, 0xFF.toByte(), 0x9B.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 127, 0, 0, 1)
        )
        val nat64Metadata = InetAddress.getByAddress(
            byteArrayOf(0x00, 0x64, 0xFF.toByte(), 0x9B.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 169.toByte(), 254.toByte(), 169.toByte(), 254.toByte())
        )
        val nat64Public = InetAddress.getByAddress(
            byteArrayOf(0x00, 0x64, 0xFF.toByte(), 0x9B.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 93, 184.toByte(), 216.toByte(), 34)
        )
        assertFalse(PublicHttpSafetyGuard.isPublicRoutableAddress(nat64Loopback))
        assertFalse(PublicHttpSafetyGuard.isPublicRoutableAddress(nat64Metadata))
        assertTrue(PublicHttpSafetyGuard.isPublicRoutableAddress(nat64Public))

        // 6to4 2002:0a00:0001:: (embeds 10.0.0.1) vs 2002:5db8:d822:: (embeds 93.184.216.34)
        val sixToFourPrivate = InetAddress.getByAddress(
            byteArrayOf(0x20, 0x02, 10, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        )
        val sixToFourPublic = InetAddress.getByAddress(
            byteArrayOf(0x20, 0x02, 93, 184.toByte(), 216.toByte(), 34, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        )
        assertFalse(PublicHttpSafetyGuard.isPublicRoutableAddress(sixToFourPrivate))
        assertTrue(PublicHttpSafetyGuard.isPublicRoutableAddress(sixToFourPublic))

        // IPv4-compatible ::10.0.0.5 and ::127.0.0.1
        val v4CompatPrivate = InetAddress.getByAddress(
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 5)
        )
        assertFalse(PublicHttpSafetyGuard.isPublicRoutableAddress(v4CompatPrivate))

        // IPv4-mapped ::ffff:169.254.169.254
        val v4MappedMeta = InetAddress.getByAddress(
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xFF.toByte(), 0xFF.toByte(), 169.toByte(), 254.toByte(), 169.toByte(), 254.toByte())
        )
        assertFalse(PublicHttpSafetyGuard.isPublicRoutableAddress(v4MappedMeta))

        // IPv6 ULA fd00::1
        val ula = InetAddress.getByAddress(
            byteArrayOf(0xFD.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        )
        assertFalse(PublicHttpSafetyGuard.isPublicRoutableAddress(ula))
    }

    @Test
    fun safePublicDns_rejectsNonPublicResolvedAddressesAtConnectTime() {
        val dns = SafePublicDns(
            allowLoopback = false,
            delegate = object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = when (hostname) {
                    "rebind.example" -> listOf(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
                    "public.example" -> listOf(InetAddress.getByAddress(byteArrayOf(93, 184.toByte(), 216.toByte(), 34)))
                    else -> emptyList()
                }
            }
        )
        assertEquals(1, dns.lookup("public.example").size)
        try {
            dns.lookup("rebind.example")
            fail("Expected UnknownHostException for loopback DNS resolution")
        } catch (_: UnknownHostException) {
            // Expected
        }
    }

    @Test
    fun safeNetwork_blocksRedirectsToPrivateLoopbackMetadataUlaAndHttpDowngrade() {
        val publicAddr = arrayOf(InetAddress.getByAddress(byteArrayOf(93, 184.toByte(), 216.toByte(), 34)))
        val fakeResolver: (String) -> Array<InetAddress> = { host ->
            when (host) {
                "start.example", "other.example" -> publicAddr
                "127.0.0.1" -> arrayOf(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
                "169.254.169.254" -> arrayOf(InetAddress.getByAddress(byteArrayOf(169.toByte(), 254.toByte(), 169.toByte(), 254.toByte())))
                "10.0.0.5" -> arrayOf(InetAddress.getByAddress(byteArrayOf(10, 0, 0, 5)))
                "fd00::1" -> arrayOf(InetAddress.getByAddress(byteArrayOf(0xFD.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)))
                else -> InetAddress.getAllByName(host)
            }
        }

        val forbiddenTargets = listOf(
            "https://127.0.0.1/admin",
            "https://169.254.169.254/latest/meta-data/",
            "https://10.0.0.5/internal",
            "https://[fd00::1]/secret",
            "http://other.example/downgraded"
        )

        for (target in forbiddenTargets) {
            var secondHopAttempted = false
            val client = SafeNetwork.buildSafeClient().newBuilder()
                .addInterceptor { chain ->
                    val req = chain.request()
                    if (req.url.host == "start.example") {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(302)
                            .message("Found")
                            .header("Location", target)
                            .body("".toResponseBody())
                            .build()
                    } else {
                        secondHopAttempted = true
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("should never reach".toResponseBody())
                            .build()
                    }
                }
                .build()

            try {
                SafeNetwork.executeWithSafeRedirects(
                    client = client,
                    initialRequest = Request.Builder().url("https://start.example/init").get().build(),
                    dnsResolver = fakeResolver
                ).close()
                fail("Expected SecurityException for redirect to $target")
            } catch (_: SecurityException) {
                assertFalse("Blocked redirect $target must NEVER be sent on wire", secondHopAttempted)
            }
        }
    }

    @Test
    fun safeNetwork_blocksCrossHostPost307AndStripsBodyOn302AndStripsAuthOnUntrustedHost() {
        val publicAddr = arrayOf(InetAddress.getByAddress(byteArrayOf(93, 184.toByte(), 216.toByte(), 34)))
        val resolver: (String) -> Array<InetAddress> = { publicAddr }

        // 1. 307 POST to different host -> MUST reject
        var hopReached = false
        val client307 = SafeNetwork.buildSafeClient().newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.url.host == "origin.example") {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(307)
                        .message("Temporary Redirect")
                        .header("Location", "https://evil.example/steal")
                        .body("".toResponseBody())
                        .build()
                } else {
                    hopReached = true
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("ok".toResponseBody()).build()
                }
            }
            .build()

        try {
            val postReq = Request.Builder()
                .url("https://origin.example/submit")
                .post("secret=123".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            SafeNetwork.executeWithSafeRedirects(client307, postReq, dnsResolver = resolver).close()
            fail("Expected SecurityException for cross-host 307 POST redirect")
        } catch (_: SecurityException) {
            assertFalse("Cross-host 307 POST must not contact destination host", hopReached)
        }

        // 2. 302 POST to different host -> switches to bodyless GET AND strips Authorization on untrusted host
        var capturedSecondReq: Request? = null
        val client302 = SafeNetwork.buildSafeClient().newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.url.host == "huggingface.co") {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "https://cdn-lfs.huggingface.co/file")
                        .body("".toResponseBody())
                        .build()
                } else {
                    capturedSecondReq = req
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("done".toResponseBody()).build()
                }
            }
            .build()

        val authPost = Request.Builder()
            .url("https://huggingface.co/api/submit")
            .header("Authorization", "Bearer hf_secret_token")
            .post("data=1".toRequestBody("text/plain".toMediaType()))
            .build()
        SafeNetwork.executeWithSafeRedirects(client302, authPost, dnsResolver = resolver).use { resp ->
            assertEquals(200, resp.code)
        }
        assertEquals("GET", capturedSecondReq?.method)
        assertNull("POST body must not be resent on 302 cross-host redirect", capturedSecondReq?.body)
        assertNull("Authorization header must be stripped on untrusted CDN host", capturedSecondReq?.header("Authorization"))
    }
}
