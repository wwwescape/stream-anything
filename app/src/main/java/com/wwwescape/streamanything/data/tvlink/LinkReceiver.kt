package com.wwwescape.streamanything.data.tvlink

import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import com.wwwescape.streamanything.MainActivity
import com.wwwescape.streamanything.R
import com.wwwescape.streamanything.data.settings.SettingsRepository
import com.wwwescape.streamanything.data.streams.StreamResolvers
import com.wwwescape.streamanything.data.tvlink.TvLinkProtocol.ERROR_BAD_REQUEST
import com.wwwescape.streamanything.data.tvlink.TvLinkProtocol.ERROR_BUSY
import com.wwwescape.streamanything.data.tvlink.TvLinkProtocol.ERROR_NOT_PAIRED
import com.wwwescape.streamanything.data.tvlink.TvLinkProtocol.ERROR_PAIR_FROM_PHONE
import com.wwwescape.streamanything.data.tvlink.TvLinkProtocol.ERROR_TOO_MANY_ATTEMPTS
import com.wwwescape.streamanything.data.tvlink.TvLinkProtocol.ERROR_UNSUPPORTED_LINK
import com.wwwescape.streamanything.data.tvlink.TvLinkProtocol.ERROR_WRONG_CODE
import com.wwwescape.streamanything.util.isTelevision
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What the TV shows while a phone pairs: who's asking, and the code to type on it. */
data class PairingPrompt(val phoneName: String, val code: String)

/** A video a paired device sent, with where to start: a position, 0 for the beginning, or
 * `null` for this device's own resume history. */
data class IncomingVideo(val link: String, val positionMs: Long?)

/** Whether this device is listening, for the status shown in Settings and on the TV's Home. */
sealed interface ReceiverStatus {
    data object Off : ReceiverStatus
    data object Starting : ReceiverStatus

    /**
     * @param addresses where it can be reached ("192.168.1.42"), to type on the other device
     *   when network discovery doesn't find it.
     * @param announced whether network discovery can find it; `null` while that's being set up.
     */
    data class Listening(val name: String, val addresses: List<String>, val announced: Boolean?) : ReceiverStatus

    /** Couldn't open a socket — usually no network. */
    data object Failed : ReceiverStatus
}

/**
 * The receiving side of [TvLinkProtocol], on both devices: a TV receives Play on TV from phones
 * (and shows the pairing code), a phone receives "Continue on phone" from its paired TVs (no
 * pairing — that's always started from the phone). Listens only while Stream Anything is
 * visible *and* the Experimental "Play on TV" option is on — on a phone, also only once a TV
 * is paired. The activities call [start] / [stop] from onStart / onStop, and the last stop
 * closes the socket and withdraws the NSD announcement. Nothing runs in the background, so
 * there's no service and no battery cost.
 */
object LinkReceiver {

    const val ACTION_PLAY_SENT_LINK = "com.wwwescape.streamanything.action.PLAY_SENT_LINK"

    private const val TAG = "LinkReceiver"
    private const val MAX_CONNECTIONS = 4
    private const val MAX_REQUESTS_PER_CONNECTION = 6
    private const val READ_TIMEOUT_MS = 15_000

    /** How long the code stays up for the user to type it on the phone. */
    private const val PAIRING_TIMEOUT_MS = 120_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var users = 0
    private var job: Job? = null

    private val _status = MutableStateFlow<ReceiverStatus>(ReceiverStatus.Off)
    val status: StateFlow<ReceiverStatus> = _status.asStateFlow()

    /** The port this device is listening on, sent along with requests so the other device
     * can reach it back; `null` while it isn't listening. */
    @Volatile var listeningPort: Int? = null
        private set

    private val _pairingPrompt = MutableStateFlow<PairingPrompt?>(null)
    val pairingPrompt: StateFlow<PairingPrompt?> = _pairingPrompt.asStateFlow()

    /** One pairing at a time — a second phone gets "busy" rather than a second code. */
    private val pairingLock = Mutex()

    @Volatile private var pairingSocket: Socket? = null

    /** Handed over in-process rather than in the Intent, so another app sending
     * [ACTION_PLAY_SENT_LINK] finds nothing to play. */
    @Volatile private var incoming: IncomingVideo? = null

