package com.wwwescape.streamanything.ui.player

import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.wwwescape.streamanything.R
import com.wwwescape.streamanything.data.settings.SeekIncrement
import com.wwwescape.streamanything.data.settings.VideoScaling
import com.wwwescape.streamanything.data.streams.StreamError
import com.wwwescape.streamanything.ui.screens.settings.label
import com.wwwescape.streamanything.ui.tvsend.SendLinkDialog
import kotlinx.coroutines.delay

private const val CONTROLS_TIMEOUT_MS = 3_500L
private const val FEEDBACK_TIMEOUT_MS = 800L
private const val KEY_SCRUB_COMMIT_MS = 800L
private const val RESUME_NOTICE_MS = 8_000L

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onExit: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scaling by viewModel.scaling.collectAsStateWithLifecycle()
    val resumedFromMs by viewModel.resumedFromMs.collectAsStateWithLifecycle()
    val continueElsewhere by viewModel.continueElsewhereAvailable.collectAsStateWithLifecycle()
    val sendState by viewModel.linkSend.state.collectAsStateWithLifecycle()
    val sendTargets by viewModel.linkSend.devices.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val player = viewModel.player
    val playback = rememberPlaybackState(player)
    val seekIncrement = settings.playback.seekIncrement
    val seekMs = seekIncrement.seconds * 1000L

    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION

    var controlsVisible by remember { mutableStateOf(true) }
    // Bumped on every interaction so the auto-hide countdown restarts.
    var lastInteraction by remember { mutableLongStateOf(0L) }
    // Focus is only moved to the controls when they were summoned from a remote/keyboard;
    // touch users never see a focus ring.
    var keyboardMode by remember { mutableStateOf(isTv) }
    val scrub = remember { ScrubState() }
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    var scalingFeedback by remember { mutableStateOf<VideoScaling?>(null) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val rootFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }
    val seekBarFocus = remember { FocusRequester() }

    fun showControls() {
        controlsVisible = true
        lastInteraction = System.nanoTime()
    }

    /** One D-pad step of a deferred scrub; the seek happens when the presses stop. */
    fun keyScrub(direction: Int, repeatCount: Int) {
        scrub.start(fromMs = player.currentPosition, source = ScrubSource.KEYS)
        scrub.step(direction, repeatCount, seekMs, playback.durationMs)
        showControls()
    }

    fun touchScrubTo(ms: Long) {
        val previous = scrub.targetMs
        scrub.start(fromMs = player.currentPosition, source = ScrubSource.TOUCH)
        scrub.moveTo(ms, playback.durationMs)
        // A light tick at every tenth of the bar, so the finger can feel where it is.
        val duration = playback.durationMs
        if (previous != null && duration > 0 && previous * 10 / duration != ms * 10 / duration) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        showControls()
    }

    fun commitScrub() {
        scrub.finish()?.let(player::seekTo)
        showControls()
    }

    fun togglePlayPause() {
        when {
            playback.isEnded -> {
                player.seekTo(0)
                player.play()
            }
            player.playWhenReady -> player.pause()
            else -> player.play()
        }
    }

    fun seekBy(direction: Int) {
        val target = (player.currentPosition + direction * seekMs)
            .coerceIn(0L, playback.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        player.seekTo(target)
        val previous = seekFeedback
        val seconds = if (previous != null && previous.direction == direction) previous.seconds + seekIncrement.seconds else seekIncrement.seconds
        seekFeedback = SeekFeedback(direction, seconds, System.nanoTime())
    }

    fun cycleScaling() {
        val current = scaling ?: VideoScaling.FIT
        val next = VideoScaling.entries[(current.ordinal + 1) % VideoScaling.entries.size]
        viewModel.setScaling(next)
        scalingFeedback = next
    }

    // Auto-hide while playing; stay up while paused, scrubbing, or a menu is open.
    LaunchedEffect(controlsVisible, playback.isPlaying, lastInteraction, scrub.isActive, speedMenuOpen) {
        if (controlsVisible && playback.isPlaying && !scrub.isActive && !speedMenuOpen) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible, uiState) {
        if (controlsVisible && keyboardMode && uiState is PlayerUiState.Ready) {
            // Summoned by left/right → land on the seek bar mid-scrub; anything else → play/pause.
            val target = if (scrub.isActive && scrub.source == ScrubSource.KEYS) seekBarFocus else playPauseFocus
            runCatching { target.requestFocus() }
        } else if (!controlsVisible) {
            runCatching { rootFocus.requestFocus() }
        }
    }
    // D-pad scrubs commit once the presses pause, so a run of taps (or a held key) ends in a
    // single seek. Every new step restarts the wait.
    LaunchedEffect(scrub.targetMs, scrub.source) {
        if (scrub.isActive && scrub.source == ScrubSource.KEYS) {
            delay(KEY_SCRUB_COMMIT_MS)
            commitScrub()
        }
    }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(FEEDBACK_TIMEOUT_MS)
            seekFeedback = null
        }
    }
    LaunchedEffect(resumedFromMs) {
        if (resumedFromMs != null) {
            delay(RESUME_NOTICE_MS)
            viewModel.dismissResumeNotice()
        }
    }
    // Once the other device has it, this player's job is done.
    LaunchedEffect(Unit) {
        viewModel.linkSend.sentTo.collect { deviceName ->
            Toast.makeText(context, resources.getString(R.string.tv_sent, deviceName), Toast.LENGTH_SHORT).show()
            onExit()
        }
    }
    LaunchedEffect(scalingFeedback) {
        if (scalingFeedback != null) {
            delay(FEEDBACK_TIMEOUT_MS)
            scalingFeedback = null
        }
    }

    // TV convention: Back first dismisses the controls, a second Back leaves.
    BackHandler(enabled = isTv && controlsVisible && uiState is PlayerUiState.Ready) {
        controlsVisible = false
    }
    // Back mid-scrub abandons it and stays where playback was (declared last, so it wins).
    BackHandler(enabled = scrub.isActive) {
        scrub.cancel()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                keyboardMode = true
                when (event.key) {
                    Key.MediaPlayPause -> { togglePlayPause(); showControls(); true }
                    Key.MediaPlay -> { player.play(); showControls(); true }
                    Key.MediaPause -> { player.pause(); showControls(); true }
                    Key.MediaFastForward -> { seekBy(1); true }
                    Key.MediaRewind -> { seekBy(-1); true }
                    else -> if (controlsVisible) {
                        lastInteraction = System.nanoTime()
                        false
                    } else {
                        // Controls hidden: the remote drives playback directly, like Google TV's
                        // own player — OK toggles, left/right seek, anything else reveals.
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                togglePlayPause(); showControls(); true
                            }
                            Key.DirectionLeft -> { keyScrub(-1, event.nativeKeyEvent.repeatCount); true }
                            Key.DirectionRight -> { keyScrub(1, event.nativeKeyEvent.repeatCount); true }
                            Key.DirectionUp, Key.DirectionDown -> { showControls(); true }
                            else -> false
                        }
                    }
                }
            },
    ) {
        VideoSurface(player = player, scaling = scaling ?: settings.playback.scaling)

        val stream = (uiState as? PlayerUiState.Ready)?.stream
        if (stream?.posterUrl != null && !playback.firstFrameRendered) {
            AsyncImage(
                model = stream.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Touch: tap toggles the controls, double-tap the left/right third seeks, double-tap
        // the middle plays/pauses.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            keyboardMode = false
                            if (controlsVisible) controlsVisible = false else showControls()
                        },
                        onDoubleTap = { offset ->
                            keyboardMode = false
                            when {
                                offset.x < size.width / 3f -> seekBy(-1)
                                offset.x > size.width * 2f / 3f -> seekBy(1)
                                else -> togglePlayPause()
                            }
                        },
                    )
                },
        )

        when (val state = uiState) {
            is PlayerUiState.Failed -> ErrorPanel(
                error = state.error,
                onRetry = viewModel::retry,
                onExit = onExit,
                modifier = Modifier.align(Alignment.Center),
            )
            PlayerUiState.Resolving -> ResolvingIndicator(modifier = Modifier.align(Alignment.Center))
            is PlayerUiState.Ready -> {
                if (!controlsVisible && playback.isBuffering) {
                    BufferingIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }

        seekFeedback?.let { feedback ->
            SeekFeedbackBadge(
                feedback = feedback,
                modifier = Modifier
                    .align(if (feedback.direction < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 64.dp),
            )
        }
        scalingFeedback?.let { mode ->
            FeedbackPill(
                text = mode.label(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 24.dp),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && uiState !is PlayerUiState.Failed,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            ControlsOverlay(
                title = stream?.title,
                playback = playback,
                ready = stream != null,
                seekIncrement = seekIncrement,
                scaling = scaling ?: settings.playback.scaling,
                scrub = scrub,
                speedMenuOpen = speedMenuOpen,
                playPauseFocus = playPauseFocus,
                seekBarFocus = seekBarFocus,
                onBack = onExit,
                onPlayPause = { togglePlayPause(); showControls() },
                onSeekBy = { seekBy(it); showControls() },
                onTouchScrub = ::touchScrubTo,
                onScrubEnd = ::commitScrub,
                onKeyScrub = ::keyScrub,
                onSeekBarConfirm = { if (scrub.isActive) commitScrub() else { togglePlayPause(); showControls() } },
                onCycleScaling = { cycleScaling(); showControls() },
                onSpeedMenuOpenChange = { speedMenuOpen = it; showControls() },
                onSpeedSelected = { player.setPlaybackSpeed(it); speedMenuOpen = false; showControls() },
                // Phone → TV, or TV → a paired phone.
                onContinueElsewhere = if (continueElsewhere) viewModel::continueElsewhere else null,
                continueOnPhone = isTv,
            )
        }

        AnimatedVisibility(
            visible = resumedFromMs != null && uiState is PlayerUiState.Ready,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            ResumeNotice(
                positionMs = resumedFromMs ?: 0L,
                onStartOver = { viewModel.startOver(); showControls() },
            )
        }
    }

    LaunchedEffect(Unit) {
        runCatching { rootFocus.requestFocus() }
    }

    sendState?.let { state ->
        SendLinkDialog(
            state = state,
            devices = sendTargets,
            onChoose = viewModel.linkSend::sendTo,
            onSubmitCode = viewModel.linkSend::submitCode,
            onDismiss = viewModel.linkSend::dismiss,
        )
    }
}

private data class SeekFeedback(val direction: Int, val seconds: Int, val at: Long)

@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(player: Player, scaling: VideoScaling) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = false
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setKeepContentOnPlayerReset(true)
                // Compose owns all input and focus; the view is purely a render surface.
                isFocusable = false
                isClickable = false
                this.player = player
            }
        },
        update = { view ->
            view.player = player
            view.resizeMode = when (scaling) {
                VideoScaling.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                VideoScaling.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                VideoScaling.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
        },
        onRelease = { view -> view.player = null },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ControlsOverlay(
    title: String?,
    playback: PlaybackState,
    ready: Boolean,
    seekIncrement: SeekIncrement,
    scaling: VideoScaling,
    scrub: ScrubState,
    speedMenuOpen: Boolean,
    playPauseFocus: FocusRequester,
    seekBarFocus: FocusRequester,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Int) -> Unit,
    onTouchScrub: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    onKeyScrub: (direction: Int, repeatCount: Int) -> Unit,
    onSeekBarConfirm: () -> Unit,
    onCycleScaling: () -> Unit,
    onSpeedMenuOpenChange: (Boolean) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onContinueElsewhere: (() -> Unit)?,
    continueOnPhone: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top: back + title over a soft gradient.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
            )
            Text(
                text = title.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 16.dp),
            )
        }

        // Center: rewind · play/pause · forward.
        if (ready) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIconButton(
                    icon = seekIncrement.rewindIcon(),
                    contentDescription = stringResource(R.string.player_rewind, seekIncrement.seconds),
                    onClick = { onSeekBy(-1) },
                    size = 56.dp,
                    iconSize = 32.dp,
                )
                PlayPauseButton(
                    playback = playback,
                    onClick = onPlayPause,
                    modifier = Modifier.focusRequester(playPauseFocus),
                )
                PlayerIconButton(
                    icon = seekIncrement.forwardIcon(),
                    contentDescription = stringResource(R.string.player_forward, seekIncrement.seconds),
                    onClick = { onSeekBy(1) },
                    size = 56.dp,
                    iconSize = 32.dp,
                )
            }
        }

        // Bottom: seek bar, then time on the left and speed/scaling on the right.
        if (ready) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 12.dp),
            ) {
                SeekBar(
                    positionMs = playback.positionMs,
                    durationMs = playback.durationMs,
                    bufferedMs = playback.bufferedMs,
                    previewMs = scrub.targetMs,
                    originMs = scrub.originMs,
                    onScrubStart = onTouchScrub,
                    onScrubMove = onTouchScrub,
                    onScrubEnd = onScrubEnd,
                    onKeyStep = onKeyScrub,
                    onConfirm = onSeekBarConfirm,
                    focusRequester = seekBarFocus,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = buildString {
                            append(formatPlaybackTime(scrub.targetMs ?: playback.positionMs))
                            if (playback.durationMs > 0) {
                                append("  /  ")
                                append(formatPlaybackTime(playback.durationMs))
                            }
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (onContinueElsewhere != null) {
                        PlayerIconButton(
                            icon = if (continueOnPhone) Icons.Rounded.PhoneAndroid else Icons.Rounded.Tv,
                            contentDescription = stringResource(
                                if (continueOnPhone) R.string.action_continue_on_phone else R.string.action_continue_on_tv,
                            ),
                            onClick = onContinueElsewhere,
                            size = 44.dp,
                            iconSize = 24.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Box {
                        PlayerTextButton(
                            text = formatSpeed(playback.speed),
                            onClick = { onSpeedMenuOpenChange(true) },
                        )
                        DropdownMenu(
                            expanded = speedMenuOpen,
                            onDismissRequest = { onSpeedMenuOpenChange(false) },
                        ) {
                            PLAYBACK_SPEEDS.forEach { speed ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (speed == 1f) stringResource(R.string.player_speed_normal) else formatSpeed(speed),
                                            color = if (speed == playback.speed) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                        )
                                    },
                                    onClick = { onSpeedSelected(speed) },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    PlayerIconButton(
                        icon = scaling.icon(),
                        contentDescription = stringResource(R.string.player_scaling, scaling.label()),
                        onClick = onCycleScaling,
                        size = 44.dp,
                        iconSize = 24.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(playback: PlaybackState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val showPause = playback.playWhenReady && !playback.isEnded
    PlayerIconButton(
        icon = if (showPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
        contentDescription = stringResource(if (showPause) R.string.player_pause else R.string.player_play),
        onClick = onClick,
        size = 80.dp,
        iconSize = 48.dp,
        containerColor = ScrimWhite,
        modifier = modifier,
        content = if (playback.isBuffering && playback.playWhenReady) {
            { CircularProgressIndicator(color = Color.White, strokeWidth = 4.dp, strokeCap = StrokeCap.Round, modifier = Modifier.size(44.dp)) }
        } else {
            null
        },
    )
}

@Composable
private fun BufferingIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(color = Color.White, strokeWidth = 4.dp, strokeCap = StrokeCap.Round, modifier = modifier.size(56.dp))
}

@Composable
private fun ResolvingIndicator(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp, strokeCap = StrokeCap.Round, modifier = Modifier.size(56.dp))
        Text(
            text = stringResource(R.string.player_resolving),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun ErrorPanel(error: Throwable, onRetry: () -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val retryFocus = remember { FocusRequester() }
    val canRetry = error !is StreamError.UnsupportedLink
    Surface(
        modifier = modifier
            .padding(24.dp)
            .widthIn(max = 480.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = stringResource(R.string.player_error_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onExit) {
                    Text(stringResource(R.string.action_back))
                }
                if (canRetry) {
                    Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) {
                        Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.action_try_again), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    LaunchedEffect(canRetry) {
        if (canRetry) runCatching { retryFocus.requestFocus() }
    }
}

/** "Resumed from 12:34 · Start over" — shown briefly when a video picks up where it was left
 * off. Top-right, level with the Back button, so it's one D-pad press away on a remote. */
@Composable
private fun ResumeNotice(positionMs: Long, onStartOver: () -> Unit) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.player_resumed_from, formatPlaybackTime(positionMs)),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(end = 12.dp),
        )
        PlayerTextButton(text = stringResource(R.string.player_start_over), onClick = onStartOver)
    }
}

@Composable
private fun SeekFeedbackBadge(feedback: SeekFeedback, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(96.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (feedback.direction < 0) "−${feedback.seconds}s" else "+${feedback.seconds}s",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun FeedbackPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

private fun formatSpeed(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')) + "×"

private fun SeekIncrement.rewindIcon(): ImageVector = when (this) {
    SeekIncrement.FIVE_SECONDS -> Icons.Rounded.Replay5
    SeekIncrement.TEN_SECONDS -> Icons.Rounded.Replay10
    SeekIncrement.THIRTY_SECONDS -> Icons.Rounded.Replay30
}

private fun SeekIncrement.forwardIcon(): ImageVector = when (this) {
    SeekIncrement.FIVE_SECONDS -> Icons.Rounded.Forward5
    SeekIncrement.TEN_SECONDS -> Icons.Rounded.Forward10
    SeekIncrement.THIRTY_SECONDS -> Icons.Rounded.Forward30
}

private fun VideoScaling.icon(): ImageVector = when (this) {
    VideoScaling.FIT -> Icons.Rounded.FitScreen
    VideoScaling.ZOOM -> Icons.Rounded.ZoomOutMap
    VideoScaling.STRETCH -> Icons.Rounded.AspectRatio
}

private fun Throwable.messageRes(): Int = when (this) {
    is StreamError.UnsupportedLink -> R.string.error_unsupported_link
    is StreamError.VideoUnavailable -> R.string.error_video_unavailable
    is StreamError.Network -> R.string.error_network
    is StreamError.Unreadable -> R.string.error_unreadable
    is PlaybackException -> when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> R.string.error_network
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        -> R.string.error_link_expired
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        -> R.string.error_format_unsupported
        else -> R.string.error_playback
    }
    else -> R.string.error_playback
}
