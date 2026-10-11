package com.wwwescape.streamanything.data.streams

import okhttp3.HttpUrl

/**
 * DoodStream — `https://dood.to/d/{id}` share pages and `/e/{id}` embeds, across its long tail
 * of mirror domains.
 *
 * The embed page's script fetches `/pass_md5/…` (with the page as Referer) to get a base CDN URL,
 * then appends 10 random characters, a token, and the current time in ms. The CDN checks the
 * Referer, so the player sends it too.
 */
object DoodStreamResolver : StreamResolver {

    override val displayName = "DoodStream"

    private val hosts = HostMatcher(
        domains = setOf(
            "dood.watch", "doodstream.com", "dood.to", "dood.so", "dood.cx", "dood.la", "dood.ws",
            "dood.sh", "doodstream.co", "dood.pm", "dood.wf", "dood.re", "dood.yt", "dooood.com",
            "dood.stream", "ds2play.com", "doods.pro", "ds2video.com", "d0o0d.com", "do0od.com",
            "d0000d.com", "d000d.com", "dood.li", "dood.work", "dooodster.com", "vidply.com",
            "all3do.com", "do7go.com", "doodcdn.io", "doply.net", "vide0.net", "vvide0.com",
            "d-s.io", "dsvplay.com", "myvidplay.com", "playmogo.com",
        ),
    )

    /** Mirrors that still serve pages; links on any other Dood domain are fetched via the first. */
    private val LIVE_HOSTS = listOf("playmogo.com", "doodstream.com", "myvidplay.com")

    private val PAGE_PATHS = setOf("d", "e")
    private val IFRAME = Regex("""<iframe\s*src="([^"]+)""")
    private val PASS_MD5 = Regex("""dsplayer\.hotkeys[^']+'([^']+).+?function\s*makePlay.+?return[^?]+([^"]+)""", RegexOption.DOT_MATCHES_ALL)
    private val TITLE = Regex("""<title>([^<]+)</title>""")
    private const val RANDOM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    override fun canResolve(url: HttpUrl): Boolean = hosts.matches(url) && videoId(url) != null

    /** Same video whatever the link shape or mirror domain. */
    override fun videoKey(url: HttpUrl): String = videoId(url)?.let { "doodstream:$it" } ?: super.videoKey(url)

    override suspend fun resolve(url: HttpUrl): ResolvedStream {
        val id = videoId(url) ?: throw StreamError.UnsupportedLink()
        val host = url.host.removePrefix("www.").takeIf { it in LIVE_HOSTS } ?: LIVE_HOSTS.first()
        var pageUrl = StreamHttp.url("https://$host/d/$id")
        val headers = mutableMapOf("Referer" to "https://$host/")

        val first = StreamHttp.get(pageUrl, headers)
        if (first.url.host != pageUrl.host) {
            // Redirected to whichever mirror is current today.
            pageUrl = StreamHttp.url("https://${first.url.host}/d/$id")
        }
        headers["Referer"] = pageUrl.toString()

        val embedUrl = IFRAME.find(first.body)?.groupValues?.get(1)?.let { pageUrl.resolve(it) }
            ?: StreamHttp.url(pageUrl.toString().replace("/d/", "/e/"))
        val embed = StreamHttp.get(embedUrl, headers).body
        if (embed.contains("Video not found", ignoreCase = true)) throw StreamError.VideoUnavailable()

        val match = PASS_MD5.find(embed) ?: throw StreamError.Unreadable("No pass_md5 call on page")
        val (passPath, token) = match.destructured
        val passUrl = pageUrl.resolve(passPath) ?: throw StreamError.Unreadable("Bad pass_md5 path")
        val base = StreamHttp.get(passUrl, headers).body.trim()
        if (!base.startsWith("http")) throw StreamError.Unreadable("Unexpected pass_md5 response")

        val streamUrl = if ("cloudflarestorage." in base) {
            base
        } else {
            base + randomSuffix() + token + System.currentTimeMillis()
        }
        val title = TITLE.find(first.body)?.groupValues?.get(1)
        return ResolvedStream(
            streamUrl = streamUrl,
            title = PageScraper.cleanTitle(title, "DoodStream"),
            posterUrl = PageScraper.findPoster(embed),
            mimeType = "video/mp4",
            requestHeaders = headers.toMap(),
        )
    }

    internal fun videoId(url: HttpUrl): String? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < 2 || segments[0] !in PAGE_PATHS) return null
        return segments[1].takeIf { id -> id.all { it.isLetterOrDigit() } }
    }

    private fun randomSuffix(): String = (1..10).map { RANDOM_CHARS.random() }.joinToString("")
}
