package com.wwwescape.streamanything.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wwwescape.streamanything.R
import com.wwwescape.streamanything.data.history.WatchEntry
import com.wwwescape.streamanything.ui.player.formatPlaybackTime
import com.wwwescape.streamanything.util.isTelevision

/** Tapping a Recent history entry with a paired device around: watch it here or there — on
 * a phone, here or the TV; on a TV, here or the phone. [otherName] is the other device's name
 * when there's exactly one, otherwise `null` (a picker follows). */
@Composable
fun WatchWhereDialog(
    entry: WatchEntry,
    otherName: String?,
    onHere: () -> Unit,
    onOther: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isTv = LocalContext.current.isTelevision()
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.watch_where_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (entry.isResumable) {
                        stringResource(R.string.watch_where_resume, formatPlaybackTime(entry.positionMs))
                    } else {
                        stringResource(R.string.watch_where_from_start)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                WhereOption(
                    icon = when {
                        isTv -> Icons.Rounded.Tv
                        isTablet -> Icons.Rounded.TabletAndroid
                        else -> Icons.Rounded.PhoneAndroid
                    },
                    label = stringResource(
                        when {
                            isTv -> R.string.watch_where_this_tv
                            isTablet -> R.string.watch_where_this_tablet
                            else -> R.string.watch_where_this_phone
                        },
                    ),
                    onClick = onHere,
                )
                WhereOption(
                    icon = if (isTv) Icons.Rounded.PhoneAndroid else Icons.Rounded.Tv,
                    label = otherName ?: stringResource(if (isTv) R.string.watch_where_phone else R.string.watch_where_tv),
                    onClick = onOther,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun WhereOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