    fun takeIncoming(): IncomingVideo? = incoming.also { incoming = null }

    @MainThread
    fun start(context: Context) {
        if (users++ > 0) return
        val app = context.applicationContext
        val isTv = app.isTelevision()
        val enabled = if (isTv) {
            SettingsRepository.settingsFlow(app).map { it.experimental.playOnTv }
        } else {
            // A phone only hears from TVs it has paired with — none paired, nothing to listen for.
            combine(
                SettingsRepository.settingsFlow(app),
                TvLinkStore.pairedFlow(app, TvLinkStore.Role.TV),
            ) { settings, tvs -> settings.experimental.playOnTv && tvs.isNotEmpty() }
        }
        job = scope.launch {
            enabled
                .distinctUntilChanged()
                .collectLatest { on ->
                    if (!on) {
                        _status.value = ReceiverStatus.Off
                        return@collectLatest
                    }
                    _status.value = ReceiverStatus.Starting
                    try {
                        serve(app, isTv)
                    } catch (e: IOException) {
                        // No network to listen on; tried again the next time the app is opened.
                        Log.w(TAG, "Can't listen: ${e.javaClass.simpleName}")
                        _status.value = ReceiverStatus.Failed
                    }
                }
        }
    }

    @MainThread
    fun stop() {
        if (users == 0 || --users > 0) return
        job?.cancel()
        job = null
    }

    /** The TV user backed out of the code dialog. */
    fun cancelPairing() {
        runCatching { pairingSocket?.close() }
    }

    private suspend fun serve(context: Context, isTv: Boolean) = coroutineScope {
        val ownId = TvLinkStore.deviceId(context)
        val nsd = context.getSystemService(NsdManager::class.java)
        val registration = RegistrationListener()
        val server = bindServer()
        val closer = closeOnCancel(server)
        try {
            listeningPort = server.localPort
            // Listening already counts, announced or not: the address works without discovery.
            _status.value = ReceiverStatus.Listening(
                name = TvLinkStore.deviceName(context),
                addresses = localAddresses(server.localPort),
                announced = null,
            )
            nsd.registerService(
                NsdServiceInfo().apply {
                    serviceName = TvLinkStore.deviceName(context)
                    serviceType = if (isTv) TvLinkProtocol.SERVICE_TYPE_TV else TvLinkProtocol.SERVICE_TYPE_PHONE
                    port = server.localPort
                },
                NsdManager.PROTOCOL_DNS_SD,
                registration,
            )
            val slots = Semaphore(MAX_CONNECTIONS)
            while (isActive) {
                val socket = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                if (!TvLinkProtocol.isLocalAddress(socket.inetAddress) || !slots.tryAcquire()) {
                    socket.close()
                    continue
                }
                launch {
                    try {
                        coroutineScope {
                            val socketCloser = closeOnCancel(socket)
                            try {
                                Connection(context, socket, ownId, isTv).run()
                            } catch (e: Exception) {
                                Log.w(TAG, "Connection ended: ${e.javaClass.simpleName}")
                            } finally {
                                socketCloser.cancel()
                            }
                        }
                    } finally {
                        socket.close()
                        slots.release()
                    }
                }
            }
        } finally {
            closer.cancel()
            server.close()
            runCatching { nsd.unregisterService(registration) }
            listeningPort = null
            _status.value = ReceiverStatus.Off
        }
    }

    /** [TvLinkProtocol.DEFAULT_PORT] when it's free, so a typed address needs no port. */
    private fun bindServer(): ServerSocket {
        val preferred = ServerSocket().apply { reuseAddress = true }
        return try {
            preferred.bind(InetSocketAddress(TvLinkProtocol.DEFAULT_PORT))
            preferred
        } catch (e: IOException) {
            preferred.close()
            ServerSocket(0)
        }
    }

