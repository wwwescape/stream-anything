package com.wwwescape.streamanything.data.tvlink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetAddress

class TvLinkProtocolTest {

    private val phoneId = "6f1c1c64-2a43-4c8e-9a51-0d6b1b2f7a10"
    private val tvId = "0b7e9a3e-5d1f-4f4a-8a0e-3c2d1e0f9b87"

    private fun secrets(phoneCode: String, tvCode: String): Pair<ByteArray, ByteArray> {
        val challenge = TvLinkProtocol.newChallenge()
        val phone = TvLinkProtocol.newKeyPair()
        val tv = TvLinkProtocol.newKeyPair()
        val phoneKey = TvLinkProtocol.encodePublicKey(phone.public)
        val tvKey = TvLinkProtocol.encodePublicKey(tv.public)
        val onPhone = TvLinkProtocol.pairingSecret(phone, tvKey, phoneCode, challenge, phoneKey, tvKey, phoneId, tvId)
        val onTv = TvLinkProtocol.pairingSecret(tv, phoneKey, tvCode, challenge, phoneKey, tvKey, phoneId, tvId)
        return onPhone to onTv
    }

    @Test
    fun `both sides derive the same secret from the same code`() {
        val (onPhone, onTv) = secrets("123456", "123456")
        assertArrayEquals(onPhone, onTv)
        assertEquals(TvLinkProtocol.SECRET_BYTES, onPhone.size)
    }

    @Test
    fun `a wrong code gives a different secret, so confirmation fails`() {
        val challenge = TvLinkProtocol.newChallenge()
        val (onPhone, onTv) = secrets("123456", "123457")
        val proof = TvLinkProtocol.confirmMac(onPhone, challenge, fromTv = false)
        assertFalse(TvLinkProtocol.macMatches(TvLinkProtocol.confirmMac(onTv, challenge, fromTv = false), proof))
    }

    @Test
    fun `confirmations are direction-specific`() {
        val (secret, _) = secrets("000000", "000000")
        val challenge = TvLinkProtocol.newChallenge()
        assertFalse(
            TvLinkProtocol.confirmMac(secret, challenge, fromTv = true)
                .contentEquals(TvLinkProtocol.confirmMac(secret, challenge, fromTv = false)),
        )
    }

    @Test
    fun `a play request's MAC is bound to the challenge, link and position`() {
        val secret = ByteArray(32) { it.toByte() }
        val challenge = TvLinkProtocol.newChallenge()
        val url = "https://streamtape.com/v/abc123/"
        val mac = TvLinkProtocol.hmac(secret, TvLinkProtocol.playMessage(challenge, phoneId, tvId, url, 60_000))

        assertTrue(TvLinkProtocol.macMatches(TvLinkProtocol.hmac(secret, TvLinkProtocol.playMessage(challenge, phoneId, tvId, url, 60_000)), mac))
        // Replayed on a new connection (new challenge), or with anything changed, it fails.
        assertFalse(TvLinkProtocol.macMatches(TvLinkProtocol.hmac(secret, TvLinkProtocol.playMessage(TvLinkProtocol.newChallenge(), phoneId, tvId, url, 60_000)), mac))
        assertFalse(TvLinkProtocol.macMatches(TvLinkProtocol.hmac(secret, TvLinkProtocol.playMessage(challenge, phoneId, tvId, "https://evil.example/v.mp4", 60_000)), mac))
        assertFalse(TvLinkProtocol.macMatches(TvLinkProtocol.hmac(secret, TvLinkProtocol.playMessage(challenge, phoneId, tvId, url, 0)), mac))
        assertFalse(TvLinkProtocol.macMatches(TvLinkProtocol.hmac(ByteArray(32), TvLinkProtocol.playMessage(challenge, phoneId, tvId, url, 60_000)), mac))
        assertFalse(TvLinkProtocol.macMatches(mac, null))
    }

    @Test
    fun `start over, a position, and leave-it-to-the-TV are all distinct requests`() {
        val challenge = TvLinkProtocol.newChallenge()
        val url = "https://streamtape.com/v/abc123/"
        val fromStart = TvLinkProtocol.playMessage(challenge, phoneId, tvId, url, 0)
        val tvDecides = TvLinkProtocol.playMessage(challenge, phoneId, tvId, url, null)
        val resume = TvLinkProtocol.playMessage(challenge, phoneId, tvId, url, 60_000)
        assertFalse(fromStart.contentEquals(tvDecides))
        assertFalse(fromStart.contentEquals(resume))
        assertFalse(tvDecides.contentEquals(resume))
    }

