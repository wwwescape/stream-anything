package com.wwwescape.streamanything.data.streams

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Streamtape — `https://streamtape.com/v/{id}[/{filename}]` share pages (and `/e/{id}` embeds).
 *
 * The page never contains the real media link as-is. It ships a few hidden elements holding
 * *decoy* `get_video` links (wrong token), then a script that overwrites each one with an
 * expression like `'//streamta' + ('xcdpe.com/get_video?…').substring(1).substring(2)`. The
 * player reads one specific element (`var srclink = $('#captchalink').text() + '&stream=1'`) —
 * so we do the same: find which element the player reads, evaluate the script's expression for
 * it with [JsStringExpression], and append the same suffix. That URL 302s to the actual MP4 on
 * a tapecontent.net CDN host; its token is bound to the requesting IP, which is why resolving
 * happens on-device right before playback rather than being cached.
 */
object StreamtapeResolver : StreamResolver {

    override val displayName = "Streamtape"

    /** Streamtape's primary domain plus the mirror domains it serves the same pages on. */
    private val HOSTS = setOf(
        "streamtape.com", "streamtape.net", "streamtape.to", "streamtape.xyz", "streamtape.cc",
        "streamtape.site", "streamtape.org", "streamta.pe", "strtape.cloud", "strtape.tech",
        "strtpe.link", "strcloud.link", "stape.fun", "shavetape.cash", "streamtapeadblock.art",
        "tapeblocker.com", "tapeadvertisement.com", "adblocktape.wiki", "watchadsontape.com", "advtpe.com",
    )

    private val PAGE_PATHS = setOf("v", "e")

    /** Fallback order if the player script isn't found — these are the element IDs Streamtape
     * has used for the real link over time. */
    private val CANDIDATE_ELEMENTS = listOf("captchalink", "norobotlink", "robotlink", "ideoooolink", "botlink")

    private const val DEFAULT_SUFFIX = "&stream=1"

    private val SRCLINK_REGEX =
        Regex("""srclink\s*=\s*\$\(\s*['"]#([\w-]+)['"]\s*\)\s*\.text\(\)\s*(?:\+\s*(['"])(.*?)\2)?""")
    private val ASSIGNMENT_REGEX =
        Regex("""getElementById\(\s*['"]([\w-]+)['"]\s*\)\s*\.innerHTML\s*=\s*([^;]+);""")
    private val OG_TITLE_REGEX = Regex("""<meta\s+name="og:title"\s+content="([^"]*)"""")
    private val OG_IMAGE_REGEX = Regex("""<meta\s+name="og:image"\s+content="([^"]*)"""")

    override fun canResolve(url: HttpUrl): Boolean = videoId(url) != null

    /** Same video whatever the link shape or mirror domain. */
    override fun videoKey(url: HttpUrl): String = videoId(url)?.let { "streamtape:$it" } ?: super.videoKey(url)

    override suspend fun resolve(url: HttpUrl): ResolvedStream {
        val id = videoId(url) ?: throw StreamError.UnsupportedLink()
        // Always fetch the /v/ share page: /e/ embeds carry the same script but no og:title.
        val pageUrl = url.newBuilder()
            .scheme("https")
            .encodedPath("/v/$id")
            .query(null)
            .fragment(null)
            .build()
        val html = StreamHttp.fetchPage(pageUrl)
        return parsePage(html, fallbackTitle = url.pathSegments.getOrNull(2))
    }

    /** `/v/{id}`, `/v/{id}/`, `/v/{id}/{filename}` and the `/e/` equivalents → `{id}`. */
    internal fun videoId(url: HttpUrl): String? {
        if (url.host.removePrefix("www.") !in HOSTS) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < 2 || segments[0] !in PAGE_PATHS) return null
        return segments[1].takeIf { it.all { c -> c.isLetterOrDigit() || c == '_' || c == '-' } }
    }

    internal fun parsePage(html: String, fallbackTitle: String? = null): ResolvedStream {
        val assignments = ASSIGNMENT_REGEX.findAll(html).associate { it.groupValues[1] to it.groupValues[2] }
        if (assignments.isEmpty()) {
            throw if (html.contains("Video not found", ignoreCase = true)) {
                StreamError.VideoUnavailable()
            } else {
                StreamError.Unreadable("No link script on page")
            }
        }

        val srclink = SRCLINK_REGEX.find(html)
        val suffix = srclink?.groupValues?.getOrNull(3)?.takeIf { srclink.groupValues[2].isNotEmpty() }
            ?: DEFAULT_SUFFIX
        val candidates = (listOfNotNull(srclink?.groupValues?.get(1)) + CANDIDATE_ELEMENTS).distinct()

        val streamUrl = candidates.firstNotNullOfOrNull { element ->
            assignments[element]
                ?.let(JsStringExpression::evaluate)
                ?.let(::toAbsoluteUrl)
                // Decoys can evaluate to a mangled host ("streamtapde.com") — only accept a link
                // that genuinely points back at Streamtape.
                ?.takeIf { it.host.removePrefix("www.") in HOSTS && it.encodedPath.contains("get_video") }
        } ?: throw StreamError.Unreadable("Couldn't decode the video link")

        val title = OG_TITLE_REGEX.find(html)?.groupValues?.get(1)?.decodeHtmlEntities()?.takeIf { it.isNotBlank() }
            ?: fallbackTitle
        val poster = OG_IMAGE_REGEX.find(html)?.groupValues?.get(1)?.decodeHtmlEntities()?.takeIf { it.startsWith("https://") }

        return ResolvedStream(
            streamUrl = streamUrl.toString() + suffix,
            title = title,
            posterUrl = poster,
            mimeType = "video/mp4",
        )
    }

    /** The script produces protocol-relative links (`//streamtape.com/…`). */
    private fun toAbsoluteUrl(link: String): HttpUrl? {
        val trimmed = link.trim()
        val absolute = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "https:/$trimmed"
            else -> trimmed
        }
        return absolute.toHttpUrlOrNull()
    }

    private fun String.decodeHtmlEntities(): String = this
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#039;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}
