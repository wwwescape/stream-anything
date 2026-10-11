package com.wwwescape.streamanything.data.streams

import okhttp3.HttpUrl

/**
 * Mixdrop — `https://mixdrop.ag/f/{id}` file pages and `/e/{id}` embeds, on its rotating domains.
 *
 * The embed page sometimes bounces through a `location = "/e/…"` script redirect, then sets the
 * media URL inside a packed script as `MDCore.wurl = "//…mp4"`. The CDN wants the embed page as
 * Referer.
 */
object MixdropResolver : StreamResolver {

    override val displayName = "Mixdrop"

    private val hosts = HostMatcher(
        domains = setOf(
            "mixdrop.co", "mixdrop.to", "mixdrop.sx", "mixdrop.bz", "mixdrop.ch", "mixdrp.co",
            "mixdrp.to", "mixdrop.gl", "mixdrop.club", "mixdroop.bz", "mixdroop.co", "mixdrop.vc",
            "mixdrop.ag", "mdy48tn97.com", "md3b0j6hj.com", "mdbekjwqa.pw", "mdfx9dc8n.net",
            "mixdropjmk.pw", "mixdrop21.net", "mixdrop.is", "mixdrop.si", "mixdrop23.net",
            "mixdrop.nu", "mixdrop.ms", "mdzsmutpcvykb.net", "mixdrop.ps", "mxdrop.to", "mixdrop.sb",
            "mixdrop.my", "m1xdrop.net", "m1xdrop.com", "m1xdrop.click", "mxdrop.sx", "mixdrop.top",
            "mixdrp.click", "miixdrop.net", "miiixdrop.net", "miiiixdrop.net",
        ),
        // New numbered/misspelled mirrors appear regularly: mixdrop22.net, miiiiixdrop.net, …
        pattern = Regex("""m(?:i*1*x|x)dro*p\d*\.[a-z]{2,6}"""),
    )

    private val PAGE_PATHS = setOf("f", "e")
    private val JS_REDIRECT = Regex("""location\s*=\s*["']([^'"]+)""")
    private val MEDIA_URL = Regex("""(?:vsr|wurl|surl)[^=]*=\s*"([^"]+)""")
    private val POSTER = Regex("""(?:poster|thumbs)[^=]*=\s*"([^"]+)""")
    private val TITLE = Regex("""class="title"[^>]*>\s*<a[^>]*>([^<]+)<""")

    override fun canResolve(url: HttpUrl): Boolean = hosts.matches(url) && videoId(url) != null

    /** Same video whatever the link shape or mirror domain. */
    override fun videoKey(url: HttpUrl): String = videoId(url)?.let { "mixdrop:$it" } ?: super.videoKey(url)

    override suspend fun resolve(url: HttpUrl): ResolvedStream {
        val id = videoId(url) ?: throw StreamError.UnsupportedLink()
        val host = url.host.removePrefix("www.").replace(".club", ".co")
        var pageUrl = StreamHttp.url("https://$host/e/$id")
        val headers = mapOf("Origin" to "https://$host", "Referer" to "https://$host/")

        var html = StreamHttp.get(pageUrl, headers).body
        JS_REDIRECT.find(html)?.groupValues?.get(1)?.takeIf { it.startsWith("/") }?.let { path ->
            pageUrl = StreamHttp.url("https://$host$path")
            html = StreamHttp.get(pageUrl, headers).body
        }
        val page = PageScraper.expand(html)
        val media = MEDIA_URL.find(page)?.groupValues?.get(1)
            ?: throw if (html.contains("can't find the", ignoreCase = true) || html.contains("deleted", ignoreCase = true)) {
                StreamError.VideoUnavailable()
            } else {
                StreamError.Unreadable("No media URL in player script")
            }
        return ResolvedStream(
            streamUrl = PageScraper.absolutize(media, pageUrl) ?: throw StreamError.Unreadable("Bad media URL"),
            title = TITLE.find(html)?.groupValues?.get(1)?.trim() ?: PageScraper.cleanTitle(PageScraper.findTitle(html), "MixDrop"),
            posterUrl = POSTER.find(page)?.groupValues?.get(1)?.let { PageScraper.absolutize(it, pageUrl) },
            mimeType = "video/mp4",
            requestHeaders = mapOf("Referer" to pageUrl.toString()),
        )
    }

    internal fun videoId(url: HttpUrl): String? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < 2 || segments[0] !in PAGE_PATHS) return null
        return segments[1].takeIf { id -> id.all { it.isLetterOrDigit() || it == '_' } }
    }
}
