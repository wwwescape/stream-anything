package com.wwwescape.streamanything.ui.screens.home

import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wwwescape.streamanything.R
import com.wwwescape.streamanything.data.history.WatchEntry
import com.wwwescape.streamanything.data.streams.StreamResolvers
import com.wwwescape.streamanything.data.tvlink.LinkReceiver
import com.wwwescape.streamanything.data.tvlink.ReceiverStatus
import com.wwwescape.streamanything.ui.player.PlayerActivity
import com.wwwescape.streamanything.ui.tvsend.SendLinkDialog

/** The hero image's width / height (1400 × 640 px). */
private const val HERO_ASPECT_RATIO = 1400f / 640f

/** A link handed to the app from outside: share sheet, "Open with", or a phone using Play on
 * TV — which also says where the phone stopped ([startPositionMs]). */
data class IncomingLink(val text: String, val startPositionMs: Long? = null)

/**
 * The whole app on one screen: paste a link, press play. [incomingLink] fills the field and
 * starts playing straight away, then [onIncomingLinkConsumed] clears it so rotating or
 * returning doesn't replay it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    incomingLink: IncomingLink?,
    onIncomingLinkConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val recentHistory by viewModel.recentHistory.collectAsStateWithLifecycle()
    val targets by viewModel.linkSend.devices.collectAsStateWithLifecycle()
    val sendState by viewModel.linkSend.state.collectAsStateWithLifecycle()
    val receiverStatus by LinkReceiver.status.collectAsStateWithLifecycle()
    val offerOtherDevice by viewModel.offerOtherDeviceForHistory.collectAsStateWithLifecycle()
    var watchWhere by remember { mutableStateOf<Pair<WatchEntry, String>?>(null) }
    var removing by remember { mutableStateOf<WatchEntry?>(null) }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
    var link by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val clearFocus = remember { FocusRequester() }

    fun play(input: String = link, startPositionMs: Long? = null) {
        if (!StreamResolvers.isSupported(input)) {
            showError = true
            return
        }
        showError = false
        keyboard?.hide()
        context.startActivity(PlayerActivity.intent(context, input.trim(), startPositionMs))
    }

    fun playOnOtherDevice() {
        if (!StreamResolvers.isSupported(link)) {
            showError = true
            return
        }
        showError = false
        keyboard?.hide()
        viewModel.playOnOtherDevice(link)
    }

    LaunchedEffect(incomingLink) {
        if (incomingLink != null) {
            // Shared text often wraps the link in a message — keep just the link.
            link = StreamResolvers.parse(incomingLink.text)?.toString() ?: incomingLink.text
            play(link, incomingLink.startPositionMs)
            onIncomingLinkConsumed()
        }
    }
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.linkSend.sentTo.collect { deviceName ->
            Toast.makeText(context, resources.getString(R.string.tv_sent, deviceName), Toast.LENGTH_SHORT).show()
        }
    }
    // On a TV the field is focused from the start but read-only until OK is pressed: focusing
    // an editable field makes many TVs pop the keyboard straight away, covering the screen.
    var tvEditing by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }

    // A remote has no pointer: put focus on the field so OK opens the on-screen keyboard.
    LaunchedEffect(Unit) {
        if (isTv) runCatching { fieldFocus.requestFocus() }
    }
    LaunchedEffect(tvEditing) {
        if (tvEditing) keyboard?.show()
    }
    // Once the keyboard goes away (Back, or Go), the next OK opens it again.
    LaunchedEffect(imeVisible) {
        if (imeWasVisible && !imeVisible) tvEditing = false
        imeWasVisible = imeVisible
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // Centered vertically when it fits, scrollable when it doesn't (landscape phone with
        // the keyboard up).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // While the keyboard is up (especially the TV's, which covers the bottom half of a
                // 720p screen) only the field matters — fold the hero away so it stays in view.
                AnimatedVisibility(visible = !WindowInsets.isImeVisible) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // A marquee of the supported services' logos around the play badge
                        // (generated by scripts/generate_home_hero.py). Decorative only.
                        Image(
                            painter = painterResource(R.drawable.home_services),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(HERO_ASPECT_RATIO),
                        )
                        Text(
                            text = stringResource(R.string.home_headline),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            text = stringResource(R.string.home_tagline),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                // The link, then its actions: Clear → Play → TV/phone. The same on every device; on
                // a TV, Right/Left move along the row and disabled buttons are skipped.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextField(
                        value = link,
                        onValueChange = {
                            link = it
                            showError = false
                        },
                        placeholder = {
                            Text(stringResource(R.string.home_link_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                        // Paste stays in the field on phones (a TV has no clipboard to paste from).
                        trailingIcon = if (!isTv && link.isEmpty()) {
                            {
                                IconButton(
                                    onClick = {
                                        clipboardText(context)?.let {
                                            link = it
                                            showError = false
                                        }
                                    },
                                ) {
                                    Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.action_paste))
                                }
                            }
                        } else {
                            null
                        },
                        isError = showError,
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                            autoCorrectEnabled = false,
                        ),
                        keyboardActions = KeyboardActions(onGo = { play() }),
                        readOnly = isTv && !tvEditing,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .focusRequester(fieldFocus)
                            .onFocusChanged { if (!it.isFocused) tvEditing = false }
                            .onPreviewKeyEvent { event ->
                                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                                when {
                                    !isTv || tvEditing || event.type != KeyEventType.KeyDown -> false
                                    ok -> {
                                        tvEditing = true
                                        true
                                    }
                                    // A text field keeps Right for its cursor, so step out of it
                                    // explicitly — onto Clear, the first button that can do anything.
                                    event.key == Key.DirectionRight -> {
                                        if (link.isNotEmpty()) runCatching { clearFocus.requestFocus() }
                                        true
                                    }
                                    else -> false
                                }
                            },
                    )
                    LinkActionButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_clear),
                        onClick = {
                            link = ""
                            showError = false
                            // The buttons all turn disabled, so focus goes back to the field.
                            if (isTv) runCatching { fieldFocus.requestFocus() }
                        },
                        enabled = link.isNotEmpty(),
                        style = LinkActionStyle.OUTLINED,
                        modifier = Modifier
                            .focusRequester(clearFocus)
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.DirectionLeft && event.type == KeyEventType.KeyDown) {
                                    runCatching { fieldFocus.requestFocus() }
                                    true
                                } else {
                                    false
                                }
                            },
                    )
                    LinkActionButton(
                        icon = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.action_play),
                        onClick = { play() },
                        enabled = link.isNotBlank(),
                        style = LinkActionStyle.FILLED,
                        iconSize = 28.dp,
                    )
                    // Only once the other device is reachable (Play on TV on).
                    if (targets.isNotEmpty()) {
                        LinkActionButton(
                            // On a TV it sends to a phone; on a phone, to a TV.
                            icon = if (isTv) Icons.Rounded.PhoneAndroid else Icons.Rounded.Tv,
                            contentDescription = stringResource(if (isTv) R.string.action_play_on_phone else R.string.action_play_on_tv),
                            onClick = ::playOnOtherDevice,
                            enabled = link.isNotBlank(),
                            style = LinkActionStyle.TONAL,
                        )
                    }
                }

                // On the TV, while it's listening for Play on TV. (A phone listens too, for
                // "Continue on phone", but quietly.)
                (receiverStatus as? ReceiverStatus.Listening)?.takeIf { isTv }?.let { listening ->
                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            // The address too, for pairing by hand when discovery doesn't work.
                            text = listening.addresses.firstOrNull()
                                ?.let { stringResource(R.string.tv_ready_at, listening.name, it) }
                                ?: stringResource(R.string.tv_ready, listening.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (showError) {
                    Text(
                        text = stringResource(R.string.home_unsupported_link),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
                if (recentHistory.isNotEmpty()) {
                    RecentHistory(
                        entries = recentHistory,
                        onPlay = { entry, historyLink ->
                            link = historyLink
                            // With a paired TV on the network, ask where; otherwise just play.
                            if (offerOtherDevice) watchWhere = entry to historyLink else play(historyLink)
                        },
                        onRemove = { removing = it },
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            }
        }
    }

    // Removing can't be undone (the resume position goes too), so ask first.
    removing?.let { entry ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.dialog_remove_history_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    entry.link?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    Text(
                        text = stringResource(R.string.dialog_remove_history_body),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeFromHistory(entry)
                        removing = null
                    },
                ) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    watchWhere?.let { (entry, historyLink) ->
        WatchWhereDialog(
            entry = entry,
            otherName = targets.singleOrNull()?.serviceName,
            onHere = {
                watchWhere = null
                play(historyLink)
            },
            onOther = {
                watchWhere = null
                viewModel.playOnOtherDevice(entry, historyLink)
            },
            onDismiss = { watchWhere = null },
        )
    }
    sendState?.let { state ->
        SendLinkDialog(
            state = state,
            devices = targets,
            onChoose = viewModel.linkSend::sendTo,
            onSubmitCode = viewModel.linkSend::submitCode,
            onDismiss = viewModel.linkSend::dismiss,
        )
    }
}

private fun clipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    return clipboard.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
