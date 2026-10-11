package com.wwwescape.streamanything.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wwwescape.streamanything.R
import com.wwwescape.streamanything.data.tvlink.LinkReceiver
import com.wwwescape.streamanything.data.tvlink.PairedDevice
import com.wwwescape.streamanything.ui.tvsend.SendLinkDialog
import com.wwwescape.streamanything.util.isTelevision

/** Settings → Advanced → Experimental: features still being shaped, off by default. */
@Composable
fun ExperimentalScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pairedDevices by viewModel.pairedDevices.collectAsStateWithLifecycle()
    val receiverStatus by LinkReceiver.status.collectAsStateWithLifecycle()
    val pairDialogOpen by viewModel.pairDialogOpen.collectAsStateWithLifecycle()
    val sendState by viewModel.linkSend.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val isTv = context.isTelevision()
    var forgetting by remember { mutableStateOf<PairedDevice?>(null) }

    LaunchedEffect(Unit) {
        viewModel.linkSend.pairedWith.collect { name ->
            Toast.makeText(context, resources.getString(R.string.tv_paired_with, name), Toast.LENGTH_SHORT).show()
        }
    }
    // Recent history is built from the resume history, so it needs Resume Playback on.
    val historyAvailable = settings.playback.resumePlayback

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.experimental_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            SettingsGroupCard {
                ToggleRow(
                    icon = Icons.Rounded.History,
                    title = stringResource(R.string.setting_show_history),
                    subtitle = stringResource(
                        if (historyAvailable) R.string.setting_show_history_subtitle else R.string.setting_show_history_requires_resume,
                    ),
                    checked = settings.experimental.showHistory && historyAvailable,
                    onCheckedChange = viewModel::setShowHistory,
                    enabled = historyAvailable,
                )
            }
            SettingsGroupCard {
                ToggleRow(
                    icon = Icons.Rounded.Tv,
                    title = stringResource(R.string.setting_play_on_tv),
                    subtitle = stringResource(if (isTv) R.string.setting_play_on_tv_subtitle_tv else R.string.setting_play_on_tv_subtitle_phone),
                    checked = settings.experimental.playOnTv,
                    onCheckedChange = viewModel::setPlayOnTv,
                )
                if (settings.experimental.playOnTv) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    if (isTv) {
                        ReceiverStatusSection(receiverStatus)
                    } else {
                        NavigationRow(
                            icon = Icons.Rounded.AddLink,
                            title = stringResource(R.string.pair_with_tv),
                            subtitle = stringResource(R.string.pair_with_tv_subtitle),
                            onClick = { viewModel.pairDialogOpen.value = true },
                        )
                    }
                }
                if (settings.experimental.playOnTv || pairedDevices.isNotEmpty()) {
                    PairedDevicesList(isTv = isTv, devices = pairedDevices, onForget = { forgetting = it })
                }
            }
        }
    }

    if (pairDialogOpen) {
        val found by viewModel.linkSend.devices.collectAsStateWithLifecycle()
        PairTvDialog(
            devices = found,
            onChoose = { device ->
                viewModel.pairDialogOpen.value = false
                viewModel.linkSend.pair(device)
            },
            onAddress = { address ->
                viewModel.linkSend.pairWithAddress(address).also { ok -> if (ok) viewModel.pairDialogOpen.value = false }
            },
            onDismiss = { viewModel.pairDialogOpen.value = false },
        )
    }
    sendState?.let { state ->
        val targets by viewModel.linkSend.devices.collectAsStateWithLifecycle()
        SendLinkDialog(
            state = state,
            devices = targets,
            onChoose = viewModel.linkSend::sendTo,
            onSubmitCode = viewModel.linkSend::submitCode,
            onDismiss = viewModel.linkSend::dismiss,
        )
    }

    forgetting?.let { device ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            title = { Text(stringResource(R.string.dialog_forget_device_title, device.name)) },
            text = { Text(stringResource(if (isTv) R.string.dialog_forget_phone_body else R.string.dialog_forget_tv_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.forgetPairedDevice(device.id); forgetting = null }) {
                    Text(stringResource(R.string.action_forget))
                }
            },
            dismissButton = {
                TextButton(onClick = { forgetting = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** The phones paired with this TV, or the TVs paired with this phone, each with Forget. */
@Composable
private fun PairedDevicesList(isTv: Boolean, devices: List<PairedDevice>, onForget: (PairedDevice) -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    Text(
        text = stringResource(if (isTv) R.string.paired_phones else R.string.paired_tvs),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
    if (devices.isEmpty()) {
        Text(
            text = stringResource(if (isTv) R.string.paired_none_tv else R.string.paired_none_phone),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
    devices.forEach { device ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIcon(if (isTv) Icons.Rounded.PhoneAndroid else Icons.Rounded.Tv)
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
            IconButton(onClick = { onForget(device) }) {
                Icon(Icons.Rounded.LinkOff, contentDescription = stringResource(R.string.action_forget_device, device.name))
            }
        }
    }
}
