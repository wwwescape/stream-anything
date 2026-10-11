package com.wwwescape.streamanything.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.wwwescape.streamanything.data.settings.ColorTheme
import com.wwwescape.streamanything.data.settings.ThemeContrast

/**
 * Stream Anything theme: uses Material You dynamic color on Android 12+ and falls back to
 * either a curated generated palette ([colorTheme]) or the Material 3 baseline palette (see
 * Color.kt) on older devices or when dynamic color is off. [themeContrast]/[pureDark]/[absoluteDark]
 * are layered on top of whichever base scheme was picked.
 */
@Composable
fun StreamAnythingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    colorTheme: ColorTheme = ColorTheme.DEFAULT,
    themeContrast: ThemeContrast = ThemeContrast.STANDARD,
    pureDark: Boolean = false,
    absoluteDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = resolveStreamAnythingColorScheme(
        context = LocalContext.current,
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        colorTheme = colorTheme,
        themeContrast = themeContrast,
        pureDark = pureDark,
        absoluteDark = absoluteDark,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StreamAnythingTypography,
        shapes = StreamAnythingShapes,
        content = content,
    )
}

/**
 * The same colorScheme resolution [StreamAnythingTheme] uses, extracted as a plain function so
 * non-Composable callers (e.g. a future widget or other view-tree surface with its own theming
 * entry point) can mirror the app's colors exactly instead of drifting to a separately resolved
 * scheme.
 */
fun resolveStreamAnythingColorScheme(
    context: Context,
    darkTheme: Boolean,
    dynamicColor: Boolean,
    colorTheme: ColorTheme = ColorTheme.DEFAULT,
    themeContrast: ThemeContrast = ThemeContrast.STANDARD,
    pureDark: Boolean = false,
    absoluteDark: Boolean = false,
): ColorScheme {
    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        colorTheme.seedHue != null -> generateColorScheme(colorTheme.seedHue, darkTheme)
        darkTheme -> darkColorScheme(
            primary = DarkPrimary,
            onPrimary = DarkOnPrimary,
            primaryContainer = DarkPrimaryContainer,
            onPrimaryContainer = DarkOnPrimaryContainer,
            inversePrimary = DarkInversePrimary,
            secondary = DarkSecondary,
            onSecondary = DarkOnSecondary,
            secondaryContainer = DarkSecondaryContainer,
            onSecondaryContainer = DarkOnSecondaryContainer,
            tertiary = DarkTertiary,
            onTertiary = DarkOnTertiary,
            tertiaryContainer = DarkTertiaryContainer,
            onTertiaryContainer = DarkOnTertiaryContainer,
            background = DarkBackground,
            onBackground = DarkOnBackground,
            surface = DarkSurface,
            onSurface = DarkOnSurface,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            surfaceTint = DarkSurfaceTint,
            inverseSurface = DarkInverseSurface,
            inverseOnSurface = DarkInverseOnSurface,
            error = DarkError,
            onError = DarkOnError,
            errorContainer = DarkErrorContainer,
            onErrorContainer = DarkOnErrorContainer,
            outline = DarkOutline,
            outlineVariant = DarkOutlineVariant,
            surfaceBright = DarkSurfaceBright,
            surfaceContainer = DarkSurfaceContainer,
            surfaceContainerHigh = DarkSurfaceContainerHigh,
            surfaceContainerHighest = DarkSurfaceContainerHighest,
            surfaceContainerLow = DarkSurfaceContainerLow,
            surfaceContainerLowest = DarkSurfaceContainerLowest,
            surfaceDim = DarkSurfaceDim,
            primaryFixed = PrimaryFixed,
            primaryFixedDim = PrimaryFixedDim,
            onPrimaryFixed = OnPrimaryFixed,
            onPrimaryFixedVariant = OnPrimaryFixedVariant,
            secondaryFixed = SecondaryFixed,
            secondaryFixedDim = SecondaryFixedDim,
            onSecondaryFixed = OnSecondaryFixed,
            onSecondaryFixedVariant = OnSecondaryFixedVariant,
            tertiaryFixed = TertiaryFixed,
            tertiaryFixedDim = TertiaryFixedDim,
            onTertiaryFixed = OnTertiaryFixed,
            onTertiaryFixedVariant = OnTertiaryFixedVariant,
        )
        else -> lightColorScheme(
            primary = Primary,
            onPrimary = OnPrimary,
            primaryContainer = PrimaryContainer,
            onPrimaryContainer = OnPrimaryContainer,
            inversePrimary = InversePrimary,
            secondary = Secondary,
            onSecondary = OnSecondary,
            secondaryContainer = SecondaryContainer,
            onSecondaryContainer = OnSecondaryContainer,
            tertiary = Tertiary,
            onTertiary = OnTertiary,
            tertiaryContainer = TertiaryContainer,
            onTertiaryContainer = OnTertiaryContainer,
            background = Background,
            onBackground = OnBackground,
            surface = Surface,
            onSurface = OnSurface,
            surfaceVariant = SurfaceVariant,
            onSurfaceVariant = OnSurfaceVariant,
            surfaceTint = SurfaceTint,
            inverseSurface = InverseSurface,
            inverseOnSurface = InverseOnSurface,
            error = Error,
            onError = OnError,
            errorContainer = ErrorContainer,
            onErrorContainer = OnErrorContainer,
            outline = Outline,
            outlineVariant = OutlineVariant,
            surfaceBright = SurfaceBright,
            surfaceContainer = SurfaceContainer,
            surfaceContainerHigh = SurfaceContainerHigh,
            surfaceContainerHighest = SurfaceContainerHighest,
            surfaceContainerLow = SurfaceContainerLow,
            surfaceContainerLowest = SurfaceContainerLowest,
            surfaceDim = SurfaceDim,
            primaryFixed = PrimaryFixed,
            primaryFixedDim = PrimaryFixedDim,
            onPrimaryFixed = OnPrimaryFixed,
            onPrimaryFixedVariant = OnPrimaryFixedVariant,
            secondaryFixed = SecondaryFixed,
            secondaryFixedDim = SecondaryFixedDim,
            onSecondaryFixed = OnSecondaryFixed,
            onSecondaryFixedVariant = OnSecondaryFixedVariant,
            tertiaryFixed = TertiaryFixed,
            tertiaryFixedDim = TertiaryFixedDim,
            onTertiaryFixed = OnTertiaryFixed,
            onTertiaryFixedVariant = OnTertiaryFixedVariant,
        )
    }
    val contrasted = base.withContrast(themeContrast)
    return if (darkTheme) contrasted.withDarkIntensity(pureDark, absoluteDark) else contrasted
}
