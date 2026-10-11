package com.wwwescape.streamanything.data.streams

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Plain `http://` is allowed only to devices on the home network — e.g. a computer running
 * Stream Anything Server — and everything on the internet stays HTTPS-only.
 *
 * Only IP literals in private ranges count (the server hands out `http://192.168.x.x:8090/…`
 * links). Hostnames never do, because a name says nothing about where it resolves: allowing them
 * would let a public site be reached over cleartext through DNS.
 */
object HomeNetwork {

    fun isHomeAddress(host: String): Boolean {
        val bytes = ipv4(host) ?: ipv6(host) ?: return false
        return when (bytes.size) {
            4 -> isPrivateIpv4(bytes)
            16 -> isPrivateIpv6(bytes)
            else -> false
        }
    }

    /** Whether a request to [url] may go out: always over HTTPS, over HTTP only to the home network. */
    fun isAllowed(url: HttpUrl): Boolean = url.isHttps || isHomeAddress(url.host)

    /** Enforced on every request and redirect hop of the shared HTTP client (and so the player),
     * since the platform's cleartext policy can't express "private addresses only". */
    val cleartextGuard = Interceptor { chain ->
        val url = chain.request().url
        if (!isAllowed(url)) throw IOException("Blocked plain HTTP to a non-home-network address")
        chain.proceed(chain.request())
    }

    private fun isPrivateIpv4(b: ByteArray): Boolean {
        val first = b[0].toInt() and 0xFF
        val second = b[1].toInt() and 0xFF
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            first == 127 ||
            (first == 169 && second == 254)
    }

    private fun isPrivateIpv6(b: ByteArray): Boolean {
        val loopback = b.sliceArray(0 until 15).all { it == 0.toByte() } && b[15] == 1.toByte()
        val first = b[0].toInt() and 0xFF
        val second = b[1].toInt() and 0xFF
        val uniqueLocal = first and 0xFE == 0xFC // fc00::/7
        val linkLocal = first == 0xFE && second and 0xC0 == 0x80 // fe80::/10
        val ipv4Mapped = b.sliceArray(0 until 10).all { it == 0.toByte() } &&
            b[10] == 0xFF.toByte() && b[11] == 0xFF.toByte()
        return loopback || uniqueLocal || linkLocal || (ipv4Mapped && isPrivateIpv4(b.sliceArray(12 until 16)))
    }

    private fun ipv4(host: String): ByteArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        for ((i, part) in parts.withIndex()) {
            if (part.isEmpty() || part.length > 3 || !part.all(Char::isDigit)) return null
            val value = part.toInt()
            if (value > 255) return null
            bytes[i] = value.toByte()
        }
        return bytes
    }

    /** IPv6 literals only (as OkHttp gives them, without brackets); never triggers a DNS lookup.
     * IPv4-mapped literals (`::ffff:192.168.1.2`) come back as their 4-byte IPv4 address. */
    private fun ipv6(host: String): ByteArray? {
        if (':' !in host || !host.all { it.isLetterOrDigit() || it == ':' || it == '.' }) return null
        return runCatching { java.net.InetAddress.getByName(host).address }.getOrNull()
    }
}
