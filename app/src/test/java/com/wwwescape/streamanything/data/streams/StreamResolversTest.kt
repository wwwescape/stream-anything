package com.wwwescape.streamanything.data.streams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StreamResolversTest {

    @Test
    fun acceptsLinksWithoutAScheme() {
        assertEquals("https://streamtape.com/v/7zeY3yGXYzfA1rz", StreamResolvers.parse("  streamtape.com/v/7zeY3yGXYzfA1rz ").toString())
    }

    @Test
    fun extractsTheLinkFromSharedText() {
        assertEquals(
            "https://streamtape.com/v/7zeY3yGXYzfA1rz",
            StreamResolvers.parse("Watch this: https://streamtape.com/v/7zeY3yGXYzfA1rz.").toString(),
        )
    }

    @Test
    fun rejectsNonLinks() {
        assertNull(StreamResolvers.parse(""))
        assertNull(StreamResolvers.parse("just some words"))
        assertFalse(StreamResolvers.isSupported("https://example.com/page.html"))
    }

    @Test
    fun routesToTheRightResolver() {
        assertSame(StreamtapeResolver, StreamResolvers.find(StreamResolvers.parse("https://streamtape.com/v/7zeY3yGXYzfA1rz")!!))
        assertSame(DirectLinkResolver, StreamResolvers.find(StreamResolvers.parse("https://cdn.example.com/a/movie.MP4?sig=1")!!))
        assertSame(DirectLinkResolver, StreamResolvers.find(StreamResolvers.parse("https://cdn.example.com/live/index.m3u8")!!))
    }
}
