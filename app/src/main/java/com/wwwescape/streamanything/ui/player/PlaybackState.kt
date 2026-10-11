package com.wwwescape.streamanything.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.delay

private const val POSITION_POLL_MS = 250L

/** A Compose-observable snapshot of the [Player]: event-driven for state changes, polled for
 * position (the player has no position-changed callback). */
@Stable
class PlaybackState(private val player: Player) {
    var isPlaying by mutableStateOf(player.isPlaying)
        private set
    var playWhenReady by mutableStateOf(player.playWhenReady)
        private set
    var playbackState by mutableIntStateOf(player.playbackState)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var bufferedMs by mutableLongStateOf(0L)
        private set
    var speed by mutableFloatStateOf(player.playbackParameters.speed)
        private set

    /** Whether the video has drawn anything yet — until then the poster stays up. Seeded from
     * the player itself so an Activity recreated mid-video doesn't cover it with the poster. */
    var firstFrameRendered by mutableStateOf(player.playbackState != Player.STATE_IDLE && player.currentPosition > 0)
        private set

    val isBuffering: Boolean get() = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
    val isEnded: Boolean get() = playbackState == Player.STATE_ENDED

    internal val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            isPlaying = player.isPlaying
            playWhenReady = player.playWhenReady
            playbackState = player.playbackState
            speed = player.playbackParameters.speed
            refreshPosition()
        }

        override fun onRenderedFirstFrame() {
            firstFrameRendered = true
        }
    }

    internal fun refreshPosition() {
        durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
        positionMs = player.currentPosition.coerceAtLeast(0L)
        bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
    }
}

@Composable
fun rememberPlaybackState(player: Player): PlaybackState {
    val state = remember(player) { PlaybackState(player) }
    DisposableEffect(player) {
        player.addListener(state.listener)
        onDispose { player.removeListener(state.listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            state.refreshPosition()
            delay(POSITION_POLL_MS)
        }
    }
    return state
}

/** `1:05`, `12:40`, `1:02:03` — hours only when needed. */
fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