    @Test
    fun `a request to the TV can't be replayed as one to the phone, or vice versa`() {
        val challenge = TvLinkProtocol.newChallenge()
        val url = "https://streamtape.com/v/abc123/"
        val toTv = TvLinkProtocol.playMessage(challenge, phoneId, tvId, url, 60_000)
        val toPhone = TvLinkProtocol.playMessage(challenge, phoneId, tvId, url, 60_000, toPhone = true)
        assertFalse(toTv.contentEquals(toPhone))
    }

    @Test
    fun `canonical encoding can't be shifted between fields`() {
        assertFalse(TvLinkProtocol.canonical("ab", "c").contentEquals(TvLinkProtocol.canonical("a", "bc")))
    }

    @Test
    fun `codes are six digits`() {
        repeat(50) { assertTrue(TvLinkProtocol.isValidCode(TvLinkProtocol.newCode())) }
        assertFalse(TvLinkProtocol.isValidCode("12345"))
        assertFalse(TvLinkProtocol.isValidCode("12345a"))
        assertFalse(TvLinkProtocol.isValidCode("1234567"))
    }

    @Test
    fun `untrusted fields are validated`() {
        assertTrue(TvLinkProtocol.isValidDeviceId(phoneId))
        assertFalse(TvLinkProtocol.isValidDeviceId("../../etc"))
        assertFalse(TvLinkProtocol.isValidDeviceId(null))
        assertTrue(TvLinkProtocol.isValidPosition(0))
        assertFalse(TvLinkProtocol.isValidPosition(-1))
        assertFalse(TvLinkProtocol.isValidPosition(TvLinkProtocol.MAX_POSITION_MS + 1))
        assertEquals("Living Room TV", TvLinkProtocol.cleanName("  Living\nRoom TV\u0000 "))
        assertEquals(TvLinkProtocol.MAX_NAME_LENGTH, TvLinkProtocol.cleanName("x".repeat(500))!!.length)
        assertNull(TvLinkProtocol.cleanName(" \n "))
        assertNull(TvLinkProtocol.decode("not base64!"))
        assertThrows(Exception::class.java) { TvLinkProtocol.decodePublicKey(ByteArray(65) { 4 }) }
    }

    @Test
    fun `only local addresses are accepted`() {
        assertTrue(TvLinkProtocol.isLocalAddress(InetAddress.getByName("192.168.1.20")))
        assertTrue(TvLinkProtocol.isLocalAddress(InetAddress.getByName("10.0.0.5")))
        assertTrue(TvLinkProtocol.isLocalAddress(InetAddress.getByName("fe80::1")))
        assertTrue(TvLinkProtocol.isLocalAddress(InetAddress.getByName("fd12:3456::1")))
        assertFalse(TvLinkProtocol.isLocalAddress(InetAddress.getByName("8.8.8.8")))
        assertFalse(TvLinkProtocol.isLocalAddress(InetAddress.getByName("2001:4860:4860::8888")))
    }

    @Test
    fun `typed addresses are local IPv4, with the default port unless given`() {
        val plain = TvLinkProtocol.parseAddress(" 192.168.1.42 ")!!
        assertEquals("192.168.1.42", plain.address.hostAddress)
        assertEquals(TvLinkProtocol.DEFAULT_PORT, plain.port)
        assertEquals(5000, TvLinkProtocol.parseAddress("10.0.0.7:5000")!!.port)

        assertNull(TvLinkProtocol.parseAddress("8.8.8.8"))
        assertNull(TvLinkProtocol.parseAddress("127.0.0.1"))
        assertNull(TvLinkProtocol.parseAddress("192.168.1.300"))
        assertNull(TvLinkProtocol.parseAddress("192.168.1.42:0"))
        assertNull(TvLinkProtocol.parseAddress("192.168.1.42:70000"))
        assertNull(TvLinkProtocol.parseAddress("tv.local"))
        assertNull(TvLinkProtocol.parseAddress(""))

        assertEquals("192.168.1.42", TvLinkProtocol.formatAddress(plain.address, TvLinkProtocol.DEFAULT_PORT))
        assertEquals("192.168.1.42:5000", TvLinkProtocol.formatAddress(plain.address, 5000))
    }

    @Test
    fun `lines are read one at a time and capped in size`() {
        val input = ByteArrayInputStream("{\"a\":1}\n{\"b\":\"é\"}\n".toByteArray())
        assertEquals("{\"a\":1}", TvLinkProtocol.readLine(input))
        assertEquals("{\"b\":\"é\"}", TvLinkProtocol.readLine(input))
        assertNull(TvLinkProtocol.readLine(input))

        val huge = ByteArrayInputStream(ByteArray(TvLinkProtocol.MAX_LINE_BYTES + 10) { 'x'.code.toByte() })
        assertThrows(IOException::class.java) { TvLinkProtocol.readLine(huge) }
        assertThrows(IOException::class.java) { TvLinkProtocol.readLine(ByteArrayInputStream("no newline".toByteArray())) }
    }
}
