package com.wwwescape.streamanything.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Mirrors [ThemeMode] outside DataStore, which is Flow-only/async. [MainActivity] reads this
 * synchronously in `attachBaseContext`, before any Compose or DataStore code can run, so the
 * window's initial (pre-Compose) theme resolution honors the user's choice instead of always
 * following the raw system day/night setting. */
const val THEME_PREFS_NAME = "theme_prefs_sync"
const val THEME_MODE_PREF_KEY = "theme_mode"

object SettingsRepository {

    private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
    private val COLOR_THEME_KEY = stringPreferencesKey("color_theme")
    private val THEME_CONTRAST_KEY = stringPreferencesKey("theme_contrast")
    private val PURE_DARK_KEY = booleanPreferencesKey("pure_dark")
    private val ABSOLUTE_DARK_KEY = booleanPreferencesKey("absolute_dark")
    private val SEEK_INCREMENT_KEY = stringPreferencesKey("seek_increment")
    private val VIDEO_SCALING_KEY = stringPreferencesKey("video_scaling")
    private val RESUME_PLAYBACK_KEY = booleanPreferencesKey("resume_playback")
    private val SHOW_HISTORY_KEY = booleanPreferencesKey("experimental_show_history")
    private val PLAY_ON_TV_KEY = booleanPreferencesKey("experimental_play_on_tv")

    fun settingsFlow(context: Context): Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[THEME_MODE_KEY].toEnumOrDefault(ThemeMode.SYSTEM),
            useDynamicColor = prefs[DYNAMIC_COLOR_KEY] ?: true,
            colorTheme = prefs[COLOR_THEME_KEY].toEnumOrDefault(ColorTheme.DEFAULT),
            themeContrast = prefs[THEME_CONTRAST_KEY].toEnumOrDefault(ThemeContrast.STANDARD),
            pureDark = prefs[PURE_DARK_KEY] ?: false,
            absoluteDark = prefs[ABSOLUTE_DARK_KEY] ?: false,
            playback = PlaybackSettings(
                seekIncrement = prefs[SEEK_INCREMENT_KEY].toEnumOrDefault(SeekIncrement.TEN_SECONDS),
                scaling = prefs[VIDEO_SCALING_KEY].toEnumOrDefault(VideoScaling.FIT),
                resumePlayback = prefs[RESUME_PLAYBACK_KEY] ?: true,
            ),
            experimental = ExperimentalSettings(
                showHistory = prefs[SHOW_HISTORY_KEY] ?: false,
                playOnTv = prefs[PLAY_ON_TV_KEY] ?: false,
            ),
        )
    }

    suspend fun setThemeMode(context: Context, mode: ThemeMode) {
        context.settingsDataStore.edit { it[THEME_MODE_KEY] = mode.name }
        context.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(THEME_MODE_PREF_KEY, mode.name).apply()
    }

    suspend fun setDynamicColor(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[DYNAMIC_COLOR_KEY] = enabled }
    }

    suspend fun setColorTheme(context: Context, theme: ColorTheme) {
        context.settingsDataStore.edit { it[COLOR_THEME_KEY] = theme.name }
    }

    suspend fun setThemeContrast(context: Context, contrast: ThemeContrast) {
        context.settingsDataStore.edit { it[THEME_CONTRAST_KEY] = contrast.name }
    }

    suspend fun setPureDark(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[PURE_DARK_KEY] = enabled }
    }

    suspend fun setAbsoluteDark(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[ABSOLUTE_DARK_KEY] = enabled }
    }

    suspend fun setSeekIncrement(context: Context, increment: SeekIncrement) {
        context.settingsDataStore.edit { it[SEEK_INCREMENT_KEY] = increment.name }
    }

    suspend fun setVideoScaling(context: Context, scaling: VideoScaling) {
        context.settingsDataStore.edit { it[VIDEO_SCALING_KEY] = scaling.name }
    }

    suspend fun setResumePlayback(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[RESUME_PLAYBACK_KEY] = enabled }
    }

    suspend fun setShowHistory(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[SHOW_HISTORY_KEY] = enabled }
    }

    suspend fun setPlayOnTv(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[PLAY_ON_TV_KEY] = enabled }
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
        this?.let { name -> runCatching { enumValueOf<T>(name) }.getOrNull() } ?: default
}
