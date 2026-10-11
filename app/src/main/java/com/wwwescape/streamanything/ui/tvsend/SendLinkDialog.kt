package com.wwwescape.streamanything.ui.tvsend

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
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wwwescape.streamanything.R
import com.wwwescape.streamanything.data.tvlink.DiscoveredDevice
import com.wwwescape.streamanything.data.tvlink.SendResult
import com.wwwescape.streamanything.data.tvlink.TvLinkProtocol
import com.wwwescape.streamanything.util.isTelevision

/** One dialog that follows [state]: pick a device, wait, type the pairing code shown on the
 * TV (phone → TV), or read why it didn't work. Worded for TVs on a phone and for phones on a
 * TV ("Continue on phone"). */
@Composable
fun SendLinkDialog(
    state: SendState,
    devices: List<DiscoveredDevice>,
    onChoose: (DiscoveredDevice) -> Unit,
    onSubmitCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val toPhone = LocalContext.current.isTelevision()
    val icon = if (toPhone) Icons.Rounded.PhoneAndroid else Icons.Rounded.Tv
    when (state) {
        SendState.Choosing -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(icon, contentDescription = null) },
            title = { Text(stringResource(if (toPhone) R.string.phone_choose_title else R.string.tv_choose_title)) },
            text = {
                if (devices.isEmpty()) {
                    Waiting(stringResource(if (toPhone) R.string.phone_searching else R.string.tv_searching))
                } else {
                    Column {
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
                                Icon(
                                    imageVector = if (toPhone) Icons.Rounded.PhoneAndroid else Icons.Rounded.Tv,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(device.serviceName, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )

        is SendState.Sending -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(icon, contentDescription = null) },
            title = { Text(state.deviceName) },
            text = { Waiting(stringResource(R.string.tv_sending)) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )

        is SendState.EnterCode -> CodeDialog(state, onSubmitCode, onDismiss)

        is SendState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(icon, contentDescription = null) },
            title = { Text(stringResource(R.string.tv_error_title, state.deviceName)) },
            text = {
                Text(
                    stringResource(
                        when (state.reason) {
                            SendResult.Reason.UNREACHABLE -> if (toPhone) R.string.phone_error_unreachable else R.string.tv_error_unreachable
                            SendResult.Reason.NOT_PAIRED -> R.string.phone_error_not_paired
                            SendResult.Reason.PAIRING_FAILED -> R.string.tv_error_pairing
                            SendResult.Reason.BUSY -> R.string.tv_error_busy
                            SendResult.Reason.UNSUPPORTED_LINK -> if (toPhone) R.string.phone_error_unsupported else R.string.tv_error_unsupported
                            SendResult.Reason.UNEXPECTED -> if (toPhone) R.string.phone_error_unexpected else R.string.tv_error_unexpected
                        },
                    ),
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun CodeDialog(state: SendState.EnterCode, onSubmitCode: (String) -> Unit, onDismiss: () -> Unit) {
    // Keyed on the attempt, so a wrong code clears the field for the next try.
    var code by remember(state) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val complete = code.length == TvLinkProtocol.CODE_LENGTH
    LaunchedEffect(state) { runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Tv, contentDescription = null) },
        title = { Text(stringResource(R.string.tv_code_title, state.deviceName)) },
        text = {
            Column {
                Text(stringResource(R.string.tv_code_body))
                OutlinedTextField(
                    value = code,
                    onValueChange = { input -> code = input.filter(Char::isDigit).take(TvLinkProtocol.CODE_LENGTH) },
                    label = { Text(stringResource(R.string.tv_code_label)) },
                    singleLine = true,
                    isError = state.wrongAttemptsLeft != null,
                    supportingText = state.wrongAttemptsLeft?.let { left ->
                        { Text(stringResource(R.string.tv_code_wrong, left)) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (complete) onSubmitCode(code) }),
                    textStyle = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmitCode(code) }, enabled = complete) { Text(stringResource(R.string.action_pair)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Waiting(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(text)
    }
}
