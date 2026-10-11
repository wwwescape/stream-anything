package com.wwwescape.streamanything.data.history

import java.security.MessageDigest
import java.util.Base64

/**
 * Where playback stopped in one video. [id] is a hash of the video's identity. [link] is only
 * present while the Experimental "Show history" option is on — otherwise the entry says nothing
 * about what was watched.
 */
data class WatchEntry(
    val id: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val link: String? = null,
) {
    val isFinished: Boolean get() = WatchHistory.isFinished(positionMs, durationMs)
    val isResumable: Boolean get() = WatchHistory.isResumable(positionMs, durationMs)
}

/**
 * The rules behind "resume where you left off" and Recent history, kept free of Android so
 * they're unit-testable.
 *
 * By default the history is unreadable: entries are keyed by a SHA-256 of the video's identity
 * ([idFor]) and carry no link, title, or anything else that says what was watched — only "some
 * video, stopped at 12:34". Links are added only while "Show history" is on, and stripped from
 * every entry ([withoutLinks]) when it's turned off.
 */
object WatchHistory {

    /** How many videos are remembered. Each entry is ~200 bytes, so this is about performance
     * only in theory — it's a privacy/usefulness cap. */
    const val MAX_ENTRIES = 50

    /** How many of them Recent history shows — what's comfortable to scroll with a remote. */
    const val RECENT_LIMIT = 20

    /** Less than this in, and it isn't worth resuming — just start over. */
    const val MIN_PROGRESS_MS = 10_000L

    /** Within this much of the end (or [END_FRACTION] of it, if longer) counts as finished —
     * credits and outros shouldn't leave a video "in progress". */
    const val END_MARGIN_MS = 15_000L
    const val END_FRACTION = 0.05

    fun idFor(videoKey: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(videoKey.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun isFinished(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0) return false
        val margin = maxOf(END_MARGIN_MS, (durationMs * END_FRACTION).toLong())
        return durationMs - positionMs <= margin
    }

    /** Whether a position is worth resuming from: a known, finite duration, past the start, and
     * not at the end. */
    fun isResumable(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= MIN_PROGRESS_MS && !isFinished(positionMs, durationMs)

    /** Records a position: replaces this video's entry, newest first, capped at [MAX_ENTRIES]
     * by dropping the least recently played. Finished videos are kept (Recent history offers
     * them as "Rewatch"); they just never resume. */
    fun record(entries: List<WatchEntry>, entry: WatchEntry): List<WatchEntry> =
        (listOf(entry) + entries.filter { it.id != entry.id })
            .sortedByDescending { it.updatedAt }
            .take(MAX_ENTRIES)

    fun resumePositionMs(entries: List<WatchEntry>, id: String): Long? =
        entries.firstOrNull { it.id == id }?.takeIf { it.isResumable }?.positionMs

    /** The newest entries that have a link to show, for Recent history. */
    fun recent(entries: List<WatchEntry>): List<WatchEntry> =
        entries.filter { it.link != null }.sortedByDescending { it.updatedAt }.take(RECENT_LIMIT)

    fun withoutLinks(entries: List<WatchEntry>): List<WatchEntry> = entries.map { it.copy(link = null) }

    /** Forgets one video entirely — its link and where it stopped. */
    fun remove(entries: List<WatchEntry>, id: String): List<WatchEntry> = entries.filter { it.id != id }

    // One entry per line, tab-separated: id, position, duration, updatedAt, and the link
    // (base64url, so no character in it can break the format) when there is one. A corrupt
    // line is skipped rather than failing the whole history.

    fun encode(entries: List<WatchEntry>): String =
        entries.joinToString("\n") { entry ->
            listOfNotNull(
                entry.id,
                entry.positionMs.toString(),
                entry.durationMs.toString(),
                entry.updatedAt.toString(),
                entry.link?.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) },
            ).joinToString("\t")
        }

    fun decode(text: String?): List<WatchEntry> =
        text.orEmpty().lineSequence().mapNotNull { line ->
            // Tab-separated now; earlier builds wrote four space-separated fields.
            val parts = line.trim().split('\t', ' ')
            if (parts.size !in 4..5 || parts[0].length != 64) return@mapNotNull null
            WatchEntry(
                id = parts[0],
                positionMs = parts[1].toLongOrNull() ?: return@mapNotNull null,
                durationMs = parts[2].toLongOrNull() ?: return@mapNotNull null,
                updatedAt = parts[3].toLongOrNull() ?: return@mapNotNull null,
                link = parts.getOrNull(4)?.let { encoded ->
                    runCatching { String(Base64.getUrlDecoder().decode(encoded)) }.getOrNull()
                },
            )
        }.toList()
}
