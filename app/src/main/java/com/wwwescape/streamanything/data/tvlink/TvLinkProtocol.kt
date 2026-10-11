package com.wwwescape.streamanything.data.tvlink

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * "Play on TV": the phone sends a link to Stream Anything on a Google TV on the same Wi-Fi —
 * and "Continue on phone", the TV sending one back to a paired phone. Kept free of Android so
 * it's unit-testable; [LinkReceiver] and [LinkSender] do the networking on both devices.
 * Pairing is always started from the phone; either side may then send.
 *
 * One TCP connection carries one short conversation of JSON lines (each at most
 * [MAX_LINE_BYTES]). The TV speaks first with a hello holding a fresh random challenge, then
 * answers the phone's requests:
 *
 * - **play** — the link and where to start (optional), MAC'd with the pair's shared secret
 *   over the challenge, so a recorded request can't be replayed and no one else on the network
 *   can push videos.
 * - **pair** → **confirm** — one-time pairing. Both sides send an ephemeral P-256 key; the TV
 *   shows a 6-digit code, the user types it on the phone, and both derive the secret from the
 *   ECDH result *and* the code ([pairingSecret]). Each side then proves it holds the same secret
 *   ([confirmMac]), so a wrong code (or a device that never saw it) fails, and a passive listener
 *   learns nothing. The TV allows [MAX_CODE_ATTEMPTS] tries per code. (An attacker actively
 *   intercepting the pairing itself could still brute-force the code offline; a full PAKE would
 *   close that, at a cost not worth it for a one-time step on a home network.)
 */
object TvLinkProtocol {

    /** What TVs announce (phones send to them), and what phones announce (TVs send back). */
    const val SERVICE_TYPE_TV = "_streamanything._tcp"
    const val SERVICE_TYPE_PHONE = "_streamanyphone._tcp"
    const val APP_ID = "streamanything"
    const val VERSION = 1

    /** The port each device listens on when it's free (another one otherwise), so an address
     * typed by hand ("192.168.1.42") is enough when network discovery doesn't work. */
    const val DEFAULT_PORT = 47621

    const val MAX_LINE_BYTES = 8 * 1024
    const val MAX_URL_LENGTH = 2048
    const val MAX_NAME_LENGTH = 48
    const val CODE_LENGTH = 6
    const val MAX_CODE_ATTEMPTS = 3

    /** A position past this (a week) is nonsense, not a real video. */
    const val MAX_POSITION_MS = 7L * 24 * 60 * 60 * 1000

    const val CHALLENGE_BYTES = 16
    const val SECRET_BYTES = 32

    // Message types and fields.
    const val TYPE_PLAY = "play"
    const val TYPE_PAIR = "pair"
    const val TYPE_CONFIRM = "confirm"

    // Errors the TV can answer with.
    const val ERROR_NOT_PAIRED = "not_paired"
    /** Pairing is only started from the phone. */
    const val ERROR_PAIR_FROM_PHONE = "pair_from_phone"
    const val ERROR_BAD_REQUEST = "bad_request"
    const val ERROR_UNSUPPORTED_LINK = "unsupported_link"
    const val ERROR_BUSY = "busy"
    const val ERROR_WRONG_CODE = "wrong_code"
    const val ERROR_TOO_MANY_ATTEMPTS = "too_many_attempts"

    private val random = SecureRandom()
    private val WHITESPACE = Regex("\\s+")
    private val UUID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    fun newChallenge(): ByteArray = ByteArray(CHALLENGE_BYTES).also(random::nextBytes)

    fun newCode(): String = (0 until CODE_LENGTH).joinToString("") { random.nextInt(10).toString() }

    fun isValidDeviceId(id: String?): Boolean = id != null && UUID_PATTERN.matches(id)

    fun isValidCode(code: String): Boolean = code.length == CODE_LENGTH && code.all { it in '0'..'9' }

    fun isValidPosition(positionMs: Long): Boolean = positionMs in 0..MAX_POSITION_MS

    /** A device name as received from the other side: printable, single-line, and short. */
    fun cleanName(name: String?): String? =
        name?.map { if (it.isISOControl()) ' ' else it }?.joinToString("")
            ?.replace(WHITESPACE, " ")?.trim()?.take(MAX_NAME_LENGTH)?.trim()?.takeIf { it.isNotEmpty() }

