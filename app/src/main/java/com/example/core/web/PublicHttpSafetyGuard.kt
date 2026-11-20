package com.example.core.web

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

sealed interface PublicHttpValidationResult {
    data class Safe(val normalizedUrl: String, val host: String) : PublicHttpValidationResult
    data class Blocked(val reason: String) : PublicHttpValidationResult
}

/**
 * Strict SSRF guard preventing internal network, loopback, metadata service, and private subnet probing.
 * Clean-room implementation inspired by AndroidHarness PublicHttpAddresses.
 */
object PublicHttpSafetyGuard {

    fun requireSafePublicUrl(rawUrl: String, allowLoopback: Boolean = false): String {
        return when (val result = validateUrl(rawUrl, allowLoopback = allowLoopback)) {
            is PublicHttpValidationResult.Safe -> result.normalizedUrl
            is PublicHttpValidationResult.Blocked -> throw SecurityException(result.reason)
        }
    }

    fun validateUrl(
        rawUrl: String,
        allowLoopback: Boolean = false,
        dnsResolver: (String) -> Array<InetAddress> = { host -> InetAddress.getAllByName(host) }
    ): PublicHttpValidationResult {
        val trimmed = rawUrl.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return PublicHttpValidationResult.Blocked("Malformed URL.")

        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme != "https" && scheme != "http") {
            return PublicHttpValidationResult.Blocked("Only http/https URLs are permitted.")
        }
        if (!uri.userInfo.isNullOrEmpty()) {
            return PublicHttpValidationResult.Blocked("URLs containing embedded user credentials are blocked.")
        }
        val host = uri.host?.lowercase()?.trim('[', ']').orEmpty()
        if (host.isBlank()) {
            return PublicHttpValidationResult.Blocked("URL has no valid host.")
        }
        if (host == "metadata.google.internal" || host.endsWith(".local") || host.endsWith(".internal")) {
            return PublicHttpValidationResult.Blocked("Internal or metadata hostnames are blocked ($host).")
        }

        val resolved = runCatching { dnsResolver(host) }.getOrNull()
        if (resolved == null || resolved.isEmpty()) {
            // If DNS fails in offline mode, reject private literal strings first cleanly.
            if (isBlockedLiteralHost(host, allowLoopback)) {
                return PublicHttpValidationResult.Blocked("Private or loopback host blocked ($host).")
            }
            return PublicHttpValidationResult.Blocked("Could not resolve public host '$host'.")
        }

