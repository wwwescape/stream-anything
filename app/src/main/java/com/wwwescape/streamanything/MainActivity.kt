package com.wwwescape.streamanything

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.wwwescape.streamanything.data.settings.AppSettings
import com.wwwescape.streamanything.data.settings.SettingsRepository
import com.wwwescape.streamanything.data.settings.THEME_MODE_PREF_KEY
import com.wwwescape.streamanything.data.settings.THEME_PREFS_NAME
import com.wwwescape.streamanything.data.settings.ThemeMode
import com.wwwescape.streamanything.data.tvlink.LinkReceiver
import com.wwwescape.streamanything.ui.StreamAnythingApp
import com.wwwescape.streamanything.ui.components.TvPairingDialog
import com.wwwescape.streamanything.ui.screens.home.IncomingLink
import com.wwwescape.streamanything.ui.theme.StreamAnythingTheme

class MainActivity : ComponentActivity() {

    /** A link handed to the app from outside — the share sheet (`ACTION_SEND`), "Open with"
     * on a supported link (`ACTION_VIEW`), or a phone using Play on TV. Held here, not in the
     * Intent, so it's consumed once and not replayed on rotation. */
    private val incomingLink = mutableStateOf<IncomingLink?>(null)

    /** Runs before any DataStore/Compose code can — reads the synchronous SharedPreferences
     * mirror of [ThemeMode] (see [SettingsRepository.setThemeMode]) and forces the base context's
     * night-mode configuration to match, so the window's initial (pre-Compose) theme resolution
     * honors the user's choice instead of always following the raw system day/night setting. */
    override fun attachBaseContext(newBase: Context) {
        val stored = newBase.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(THEME_MODE_PREF_KEY, null)
        val mode = stored?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        val forcedNightBit = when (mode) {
            ThemeMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
            ThemeMode.DARK -> Configuration.UI_MODE_NIGHT_YES
            ThemeMode.SYSTEM -> null
        }
        val context = if (forcedNightBit == null) {
            newBase
        } else {
            val overridden = Configuration(newBase.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or forcedNightBit
            }
            newBase.createConfigurationContext(overridden)
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            incomingLink.value = intent.extractLink()
        }

        setContent {
            val settings by remember { SettingsRepository.settingsFlow(applicationContext) }
                .collectAsState(initial = AppSettings())
            val systemInDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemInDarkTheme
            }

            StreamAnythingTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.useDynamicColor,
                colorTheme = settings.colorTheme,
                themeContrast = settings.themeContrast,
                pureDark = settings.pureDark,
                absoluteDark = settings.absoluteDark,
            ) {
                StreamAnythingApp(
                    incomingLink = incomingLink.value,
                    onIncomingLinkConsumed = { incomingLink.value = null },
                )
                TvPairingDialog()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LinkReceiver.start(this)
    }

    override fun onStop() {
        super.onStop()
        LinkReceiver.stop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.extractLink()?.let { incomingLink.value = it }
    }

    private fun Intent.extractLink(): IncomingLink? = when (action) {
        Intent.ACTION_VIEW -> dataString?.let(::IncomingLink)
        Intent.ACTION_SEND -> getStringExtra(Intent.EXTRA_TEXT)?.let(::IncomingLink)
        // The video itself is handed over in-process; the Intent only brings Home to the front.
        LinkReceiver.ACTION_PLAY_SENT_LINK -> LinkReceiver.takeIncoming()?.let { IncomingLink(it.link, it.positionMs) }
        else -> null
    }?.takeIf { it.text.isNotBlank() }
}
