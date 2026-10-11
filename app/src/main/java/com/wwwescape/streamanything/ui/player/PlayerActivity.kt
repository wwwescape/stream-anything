package com.wwwescape.streamanything.ui.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaSession
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wwwescape.streamanything.data.tvlink.LinkReceiver
import com.wwwescape.streamanything.ui.components.TvPairingDialog
import com.wwwescape.streamanything.ui.theme.StreamAnythingTheme

/**
 * The full-screen player. A separate Activity (rather than a NavHost destination) so it can own
 * its window outright — immersive system bars, keep-screen-on, and orientation — without any of
 * that leaking back into the regular app UI.
 */
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    /** Lets the system, TV remotes' media keys, Bluetooth headsets, and Google Assistant
     * ("pause", "skip forward 30 seconds") control playback while the player is open. */
    private var mediaSession: MediaSession? = null

    private val windowListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            matchOrientationTo(videoSize)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        val url = intent.getStringExtra(EXTRA_URL)
        if (url == null) {
            finish()
            return
        }
        viewModel.load(url, if (intent.hasExtra(EXTRA_START_POSITION)) intent.getLongExtra(EXTRA_START_POSITION, 0L) else null)
        viewModel.player.addListener(windowListener)
        matchOrientationTo(viewModel.player.videoSize)
        mediaSession = MediaSession.Builder(this, viewModel.player).build()

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            // Always dark: video looks best against black, whatever the app theme is. The user's
            // color choices still tint the controls.
            StreamAnythingTheme(
                darkTheme = true,
                dynamicColor = settings.useDynamicColor,
                colorTheme = settings.colorTheme,
                pureDark = true,
                absoluteDark = true,
            ) {
                PlayerScreen(viewModel = viewModel, onExit = ::finish)
                TvPairingDialog()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs and the speed menu briefly take focus and can bring the bars back.
        if (hasFocus) hideSystemBars()
    }

    override fun onStart() {
        super.onStart()
        // A phone can send the next video while this one plays.
        LinkReceiver.start(this)
    }

    override fun onStop() {
        super.onStop()
        LinkReceiver.stop()
        // Nothing plays behind the user's back once the player is no longer visible.
        viewModel.player.pause()
    }

    override fun onDestroy() {
        viewModel.player.removeListener(windowListener)
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** Landscape for landscape (and not-yet-known) videos, portrait for portrait ones — either
     * way still following the sensor, so the user can flip the device. TVs ignore this. */
    private fun matchOrientationTo(videoSize: VideoSize) {
        if (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION) return
        val portrait = videoSize.width > 0 && videoSize.height > videoSize.width
        requestedOrientation = if (portrait) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    companion object {
        private const val EXTRA_URL = "com.wwwescape.streamanything.extra.URL"
        private const val EXTRA_START_POSITION = "com.wwwescape.streamanything.extra.START_POSITION"

        /** @param startPositionMs where a phone using Play on TV said to start (0 = the
         * beginning); `null` applies the usual resume rules. */
        fun intent(context: Context, url: String, startPositionMs: Long? = null): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .apply { startPositionMs?.let { putExtra(EXTRA_START_POSITION, it) } }
    }
}
