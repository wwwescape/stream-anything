package com.wwwescape.streamanything.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Translucent white for inactive surfaces over video — readable on any frame. */
internal val ScrimWhite = Color.White.copy(alpha = 0.14f)

/**
 * A round, borderless player button. Focus (D-pad on TV, keyboard on tablets) inverts it to a
 * solid white disc with a dark icon and scales it up slightly — the same unmistakable focus
 * treatment Google TV uses, since the stock state-layer highlight is too faint across a room.
 */
@Composable
fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 26.dp,
    containerColor: Color = Color.Transparent,
    content: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "focusScale")

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (focused) Color.White else containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = if (focused) Color.Black else Color.White),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content()
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (focused) Color.Black else Color.White,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** A text pill (e.g. the "1.5×" speed button) with the same focus treatment as
 * [PlayerIconButton]. */
@Composable
fun PlayerTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "focusScale")

    Box(
        modifier = modifier
            .height(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (focused) Color.White else ScrimWhite)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = if (focused) Color.Black else Color.White),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (focused) Color.Black else Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 14.sp,
        )
    }
}
