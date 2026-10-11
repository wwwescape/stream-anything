package com.wwwescape.streamanything.ui.player

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.wwwescape.streamanything.data.history.WatchHistoryRepository
import com.wwwescape.streamanything.data.settings.AppSettings
import com.wwwescape.streamanything.data.settings.SettingsRepository
import com.wwwescape.streamanything.data.settings.VideoScaling
import com.wwwescape.streamanything.data.streams.ResolvedStream
import com.wwwescape.streamanything.data.streams.StreamHttp
import com.wwwescape.streamanything.data.streams.StreamResolvers
import com.wwwescape.streamanything.data.tvlink.TvLinkStore
import com.wwwescape.streamanything.ui.tvsend.LinkSendController
import com.wwwescape.streamanything.util.isTelevision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data object Resolving : PlayerUiState
    data class Ready(val stream: ResolvedStream) : PlayerUiState
    data class Failed(val error: Throwable) : PlayerUiState
}

/**
 * Owns the [ExoPlayer] so playback survives rotation and other configuration changes untouched,
 * and turns the user's link into a stream via [StreamResolvers] before handing it to the player.
 */
@OptIn(UnstableApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    val settings: StateFlow<AppSettings> = SettingsRepository.settingsFlow(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Resolving)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** `null` until the first video starts — then whatever the user last picked in the player,
     * falling back to the Settings default. */
    private val _scaling = MutableStateFlow<VideoScaling?>(null)
    val scaling: StateFlow<VideoScaling?> = _scaling.asStateFlow()

    /** Set when this video picked up where it was left off — the player shows a brief
     * "Resumed from 12:34 · Start over" notice while it's non-null. */
    private val _resumedFromMs = MutableStateFlow<Long?>(null)
    val resumedFromMs: StateFlow<Long?> = _resumedFromMs.asStateFlow()

    private val dataSourceFactory = OkHttpDataSource.Factory(StreamHttp.client)
        .setUserAgent(StreamHttp.USER_AGENT)

    val player: ExoPlayer = ExoPlayer.Builder(application)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            addListener(errorListener())
            addListener(historyListener())
        }

    private var sourceUrl: String? = null

    /** Identity of the current video in the resume history; `null` for unsupported input. */
    private var videoKey: String? = null
    private var resolveJob: Job? = null
    private var progressSaveJob: Job? = null

    /** Only one silent re-resolve per load — a second HTTP failure in a row is a real error. */
    private var autoRetried = false

    /** Where to start a video sent with Play on TV (0 = the beginning); used once, on the
     * first load, in place of this device's own resume position. */
    private var handoffPositionMs: Long? = null

    fun load(url: String, handoffPositionMs: Long? = null) {
        if (url == sourceUrl) return
        sourceUrl = url
        videoKey = StreamResolvers.videoKey(url)
        autoRetried = false
        this.handoffPositionMs = handoffPositionMs
        resolveAndPlay(url, startPositionMs = null)
    }

    fun retry() {
        val url = sourceUrl ?: return
        resolveAndPlay(url, startPositionMs = player.currentPosition.coerceAtLeast(0L))
    }

    fun startOver() {
        player.seekTo(0L)
        player.play()
        _resumedFromMs.value = null
    }

    fun dismissResumeNotice() {
        _resumedFromMs.value = null
    }

    /**
     * Remembers where playback is, if resume is on. Only once a video has actually loaded —
     * a failed or still-resolving video must not overwrite (or erase) its earlier position.
     * Finished videos stay listed (for "Rewatch") but never resume (see WatchHistory).
     */
    fun saveProgress() {
        val key = videoKey ?: return
        val current = settings.value
        if (_uiState.value !is PlayerUiState.Ready || !current.playback.resumePlayback) return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        // The link itself is only kept while Recent history is on; otherwise just the hash.
        val link = sourceUrl?.takeIf { current.experimental.showHistory }
            ?.let { StreamResolvers.parse(it)?.toString() ?: it }
        WatchHistoryRepository.record(getApplication(), key, player.currentPosition.coerceAtLeast(0L), duration, link)
    }

    private val isTv = application.isTelevision()

    /** "Continue on TV" on a phone with Play on TV on; "Continue on phone" on a TV with it on
     * and a phone paired. The other devices are looked for only once it's pressed, never during
     * playback. */
    val continueElsewhereAvailable: StateFlow<Boolean> = if (isTv) {
        combine(settings, TvLinkStore.pairedFlow(application, TvLinkStore.Role.PHONE)) { current, phones ->
            current.experimental.playOnTv && phones.isNotEmpty()
        }
    } else {
        settings.map { it.experimental.playOnTv }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val linkSend = LinkSendController(application, viewModelScope, discoverAlways = flowOf(false))

    /** Pauses here (which saves the position) and sends the video to the other device to carry
     * on from the same spot — or from the start if it had ended. */
    fun continueElsewhere() {
        val url = sourceUrl ?: return
        if (_uiState.value !is PlayerUiState.Ready) return
        player.pause()
        val position = if (player.playbackState == Player.STATE_ENDED) 0L else player.currentPosition.coerceAtLeast(0L)
        linkSend.request(url, position)
    }

    fun setScaling(scaling: VideoScaling) {
        _scaling.value = scaling
    }

    /** @param startPositionMs where to start; `null` on a fresh load means "where it was left
     * off (or where the phone stopped, for Play on TV), if resume is on, otherwise the
     * beginning". */
    private fun resolveAndPlay(url: String, startPositionMs: Long?) {
        resolveJob?.cancel()
        _uiState.value = PlayerUiState.Resolving
        resolveJob = viewModelScope.launch {
            val result = runCatching { StreamResolvers.resolve(url) }
            result.onSuccess { stream ->
                val playback = settings.first().playback
                if (_scaling.value == null) {
                    _scaling.value = playback.scaling
                }
                val handoff = handoffPositionMs.also { handoffPositionMs = null }
                val resumeAt = startPositionMs ?: (
                    handoff ?: videoKey
                        ?.takeIf { playback.resumePlayback }
                        ?.let { WatchHistoryRepository.resumePositionMs(getApplication(), it) }
                    )?.also { if (it > 0) _resumedFromMs.value = it }
                dataSourceFactory.setDefaultRequestProperties(stream.requestHeaders)
                player.setMediaItem(stream.toMediaItem(), resumeAt ?: 0L)
                player.prepare()
                player.playWhenReady = true
                _uiState.value = PlayerUiState.Ready(stream)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _uiState.value = PlayerUiState.Failed(error)
            }
        }
    }

    /** Generated links (Streamtape's included) expire. If the CDN starts refusing requests —
     * typically after the video has been paused for a long while — quietly fetch a fresh link
     * and pick up where playback stopped, instead of showing an error. */
    private fun errorListener() = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val httpFailure = error.cause is HttpDataSource.InvalidResponseCodeException ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
            if (httpFailure && !autoRetried) {
                autoRetried = true
                retry()
            } else {
                _uiState.value = PlayerUiState.Failed(error)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Playing successfully again means the next HTTP failure deserves its own retry.
            if (isPlaying) autoRetried = false
        }
    }

    /** Saves progress whenever playback pauses, every [PROGRESS_SAVE_INTERVAL_MS] while it
     * plays (so a crash or force-stop loses little), and forgets the video once it ends. */
    private fun historyListener() = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            progressSaveJob?.cancel()
            if (isPlaying) {
                progressSaveJob = viewModelScope.launch {
                    while (true) {
                        delay(PROGRESS_SAVE_INTERVAL_MS)
                        saveProgress()
                    }
                }
            } else {
                saveProgress()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                _resumedFromMs.value = null
                saveProgress()
            }
        }
    }

    private fun ResolvedStream.toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(streamUrl)
        .apply { mimeType?.let(::setMimeType) }
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .apply { posterUrl?.let { setArtworkUri(Uri.parse(it)) } }
                .build(),
        )
        .build()

    override fun onCleared() {
        saveProgress()
        player.release()
    }

    private companion object {
        const val PROGRESS_SAVE_INTERVAL_MS = 15_000L
    }
}
