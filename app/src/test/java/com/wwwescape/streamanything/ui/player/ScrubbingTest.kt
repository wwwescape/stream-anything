package com.wwwescape.streamanything.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ScrubbingTest {

    @Test
    fun singlePressesMoveByTheSkipDuration() {
        assertEquals(10_000L, scrubStepMs(10_000L, repeatCount = 0, durationMs = 7_200_000L))
        assertEquals(10_000L, scrubStepMs(10_000L, repeatCount = 3, durationMs = 7_200_000L))
    }

    @Test
    fun holdingAccelerates() {
        assertEquals(20_000L, scrubStepMs(10_000L, repeatCount = 4, durationMs = 60_000L))
        assertEquals(40_000L, scrubStepMs(10_000L, repeatCount = 12, durationMs = 60_000L))
        assertEquals(80_000L, scrubStepMs(10_000L, repeatCount = 30, durationMs = 60_000L))
    }

    @Test
    fun longHoldsOnLongVideosStepAtLeastOnePercent() {
        // 10 hours: 1% = 6 minutes beats 4 × 10s.
        assertEquals(360_000L, scrubStepMs(10_000L, repeatCount = 12, durationMs = 36_000_000L))
    }

    @Test
    fun scrubIsClampedAndCommitsOnce() {
        val scrub = ScrubState()
        scrub.start(fromMs = 5_000L, source = ScrubSource.KEYS)
        scrub.step(direction = -1, repeatCount = 0, baseStepMs = 10_000L, durationMs = 60_000L)
        assertEquals(0L, scrub.targetMs)
        scrub.moveTo(90_000L, durationMs = 60_000L)
        assertEquals(60_000L, scrub.targetMs)
        assertEquals(5_000L, scrub.originMs)

        assertEquals(60_000L, scrub.finish())
        assertFalse(scrub.isActive)
        assertNull(scrub.finish())
    }

    @Test
    fun restartingAnActiveScrubKeepsItsOrigin() {
        val scrub = ScrubState()
        scrub.start(fromMs = 1_000L, source = ScrubSource.KEYS)
        scrub.moveTo(20_000L, durationMs = 60_000L)
        scrub.start(fromMs = 3_000L, source = ScrubSource.TOUCH)
        assertEquals(1_000L, scrub.originMs)
        assertEquals(20_000L, scrub.targetMs)
    }
}