    private val IPV4_WITH_PORT = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})(?::(\d{1,5}))?$""")

    /**
     * A device address typed by hand: a local IPv4 address, optionally with a port
     * ("192.168.1.42" or "192.168.1.42:47621"). Parsed without any DNS lookup, and only local
     * addresses are accepted; `null` for anything else.
     */
    fun parseAddress(input: String): InetSocketAddress? {
        val match = IPV4_WITH_PORT.matchEntire(input.trim()) ?: return null
        val octets = match.groupValues.subList(1, 5).map { it.toInt() }
        if (octets.any { it > 255 }) return null
        val port = match.groupValues[5].takeIf { it.isNotEmpty() }?.toInt() ?: DEFAULT_PORT
        if (port !in 1..65535) return null
        val address = InetAddress.getByAddress(ByteArray(4) { octets[it].toByte() })
        return InetSocketAddress(address, port).takeIf { isLocalAddress(address) && !address.isLoopbackAddress }
    }

    /** How an address is shown and stored: "192.168.1.42", plus ":port" when not the default. */
    fun formatAddress(address: InetAddress, port: Int): String {
        val host = address.hostAddress?.substringBefore('%').orEmpty()
        val shown = if (address is Inet6Address) "[$host]" else host
        return if (port == DEFAULT_PORT) shown else "$shown:$port"
    }

    /** Only devices on the local network may talk to the TV — never a public address. */
    fun isLocalAddress(address: InetAddress): Boolean =
        address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
            (address is Inet6Address && (address.address[0].toInt() and 0xfe) == 0xfc)

    // --- Pairing --------------------------------------------------------------------------

    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1"), random) }
        .generateKeyPair()

    fun encodePublicKey(key: PublicKey): ByteArray = key.encoded

    /** @throws java.security.GeneralSecurityException for anything that isn't a P-256 key. */
    fun decodePublicKey(encoded: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))

    /**
     * The pair's shared secret: the ECDH result bound to the code and to everything else both
     * sides saw (challenge, both keys, both IDs), so a different code or a swapped key on either
     * side gives a different secret.
     */
    fun pairingSecret(
        ownKeys: KeyPair,
        peerPublicKey: ByteArray,
        code: String,
        challenge: ByteArray,
        phoneKey: ByteArray,
        tvKey: ByteArray,
        phoneId: String,
        tvId: String,
    ): ByteArray {
        val shared = KeyAgreement.getInstance("ECDH").run {
            init(ownKeys.private)
            doPhase(decodePublicKey(peerPublicKey), true)
            generateSecret()
        }
        return MessageDigest.getInstance("SHA-256").digest(
            canonical("streamanything-pair-v$VERSION", shared, code, challenge, phoneKey, tvKey, phoneId, tvId),
        )
    }

    /** Proof of holding the pairing secret; [fromTv] keeps the two directions distinct so one
     * side's proof can't be echoed back as the other's. */
    fun confirmMac(secret: ByteArray, challenge: ByteArray, fromTv: Boolean): ByteArray =
        hmac(secret, canonical(if (fromTv) "confirm-tv" else "confirm-phone", challenge))

    // --- Play -----------------------------------------------------------------------------

    /** The bytes a play request's MAC covers. [positionMs] is where to start — 0 for the
     * beginning — or `null` to leave it to the receiver's own resume history. [toPhone] marks
     * the direction (TV → phone for "Continue on phone"), so a request can't be bounced back
     * the other way. */
    fun playMessage(
        challenge: ByteArray,
        phoneId: String,
        tvId: String,
        url: String,
        positionMs: Long?,
        toPhone: Boolean = false,
    ): ByteArray = canonical(
        if (toPhone) "$TYPE_PLAY-to-phone" else TYPE_PLAY,
        challenge, phoneId, tvId, url, positionMs?.toString().orEmpty(),
    )

    // --- Encoding -------------------------------------------------------------------------

    fun hmac(key: ByteArray, message: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(message)
        }

    /** Constant-time, so a forged MAC can't be found byte by byte from response timing. */
    fun macMatches(expected: ByteArray, actual: ByteArray?): Boolean =
        actual != null && MessageDigest.isEqual(expected, actual)

    /** Length-prefixed fields, so no two different field lists produce the same bytes. */
    fun canonical(vararg fields: Any): ByteArray {
        val out = ByteArrayOutputStream()
        for (field in fields) {
            val bytes = when (field) {
                is ByteArray -> field
                else -> field.toString().toByteArray(Charsets.UTF_8)
            }
            out.write(ByteBuffer.allocate(4).putInt(bytes.size).array())
            out.write(bytes)
        }
        return out.toByteArray()
    }

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    /** `null` for anything that isn't valid base64 — untrusted input fails safely. */
    fun decode(text: String?): ByteArray? = text?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }

    /**
     * Reads one `\n`-terminated UTF-8 line, refusing to buffer more than [MAX_LINE_BYTES] — a
     * peer can't make the TV hold an unbounded line in memory. `null` at end of stream.
     */
    fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte == -1) return if (buffer.size() == 0) null else throw IOException("Truncated message")
            if (byte == '\n'.code) return buffer.toString(Charsets.UTF_8.name())
            if (buffer.size() >= MAX_LINE_BYTES) throw IOException("Message too long")
            buffer.write(byte)
        }
    }
}
