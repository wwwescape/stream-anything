package com.wwwescape.streamanything.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wwwescape.streamanything.R
import com.wwwescape.streamanything.data.settings.ColorTheme
import com.wwwescape.streamanything.data.settings.SeekIncrement
import com.wwwescape.streamanything.data.settings.ThemeContrast
import com.wwwescape.streamanything.data.settings.ThemeMode
import com.wwwescape.streamanything.data.settings.VideoScaling
import java.util.Locale

/** `null` means "follow the system language" ([androidx.appcompat.app.AppCompatDelegate]'s empty
 * locale list) — see the Settings screen's Language row. English is listed explicitly too since
 * the system default might not be English even though that's this app's base `values/strings.xml`. */
val SUPPORTED_APP_LANGUAGES = listOf(null, "en", "es", "fr", "hi", "pt")

@Composable
fun appLanguageLabel(languageTag: String?): String =
    if (languageTag == null) {
        stringResource(R.string.language_system_default)
    } else {
        val locale = Locale.forLanguageTag(languageTag)
        locale.getDisplayName(locale).replaceFirstChar { it.uppercase(locale) }
    }

@Composable
fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.LIGHT -> R.string.theme_mode_light
        ThemeMode.DARK -> R.string.theme_mode_dark
        ThemeMode.SYSTEM -> R.string.theme_mode_system
    },
)

@Composable
fun ColorTheme.label(): String = stringResource(
    when (this) {
        ColorTheme.DEFAULT -> R.string.color_theme_default
        ColorTheme.OCEAN -> R.string.color_theme_ocean
        ColorTheme.FOREST -> R.string.color_theme_forest
        ColorTheme.SUNSET -> R.string.color_theme_sunset
        ColorTheme.GRAPE -> R.string.color_theme_grape
        ColorTheme.ROSE -> R.string.color_theme_rose
        ColorTheme.SLATE -> R.string.color_theme_slate
        ColorTheme.GOLD -> R.string.color_theme_gold
        ColorTheme.TEAL -> R.string.color_theme_teal
        ColorTheme.PLUM -> R.string.color_theme_plum
        ColorTheme.MOSS -> R.string.color_theme_moss
        ColorTheme.CORAL -> R.string.color_theme_coral
        ColorTheme.INDIGO -> R.string.color_theme_indigo
        ColorTheme.MUSTARD -> R.string.color_theme_mustard
        ColorTheme.CRIMSON -> R.string.color_theme_crimson
        ColorTheme.MINT -> R.string.color_theme_mint
        ColorTheme.PERIWINKLE -> R.string.color_theme_periwinkle
    },
)

@Composable
fun ThemeContrast.label(): String = stringResource(
    when (this) {
        ThemeContrast.STANDARD -> R.string.contrast_standard
        ThemeContrast.MEDIUM -> R.string.contrast_medium
        ThemeContrast.HIGH -> R.string.contrast_high
    },
)

@Composable
fun SeekIncrement.label(): String = stringResource(
    when (this) {
        SeekIncrement.FIVE_SECONDS -> R.string.seek_5s
        SeekIncrement.TEN_SECONDS -> R.string.seek_10s
        SeekIncrement.THIRTY_SECONDS -> R.string.seek_30s
    },
)

@Composable
fun VideoScaling.label(): String = stringResource(
    when (this) {
        VideoScaling.FIT -> R.string.scaling_fit
        VideoScaling.ZOOM -> R.string.scaling_zoom
        VideoScaling.STRETCH -> R.string.scaling_stretch
    },
)
