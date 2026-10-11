package com.wwwescape.streamanything.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Where a scrub came from — decides how it's committed. Touch commits when the finger lifts;
 * keys commit after a short pause in presses (or straight away on OK). */
enum class ScrubSource { TOUCH, KEYS }

/**
 * A pending seek. While scrubbing, the seek bar, time label, and preview bubble all show
 * [targetMs] but the player isn't touched — so a long run of D-pad presses or a drag across the
 * whole bar costs one seek (and one rebuffer) at the end instead of dozens along the way. That
 * single deferred seek is most of what makes Netflix-style scrubbing feel smooth.
 */
@Stable
class ScrubState {
    var targetMs by mutableStateOf<Long?>(null)
        private set

    /** Playback position when the scrub began, for the "+1:30" delta in the bubble. */
    var originMs by mutableLongStateOf(0L)
        private set

    var source by mutableStateOf(ScrubSource.TOUCH)
        private set

    val isActive: Boolean get() = targetMs != null

    fun start(fromMs: Long, source: ScrubSource) {
        if (targetMs == null) {
            originMs = fromMs
            targetMs = fromMs
        }
        this.source = source
    }

    fun moveTo(ms: Long, durationMs: Long) {
        targetMs = ms.clampTo(durationMs)
    }

    /** One D-pad step, accelerating while the key is held (see [scrubStepMs]). */
    fun step(direction: Int, repeatCount: Int, baseStepMs: Long, durationMs: Long) {
        val current = targetMs ?: return
        moveTo(current + direction * scrubStepMs(baseStepMs, repeatCount, durationMs), durationMs)
    }

    /** Ends the scrub and returns where to seek, or `null` if there was nothing pending. */
    fun finish(): Long? = targetMs.also { targetMs = null }

    fun cancel() {
        targetMs = null
    }

    private fun Long.clampTo(durationMs: Long): Long =
        if (durationMs > 0) coerceIn(0L, durationMs) else coerceAtLeast(0L)
}

/**
 * How far one D-pad event moves the scrub target. A single press moves by the user's skip
 * duration; holding the key (Android repeats it ~20×/s) speeds up in stages so crossing a
 * two-hour film takes a few seconds rather than a minute, while a short hold stays precise.
 * Once the hold is long, a step is never less than 1% of the video, so long films accelerate
 * as fast (relative to their length) as short clips.
 */
fun scrubStepMs(baseStepMs: Long, repeatCount: Int, durationMs: Long): Long {
    val multiplier = when {
        repeatCount < 4 -> 1
        repeatCount < 12 -> 2
        repeatCount < 24 -> 4
        else -> 8
    }
    val step = baseStepMs * multiplier
    return if (repeatCount >= 12 && durationMs > 0) maxOf(step, durationMs / 100) else step
}
