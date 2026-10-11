package com.wwwescape.streamanything.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Expressive type scale using the system default font family for now. A bundled variable font
 * in res/font, wired up here via FontFamily, can be dropped in later without touching call
 * sites — swap `fontFamily` below once real font assets are added.
 */
private val baseline = Typography()

val StreamAnythingTypography = baseline.copy(
    displayLarge = baseline.displayLarge.copy(
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = baseline.headlineLarge.copy(
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    titleLarge = baseline.titleLarge.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = baseline.titleMedium.copy(fontWeight = FontWeight.Medium),
    bodyLarge = baseline.bodyLarge.copy(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
)
