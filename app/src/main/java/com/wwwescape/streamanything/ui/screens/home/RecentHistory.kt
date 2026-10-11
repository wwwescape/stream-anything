package com.wwwescape.streamanything.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wwwescape.streamanything.R
import com.wwwescape.streamanything.data.history.WatchEntry
import com.wwwescape.streamanything.ui.player.formatPlaybackTime

/** "Recent history" under the link field: the newest links played, each tappable to play again
 * (resuming, or from the start once finished), with a delete button to forget just that one. */
@Composable
fun RecentHistory(
    entries: List<WatchEntry>,
    onPlay: (WatchEntry, String) -> Unit,
    onRemove: (WatchEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.home_recent_history),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        entries.forEach { entry ->
            entry.link?.let { link ->
                // The delete button sits beside the card rather than inside it, so on TV the
                // card stays one focus stop and Right moves to the button.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RecentHistoryItem(
                        entry = entry,
                        link = link,
                        onClick = { onPlay(entry, link) },
                        modifier = Modifier.weight(1f),
                    )
                    RemoveButton(onClick = { onRemove(entry) })
                }
            }
        }
    }
}

/** One video: the link, watched / total time, and what tapping it does. The whole card is one
 * focus stop on TV (the chip is a label, not a second button), outlined in the accent color
 * when focused so it reads from across the room. */
@Composable
private fun RecentHistoryItem(entry: WatchEntry, link: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = link,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatPlaybackTime(entry.positionMs)} / ${formatPlaybackTime(entry.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            when {
                entry.isFinished -> HistoryChip(stringResource(R.string.chip_rewatch), Icons.Rounded.Replay, finished = true)
                entry.isResumable -> HistoryChip(stringResource(R.string.chip_resume), Icons.Rounded.PlayArrow, finished = false)
                // Barely started: tapping just plays from the beginning, so there's nothing to label.
            }
        }
    }
}

/** Filled in the accent color while focused, so it's obvious from the couch which of the
 * row's two stops (card or delete) the remote is on. */
@Composable
private fun RemoveButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.action_remove_from_history))
    }
}

@Composable
private fun HistoryChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, finished: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (finished) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (finished) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
