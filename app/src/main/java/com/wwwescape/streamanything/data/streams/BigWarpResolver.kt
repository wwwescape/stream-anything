package com.wwwescape.streamanything.data.streams

import okhttp3.HttpUrl

/**
 * BigWarp — `bigwarp.io/e/{id}`, `/{id}`, `/embed-{id}.html`. Its player config comes from a
 * form POST to `/dl` (`op=embed`) rather than the page itself; the result is a regular JW
 * Player `sources` list.
 */
object BigWarpResolver : StreamResolver {

    override val displayName = "BigWarp"

    private val hosts = HostMatcher(domains = setOf("bigwarp.io", "bgwp.cc", "bigwarp.art", "bigwarp.cc", "bigwarp.pro"))

    override fun canResolve(url: HttpUrl): Boolean = hosts.matches(url) && videoId(url) != null

    /** Same video whatever the link shape or mirror domain. */
    override fun videoKey(url: HttpUrl): String = videoId(url)?.let { "bigwarp:$it" } ?: super.videoKey(url)

    override suspend fun resolve(url: HttpUrl): ResolvedStream {
        val id = videoId(url) ?: throw StreamError.UnsupportedLink()
        val root = "https://bigwarp.io/"
        val headers = mapOf("Referer" to root, "Origin" to root.removeSuffix("/"))
        val page = StreamHttp.postForm(
            StreamHttp.url("${root}dl"),
            form = mapOf("op" to "embed", "file_code" to id, "auto" to "0"),
            headers = headers,
        )
        val expanded = PageScraper.expand(page.body)
        val stream = PageScraper.findStream(expanded, page.url)
            ?: throw if (page.body.contains("not found", ignoreCase = true) || page.body.contains("deleted", ignoreCase = true)) {
                StreamError.VideoUnavailable()
            } else {
                StreamError.Unreadable("No player sources from BigWarp")
            }
        // Unknown IDs get a stock placeholder clip from the site's own /player/ folder instead
        // of an error.
        if (StreamHttp.url(stream).encodedPath.startsWith("/player/")) throw StreamError.VideoUnavailable()
        return ResolvedStream(
            streamUrl = stream,
            title = PageScraper.cleanTitle(PageScraper.findTitle(page.body), "BigWarp"),
            posterUrl = PageScraper.findPoster(expanded),
            mimeType = if ("m3u8" in stream) MIME_HLS else null,
            requestHeaders = headers,
        )
    }

    internal fun videoId(url: HttpUrl): String? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val raw = if (segments.firstOrNull() in setOf("e", "d")) segments.getOrNull(1) else segments.firstOrNull()
        return raw?.removePrefix("embed-")?.removeSuffix(".html")
            ?.takeIf { id -> id.length >= 6 && id.all { it.isLetterOrDigit() } }
    }
}
