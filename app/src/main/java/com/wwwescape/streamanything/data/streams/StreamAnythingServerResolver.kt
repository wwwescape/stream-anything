package com.wwwescape.streamanything.data.streams

import okhttp3.HttpUrl

/**
 * Videos shared from a computer by Stream Anything Server:
 * `http://192.168.0.140:8090/media/{library}/{path…}?key=…` on the home network.
 *
 * Nothing to resolve — the link is the video — but a dedicated resolver gives these links a proper
 * title (the file name), accepts every format the server shares, and keys resume history on the
 * file itself, not the access key, so resume survives a new key.
 */
object StreamAnythingServerResolver : StreamResolver {

    override val displayName = "Stream Anything Server"

    /** What the server shares and Media3 can play. (WMV and M2TS are shared but not playable.) */
    private val PLAYABLE = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "avi", "ts", "mpg", "mpeg", "3gp", "flv", "ogv",
    )

    override fun canResolve(url: HttpUrl): Boolean =
        HomeNetwork.isHomeAddress(url.host) &&
            url.pathSegments.size >= 3 &&
            url.pathSegments[0] == "media" &&
            url.queryParameter("key") != null &&
            extension(url) in PLAYABLE

    /** Same file on the same server, whatever the access key. */
    override fun videoKey(url: HttpUrl): String =
        "streamanythingserver:${url.host}:${url.port}/${url.pathSegments.drop(1).joinToString("/")}"

    override suspend fun resolve(url: HttpUrl): ResolvedStream {
        if (!canResolve(url)) throw StreamError.UnsupportedLink()
        return ResolvedStream(
            streamUrl = url.toString(),
            title = url.pathSegments.last().substringBeforeLast('.'),
            // The server sends a proper Content-Type; let the player sniff the container.
            mimeType = null,
        )
    }

    private fun extension(url: HttpUrl): String =
        url.pathSegments.lastOrNull { it.isNotEmpty() }?.substringAfterLast('.', "")?.lowercase().orEmpty()
}
