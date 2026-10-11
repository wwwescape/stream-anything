package com.wwwescape.streamanything.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// M3 Expressive rounded scale (sm/DEFAULT/md/lg); extraLarge uses 28dp for the app's most
// visible surfaces (cards, the URL field, player panels).
val StreamAnythingShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Pill/"full" shape for buttons, chips, and nav-item indicators. */
val PillShape = RoundedCornerShape(percent = 50)
