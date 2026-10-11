package com.wwwescape.streamanything.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchHistoryTest {

    private val hour = 3_600_000L

    private fun entry(n: Int, position: Long = 60_000L, duration: Long = hour, at: Long = n.toLong(), link: String? = null) =
        WatchEntry(WatchHistory.idFor("video-$n"), position, duration, at, link)

    @Test
    fun storesHashesNotLinks() {
        val id = WatchHistory.idFor("streamtape:oAyl8rV67auW39")
        assertEquals(64, id.length)
        assertFalse(id.contains("streamtape"))
        assertEquals(id, WatchHistory.idFor("streamtape:oAyl8rV67auW39"))
    }

    @Test
    fun onlyMidVideoPositionsAreResumable() {
        assertFalse("barely started", WatchHistory.isResumable(5_000L, hour))
        assertTrue(WatchHistory.isResumable(20 * 60_000L, hour))
        // Last 5% of an hour is 3 minutes — credits count as finished.
        assertFalse("in the credits", WatchHistory.isResumable(hour - 2 * 60_000L, hour))
        assertTrue(WatchHistory.isFinished(hour - 2 * 60_000L, hour))
        // Short videos use the 15s margin instead.
        assertTrue(WatchHistory.isResumable(40_000L, 60_000L))
        assertFalse(WatchHistory.isResumable(50_000L, 60_000L))
        assertFalse("live stream", WatchHistory.isResumable(60_000L, 0L))
        assertFalse("live stream", WatchHistory.isFinished(60_000L, 0L))
    }

    @Test
    fun recordingReplacesAndReordersNewestFirst() {
        var entries = listOf(entry(1), entry(2))
        entries = WatchHistory.record(entries, entry(1, position = 120_000L, at = 10))
        assertEquals(listOf(entry(1, position = 120_000L, at = 10), entry(2)), entries)
        assertEquals(120_000L, WatchHistory.resumePositionMs(entries, WatchHistory.idFor("video-1")))
    }

    @Test
    fun finishedVideosStayListedButNeverResume() {
        val entries = WatchHistory.record(listOf(entry(2)), entry(1, position = hour, at = 10))
        assertEquals(2, entries.size)
        assertTrue(entries.first().isFinished)
        assertNull(WatchHistory.resumePositionMs(entries, WatchHistory.idFor("video-1")))
    }

    @Test
    fun keepsOnlyTheFiftyMostRecent() {
        val full = (1..WatchHistory.MAX_ENTRIES).map { entry(it) }
        val updated = WatchHistory.record(full, entry(99, at = 1_000))
        assertEquals(WatchHistory.MAX_ENTRIES, updated.size)
        assertEquals(WatchHistory.idFor("video-99"), updated.first().id)
        assertNull("oldest dropped", WatchHistory.resumePositionMs(updated, WatchHistory.idFor("video-1")))
    }

    @Test
    fun recentShowsOnlyLinkedEntriesNewestFirstCapped() {
        val entries = (1..30).map { entry(it, link = if (it % 3 == 0) null else "https://example.com/v/$it") }
        val recent = WatchHistory.recent(entries)
        assertEquals(WatchHistory.RECENT_LIMIT, recent.size)
        assertEquals("https://example.com/v/29", recent.first().link)
        assertTrue(recent.all { it.link != null })
    }

    @Test
    fun turningShowHistoryOffKeepsPositionsButDropsLinks() {
        val stripped = WatchHistory.withoutLinks(listOf(entry(1, link = "https://example.com/v/1")))
        assertNull(stripped.single().link)
        assertEquals(60_000L, stripped.single().positionMs)
    }

    @Test
    fun roundTripsLinksAndSkipsCorruptLines() {
        val entries = listOf(entry(1, link = "https://streamtape.com/v/abc?x=1 2\ttab"), entry(2, position = 99_000L))
        assertEquals(entries, WatchHistory.decode(WatchHistory.encode(entries)))
        assertEquals(entries.take(1), WatchHistory.decode(WatchHistory.encode(entries.take(1)) + "\ngarbage line\n"))
        assertTrue(WatchHistory.decode(null).isEmpty())
    }

    @Test
    fun readsTheEarlierSpaceSeparatedFormat() {
        val id = WatchHistory.idFor("video-1")
        assertEquals(listOf(WatchEntry(id, 60_000L, hour, 5L)), WatchHistory.decode("$id 60000 $hour 5"))
    }

    @Test
    fun `removing one video forgets only that one`() {
        val entries = listOf(entry(1, link = "https://a"), entry(2, link = "https://b"), entry(3))
        val remaining = WatchHistory.remove(entries, entries[1].id)
        assertEquals(listOf(entries[0], entries[2]), remaining)
        assertNull(WatchHistory.resumePositionMs(remaining, entries[1].id))
        assertEquals(entries, WatchHistory.remove(entries, "not-there"))
    }
}
