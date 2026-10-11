package com.wwwescape.streamanything.data.streams

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamtapeResolverTest {

    @Test
    fun recognizesEveryShareUrlShape() {
        val id = "oAyl8rV67auW39"
        listOf(
            "https://streamtape.com/v/$id",
            "https://streamtape.com/v/$id/",
            "https://streamtape.com/v/$id/Jellyfish_1080_10s_5MB.mp4",
            "https://www.streamtape.com/v/$id",
            "https://streamtape.to/e/$id",
            "http://strtape.cloud/v/$id/name.mp4?x=1",
            "https://advtpe.com/v/$id/",
        ).forEach { assertEquals(it, id, StreamtapeResolver.videoId(it.toHttpUrl())) }
    }

    @Test
    fun ignoresOtherUrls() {
        listOf(
            "https://streamtape.com/",
            "https://streamtape.com/v/",
            "https://streamtape.com/accpanel",
            "https://example.com/v/oAyl8rV67auW39",
        ).forEach { assertNull(it, StreamtapeResolver.videoId(it.toHttpUrl())) }
    }

    @Test
    fun decodesTheLinkThePlayerActuallyUses() {
        val stream = StreamtapeResolver.parsePage(SAMPLE_PAGE)
        // captchalink's expression, not its decoy innerHTML (token ending "defg") and not
        // ideoooolink's, which evaluates to a mangled host.
        assertEquals(
            "https://streamtape.com/get_video?id=oAyl8rV67auW39&expires=1791783968&ip=F0OPKRWPE19XKxR&token=MdG8bwhGbzJW&stream=1",
            stream.streamUrl,
        )
        assertEquals("Jellyfish_1080_10s_5MB.mp4", stream.title)
        assertEquals("https://thumb.tapecontent.net/thumb/oAyl8rV67auW39/AllbPBAwk9CXOkl.jpg", stream.posterUrl)
    }

    @Test
    fun fallsBackToKnownElementsWithoutThePlayerScript() {
        val page = SAMPLE_PAGE.substringBefore("<script>\$(function")
        val stream = StreamtapeResolver.parsePage(page)
        assertTrue(stream.streamUrl.startsWith("https://streamtape.com/get_video?id=oAyl8rV67auW39"))
        assertTrue(stream.streamUrl.endsWith("token=MdG8bwhGbzJW&stream=1"))
    }

    @Test
    fun reportsMissingVideos() {
        assertThrows(StreamError.VideoUnavailable::class.java) {
            StreamtapeResolver.parsePage("<html><h1>Video not found!</h1></html>")
        }
    }

    @Test
    fun reportsUnrecognizedPages() {
        assertThrows(StreamError.Unreadable::class.java) {
            StreamtapeResolver.parsePage("<html><body>Something else entirely</body></html>")
        }
    }

    private companion object {
        /** Trimmed from the real share page for https://streamtape.com/v/oAyl8rV67auW39. */
        val SAMPLE_PAGE = """
            <meta name="og:title" content="Jellyfish_1080_10s_5MB.mp4">
            <meta name="og:image" content="https://thumb.tapecontent.net/thumb/oAyl8rV67auW39/AllbPBAwk9CXOkl.jpg">
            <div id="ideoooolink" style="display:none;">/streamtape.com/get_video?id=oAyl8rV67auW39&expires=1791783968&ip=F0OPKRWPE19XKxR&token=MdG8bwhGbzzZ</div>
            <span id="captchalink" style="display:none;">/streamtape.com/get_video?id=oAyl8rV67auW39&expires=1791783968&ip=F0OPKRWPE19XKxR&token=MdG8bwhGdefg</span>
            <div id="norobotlink" style="display:none;">/streamtape.com/get_video?id=oAyl8rV67auW39&expires=1791783968&ip=F0OPKRWPE19XKxR&token=MdG8bwhGbzzZ</div>
            <script>
            document.getElementById('ideoooolink').innerHTML = "/streamtap" + ''+ ('xcdde.com/get_video?id=oAyl8rV67auW39&expires=1791783968&ip=F0OPKRWPE19XKxR&token=MdG8bwhGbzJW').substring(1).substring(2);
            document.getElementById('captchalink').innerHTML = '//streamta'+ ('defgpe.com/get_video?id=oAyl8rV67auW39&expires=1791783968&ip=F0OPKRWPE19XKxR&token=MdG8bwhGbzJW').substring(4);
            document.getElementById('norobotlink').innerHTML = '//streamta' + ('xcdpe.com/get_video?id=oAyl8rV67auW39&expires=1791783968&ip=F0OPKRWPE19XKxR&token=MdG8bwhGbzJW').substring(1).substring(2);</script>
            <script>${'$'}(function(){
            var srclink = ${'$'}('#captchalink').text() + '&stream=1';
            document.getElementById("mainvideo").src = srclink;player.currentTrack=0;});</script>
        """.trimIndent()
    }
}
