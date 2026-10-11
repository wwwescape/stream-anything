package com.wwwescape.streamanything.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.wwwescape.streamanything.data.settings.ThemeContrast

/** Hand-authoring a full Material 3 role set (~30 colors) independently for light *and* dark,
 * ×16 curated themes, is ~1,000 individual hex values — not a reasonable spend for this pass.
 * Instead, one tonal-role structure (built once, matching this app's hand-authored default
 * palette in `Color.kt`) is shared across every curated theme; only the seed hue changes. This
 * is an HSV approximation of Material's real HCT tonal system, not a reproduction of it — hues
 * stay true but perceptual lightness/chroma won't exactly match Material Theme Builder output.
 * Curation here means *which 16 seed hues made the list*, not 16 independently tuned palettes. */
private fun hsv(hue: Float, saturation: Float, value: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.mod(360f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))))

fun generateColorScheme(seedHue: Float, dark: Boolean): ColorScheme {
    val tertiaryHue = (seedHue + 60f).mod(360f)
    return if (!dark) {
        lightColorScheme(
            primary = hsv(seedHue, 0.58f, 0.52f),
            onPrimary = hsv(seedHue, 0.05f, 1.00f),
            primaryContainer = hsv(seedHue, 0.28f, 0.97f),
            onPrimaryContainer = hsv(seedHue, 0.55f, 0.22f),
            inversePrimary = hsv(seedHue, 0.35f, 0.80f),
            secondary = hsv(seedHue, 0.20f, 0.46f),
            onSecondary = hsv(seedHue, 0.05f, 1.00f),
            secondaryContainer = hsv(seedHue, 0.15f, 0.93f),
            onSecondaryContainer = hsv(seedHue, 0.20f, 0.18f),
            tertiary = hsv(tertiaryHue, 0.32f, 0.48f),
            onTertiary = hsv(tertiaryHue, 0.05f, 1.00f),
            tertiaryContainer = hsv(tertiaryHue, 0.20f, 0.93f),
            onTertiaryContainer = hsv(tertiaryHue, 0.32f, 0.18f),
            background = hsv(seedHue, 0.03f, 0.99f),
            onBackground = hsv(seedHue, 0.10f, 0.12f),
            surface = hsv(seedHue, 0.03f, 0.99f),
            onSurface = hsv(seedHue, 0.10f, 0.12f),
            surfaceVariant = hsv(seedHue, 0.10f, 0.90f),
            onSurfaceVariant = hsv(seedHue, 0.08f, 0.32f),
            surfaceTint = hsv(seedHue, 0.58f, 0.52f),
            inverseSurface = hsv(seedHue, 0.10f, 0.20f),
            inverseOnSurface = hsv(seedHue, 0.05f, 0.96f),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            outline = hsv(seedHue, 0.10f, 0.52f),
            outlineVariant = hsv(seedHue, 0.10f, 0.82f),
            surfaceBright = hsv(seedHue, 0.03f, 0.99f),
            surfaceContainer = hsv(seedHue, 0.06f, 0.95f),
            surfaceContainerHigh = hsv(seedHue, 0.07f, 0.93f),
            surfaceContainerHighest = hsv(seedHue, 0.08f, 0.90f),
            surfaceContainerLow = hsv(seedHue, 0.05f, 0.97f),
            surfaceContainerLowest = hsv(seedHue, 0.02f, 1.00f),
            surfaceDim = hsv(seedHue, 0.10f, 0.87f),
            primaryFixed = hsv(seedHue, 0.28f, 0.97f),
            primaryFixedDim = hsv(seedHue, 0.35f, 0.80f),
            onPrimaryFixed = hsv(seedHue, 0.55f, 0.22f),
            onPrimaryFixedVariant = hsv(seedHue, 0.45f, 0.35f),
            secondaryFixed = hsv(seedHue, 0.15f, 0.93f),
            secondaryFixedDim = hsv(seedHue, 0.20f, 0.78f),
            onSecondaryFixed = hsv(seedHue, 0.20f, 0.18f),
            onSecondaryFixedVariant = hsv(seedHue, 0.20f, 0.32f),
            tertiaryFixed = hsv(tertiaryHue, 0.20f, 0.93f),
            tertiaryFixedDim = hsv(tertiaryHue, 0.28f, 0.78f),
            onTertiaryFixed = hsv(tertiaryHue, 0.32f, 0.18f),
            onTertiaryFixedVariant = hsv(tertiaryHue, 0.28f, 0.32f),
        )
    } else {
        darkColorScheme(
            primary = hsv(seedHue, 0.35f, 0.90f),
            onPrimary = hsv(seedHue, 0.45f, 0.24f),
            primaryContainer = hsv(seedHue, 0.45f, 0.35f),
            onPrimaryContainer = hsv(seedHue, 0.28f, 0.92f),
            inversePrimary = hsv(seedHue, 0.58f, 0.52f),
            secondary = hsv(seedHue, 0.18f, 0.82f),
            onSecondary = hsv(seedHue, 0.20f, 0.24f),
            secondaryContainer = hsv(seedHue, 0.20f, 0.32f),
            onSecondaryContainer = hsv(seedHue, 0.15f, 0.93f),
            tertiary = hsv(tertiaryHue, 0.26f, 0.80f),
            onTertiary = hsv(tertiaryHue, 0.32f, 0.20f),
            tertiaryContainer = hsv(tertiaryHue, 0.30f, 0.30f),
            onTertiaryContainer = hsv(tertiaryHue, 0.20f, 0.93f),
            background = hsv(seedHue, 0.12f, 0.10f),
            onBackground = hsv(seedHue, 0.08f, 0.90f),
            surface = hsv(seedHue, 0.12f, 0.10f),
            onSurface = hsv(seedHue, 0.08f, 0.90f),
            surfaceVariant = hsv(seedHue, 0.10f, 0.32f),
            onSurfaceVariant = hsv(seedHue, 0.10f, 0.82f),
            surfaceTint = hsv(seedHue, 0.35f, 0.90f),
            inverseSurface = hsv(seedHue, 0.08f, 0.90f),
            inverseOnSurface = hsv(seedHue, 0.10f, 0.20f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            outline = hsv(seedHue, 0.10f, 0.62f),
            outlineVariant = hsv(seedHue, 0.10f, 0.32f),
            surfaceBright = hsv(seedHue, 0.10f, 0.26f),
            surfaceContainer = hsv(seedHue, 0.11f, 0.15f),
            surfaceContainerHigh = hsv(seedHue, 0.11f, 0.19f),
            surfaceContainerHighest = hsv(seedHue, 0.11f, 0.24f),
            surfaceContainerLow = hsv(seedHue, 0.11f, 0.13f),
            surfaceContainerLowest = hsv(seedHue, 0.12f, 0.07f),
            surfaceDim = hsv(seedHue, 0.12f, 0.10f),
            primaryFixed = hsv(seedHue, 0.28f, 0.97f),
            primaryFixedDim = hsv(seedHue, 0.35f, 0.80f),
            onPrimaryFixed = hsv(seedHue, 0.55f, 0.22f),
            onPrimaryFixedVariant = hsv(seedHue, 0.45f, 0.35f),
            secondaryFixed = hsv(seedHue, 0.15f, 0.93f),
            secondaryFixedDim = hsv(seedHue, 0.20f, 0.78f),
            onSecondaryFixed = hsv(seedHue, 0.20f, 0.18f),
            onSecondaryFixedVariant = hsv(seedHue, 0.20f, 0.32f),
            tertiaryFixed = hsv(tertiaryHue, 0.20f, 0.93f),
            tertiaryFixedDim = hsv(tertiaryHue, 0.28f, 0.78f),
            onTertiaryFixed = hsv(tertiaryHue, 0.32f, 0.18f),
            onTertiaryFixedVariant = hsv(tertiaryHue, 0.28f, 0.32f),
        )
    }
}

/** Standard leaves the scheme untouched; medium/high push text/outline colors further from
 * their surface for stronger contrast — a hand-rolled approximation rather than a platform
 * contrast API, since Compose Material3 has no public "regenerate this ColorScheme at a higher
 * contrast level" entry point outside the dynamic-color (wallpaper-derived) path. */
fun ColorScheme.withContrast(level: ThemeContrast): ColorScheme {
    if (level == ThemeContrast.STANDARD) return this
    val boost = if (level == ThemeContrast.HIGH) 0.5f else 0.25f
    fun push(base: Color, onto: Color): Color = lerp(base, onto, boost)
    val towardsSurface = if (surface.luminance() > 0.5f) Color.Black else Color.White
    val towardsBackground = if (background.luminance() > 0.5f) Color.Black else Color.White
    val towardsSurfaceVariant = if (surfaceVariant.luminance() > 0.5f) Color.Black else Color.White
    return copy(
        onSurface = push(onSurface, towardsSurface),
        onBackground = push(onBackground, towardsBackground),
        onSurfaceVariant = push(onSurfaceVariant, towardsSurfaceVariant),
        outline = push(outline, towardsSurface),
    )
}

private fun Color.luminance(): Float = (0.299f * red + 0.587f * green + 0.114f * blue)

/** Pure dark forces just the base background/surface to true black (OLED); absolute dark goes
 * further and flattens every elevated surface tone to near-black too, for a completely flat
 * black reading experience. Both are no-ops outside dark theme. */
fun ColorScheme.withDarkIntensity(pureDark: Boolean, absoluteDark: Boolean): ColorScheme = when {
    absoluteDark -> copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color(0xFF0A0A0A),
        surfaceContainerHighest = Color(0xFF121212),
        surfaceBright = Color(0xFF121212),
    )
    pureDark -> copy(background = Color.Black, surface = Color.Black)
    else -> this
}
