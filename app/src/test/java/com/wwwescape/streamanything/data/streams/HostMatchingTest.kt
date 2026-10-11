package com.wwwescape.streamanything.data.streams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Every supported link shape goes to the right resolver — and lookalikes don't. */
class HostMatchingTest {

    private fun resolverFor(link: String) = StreamResolvers.parse(link)?.let(StreamResolvers::find)

    @Test
    fun routesEachServicesLinkShapes() {
        mapOf(
            "https://bigwarp.io/e/abc123def456" to BigWarpResolver,
            "https://bigwarp.io/abc123def456" to BigWarpResolver,
            "https://bgwp.cc/embed-abc123def456.html" to BigWarpResolver,
            "https://dood.to/d/abc123def456" to DoodStreamResolver,
            "https://dood.la/e/abc123def456" to DoodStreamResolver,
            "https://myvidplay.com/e/abc123def456" to DoodStreamResolver,
            "https://lulustream.com/e/abc123def456" to LuluStreamResolver,
            "https://luluvdo.com/abc123def456" to LuluStreamResolver,
            "https://mixdrop.ag/f/abc123def456" to MixdropResolver,
            "https://mixdrop.co/e/abc123def456" to MixdropResolver,
            "https://mixdrop24.net/e/abc123def456" to MixdropResolver,
            "https://streamtape.com/v/abc123def456" to StreamtapeResolver,
            "https://streamwish.to/e/abc123def456" to StreamWishResolver,
            "https://embedwish.com/e/abc123def456" to StreamWishResolver,
            "https://hglink.to/e/abc123def456" to StreamWishResolver,
            "https://veev.to/e/abc123def456" to VeevResolver,
            "https://vidhide.com/v/abc123def456" to VidHideResolver,
            "https://filelions.to/f/abc123def456" to VidHideResolver,
            "https://vidnest.io/abc123def456" to VidNestResolver,
            "https://vidnest.io/embed-abc123def456.html" to VidNestResolver,
            "https://voe.sx/e/abc123def456" to VoeResolver,
            "https://voe.sx/abc123def456" to VoeResolver,
            "https://cdn.example.com/file.mp4" to DirectLinkResolver,
        ).forEach { (link, resolver) -> assertSame(link, resolver, resolverFor(link)) }
    }

    @Test
    fun sameVideoSharesOneHistoryKeyAcrossLinkShapes() {
        val key = StreamResolvers.videoKey("https://streamtape.com/v/oAyl8rV67auW39")
        assertEquals("streamtape:oAyl8rV67auW39", key)
        listOf(
            "https://streamtape.com/v/oAyl8rV67auW39/",
            "https://streamtape.com/v/oAyl8rV67auW39/Jellyfish_1080_10s_5MB.mp4",
            "https://strtape.cloud/e/oAyl8rV67auW39",
        ).forEach { assertEquals(it, key, StreamResolvers.videoKey(it)) }
        assertEquals(
            StreamResolvers.videoKey("https://lulustream.com/abc123def456"),
            StreamResolvers.videoKey("https://luluvdo.com/e/abc123def456"),
        )
        assertNull(StreamResolvers.videoKey("not a link"))
    }

    @Test
    fun ignoresHomepagesAndLookalikes() {
        listOf(
            "https://dood.to/",
            "https://mixdrop.ag/",
            "https://notmixdrop.example.com/e/abc123def456",
            "https://lulustream.com/",
            "https://example.com/e/abc123def456",
        ).forEach { assertNull(it, resolverFor(it)) }
    }
}