    /** This device's local IPv4 addresses (Wi-Fi, Ethernet), as the other device would type them. */
    private fun localAddresses(port: Int): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filter { it is Inet4Address && TvLinkProtocol.isLocalAddress(it) }
            .map { TvLinkProtocol.formatAddress(it, port) }
            .distinct()
    }.getOrDefault(emptyList())

    private fun updateAnnounced(name: String?, announced: Boolean) {
        val current = _status.value as? ReceiverStatus.Listening ?: return
        _status.value = current.copy(name = name ?: current.name, announced = announced)
    }

    /** A blocked accept/read only returns when its socket closes, so close it on cancel. */
    private fun CoroutineScope.closeOnCancel(closeable: Closeable): Job = launch {
        try {
            awaitCancellation()
        } finally {
            runCatching { closeable.close() }
        }
    }

    private class RegistrationListener : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) {
            // The system may have renamed it ("Living Room TV (2)") to avoid a clash.
            updateAnnounced(info.serviceName, announced = true)
        }

        override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
            // Still reachable by address — only automatic discovery is out.
            Log.w(TAG, "NSD registration failed: $errorCode")
            updateAnnounced(null, announced = false)
        }

        override fun onServiceUnregistered(info: NsdServiceInfo) = Unit

        override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
    }

    /** One sender's conversation: hello, then up to [MAX_REQUESTS_PER_CONNECTION] requests. */
    private class Connection(
        private val context: Context,
        private val socket: Socket,
        private val ownId: String,
        private val isTv: Boolean,
    ) {
        private val input = BufferedInputStream(socket.getInputStream())
        private val output: OutputStream = socket.getOutputStream()
        private val challenge = TvLinkProtocol.newChallenge()

        private var pairing: Pairing? = null

        private class Pairing(
            val phoneId: String,
            val phoneName: String,
            val phoneKey: ByteArray,
            val keys: KeyPair,
            val code: String,
            val request: JSONObject,
            var attempts: Int = 0,
        )

        suspend fun run() {
            socket.soTimeout = READ_TIMEOUT_MS
            send(
                JSONObject()
                    .put("app", TvLinkProtocol.APP_ID)
                    .put("v", TvLinkProtocol.VERSION)
                    .put("id", ownId)
                    .put("name", (_status.value as? ReceiverStatus.Listening)?.name ?: TvLinkStore.deviceName(context))
                    .put("challenge", TvLinkProtocol.encode(challenge)),
            )
            try {
                repeat(MAX_REQUESTS_PER_CONNECTION) {
                    val line = TvLinkProtocol.readLine(input) ?: return
                    val request = runCatching { JSONObject(line) }.getOrNull()
                    val keepGoing = when (request?.optString("type")) {
                        TvLinkProtocol.TYPE_PLAY -> play(request)
                        TvLinkProtocol.TYPE_PAIR -> pair(request)
                        TvLinkProtocol.TYPE_CONFIRM -> confirm(request)
                        else -> fail(ERROR_BAD_REQUEST)
                    }
                    if (!keepGoing) return
                }
            } finally {
                if (pairing != null) endPairing()
            }
        }

        private suspend fun play(request: JSONObject): Boolean {
            val senderId = request.optString("id").takeIf(TvLinkProtocol::isValidDeviceId) ?: return fail(ERROR_BAD_REQUEST)
            val url = request.optString("url").takeIf { it.length in 1..TvLinkProtocol.MAX_URL_LENGTH }
                ?: return fail(ERROR_BAD_REQUEST)
            // Absent: the TV's own resume history decides; 0: from the beginning.
            val positionMs = if (request.has("position")) {
                (request.opt("position") as? Number)?.toLong()?.takeIf(TvLinkProtocol::isValidPosition)
                    ?: return fail(ERROR_BAD_REQUEST)
            } else {
                null
            }
            val mac = TvLinkProtocol.decode(request.optString("mac")) ?: return fail(ERROR_BAD_REQUEST)
            val message = if (isTv) {
                TvLinkProtocol.playMessage(challenge, phoneId = senderId, tvId = ownId, url = url, positionMs = positionMs)
            } else {
                TvLinkProtocol.playMessage(challenge, phoneId = ownId, tvId = senderId, url = url, positionMs = positionMs, toPhone = true)
            }
            val senderRole = if (isTv) TvLinkStore.Role.PHONE else TvLinkStore.Role.TV
            val expected = TvLinkStore.mac(senderRole, senderId, message) ?: return fail(ERROR_NOT_PAIRED)
            if (!TvLinkProtocol.macMatches(expected, mac)) return fail(ERROR_NOT_PAIRED)
            rememberSender(senderRole, senderId, request)
            // Only now, from a paired phone, is the link looked at at all.
            if (!StreamResolvers.isSupported(url)) return fail(ERROR_UNSUPPORTED_LINK)

            send(JSONObject().put("ok", true))
            incoming = IncomingVideo(url, positionMs)
            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .setAction(ACTION_PLAY_SENT_LINK)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
            return false
        }

        private fun pair(request: JSONObject): Boolean {
            if (!isTv) return fail(ERROR_PAIR_FROM_PHONE)
            if (pairing != null) return fail(ERROR_BAD_REQUEST)
            val phoneId = request.optString("id").takeIf(TvLinkProtocol::isValidDeviceId) ?: return fail(ERROR_BAD_REQUEST)
            val phoneName = TvLinkProtocol.cleanName(request.optString("name")) ?: return fail(ERROR_BAD_REQUEST)
            val phoneKey = TvLinkProtocol.decode(request.optString("key"))
                ?.takeIf { runCatching { TvLinkProtocol.decodePublicKey(it) }.isSuccess }
                ?: return fail(ERROR_BAD_REQUEST)
            if (!pairingLock.tryLock()) return fail(ERROR_BUSY)

            val keys = TvLinkProtocol.newKeyPair()
            val code = TvLinkProtocol.newCode()
            pairing = Pairing(phoneId, phoneName, phoneKey, keys, code, request)
            pairingSocket = socket
            socket.soTimeout = PAIRING_TIMEOUT_MS
            _pairingPrompt.value = PairingPrompt(phoneName, code)
            send(JSONObject().put("ok", true).put("key", TvLinkProtocol.encode(TvLinkProtocol.encodePublicKey(keys.public))))
            return true
        }

        private suspend fun confirm(request: JSONObject): Boolean {
            val current = pairing ?: return fail(ERROR_BAD_REQUEST)
            val mac = TvLinkProtocol.decode(request.optString("mac")) ?: return fail(ERROR_BAD_REQUEST)
            val tvKey = TvLinkProtocol.encodePublicKey(current.keys.public)
            val secret = TvLinkProtocol.pairingSecret(
                current.keys, current.phoneKey, current.code, challenge, current.phoneKey, tvKey, current.phoneId, ownId,
            )
            try {
                if (!TvLinkProtocol.macMatches(TvLinkProtocol.confirmMac(secret, challenge, fromTv = false), mac)) {
                    current.attempts++
                    val left = TvLinkProtocol.MAX_CODE_ATTEMPTS - current.attempts
                    if (left <= 0) return fail(ERROR_TOO_MANY_ATTEMPTS)
                    send(JSONObject().put("ok", false).put("error", ERROR_WRONG_CODE).put("attemptsLeft", left))
                    return true
                }
                TvLinkStore.pair(context, TvLinkStore.Role.PHONE, PairedDevice(current.phoneId, current.phoneName), secret)
                rememberSender(TvLinkStore.Role.PHONE, current.phoneId, current.request)
                send(JSONObject().put("ok", true).put("mac", TvLinkProtocol.encode(TvLinkProtocol.confirmMac(secret, challenge, fromTv = true))))
            } finally {
                secret.fill(0)
            }
            endPairing()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.tv_paired_with, current.phoneName), Toast.LENGTH_SHORT).show()
            }
            // The phone usually follows with its play request on this same connection.
            socket.soTimeout = READ_TIMEOUT_MS
            return true
        }

        private fun endPairing() {
            pairing = null
            pairingSocket = null
            _pairingPrompt.value = null
            pairingLock.unlock()
        }

        /** Remembers where a verified sender can be reached back: the address it connected
         * from, at the port it says it listens on. */
        private suspend fun rememberSender(role: TvLinkStore.Role, id: String, request: JSONObject) {
            val port = request.optInt("port", TvLinkProtocol.DEFAULT_PORT).takeIf { it in 1..65535 } ?: TvLinkProtocol.DEFAULT_PORT
            TvLinkStore.updateAddress(context, role, id, InetSocketAddress(socket.inetAddress, port))
        }

        /** Answers with an error; `false` ends the conversation. */
        private fun fail(error: String): Boolean {
            runCatching { send(JSONObject().put("ok", false).put("error", error)) }
            return false
        }

        private fun send(message: JSONObject) {
            output.write((message.toString() + "\n").toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }
}
