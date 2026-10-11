package com.wwwescape.streamanything.data.streams

import okhttp3.HttpUrl
import org.json.JSONObject

/**
 * Veev — `veev.to/e/{id}` and `/d/{id}`.
 *
 * The page hides a key (`fc: '…'`) compressed with a small LZW-style scheme ([lzwDecode]).
 * Decoded, it's both the `ch` parameter for the player API (`/dl?op=player_api&cmd=gi…`) and,
 * via [keySteps], the recipe for unscrambling the API's (also compressed) media URL: a list of
 * hex-decode steps, some preceded by a string reversal.
 */
object VeevResolver : StreamResolver {

    override val displayName = "Veev"

    private val hosts = HostMatcher(domains = setOf("veev.to", "veev.pro", "poophq.com", "doods.to"))
    private val KEY_CANDIDATES = Regex("""[.\s'](?:fc|_vvto\[[^\]]*)(?:['\]]*)?\s*[:=]\s*['"]([^'"]+)""")
    private val PAGE_PATHS = setOf("e", "d")

    override fun canResolve(url: HttpUrl): Boolean = hosts.matches(url) && videoId(url) != null

    /** Same video whatever the link shape or mirror domain. */
    override fun videoKey(url: HttpUrl): String = videoId(url)?.let { "veev:$it" } ?: super.videoKey(url)

    override suspend fun resolve(url: HttpUrl): ResolvedStream {
        var id = videoId(url) ?: throw StreamError.UnsupportedLink()
        val pageUrl = StreamHttp.url("https://${url.host.removePrefix("www.")}/e/$id")
        val headers = mapOf("Referer" to pageUrl.toString())
        val page = StreamHttp.get(pageUrl, headers)
        if (page.url != pageUrl) page.url.pathSegments.lastOrNull { it.isNotEmpty() }?.let { id = it }

        val candidates = KEY_CANDIDATES.findAll(page.body).map { it.groupValues[1] }.toList()
        if (candidates.isEmpty()) throw StreamError.VideoUnavailable()
        for (candidate in candidates.asReversed()) {
            val key = lzwDecode(candidate)
            if (key == candidate) continue
            val api = page.url.newBuilder()
                .encodedPath("/dl")
                .query(null)
                .addQueryParameter("op", "player_api")
                .addQueryParameter("cmd", "gi")
                .addQueryParameter("file_code", id)
                .addQueryParameter("ch", key)
                .addQueryParameter("ie", "1")
                .build()
            val file = JSONObject(StreamHttp.get(api, headers).body).optJSONObject("file")
            if (file == null || file.optString("file_status") != "OK") throw StreamError.VideoUnavailable()
            val scrambled = file.optJSONArray("dv")?.optJSONObject(0)?.optString("s")
                ?: throw StreamError.Unreadable("No media in Veev API response")
            val stream = unscramble(lzwDecode(scrambled), keySteps(key).firstOrNull().orEmpty())
            return ResolvedStream(
                streamUrl = stream,
                title = file.optString("file_title").takeIf { it.isNotEmpty() }
                    ?: PageScraper.cleanTitle(PageScraper.findTitle(page.body), "Veev"),
                posterUrl = PageScraper.findPoster(page.body),
                mimeType = if ("m3u8" in stream) MIME_HLS else null,
                requestHeaders = headers,
            )
        }
        throw StreamError.Unreadable("No Veev key on page")
    }

    internal fun lzwDecode(encoded: String): String {
        if (encoded.isEmpty()) return encoded
        val table = HashMap<Int, String>()
        var next = 256
        var previous = encoded[0].toString()
        val result = StringBuilder(previous)
        for (char in encoded.substring(1)) {
            val code = char.code
            val entry = if (code < 256) char.toString() else table[code] ?: (previous + previous[0])
            result.append(entry)
            table[next++] = previous + entry[0]
            previous = entry
        }
        return result.toString()
    }

    /** "3101…" → [[1,0,1], …]: each group is a count digit followed by that many step digits
     * (stored in reverse). */
    internal fun keySteps(key: String): List<List<Int>> {
        val chars = ArrayDeque(key.toList())
        fun nextInt() = chars.removeFirstOrNull()?.digitToIntOrNull() ?: 0
        val groups = mutableListOf<List<Int>>()
        var count = nextInt()
        while (count != 0) {
            val group = ArrayDeque<Int>()
            repeat(count) { group.addFirst(nextInt()) }
            groups += group.toList()
            count = nextInt()
        }
        return groups
    }

    /** Applies [steps] to [text]: each step hex-decodes, reversing first when the step is 1. */
    internal fun unscramble(text: String, steps: List<Int>): String {
        var value = text
        for (step in steps) {
            if (step == 1) value = value.reversed()
            value = String(value.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), Charsets.UTF_8)
                .replace("dXRmOA==", "")
        }
        return value
    }

    internal fun videoId(url: HttpUrl): String? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < 2 || segments[0] !in PAGE_PATHS) return null
        return segments[1].takeIf { id -> id.all { it.isLetterOrDigit() } }
    }
}
