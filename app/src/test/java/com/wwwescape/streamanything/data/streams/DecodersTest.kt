package com.wwwescape.streamanything.data.streams

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Expected values here were produced by running ResolveURL's own Python decoders (jsunpack.py,
 * voe.py, veev.py) on the same inputs, so these pin the Kotlin ports to the reference behavior.
 */
class DecodersTest {

    @Test
    fun unpacksBase36Packer() {
        val packed = "eval(function(p,a,c,k,e,d){e=function(c){return c.toString(36)};" +
            "if(!''.replace(/^/,String)){while(c--){d[c.toString(a)]=k[c]||c.toString(a)}" +
            "k=[function(e){return d[e]}];e=function(){return'\\\\w+'};c=1};" +
            "while(c--){if(k[c]){p=p.replace(new RegExp('\\\\b'+e(c)+'\\\\b','g'),k[c])}}return p}" +
            "('0 1={2:\"3://4.5/6.7\"};',36,8,'var|player|file|https|cdn|example|master|m3u8'.split('|'),0,{}))"
        assertTrue(JsUnpacker.isPacked(packed))
        assertEquals("var player={file:\"https://cdn.example/master.m3u8\"};", JsUnpacker.unpack(packed))
    }

    @Test
    fun unpacksBase62Packer() {
        val words = (0 until 40).map { "w$it" }.toMutableList()
        words[10] = "sources"
        words[36] = "hls2"
        val packed = "eval(function(p,a,c,k,e,d){}('a A 0',62,40,'${words.joinToString("|")}'.split('|'),0,{}))"
        assertEquals("sources hls2 w0", JsUnpacker.unpack(packed))
    }

    @Test
    fun rejectsMalformedPacker() {
        assertNull(JsUnpacker.unpack("eval(function(p,a,c,k,e,d){}('0 1',36,5,'a|b'.split('|'),0,{}))"))
        assertEquals("", JsUnpacker.unpackAll("<script>var x = 1;</script>"))
    }

    @Test
    fun scrapesJwPlayerAndHlsLinks() {
        val base = "https://host.example/e/abc123".toHttpUrl()
        assertEquals(
            "https://cdn.example/a.m3u8",
            PageScraper.findStream("""jwplayer("x").setup({sources:[{file:"https://cdn.example/a.m3u8"}]})""", base),
        )
        // The links map wins, highest-numbered CDN first; relative links resolve against the page.
        assertEquals(
            "https://host.example/stream/b.m3u8",
            PageScraper.findStream("""var links={"hls2":"https://cdn2/x.m3u8","hls4":"/stream/b.m3u8"};sources:[{file:links.hls4}]""", base),
        )
        assertNull(PageScraper.findStream("<html>nothing here</html>", base))
    }

    @Test
    fun decodesVoeConfig() {
        val encoded = "DROHn@\$Jj7Jy1h^^o3OMnyuXMKb0JykKGHMZBHkJKKMIJ2DmGHMZqxkTHKb0pR81CSMqqxIoMJgeJ114A0cqoGk8GmyDF2LmIHgxoREfHzk6pTL0CQEzsIx1KT1WAIfmHIcqsT9XKJkREx9fnyqasGAjG3kMFzq9FIcyrIOYMKV8Iy12EIgyn2goKKt3Fy1gCUkCBIOYMwAIF2EfETkFoSt1KUkMAzI9GKkb"
        val config = VoeResolver.decode(encoded, "['@\$','^^','~@']")
        assertEquals("https://cdn.example/hls/master.m3u8", config.getString("source"))
        assertEquals("https://cdn.example/v.mp4", config.getString("direct_access_url"))
        assertEquals("Sample Video", config.getString("title"))
    }

    @Test
    fun decodesVeevLzw() {
        val codes = listOf(84, 79, 66, 69, 79, 82, 78, 79, 84, 256, 258, 260, 265, 259, 261, 263, 268, 260, 262, 264, 257, 269, 272, 270, 275, 266, 279, 278, 278, 274)
        val encoded = codes.map { it.toChar() }.joinToString("")
        assertEquals("TOBEORNOTTOBEORTOBEORNOT".repeat(3), VeevResolver.lzwDecode(encoded))
    }

    @Test
    fun readsVeevKeyStepsAndUnscrambles() {
        assertEquals(listOf(listOf(1, 0, 1)), VeevResolver.keySteps("31010"))
        val media = "https://cdn.example/veev/master.m3u8"
        // Build the scrambled form the same way the site does (verified against ResolveURL).
        var scrambled = media
        for (step in listOf(1, 0, 1).asReversed()) {
            val hex = scrambled.toByteArray().joinToString("") { "%02x".format(it) }
            scrambled = if (step == 1) hex.reversed() else hex
        }
        assertEquals(media, VeevResolver.unscramble(scrambled, listOf(1, 0, 1)))
    }
}
