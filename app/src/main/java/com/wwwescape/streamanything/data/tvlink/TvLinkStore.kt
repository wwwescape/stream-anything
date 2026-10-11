package com.wwwescape.streamanything.data.tvlink

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** A phone paired with this TV, or a TV paired with this phone. Remembered by [id] — a random
 * ID the other app made up once — never by IP address, so router changes don't break it.
 * [address] ("192.168.1.42:47621") is only where it was last seen: tried when network
 * discovery doesn't find it, and refreshed every time the two talk. */
data class PairedDevice(val id: String, val name: String, val address: String? = null)

/** Its own DataStore file, separate from settings, so backup rules (which include only
 * `settings.preferences_pb`) keep pairings on this device. */
private val Context.tvLinkDataStore by preferencesDataStore(name = "tv_link")

/**
 * Persists Play on TV pairings. The list of paired devices (ID + name) lives in DataStore; each
 * pair's shared secret is imported into the Android Keystore as a non-exportable HMAC key, so
 * the secret itself is never stored in a file. Clearing the app's data or reinstalling drops
 * both, which is when re-pairing is needed.
 */
object TvLinkStore {

    /** Enough for a household; adding one more forgets the least recently paired. */
    const val MAX_PAIRED = 10

    private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    private val TVS_KEY = stringPreferencesKey("paired_tvs")
    private val PHONES_KEY = stringPreferencesKey("paired_phones")

    private const val KEYSTORE = "AndroidKeyStore"

    enum class Role(internal val listKey: Preferences.Key<String>, internal val aliasPrefix: String) {
        /** TVs this phone can send to. */
        TV(TVS_KEY, "tvlink.tv."),

        /** Phones allowed to send to this TV. */
        PHONE(PHONES_KEY, "tvlink.phone."),
    }

    /** This install's stable ID, created on first use. */
    suspend fun deviceId(context: Context): String {
        context.tvLinkDataStore.data.first()[DEVICE_ID_KEY]?.let { return it }
        var id = ""
        context.tvLinkDataStore.edit { prefs ->
            id = prefs[DEVICE_ID_KEY] ?: UUID.randomUUID().toString().also { prefs[DEVICE_ID_KEY] = it }
        }
        return id
    }

    /** The name shown on the other device: the one set in system settings ("Living Room TV"),
     * falling back to the model. */
    fun deviceName(context: Context): String =
        TvLinkProtocol.cleanName(Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME))
            ?: TvLinkProtocol.cleanName(Build.MODEL)
            ?: "Android"

    fun pairedFlow(context: Context, role: Role): Flow<List<PairedDevice>> =
        context.tvLinkDataStore.data.map { decode(it[role.listKey]) }

    suspend fun isPaired(context: Context, role: Role, id: String): Boolean =
        pairedFlow(context, role).first().any { it.id == id } && key(role, id) != null

    /** Stores (or replaces) a pairing — the secret goes into the Keystore, not the file. */
    suspend fun pair(context: Context, role: Role, device: PairedDevice, secret: ByteArray) {
        keyStore().setEntry(
            role.aliasPrefix + device.id,
            KeyStore.SecretKeyEntry(SecretKeySpec(secret, KeyProperties.KEY_ALGORITHM_HMAC_SHA256)),
            KeyProtection.Builder(KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY).build(),
        )
        var dropped = emptyList<PairedDevice>()
        context.tvLinkDataStore.edit { prefs ->
            val others = decode(prefs[role.listKey]).filter { it.id != device.id }
            // Re-pairing without an address keeps the one already known.
            val known = decode(prefs[role.listKey]).firstOrNull { it.id == device.id }?.address
            dropped = others.drop(MAX_PAIRED - 1)
            prefs[role.listKey] = encode(listOf(device.copy(address = device.address ?: known)) + others.take(MAX_PAIRED - 1))
        }
        dropped.forEach { deleteKey(role, it.id) }
    }

    /** Remembers where a paired device was just reached (or reached us from). */
    suspend fun updateAddress(context: Context, role: Role, id: String, address: InetSocketAddress) {
        // IPv4 only, like addresses typed by hand; home networks always have one.
        if (address.address !is Inet4Address) return
        val formatted = "${address.address.hostAddress}:${address.port}"
        context.tvLinkDataStore.edit { prefs ->
            val devices = decode(prefs[role.listKey])
            if (devices.none { it.id == id && it.address != formatted }) return@edit
            prefs[role.listKey] = encode(devices.map { if (it.id == id) it.copy(address = formatted) else it })
        }
    }

    suspend fun forget(context: Context, role: Role, id: String) {
        context.tvLinkDataStore.edit { prefs ->
            val remaining = decode(prefs[role.listKey]).filter { it.id != id }
            if (remaining.isEmpty()) prefs.remove(role.listKey) else prefs[role.listKey] = encode(remaining)
        }
        deleteKey(role, id)
    }

    /** HMAC-SHA256 of [message] with the pair's Keystore key; `null` if not paired. */
    fun mac(role: Role, id: String, message: ByteArray): ByteArray? {
        val key = key(role, id) ?: return null
        return Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256).run {
            init(key)
            doFinal(message)
        }
    }

    private fun key(role: Role, id: String): SecretKey? =
        runCatching { keyStore().getKey(role.aliasPrefix + id, null) as? SecretKey }.getOrNull()

    private fun deleteKey(role: Role, id: String) {
        runCatching { keyStore().deleteEntry(role.aliasPrefix + id) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    // One device per line: id, tab, base64url name, and (tab) its last address when known. A
    // corrupt line is skipped; an address that doesn't parse is dropped.

    private fun encode(devices: List<PairedDevice>): String =
        devices.joinToString("\n") { device ->
            listOfNotNull(
                device.id,
                Base64.getUrlEncoder().withoutPadding().encodeToString(device.name.toByteArray()),
                device.address,
            ).joinToString("\t")
        }

    private fun decode(text: String?): List<PairedDevice> =
        text.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size !in 2..3 || !TvLinkProtocol.isValidDeviceId(parts[0])) return@mapNotNull null
            val name = runCatching { String(Base64.getUrlDecoder().decode(parts[1])) }.getOrNull() ?: return@mapNotNull null
            PairedDevice(parts[0], name, parts.getOrNull(2)?.takeIf { TvLinkProtocol.parseAddress(it) != null })
        }.toList()
}
