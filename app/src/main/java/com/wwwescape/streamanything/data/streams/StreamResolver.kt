package com.wwwescape.streamanything.data.streams

import okhttp3.HttpUrl

/** Everything the player needs to start a video: the actual media URL (not the share page it
 * came from) plus whatever the host told us about it for display. */
data class ResolvedStream(
    val streamUrl: String,
    val title: String?,
    val posterUrl: String? = null,
    /** A hint for the player when the URL alone doesn't say what the media is (e.g. an HLS
     * playlist served without a `.m3u8` extension). `null` lets the player sniff the content. */
    val mimeType: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
)

/** Why a link couldn't be turned into a playable stream — each maps to its own user-facing
 * message rather than one generic "something went wrong". */
sealed class StreamError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Not a link any resolver recognizes. */
    class UnsupportedLink : StreamError("Unsupported link")

    /** The host says the video doesn't exist (deleted, expired, or a typo in the ID). */
    class VideoUnavailable : StreamError("Video unavailable")

    /** Couldn't reach the host at all. */
    class Network(cause: Throwable) : StreamError("Network error", cause)

    /** The host answered, but not in a shape we know how to read — most likely the site changed
     * how it builds its links and the resolver needs updating. */
    class Unreadable(detail: String) : StreamError(detail)
}

/**
 * One video host. Each implementation knows which links belong to it ([canResolve], a cheap,
 * offline URL check used to validate input before opening the player) and how to turn such a
 * link into a playable stream ([resolve], which may hit the network).
 *
 * Adding a new service is: implement this, then register it in [StreamResolvers].
 */
interface StreamResolver {
    /** Shown in the "Works with" list on the home screen. */
    val displayName: String

    fun canResolve(url: HttpUrl): Boolean

    /** A stable identity for the video behind [url], for resume history — the same for every
     * link shape and mirror domain that points at it (`/v/{id}`, `/e/{id}/name.mp4`…). Hosts
     * with video IDs override this; the default is the link minus its fragment. */
    fun videoKey(url: HttpUrl): String = url.newBuilder().fragment(null).build().toString()

    /** @throws StreamError */
    suspend fun resolve(url: HttpUrl): ResolvedStream
}
