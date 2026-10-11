package com.wwwescape.streamanything.data.streams

import okhttp3.HttpUrl
import org.json.JSONObject
import java.util.Base64

/**
 * VOE — `voe.sx/e/{id}` (and `/{id}`), plus the hundreds of random-word mirror domains it
 * redirects through (`kristiesoundsimply.com`…).
 *
 * The page first script-redirects (`window.location.href = '…'`) to today's mirror. That page
 * carries the player config as an obfuscated JSON string, decoded in layers: ROT13 → strip
 * junk tokens (listed in the page's external script) → base64 → shift every char by −3 →
 * reverse → base64 → JSON with `source` (HLS) and `direct_access_url` (MP4).
 */
object VoeResolver : StreamResolver {

    override val displayName = "VOE"

    private val hosts = HostMatcher(
        domains = setOf(
            "voe.sx", "voe-unblock.com", "voe-unblock.net", "voeunblock.com", "un-block-voe.net",
            "voeunbl0ck.com", "voeunblck.com", "voeunblk.com", "voe-un-block.com", "voeun-block.net",
            "v-o-e-unblock.com", "tinycat-voe-fashion.com", "jonathansociallike.com", "edwardarriveoften.com",
            "nathanfromsubject.com", "kristiesoundsimply.com", "chuckle-tube.com", "goofy-banana.com",
            "smoki.cc", "ogladaj.me", "kinoger.ru", "lancewhosedifficult.com", "dianaavoidthey.com",
            "jefferycontrolmodel.com", "marissasharecareer.com", "charlestoughrace.com",
            "ianrequireadult.com", "timmaybealready.com", "jessicayeahcatch.com", "johnbeyondnation.com",
            "jeanprofessorcentral.com", "juliewomanwish.com", "garylargeavailable.com",
            "jennifereconomicgive.com", "pamelachangemission.com", "ellenpoliticalfollow.com",
            "caseyimpactstation.com", "matthewhotelscience.com", "jessicachoosemake.com",
            "stevenfamilyedge.com", "tracylocalschool.com", "eugenemakedraw.com", "johnfullwonder.com",
            "katherineschoolphone.com", "jamesbornmain.com", "jeremyparticipantanything.com",
            "teresapoliticallearn.com",
        ),
        // voeunblock1-10.com, and VOE's people-name mirror pattern ("<name><words>.com") is too
        // broad to match safely, so only the listed ones are claimed — the rest still work when
        // pasted as their voe.sx link.
        pattern = Regex("""voeunblock\d{1,2}\.com"""),
    )

    private val CURRENT_URL_REDIRECT = Regex("""window\.location\.href\s*=\s*'([^']+)""")
    private val ENCODED_CONFIG = Regex("""json">\["([^"]+)"]</script>\s*<script\s*src="([^"]+)""")
    private val JUNK_TOKENS = Regex("""\[(?:'\W{2}'[,\]]){1,9}""")
    private val FALLBACK_SOURCES = listOf(
        Regex("""'hls'\s*:\s*'([^']+)'"""),
        Regex(""""hls"\s*:\s*"([^"]+)""""),
        Regex("""mp4["']\s*:\s*["']([^"']+)"""),
    )

    override fun canResolve(url: HttpUrl): Boolean = hosts.matches(url) && videoId(url) != null

    /** Same video whatever the link shape or mirror domain. */
    override fun videoKey(url: HttpUrl): String = videoId(url)?.let { "voe:$it" } ?: super.videoKey(url)

    override suspend fun resolve(url: HttpUrl): ResolvedStream {
        val id = videoId(url) ?: throw StreamError.UnsupportedLink()
        var page = StreamHttp.get(StreamHttp.url("https://${url.host.removePrefix("www.")}/e/$id"))
        repeat(3) {
            if ("const currentUrl" !in page.body) return@repeat
            val next = CURRENT_URL_REDIRECT.find(page.body)?.groupValues?.get(1) ?: return@repeat
            page = StreamHttp.get(StreamHttp.url(next))
        }
        val headers = mapOf("Referer" to page.url.rootUrl())

        ENCODED_CONFIG.find(page.body)?.let { match ->
            val (encoded, scriptPath) = match.destructured
            val script = page.url.resolve(scriptPath)?.let { StreamHttp.get(it).body }.orEmpty()
            val junk = JUNK_TOKENS.find(script)?.value
            if (junk != null) {
                val config = decode(encoded, junk)
                val stream = config.optString("source").takeIf { it.isNotEmpty() }
                    ?: config.optString("direct_access_url").takeIf { it.isNotEmpty() }
                    ?: config.optString("file").takeIf { it.isNotEmpty() }
                if (stream != null) {
                    return ResolvedStream(
                        streamUrl = stream,
                        title = config.optString("title").takeIf { it.isNotEmpty() }
                            ?: PageScraper.cleanTitle(PageScraper.findTitle(page.body), "VOE"),
                        posterUrl = config.optString("thumbnail").takeIf { it.startsWith("https://") }
                            ?: PageScraper.findPoster(page.body),
                        mimeType = if ("m3u8" in stream) MIME_HLS else null,
                        requestHeaders = headers,
                    )
                }
            }
        }

        val stream = FALLBACK_SOURCES.firstNotNullOfOrNull { it.find(page.body)?.groupValues?.get(1) }
            ?.let { link -> if (link.startsWith("http")) link else String(Base64.getDecoder().decode(link)) }
            ?: throw if (page.body.contains("not found", ignoreCase = true)) StreamError.VideoUnavailable()
            else StreamError.Unreadable("No VOE player config")
        return ResolvedStream(
            streamUrl = stream,
            title = PageScraper.cleanTitle(PageScraper.findTitle(page.body), "VOE"),
            posterUrl = PageScraper.findPoster(page.body),
            mimeType = if ("m3u8" in stream) MIME_HLS else null,
            requestHeaders = headers,
        )
    }

    /** See the class doc. [junkList] is the JS array literal, e.g. `['@$','^^','~@']`. */
    internal fun decode(encoded: String, junkList: String): JSONObject {
        val junk = junkList.removePrefix("['").removeSuffix("']").split("','")
        var text = encoded.map { c ->
            when (c) {
                in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
                in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
                else -> c
            }
        }.joinToString("")
        junk.forEach { text = text.replace(it, "") }
        val shifted = String(base64(text), Charsets.ISO_8859_1).map { it - 3 }.joinToString("")
        return JSONObject(String(base64(shifted.reversed()), Charsets.UTF_8))
    }

    private fun base64(text: String): ByteArray =
        Base64.getDecoder().decode(text.padEnd((text.length + 3) / 4 * 4, '='))

    internal fun videoId(url: HttpUrl): String? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val raw = if (segments.firstOrNull() == "e") segments.getOrNull(1) else segments.firstOrNull()
        return raw?.takeIf { id -> id.length >= 8 && id.all { it.isLetterOrDigit() } }
    }
}