        for (addr in resolved) {
            if (!isPublicRoutableAddress(addr, allowLoopback)) {
                return PublicHttpValidationResult.Blocked(
                    "SSRF block: '$host' resolved to non-public IP (${addr.hostAddress})."
                )
            }
        }
        return PublicHttpValidationResult.Safe(trimmed, host)
    }

    fun isPublicRoutableAddress(addr: InetAddress, allowLoopback: Boolean = false): Boolean {
        if (allowLoopback && addr.isLoopbackAddress) return true
        if (addr.isAnyLocalAddress ||
            addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isMulticastAddress ||
            addr.isSiteLocalAddress
        ) {
            return false
        }

        val bytes = addr.address
        if (addr is Inet4Address && bytes.size == 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0) return false // 0.0.0.0/8
            if (b0 == 10) return false // 10.0.0.0/8
            if (b0 == 100 && b1 in 64..127) return false // 100.64.0.0/10 CGNAT
            if (b0 == 127) return false // 127.0.0.0/8
            if (b0 == 169 && b1 == 254) return false // 169.254.0.0/16 link-local & cloud metadata
            if (b0 == 172 && b1 in 16..31) return false // 172.16.0.0/12
            if (b0 == 192 && b1 == 168) return false // 192.168.0.0/16
            if (b0 == 192 && b1 == 0 && (bytes[2].toInt() and 0xFF) == 0) return false
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return false // Benchmarking
            if (b0 >= 224) return false // Multicast / reserved
            return true
        }

        if (addr is Inet6Address && bytes.size == 16) {
            val firstByte = bytes[0].toInt() and 0xFF
            val secondByte = bytes[1].toInt() and 0xFF
            // Unique Local Address (fc00::/7)
            if ((firstByte and 0xFE) == 0xFC) return false
            // Link-local fe80::/10
            if (firstByte == 0xFE && (secondByte and 0xC0) == 0x80) return false
            // Documentation 2001:db8::/32
            if (firstByte == 0x20 && secondByte == 0x01 &&
                (bytes[2].toInt() and 0xFF) == 0x0D && (bytes[3].toInt() and 0xFF) == 0xB8
            ) return false
            // Local-use NAT64 64:ff9b:1::/48 (RFC 8215)
            if (firstByte == 0x00 && secondByte == 0x64 &&
                (bytes[2].toInt() and 0xFF) == 0xFF && (bytes[3].toInt() and 0xFF) == 0x9B &&
                (bytes[4].toInt() and 0xFF) == 0x00 && (bytes[5].toInt() and 0xFF) == 0x01
            ) return false
            // Well-known NAT64 64:ff9b::/96 (RFC 6052) -> inspect embedded IPv4 in bytes 12..15
            val isNat64WellKnown = firstByte == 0x00 && secondByte == 0x64 &&
                (bytes[2].toInt() and 0xFF) == 0xFF && (bytes[3].toInt() and 0xFF) == 0x9B &&
                (4..11).all { bytes[it].toInt() == 0 }
            if (isNat64WellKnown) {
                val v4 = InetAddress.getByAddress(bytes.copyOfRange(12, 16))
                return isPublicRoutableAddress(v4, allowLoopback)
            }
            // 6to4 2002::/16 (RFC 3056) -> inspect embedded IPv4 in bytes 2..5
            if (firstByte == 0x20 && secondByte == 0x02) {
                val v4 = InetAddress.getByAddress(bytes.copyOfRange(2, 6))
                return isPublicRoutableAddress(v4, allowLoopback)
            }
            // IPv4-mapped ::ffff:0:0/96 -> inspect embedded IPv4 in bytes 12..15
            val isV4Mapped = (0..9).all { bytes[it].toInt() == 0 } &&
                (bytes[10].toInt() and 0xFF) == 0xFF &&
                (bytes[11].toInt() and 0xFF) == 0xFF
            if (isV4Mapped) {
                val v4 = InetAddress.getByAddress(bytes.copyOfRange(12, 16))
                return isPublicRoutableAddress(v4, allowLoopback)
            }
            // IPv4-translated ::ffff:0:0:0/96 -> inspect embedded IPv4 in bytes 12..15
            val isV4Translated = (0..7).all { bytes[it].toInt() == 0 } &&
                (bytes[8].toInt() and 0xFF) == 0xFF &&
                (bytes[9].toInt() and 0xFF) == 0xFF &&
                bytes[10].toInt() == 0 &&
                bytes[11].toInt() == 0
            if (isV4Translated) {
                val v4 = InetAddress.getByAddress(bytes.copyOfRange(12, 16))
                return isPublicRoutableAddress(v4, allowLoopback)
            }
            // IPv4-compatible ::/96 (deprecated RFC 4291, excluding :: and ::1 already handled above)
            val isV4Compatible = (0..11).all { bytes[it].toInt() == 0 }
            if (isV4Compatible) {
                val v4 = InetAddress.getByAddress(bytes.copyOfRange(12, 16))
                return isPublicRoutableAddress(v4, allowLoopback)
            }
            return true
        }
        return false
    }

    private fun isBlockedLiteralHost(host: String, allowLoopback: Boolean): Boolean {
        if (!allowLoopback && (host == "localhost" || host == "127.0.0.1" || host == "::1")) return true
        return host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            host.startsWith("169.254.") ||
            host.startsWith("172.") ||
            host.startsWith("100.") ||
            host == "0.0.0.0" ||
            host.startsWith("fc") ||
            host.startsWith("fd") ||
            host.startsWith("fe80:") ||
            host.startsWith("64:ff9b:") ||
            host.startsWith("2002:") ||
            host.startsWith("::ffff:") ||
            host.startsWith("::")
    }
}
