package com.wwwescape.streamanything.data.streams

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The registry of supported services. Host-specific resolvers come first; the extension-based
 * [DirectLinkResolver] is the catch-all, so it must stay last. */
object StreamResolvers {

    val all: List<StreamResolver> = listOf(
        BigWarpResolver,
        DoodStreamResolver,
        LuluStreamResolver,
        MixdropResolver,
        StreamtapeResolver,
        StreamWishResolver,
        VeevResolver,
        VidHideResolver,
        VidNestResolver,
        VoeResolver,
        StreamAnythingServerResolver,
        DirectLinkResolver,
    )

    private val URL_IN_TEXT = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    /**
     * Turns whatever the user typed, pasted, or shared into a URL — tolerating a missing scheme
     * ("streamtape.com/v/…") and surrounding text (share sheets often send "Check this out:
     * https://…"). Only http(s) is accepted.
     */
    fun parse(input: String): HttpUrl? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val candidate = URL_IN_TEXT.find(text)?.value?.trimEnd('.', ',', ')', ']', '"', '\'')
            ?: text.takeUnless { it.contains(' ') }?.let { "https://$it" }
            ?: return null
        return candidate.toHttpUrlOrNull()
    }

    fun find(url: HttpUrl): StreamResolver? = all.firstOrNull { it.canResolve(url) }

    fun isSupported(input: String): Boolean = parse(input)?.let(::find) != null

    /** See [StreamResolver.videoKey]; `null` for unsupported input. */
    fun videoKey(input: String): String? {
        val url = parse(input) ?: return null
        return find(url)?.videoKey(url)
    }

    /** @throws StreamError */
    suspend fun resolve(input: String): ResolvedStream {
        val url = parse(input) ?: throw StreamError.UnsupportedLink()
        val resolver = find(url) ?: throw StreamError.UnsupportedLink()
        return resolver.resolve(url)
    }
}
