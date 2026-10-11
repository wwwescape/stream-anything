package com.wwwescape.streamanything.data.streams

import okhttp3.HttpUrl

/** A plain link straight to a media file or HLS playlist — nothing to resolve, it just plays.
 * Recognized by file extension only, so it's checked after every host-specific resolver.
 * HTTPS anywhere; plain HTTP only on the home network (see [HomeNetwork]). */
object DirectLinkResolver : StreamResolver {

    override val displayName = "MP4 · WebM · MKV · HLS"

    private const val MIME_HLS = "application/x-mpegURL"

    private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "webm", "mkv", "mov", "3gp", "avi", "ts", "mpg", "mpeg", "flv")
    private val HLS_EXTENSIONS = setOf("m3u8")

    override fun canResolve(url: HttpUrl): Boolean =
        HomeNetwork.isAllowed(url) && extension(url) in VIDEO_EXTENSIONS + HLS_EXTENSIONS

    override suspend fun resolve(url: HttpUrl): ResolvedStream {
        val ext = extension(url)
        if (ext !in VIDEO_EXTENSIONS + HLS_EXTENSIONS) throw StreamError.UnsupportedLink()
        val fileName = url.pathSegments.lastOrNull { it.isNotEmpty() }
        return ResolvedStream(
            streamUrl = url.toString(),
            title = if (ext in HLS_EXTENSIONS) url.host else fileName,
            mimeType = if (ext in HLS_EXTENSIONS) MIME_HLS else null,
        )
    }

    private fun extension(url: HttpUrl): String =
        url.pathSegments.lastOrNull { it.isNotEmpty() }?.substringAfterLast('.', "")?.lowercase().orEmpty()
}
