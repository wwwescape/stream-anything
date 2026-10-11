package com.wwwescape.streamanything.data.streams

import okhttp3.HttpUrl

/**
 * Finds the media URL in a video host's player page. Most hosts embed JW Player and configure it
 * as `sources: [{file: "…m3u8"}]`, often inside a packed script, sometimes via a
 * `links = {"hls4": …, "hls2": …}` map — so this looks in the page plus its unpacked scripts.
 */
object PageScraper {

    private val JW_SOURCES = Regex("""sources\s*:\s*\[\s*\{\s*["']?file["']?\s*:\s*["']([^"']+)""")
    private val HLS_LINKS = Regex(""""(hls[234])"\s*:\s*"([^"]+)"""")
    private val FILE_FIELD = Regex("""["']?file["']?\s*:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)""")
    private val OG_TITLE = Regex("""<meta\s+(?:property|name)="og:title"\s+content="([^"]*)"""")
    private val TITLE_TAG = Regex("""<title>([^<]*)</title>""", RegexOption.IGNORE_CASE)
    private val OG_IMAGE = Regex("""<meta\s+(?:property|name)="og:image"\s+content="([^"]*)"""")
    private val JW_IMAGE = Regex("""image\s*:\s*["'](https?://[^"']+)""")

    /** [html] with its packed scripts unpacked and appended, `\/` unescaped. */
    fun expand(html: String): String {
        val plain = html.replace("\\/", "/")
        return plain + "\n" + JsUnpacker.unpackAll(plain)
    }

    /** The best stream in an (already [expand]ed) page: HLS links map first (highest numbered
     * wins — hls4 is the host's primary CDN), then JW Player sources, then any file field. */
    fun findStream(page: String, pageUrl: HttpUrl): String? {
        val links = HLS_LINKS.findAll(page).associate { it.groupValues[1] to it.groupValues[2] }
        val candidate = listOf("hls4", "hls3", "hls2").firstNotNullOfOrNull { links[it] }
            ?: JW_SOURCES.find(page)?.groupValues?.get(1)
            ?: FILE_FIELD.find(page)?.groupValues?.get(1)
            ?: return null
        return absolutize(candidate, pageUrl)
    }

    fun findTitle(html: String): String? =
        (OG_TITLE.find(html) ?: TITLE_TAG.find(html))
            ?.groupValues?.get(1)
            ?.decodeHtmlEntities()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("Watch", ignoreCase = true) }

    fun findPoster(page: String): String? =
        (OG_IMAGE.find(page) ?: JW_IMAGE.find(page))?.groupValues?.get(1)?.takeIf { it.startsWith("https://") }

    fun absolutize(link: String, base: HttpUrl): String? {
        val clean = link.trim().replace("&amp;", "&")
        return when {
            clean.startsWith("//") -> "${base.scheme}:$clean"
            clean.startsWith("http://") || clean.startsWith("https://") -> clean
            else -> base.resolve(clean)?.toString()
        }
    }

    fun String.decodeHtmlEntities(): String = this
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#039;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

    /** "Movie.Name.2024.mp4" → shown as-is; hosts often append " - Host" to <title>. */
    fun cleanTitle(title: String?, hostName: String): String? = title
        ?.replace(Regex("""\s*[-|–]\s*$hostName.*$""", RegexOption.IGNORE_CASE), "")
        ?.removePrefix("Watch ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

/** Matches a URL's host against a resolver's domains: exact names plus an optional pattern for
 * the families of throwaway mirror domains these hosts rotate through. */
class HostMatcher(private val domains: Set<String>, private val pattern: Regex? = null) {
    fun matches(url: HttpUrl): Boolean {
        val host = url.host.lowercase().removePrefix("www.")
        return host in domains || pattern?.matches(host) == true
    }
}
