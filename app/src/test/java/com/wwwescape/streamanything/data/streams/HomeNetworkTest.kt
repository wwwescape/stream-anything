package com.wwwescape.streamanything.data.streams

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeNetworkTest {

    @Test
    fun privateAddressesAreHome() {
        listOf(
            "192.168.0.140", "10.0.0.7", "172.16.0.1", "172.31.255.255", "127.0.0.1", "169.254.1.1",
            "::1", "fd12:3456::1", "fe80::1", "::ffff:192.168.1.2",
        ).forEach { assertTrue(it, HomeNetwork.isHomeAddress(it)) }
    }

    @Test
    fun publicAddressesVpnAndHostnamesAreNot() {
        listOf(
            "8.8.8.8", "172.32.0.1", "192.169.0.1", "100.70.47.107", // Tailscale/CGNAT
            "2001:db8::1", "::ffff:8.8.8.8",
            "192.168.0", "192.168.0.256", "1.2.3.4.5",
            "localhost", "laptop.local", "example.com", "192.168.0.140.example.com",
        ).forEach { assertFalse(it, HomeNetwork.isHomeAddress(it)) }
    }

    @Test
    fun plainHttpOnlyToTheHomeNetwork() {
        assertTrue(HomeNetwork.isAllowed("https://example.com/v.mp4".toHttpUrl()))
        assertTrue(HomeNetwork.isAllowed("http://192.168.0.140:8090/media/Movies/a.mp4".toHttpUrl()))
        assertFalse(HomeNetwork.isAllowed("http://example.com/v.mp4".toHttpUrl()))
        assertFalse(HomeNetwork.isAllowed("http://8.8.8.8/v.mp4".toHttpUrl()))
    }

    @Test
    fun publicHttpDirectLinksAreNotAccepted() {
        assertNull(StreamResolvers.find("http://example.com/movie.mp4".toHttpUrl()))
        assertSame(DirectLinkResolver, StreamResolvers.find("https://example.com/movie.mp4".toHttpUrl()))
        assertSame(DirectLinkResolver, StreamResolvers.find("http://192.168.0.140:8000/movie.mp4".toHttpUrl()))
    }

    @Test
    fun streamAnythingServerLinksGetTheirOwnResolver() {
        val link = "http://192.168.0.140:8090/media/Downloads/Holiday%202025/beach%20day.avi?key=abc123"
        assertSame(StreamAnythingServerResolver, StreamResolvers.find(link.toHttpUrl()))
        val stream = runBlocking { StreamResolvers.resolve(link) }
        assertEquals(link, stream.streamUrl)
        assertEquals("beach day", stream.title)
        // Resume survives a new access key.
        assertEquals(
            StreamResolvers.videoKey(link),
            StreamResolvers.videoKey(link.replace("key=abc123", "key=newkey456")),
        )
        // Not on the home network, or no key: not a server link.
        assertNull(StreamResolvers.find("http://8.8.8.8:8090/media/Downloads/a.mp4?key=x".toHttpUrl()))
        assertSame(
            DirectLinkResolver,
            StreamResolvers.find("http://192.168.0.140:8090/media/Downloads/a.mp4".toHttpUrl()),
        )
    }
}
