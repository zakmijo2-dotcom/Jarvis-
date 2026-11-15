package com.example.core.security

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream

/**
 * Network-layer helpers for redirect credential isolation and bounded HTTP body reading.
 */
object HttpSecuritySupport {

    const val DEFAULT_MAX_BODY_BYTES = 2L * 1024L * 1024L

    fun isTrustedHfAuthHost(url: HttpUrl): Boolean {
        val host = url.host.lowercase()
        val isTrustedHttpsHf = url.isHttps && (
            host == "huggingface.co" ||
                host == "hf.co"
            )
        val isLoopback = host == "localhost" || host == "127.0.0.1" || host == "::1"
        return isTrustedHttpsHf || isLoopback
    }

    /**
     * OkHttp network interceptor that strips any `Authorization` header on hops to external CDN/S3 hosts
     * (e.g., after HTTP 302 redirects from huggingface.co/resolve/... to pre-signed CDN URLs).
     */
    val hfRedirectAuthSanitizer: Interceptor = Interceptor { chain ->
        val req = chain.request()
        val finalReq = if (req.header("Authorization") != null && !isTrustedHfAuthHost(req.url)) {
            req.newBuilder()
                .removeHeader("Authorization")
                .build()
        } else {
            req
        }
        chain.proceed(finalReq)
    }

    fun readBoundedUtf8Body(
        body: ResponseBody?,
        maxBytes: Long = DEFAULT_MAX_BODY_BYTES
    ): String {
        if (body == null) return ""
        val contentLength = body.contentLength()
        if (contentLength > maxBytes) {
            throw IllegalStateException("HTTP response exceeded max allowed size (${maxBytes / 1024L} KB).")
        }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        body.byteStream().use { input ->
            while (true) {
                val read = input.read(buf)
                if (read == -1) break
                total += read
                if (total > maxBytes) {
                    throw IllegalStateException("HTTP response stream exceeded max allowed size (${maxBytes / 1024L} KB).")
                }
                out.write(buf, 0, read)
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
