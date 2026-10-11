package com.wwwescape.streamanything.data.tvlink

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.wwwescape.streamanything.util.isTelevision
import java.io.BufferedInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A device announcing itself on the local network (a TV to a phone, a phone to a TV), by the
 * name it advertises. */
data class DiscoveredDevice(val serviceName: String, val address: InetSocketAddress? = null)

sealed interface SendResult {
    data class Played(val deviceName: String) : SendResult

    /** Pairing only (no link): the phone and TV are now paired. */
    data class Paired(val deviceName: String) : SendResult

    /** The user backed out of entering the code. */
    data object Cancelled : SendResult
    data class Failed(val reason: Reason, val deviceName: String) : SendResult

    enum class Reason {
        /** Couldn't find or connect to it — closed app, other network, client isolation. */
        UNREACHABLE,

        /** TV → phone only: they aren't paired (pairing is started from the phone). */
        NOT_PAIRED,

        /** Wrong code too many times, or the TV's proof didn't check out. */
        PAIRING_FAILED,

        /** Another phone is pairing with the TV right now. */
        BUSY,

        /** The TV doesn't play this link. */
        UNSUPPORTED_LINK,
        UNEXPECTED,
    }
}

/**
 * The sending side of [TvLinkProtocol]: a phone finds TVs and sends them links (pairing first
 * when needed); a TV finds phones and sends back to the paired ones ("Continue on phone").
 * Discovery is a cold [Flow], so it only runs while someone collects it.
 */
object LinkSender {

    private const val TAG = "LinkSender"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 15_000

    /** Long enough for the user to read the code off the TV and type it in. */
    private const val CODE_ENTRY_TIMEOUT_MS = 120_000

    /** TVs look for phones; phones look for TVs. */
    private fun targetServiceType(context: Context): String =
        if (context.isTelevision()) TvLinkProtocol.SERVICE_TYPE_PHONE else TvLinkProtocol.SERVICE_TYPE_TV

    fun discover(context: Context): Flow<List<DiscoveredDevice>> = callbackFlow {
        val serviceType = targetServiceType(context)
        val nsd = context.getSystemService(NsdManager::class.java)
        val found = linkedMapOf<String, DiscoveredDevice>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                synchronized(found) { found[info.serviceName] = DiscoveredDevice(info.serviceName) }
                trySend(synchronized(found) { found.values.toList() })
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                synchronized(found) { found.remove(info.serviceName) }
                trySend(synchronized(found) { found.values.toList() })
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD discovery failed: $errorCode")
                trySend(emptyList())
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        trySend(emptyList())
        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
    }.conflate()

