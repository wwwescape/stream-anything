package com.wwwescape.streamanything.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * A slim seek bar showing played and buffered progress. It never seeks by itself — it reports
 * scrub gestures and key presses, and the caller decides when to commit (see [ScrubState]):
 *
 * - Touch: [onScrubStart] on press, [onScrubMove] while dragging, [onScrubEnd] on release —
 *   so a tap is just a very short scrub.
 * - D-pad, when focused: left/right call [onKeyStep] with the key's repeat count so holding
 *   accelerates; OK calls [onConfirm]. Both are consumed, so they never move focus off the bar.
 *
 * While [previewMs] is set, the thumb sits at the preview position, a lighter segment shows the
 * jump from the current position, and a bubble above the thumb shows the target time and its
 * distance from where playback was ([originMs]).
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    previewMs: Long?,
    originMs: Long,
    onScrubStart: (Long) -> Unit,
    onScrubMove: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    onKeyStep: (direction: Int, repeatCount: Int) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var dragging by remember { mutableStateOf(false) }
    val currentDuration by rememberUpdatedState(durationMs)
    val active = focused || dragging || previewMs != null

    val trackHeight by animateDpAsState(if (active) 6.dp else 4.dp, label = "trackHeight")
    val thumbRadius by animateDpAsState(if (active) 8.dp else 6.dp, label = "thumbRadius")
    val primary = MaterialTheme.colorScheme.primary

    fun fractionOf(ms: Long) = if (durationMs > 0) (ms.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val played = fractionOf(positionMs)
    val buffered = fractionOf(bufferedMs)
    val thumb = previewMs?.let(::fractionOf) ?: played

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onKeyStep(-1, event.nativeKeyEvent.repeatCount)
                        true
                    }
                    Key.DirectionRight -> {
                        onKeyStep(1, event.nativeKeyEvent.repeatCount)
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onConfirm()
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        if (currentDuration <= 0) return@detectTapGestures
                        onScrubStart(((offset.x / size.width).coerceIn(0f, 1f) * currentDuration).toLong())
                        // A drag is handed to the handler below, which commits on its own release.
                        if (tryAwaitRelease() && !dragging) onScrubEnd()
                    },
                )
            }
            .pointerInput(Unit) {
                fun msAt(x: Float) = ((x / size.width).coerceIn(0f, 1f) * currentDuration).toLong()
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        onScrubStart(msAt(offset.x))
                    },
                    onDragEnd = {
                        dragging = false
                        onScrubEnd()
                    },
                    onDragCancel = {
                        dragging = false
                        onScrubEnd()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onScrubMove(msAt(change.position.x))
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height / 2
            val stroke = trackHeight.toPx()
            val inset = thumbRadius.toPx()
            val width = size.width - inset * 2
            fun x(fraction: Float) = inset + width * fraction

            drawLine(Color.White.copy(alpha = 0.24f), Offset(x(0f), y), Offset(x(1f), y), stroke, StrokeCap.Round)
            if (buffered > 0f) {
                drawLine(Color.White.copy(alpha = 0.45f), Offset(x(0f), y), Offset(x(buffered), y), stroke, StrokeCap.Round)
            }
            drawLine(primary, Offset(x(0f), y), Offset(x(minOf(played, thumb)), y), stroke, StrokeCap.Round)
            if (thumb != played) {
                // The jump about to happen, forwards or backwards.
                drawLine(
                    primary.copy(alpha = 0.5f),
                    Offset(x(minOf(played, thumb)), y),
                    Offset(x(maxOf(played, thumb)), y),
                    stroke,
                    StrokeCap.Round,
                )
            }
            drawCircle(if (focused) Color.White else primary, thumbRadius.toPx(), Offset(x(thumb), y))
        }

        if (previewMs != null) {
            ScrubBubble(
                targetMs = previewMs,
                deltaMs = previewMs - originMs,
                modifier = Modifier.layout { measurable, constraints ->
                    // Centered over the thumb, kept within the bar's width, floating above it.
                    // Measured free of the bar's 32dp height — it floats above it.
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity),
                    )
                    val barWidth = constraints.maxWidth
                    val inset = thumbRadius.toPx()
                    val thumbX = inset + (barWidth - inset * 2) * thumb
                    val maxX = (barWidth - placeable.width).coerceAtLeast(0)
                    val x = (thumbX - placeable.width / 2f).toInt().coerceIn(0, maxX)
                    layout(barWidth, constraints.minHeight) {
                        // The bar is centered in this 32dp box, so lift the bubble clear of the
                        // grown thumb (8dp radius) plus a small gap.
                        placeable.place(x, -placeable.height - 14.dp.roundToPx())
                    }
                },
            )
        }
    }
}

/** The floating "12:34 / +1:30" label above the thumb while scrubbing — the slot a frame
 * preview can later grow into without moving anything else. */
@Composable
private fun ScrubBubble(targetMs: Long, deltaMs: Long, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatPlaybackTime(targetMs),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        if (deltaMs != 0L) {
            Text(
                text = (if (deltaMs > 0) "+" else "−") + formatPlaybackTime(abs(deltaMs)),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
