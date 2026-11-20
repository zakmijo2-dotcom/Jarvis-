package com.example.core.security

import com.example.core.web.PublicHttpSafetyGuard
import com.example.core.web.PublicHttpValidationResult
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Connect-time OkHttp DNS filter that blocks DNS rebinding and non-public targets
 * (loopback, RFC-1918, link-local / cloud metadata, CGNAT, IPv6 ULA, NAT64, 6to4,
 * IPv4-compatible, and IPv4-mapped non-public addresses).
 */
class SafePublicDns(
    private val allowLoopback: Boolean = false,
    private val delegate: Dns = Dns.SYSTEM
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val resolved = delegate.lookup(hostname)
        if (resolved.isEmpty()) {
            throw UnknownHostException("SSRF block: no addresses resolved for '$hostname'.")
        }
        for (addr in resolved) {
            if (!PublicHttpSafetyGuard.isPublicRoutableAddress(addr, allowLoopback = allowLoopback)) {
                throw UnknownHostException(
                    "SSRF block at connect time: '$hostname' resolved to non-public address (${addr.hostAddress})."
                )
            }
        }
        return resolved
    }
}

/**
 * Shared SSRF-safe HTTP client builder and manual redirect executor used across
 * browser automation, web extraction, model downloads, Hugging Face API, and MCP.
 */
object SafeNetwork {

    const val MAX_REDIRECT_HOPS = 4

    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

    /**
     * Builds an OkHttpClient with automatic redirects disabled and connect-time [SafePublicDns].
     */
    fun buildSafeClient(
        baseClient: OkHttpClient? = null,
        connectTimeoutSeconds: Long = 15L,
        readTimeoutSeconds: Long = 25L,
        allowLoopback: Boolean = false,
        dnsDelegate: Dns = Dns.SYSTEM
    ): OkHttpClient {
        val builder = baseClient?.newBuilder() ?: OkHttpClient.Builder()
        return builder
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .dns(SafePublicDns(allowLoopback = allowLoopback, delegate = dnsDelegate))
            .build()
    }

    /**
     * Executes [initialRequest] following up to [maxHops] HTTP redirects manually.
     *
     * Guarantees enforced on initial URL and every redirect hop BEFORE sending:
     * 1. Host & resolved IPs validated via [PublicHttpSafetyGuard.validateUrl].
     * 2. HTTPS -> HTTP downgrades are rejected.
     * 3. POST/body requests are never resent to a different host (307/308 cross-host rejected;
     *    301/302/303 drops body and downgrades to GET).
     * 4. `Authorization` header is stripped on any hop whose host is not trusted by [isTrustedAuthHost].
     *
     * The caller is responsible for closing the returned [Response].
     */
    fun executeWithSafeRedirects(
        client: OkHttpClient,
        initialRequest: Request,
        maxHops: Int = MAX_REDIRECT_HOPS,
        allowLoopback: Boolean = false,
        dnsResolver: (String) -> Array<InetAddress> = { host -> InetAddress.getAllByName(host) },
        isTrustedAuthHost: (HttpUrl) -> Boolean = HttpSecuritySupport::isTrustedHfAuthHost
    ): Response {
        val boundedMaxHops = maxHops.coerceIn(0, MAX_REDIRECT_HOPS)
        validateOrThrow(initialRequest.url.toString(), allowLoopback, dnsResolver)

        var currentRequest = initialRequest
        var hop = 0

        while (true) {
            val response = client.newCall(currentRequest).execute()
            if (response.code !in REDIRECT_CODES) {
                return response
            }

            val code = response.code
            val location = response.header("Location")
            val currentUrl = currentRequest.url
            response.close()

            if (location.isNullOrBlank()) {
                throw SecurityException("Redirect HTTP $code missing Location header.")
            }
            if (hop >= boundedMaxHops) {
                throw SecurityException("Too many HTTP redirects (exceeded max $boundedMaxHops hops).")
            }

            val nextUrl = currentUrl.resolve(location)
                ?: throw SecurityException("Invalid redirect target URL.")

            if (currentUrl.isHttps && !nextUrl.isHttps) {
                throw SecurityException(
                    "Blocked HTTPS-to-HTTP redirect downgrade (${currentUrl.host} -> ${nextUrl.host})."
                )
            }

            validateOrThrow(nextUrl.toString(), allowLoopback, dnsResolver)

            val isDifferentHost = !currentUrl.host.equals(nextUrl.host, ignoreCase = true) ||
                currentUrl.port != nextUrl.port
            val isPostOrHasBody = currentRequest.method.equals("POST", ignoreCase = true) ||
                currentRequest.body != null

            val nextReqBuilder = currentRequest.newBuilder().url(nextUrl)

            if (isPostOrHasBody) {
                when (code) {
                    307, 308 -> {
                        if (isDifferentHost) {
                            throw SecurityException(
                                "Blocked cross-host HTTP $code redirect that would resend ${currentRequest.method} body to '${nextUrl.host}'."
                            )
                        }
                    }

                    301, 302, 303 -> {
                        // Never resend POST body on 301/302/303; convert to bodyless GET
                        nextReqBuilder.method("GET", null)
                        nextReqBuilder.removeHeader("Content-Type")
                        nextReqBuilder.removeHeader("Content-Length")
                        nextReqBuilder.removeHeader("Transfer-Encoding")
                    }
                }
            }

            if (currentRequest.header("Authorization") != null) {
                val trusted = isTrustedAuthHost(nextUrl) && (!isDifferentHost || nextUrl.isHttps)
                if (!trusted) {
                    nextReqBuilder.removeHeader("Authorization")
                }
            }

            currentRequest = nextReqBuilder.build()
            hop++
        }
    }

    private fun validateOrThrow(
        rawUrl: String,
        allowLoopback: Boolean,
        dnsResolver: (String) -> Array<InetAddress>
    ) {
        when (val result = PublicHttpSafetyGuard.validateUrl(rawUrl, allowLoopback = allowLoopback, dnsResolver = dnsResolver)) {
            is PublicHttpValidationResult.Safe -> Unit
            is PublicHttpValidationResult.Blocked -> throw SecurityException("Blocked unsafe URL: ${result.reason}")
        }
    }
}
