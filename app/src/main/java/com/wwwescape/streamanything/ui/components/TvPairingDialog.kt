package com.wwwescape.streamanything.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.streamanything.R
import com.wwwescape.streamanything.data.tvlink.LinkReceiver

/** On the TV, while a phone pairs for Play on TV: the code to type on the phone, large enough
 * to read from the couch. Shown over whatever screen is open, and gone once pairing ends. */
@Composable
fun TvPairingDialog() {
    val prompt by LinkReceiver.pairingPrompt.collectAsStateWithLifecycle()
    val current = prompt ?: return
    AlertDialog(
        onDismissRequest = LinkReceiver::cancelPairing,
        icon = { Icon(Icons.Rounded.Tv, contentDescription = null) },
        title = { Text(stringResource(R.string.tv_pair_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.tv_pair_body, current.phoneName),
                    textAlign = TextAlign.Center,
                )
                Text(
                    // "123 456" reads more easily at a distance than "123456".
                    text = current.code.chunked(3).joinToString(" "),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = LinkReceiver::cancelPairing) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
