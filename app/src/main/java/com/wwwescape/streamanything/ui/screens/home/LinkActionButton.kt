package com.wwwescape.streamanything.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How prominent a [LinkActionButton] is: Play is the main action, sending to the other device
 * the secondary one, and Clear the quiet one. */
enum class LinkActionStyle { FILLED, TONAL, OUTLINED }

/**
 * One of the round buttons beside the link field (Clear → Play → TV/phone), the same on phones,
 * tablets and TV:
 * - **Enabled:** its [style]'s colors.
 * - **Disabled:** Material's disabled colors (faded icon, faint container), and skipped by the
 *   D-pad, so a remote never lands on a button that does nothing.
 * - **Focused** (remote or keyboard): grows slightly and gets a thick ring, so it's obvious from
 *   across the room which button OK will press. Touch never shows the ring.
 */
@Composable
fun LinkActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    style: LinkActionStyle,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused && enabled) 1.1f else 1f, label = "linkActionFocus")
    val colors = MaterialTheme.colorScheme
    val disabledContent = colors.onSurface.copy(alpha = 0.38f)
    val disabledContainer = colors.onSurface.copy(alpha = 0.12f)

    val container = when {
        style == LinkActionStyle.OUTLINED -> Color.Transparent
        !enabled -> disabledContainer
        style == LinkActionStyle.FILLED -> colors.primary
        else -> colors.secondaryContainer
    }
    val content = when {
        !enabled -> disabledContent
        style == LinkActionStyle.FILLED -> colors.onPrimary
        style == LinkActionStyle.TONAL -> colors.onSecondaryContainer
        else -> colors.onSurfaceVariant
    }
    val border = when {
        // A primary ring would vanish against the filled (primary) button, so that one gets a
        // contrasting ring instead.
        focused && enabled -> BorderStroke(3.dp, if (style == LinkActionStyle.FILLED) colors.onSurface else colors.primary)
        style == LinkActionStyle.OUTLINED -> BorderStroke(1.dp, if (enabled) colors.outline else disabledContainer)
        else -> null
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = border,
        interactionSource = interactionSource,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics { role = Role.Button },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
        }
    }
}
