package com.wwwescape.streamanything.ui.tvsend

import android.app.Application
import com.wwwescape.streamanything.data.streams.StreamResolvers
import com.wwwescape.streamanything.data.tvlink.DiscoveredDevice
import com.wwwescape.streamanything.data.tvlink.LinkSender
import com.wwwescape.streamanything.data.tvlink.PairedDevice
import com.wwwescape.streamanything.data.tvlink.SendResult
import com.wwwescape.streamanything.data.tvlink.TvLinkProtocol
import com.wwwescape.streamanything.data.tvlink.TvLinkStore
import com.wwwescape.streamanything.util.isTelevision
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where this device is in sending a link to another one; `null` when it isn't. */
sealed interface SendState {
    /** Several devices (or none yet) — the user picks one. */
    data object Choosing : SendState
    data class Sending(val deviceName: String) : SendState

    /** Phone → TV only: the TV is showing a pairing code; [wrongAttemptsLeft] is set after a
     * wrong one. */
    data class EnterCode(val deviceName: String, val wrongAttemptsLeft: Int?) : SendState
    data class Failed(val reason: SendResult.Reason, val deviceName: String) : SendState
}

/**
 * Sending a link to another device — pick one, pair if needed (phone → TV), send. On a phone
 * it targets TVs (Home's link field and Recent history, the player's "Continue on TV"); on a TV
 * it targets paired phones (the player's "Continue on phone"). Lives in the owning ViewModel's
 * [scope].
 *
 * @param discoverAlways whether to look for devices whenever [devices] is observed (Home, so
 *   its TV button can appear). Otherwise they're only looked for while a send is under way
 *   (the player, where it would otherwise run for a whole film).
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LinkSendController(
    private val app: Application,
    private val scope: CoroutineScope,
    discoverAlways: Flow<Boolean>,
) {
    private val _state = MutableStateFlow<SendState?>(null)
    val state: StateFlow<SendState?> = _state.asStateFlow()

    /** Paired devices with a last-known address, so they can be reached even when network
     * discovery doesn't find them (some routers block it). */
    private val remembered: Flow<List<PairedDevice>> =
        TvLinkStore.pairedFlow(app, if (app.isTelevision()) TvLinkStore.Role.PHONE else TvLinkStore.Role.TV)

    /** Devices found on the network, plus remembered ones that weren't (by name). */
    val devices: StateFlow<List<DiscoveredDevice>> = combine(discoverAlways, _state.map { it != null }) { always, sending -> always || sending }
        .distinctUntilChanged()
        .flatMapLatest { active ->
            if (!active) {
                flowOf(emptyList())
            } else {
                combine(LinkSender.discover(app), remembered) { found, paired ->
                    found + paired
                        .filter { device -> found.none { it.serviceName == device.name } }
                        .mapNotNull { device ->
                            device.address?.let(TvLinkProtocol::parseAddress)?.let { DiscoveredDevice(device.name, it) }
                        }
                }
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sentTo = Channel<String>(Channel.BUFFERED)

    /** The other device's name, once a link has started playing on it. */
    val sentTo: Flow<String> = _sentTo.receiveAsFlow()

    private val _pairedWith = Channel<String>(Channel.BUFFERED)

    /** The TV's name, once pairing on its own ([pair]) has worked. */
    val pairedWith: Flow<String> = _pairedWith.receiveAsFlow()

    /** `null` with [pairOnly]: pair without sending anything. */
    private var pendingLink: String? = null
    private var pairOnly = false
    private var pendingPositionMs: Long? = null
    private var job: Job? = null
    private var codeReply: CompletableDeferred<String?>? = null

    /**
     * Sends [link] — straight to the device when there's just one, otherwise after asking
     * which.
     * @param startPositionMs where it should start: a position, 0 for the beginning, or `null`
     *   to let it apply its own resume history.
     */
    fun request(link: String, startPositionMs: Long?) {
        pendingLink = StreamResolvers.parse(link)?.toString() ?: link.trim()
        pendingPositionMs = startPositionMs
        pairOnly = false
        job?.cancel()
        val found = devices.value
        if (found.size == 1) {
            sendTo(found.single())
            return
        }
        _state.value = SendState.Choosing
        if (found.isEmpty()) {
            // Only just started looking: if exactly one answers (and no other turns up for a
            // moment), go straight to it, as when one was already known.
            job = scope.launch {
                val settled = devices.debounce(AUTO_PICK_SETTLE_MS).first { it.isNotEmpty() }
                if (settled.size == 1 && _state.value == SendState.Choosing) sendTo(settled.single())
            }
        }
    }

    /** Phone only: pairs with [target] (found on the network, or typed by address) without
     * sending a video. */
    fun pair(target: DiscoveredDevice) {
        pendingLink = null
        pendingPositionMs = null
        pairOnly = true
        sendTo(target)
    }

    /** [pair] with an address typed by hand; `false` if it isn't a local address. */
    fun pairWithAddress(input: String): Boolean {
        val address = TvLinkProtocol.parseAddress(input) ?: return false
        pair(DiscoveredDevice(input.trim(), address))
        return true
    }

    fun sendTo(target: DiscoveredDevice) {
        val link = pendingLink
        if (link == null && !pairOnly) return
        val startPositionMs = pendingPositionMs
        job?.cancel()
        job = scope.launch {
            _state.value = SendState.Sending(target.serviceName)
            val result = LinkSender.send(app, target, link, startPositionMs) { wrongAttemptsLeft ->
                val reply = CompletableDeferred<String?>()
                codeReply = reply
                _state.value = SendState.EnterCode(target.serviceName, wrongAttemptsLeft)
                reply.await().also { _state.value = SendState.Sending(target.serviceName) }
            }
            _state.value = when (result) {
                is SendResult.Played -> {
                    _sentTo.trySend(result.deviceName)
                    null
                }
                is SendResult.Paired -> {
                    _pairedWith.trySend(result.deviceName)
                    null
                }
                SendResult.Cancelled -> null
                is SendResult.Failed -> SendState.Failed(result.reason, result.deviceName)
            }
        }
    }

    fun submitCode(code: String) {
        codeReply?.complete(code)
    }

    fun dismiss() {
        codeReply?.complete(null)
        codeReply = null
        job?.cancel()
        job = null
        _state.value = null
    }

    private companion object {
        const val AUTO_PICK_SETTLE_MS = 1_000L
    }
}