    /**
     * Sends [link] to [target], starting at [positionMs] (0 for the beginning, `null` to let it
     * use its own resume history). From a phone, if the TV doesn't know this phone yet (or
     * forgot it), pairs first: the TV shows a code and [askCode] collects it from the user —
     * `wrongAttemptsLeft` is `null` the first time, then how many tries remain after a wrong
     * code; returning `null` cancels. From a TV, the phone must already be paired.
     *
     * A [target] with an address (typed by hand, or remembered from last time) is connected to
     * directly; otherwise it's looked up with NSD. A `null` [link] (phone only) just pairs.
     */
    suspend fun send(
        context: Context,
        target: DiscoveredDevice,
        link: String?,
        positionMs: Long?,
        askCode: suspend (wrongAttemptsLeft: Int?) -> String?,
    ): SendResult = withContext(Dispatchers.IO) {
        val ownId = TvLinkStore.deviceId(context)
        val address = target.address ?: resolve(context, target)
            ?: return@withContext SendResult.Failed(SendResult.Reason.UNREACHABLE, target.serviceName)
        val socket = Socket()
        try {
            socket.connect(address, CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            val position = positionMs?.takeIf(TvLinkProtocol::isValidPosition)
            val session = Session(context, socket, address)
            if (context.isTelevision()) {
                link?.let { session.runToPhone(target, ownId, it, position) }
                    ?: SendResult.Failed(SendResult.Reason.UNEXPECTED, target.serviceName)
            } else {
                session.run(target, ownId, link, position, askCode)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Send failed: ${e.javaClass.simpleName}")
            SendResult.Failed(SendResult.Reason.UNREACHABLE, target.serviceName)
        } finally {
            runCatching { socket.close() }
        }
    }

    @Suppress("DEPRECATION") // resolveService/host: the replacements need API 34; these still work there.
    private suspend fun resolve(context: Context, target: DiscoveredDevice): InetSocketAddress? {
        val nsd = context.getSystemService(NsdManager::class.java)
        val query = NsdServiceInfo().apply {
            serviceName = target.serviceName
            serviceType = targetServiceType(context)
        }
        return suspendCancellableCoroutine { continuation ->
            nsd.resolveService(
                query,
                object : NsdManager.ResolveListener {
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host
                        continuation.resume(if (host != null && TvLinkProtocol.isLocalAddress(host)) InetSocketAddress(host, info.port) else null)
                    }

                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "NSD resolve failed: $errorCode")
                        continuation.resume(null)
                    }
                },
            )
        }
    }

    private class Session(
        private val context: Context,
        private val socket: Socket,
        /** Where the other device was reached — remembered for when discovery can't find it. */
        private val address: InetSocketAddress,
    ) {
        private var sentFromTv = false
        private val input = BufferedInputStream(socket.getInputStream())
        private val output = socket.getOutputStream()

        /** The receiver's hello: its ID, name and this connection's challenge. */
        private class Hello(val id: String, val name: String, val challenge: ByteArray)

        /** `null` if the other end isn't a compatible Stream Anything. */
        private fun receiveHello(target: DiscoveredDevice): Hello? {
            val hello = receive()
            val id = hello.optString("id")
            val challenge = TvLinkProtocol.decode(hello.optString("challenge"))
            if (hello.optString("app") != TvLinkProtocol.APP_ID || hello.optInt("v") != TvLinkProtocol.VERSION ||
                !TvLinkProtocol.isValidDeviceId(id) || challenge?.size != TvLinkProtocol.CHALLENGE_BYTES
            ) {
                return null
            }
            return Hello(id, TvLinkProtocol.cleanName(hello.optString("name")) ?: target.serviceName, challenge)
        }

        /** TV → phone ("Continue on phone"): only to a phone that paired with this TV. */
        suspend fun runToPhone(target: DiscoveredDevice, tvId: String, link: String, positionMs: Long?): SendResult {
            sentFromTv = true
            val hello = receiveHello(target) ?: return SendResult.Failed(SendResult.Reason.UNEXPECTED, target.serviceName)
            val mac = TvLinkStore.mac(
                TvLinkStore.Role.PHONE,
                hello.id,
                TvLinkProtocol.playMessage(hello.challenge, phoneId = hello.id, tvId = tvId, url = link, positionMs = positionMs, toPhone = true),
            ) ?: return SendResult.Failed(SendResult.Reason.NOT_PAIRED, hello.name)
            return sendPlay(tvId, link, positionMs, mac).toResult(hello.name)
                .also { if (it is SendResult.Played) TvLinkStore.updateAddress(context, TvLinkStore.Role.PHONE, hello.id, address) }
        }

        /** Phone → TV (Play on TV), pairing first if needed; a `null` [link] only pairs. */
        suspend fun run(
            target: DiscoveredDevice,
            phoneId: String,
            link: String?,
            positionMs: Long?,
            askCode: suspend (Int?) -> String?,
        ): SendResult {
            val hello = receiveHello(target) ?: return SendResult.Failed(SendResult.Reason.UNEXPECTED, target.serviceName)
            val tvId = hello.id
            val challenge = hello.challenge
            val tvName = hello.name

            if (TvLinkStore.isPaired(context, TvLinkStore.Role.TV, tvId)) {
                if (link == null) return paired(tvId, tvName)
                val response = play(phoneId, tvId, challenge, link, positionMs)
                if (response.optString("error") != TvLinkProtocol.ERROR_NOT_PAIRED) return played(response, tvId, tvName)
                // The TV forgot this phone (or was reset): pair again on this same connection.
                TvLinkStore.forget(context, TvLinkStore.Role.TV, tvId)
            }

            val pairResult = pair(phoneId, tvId, tvName, challenge, askCode)
            if (pairResult != null) return pairResult
            if (link == null) return paired(tvId, tvName)
            return played(play(phoneId, tvId, challenge, link, positionMs), tvId, tvName)
        }

        private suspend fun paired(tvId: String, tvName: String): SendResult {
            TvLinkStore.updateAddress(context, TvLinkStore.Role.TV, tvId, address)
            return SendResult.Paired(tvName)
        }

        private suspend fun played(response: JSONObject, tvId: String, tvName: String): SendResult =
            response.toResult(tvName).also {
                if (it is SendResult.Played) TvLinkStore.updateAddress(context, TvLinkStore.Role.TV, tvId, address)
            }

        private fun play(phoneId: String, tvId: String, challenge: ByteArray, link: String, positionMs: Long?): JSONObject {
            val mac = TvLinkStore.mac(TvLinkStore.Role.TV, tvId, TvLinkProtocol.playMessage(challenge, phoneId, tvId, link, positionMs))
                ?: return JSONObject().put("ok", false).put("error", TvLinkProtocol.ERROR_NOT_PAIRED)
            return sendPlay(phoneId, link, positionMs, mac)
        }

        private fun sendPlay(senderId: String, link: String, positionMs: Long?, mac: ByteArray): JSONObject {
            send(
                JSONObject()
                    .put("type", TvLinkProtocol.TYPE_PLAY)
                    .put("id", senderId)
                    .put("url", link)
                    .apply { positionMs?.let { put("position", it) } }
                    .put("port", replyPort())
                    .put("mac", TvLinkProtocol.encode(mac)),
            )
            return receive()
        }

        /** Where this device can be reached back: its own listening port, or the default one
         * it will use once it listens (a phone starts listening once it's paired). */
        private fun replyPort(): Int = LinkReceiver.listeningPort ?: TvLinkProtocol.DEFAULT_PORT

        /** `null` once paired; otherwise why it didn't work. */
        private suspend fun pair(
            phoneId: String,
            tvId: String,
            tvName: String,
            challenge: ByteArray,
            askCode: suspend (Int?) -> String?,
        ): SendResult? {
            val keys = TvLinkProtocol.newKeyPair()
            val phoneKey = TvLinkProtocol.encodePublicKey(keys.public)
            send(
                JSONObject()
                    .put("type", TvLinkProtocol.TYPE_PAIR)
                    .put("id", phoneId)
                    .put("name", TvLinkStore.deviceName(context))
                    .put("key", TvLinkProtocol.encode(phoneKey))
                    .put("port", replyPort()),
            )
            val accepted = receive()
            if (!accepted.optBoolean("ok")) return accepted.toResult(tvName)
            val tvKey = TvLinkProtocol.decode(accepted.optString("key"))
                ?.takeIf { runCatching { TvLinkProtocol.decodePublicKey(it) }.isSuccess }
                ?: return SendResult.Failed(SendResult.Reason.UNEXPECTED, tvName)

            socket.soTimeout = CODE_ENTRY_TIMEOUT_MS
            var attemptsLeft: Int? = null
            while (true) {
                val code = askCode(attemptsLeft)?.takeIf(TvLinkProtocol::isValidCode) ?: return SendResult.Cancelled
                val secret = TvLinkProtocol.pairingSecret(keys, tvKey, code, challenge, phoneKey, tvKey, phoneId, tvId)
                try {
                    send(
                        JSONObject()
                            .put("type", TvLinkProtocol.TYPE_CONFIRM)
                            .put("mac", TvLinkProtocol.encode(TvLinkProtocol.confirmMac(secret, challenge, fromTv = false))),
                    )
                    val response = receive()
                    if (response.optString("error") == TvLinkProtocol.ERROR_WRONG_CODE) {
                        attemptsLeft = response.optInt("attemptsLeft", 1)
                        continue
                    }
                    if (!response.optBoolean("ok")) return response.toResult(tvName)
                    // The TV proves it derived the same secret — i.e. it showed this code.
                    val tvProof = TvLinkProtocol.decode(response.optString("mac"))
                    if (!TvLinkProtocol.macMatches(TvLinkProtocol.confirmMac(secret, challenge, fromTv = true), tvProof)) {
                        return SendResult.Failed(SendResult.Reason.PAIRING_FAILED, tvName)
                    }
                    TvLinkStore.pair(context, TvLinkStore.Role.TV, PairedDevice(tvId, tvName), secret)
                    socket.soTimeout = READ_TIMEOUT_MS
                    return null
                } finally {
                    secret.fill(0)
                }
            }
        }

        private fun JSONObject.toResult(deviceName: String): SendResult = if (optBoolean("ok")) {
            SendResult.Played(deviceName)
        } else {
            val reason = when (optString("error")) {
                TvLinkProtocol.ERROR_BUSY -> SendResult.Reason.BUSY
                TvLinkProtocol.ERROR_UNSUPPORTED_LINK -> SendResult.Reason.UNSUPPORTED_LINK
                TvLinkProtocol.ERROR_TOO_MANY_ATTEMPTS -> SendResult.Reason.PAIRING_FAILED
                // From a TV: the phone forgot it. From a phone: only right after a fresh pairing.
                TvLinkProtocol.ERROR_NOT_PAIRED -> if (sentFromTv) SendResult.Reason.NOT_PAIRED else SendResult.Reason.PAIRING_FAILED
                else -> SendResult.Reason.UNEXPECTED
            }
            SendResult.Failed(reason, deviceName)
        }

        private fun receive(): JSONObject {
            val line = TvLinkProtocol.readLine(input) ?: throw IOException("Connection closed")
            return runCatching { JSONObject(line) }.getOrElse { throw IOException("Malformed message") }
        }

        private fun send(message: JSONObject) {
            output.write((message.toString() + "\n").toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }
}
