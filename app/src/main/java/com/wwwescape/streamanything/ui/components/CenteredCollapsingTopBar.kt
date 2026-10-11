package com.wwwescape.streamanything.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp

private val ExpandedHeight = 96.dp
private val CollapsedHeight = 64.dp
private val ExpandedFontSize = 28.sp
private val CollapsedFontSize = 20.sp

/**
 * A top app bar whose title is always horizontally *and* vertically centered, with its font
 * size smoothly interpolating between an expanded and collapsed size as [scrollBehavior] tracks
 * scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenteredCollapsingTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surface,
    titleContentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val density = LocalDensity.current
    SideEffect {
        val limitPx = with(density) { (CollapsedHeight - ExpandedHeight).toPx() }
        scrollBehavior.state.heightOffsetLimit = limitPx
    }

    val fraction = scrollBehavior.state.collapsedFraction
    val height = lerp(ExpandedHeight, CollapsedHeight, fraction)
    val fontSize = lerp(ExpandedFontSize, CollapsedFontSize, fraction)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .height(height),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CollapsedHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.padding(start = 4.dp)) { navigationIcon() }
            Box(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        Text(
            text = title,
            color = titleContentColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 72.dp),
        )
    }
}
