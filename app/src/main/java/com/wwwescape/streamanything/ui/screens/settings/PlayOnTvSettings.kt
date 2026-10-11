package com.wwwescape.streamanything.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wwwescape.streamanything.R
import com.wwwescape.streamanything.data.tvlink.DiscoveredDevice
import com.wwwescape.streamanything.data.tvlink.ReceiverStatus

/**
 * On a TV, under the Play on TV toggle: whether it's listening, and the address to type on a
 * phone when the network won't let the phone find it on its own.
 */
@Composable
internal fun ReceiverStatusSection(status: ReceiverStatus) {
    Column(
        modifier = Modifier.padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (status) {
            ReceiverStatus.Off, ReceiverStatus.Starting -> StatusText(stringResource(R.string.receiver_starting))
            ReceiverStatus.Failed -> StatusText(stringResource(R.string.receiver_failed), error = true)
            is ReceiverStatus.Listening -> {
                Text(
                    text = stringResource(R.string.receiver_listening, status.name),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = status.addresses.firstOrNull()?.let { stringResource(R.string.receiver_address, it) }
                        ?: stringResource(R.string.receiver_no_address),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                when (status.announced) {
                    true -> StatusText(stringResource(R.string.receiver_announced))
                    false -> StatusText(stringResource(R.string.receiver_not_announced), error = true)
                    null -> Unit
                }
                StatusText(stringResource(R.string.receiver_pair_hint))
            }
        }
    }
}

@Composable
private fun StatusText(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** On a phone: pick a TV found on the network, or type the address the TV shows. */
@Composable
internal fun PairTvDialog(
    devices: List<DiscoveredDevice>,
    onChoose: (DiscoveredDevice) -> Unit,
    onAddress: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    fun connect() {
        invalid = !onAddress(address)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Tv, contentDescription = null) },
        title = { Text(stringResource(R.string.pair_with_tv)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.pair_dialog_found),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (devices.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.tv_searching), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    devices.forEach { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onChoose(device) }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(Icons.Rounded.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(device.serviceName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { input ->
                        address = input.filter { it.isDigit() || it == '.' || it == ':' }.take(21)
                        invalid = false
                    },
                    label = { Text(stringResource(R.string.pair_dialog_address_label)) },
                    placeholder = { Text("192.168.1.42") },
                    supportingText = {
                        Text(stringResource(if (invalid) R.string.pair_dialog_address_invalid else R.string.pair_dialog_address_hint))
                    },
                    isError = invalid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { connect() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::connect, enabled = address.isNotBlank()) { Text(stringResource(R.string.action_connect)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
