package com.wwwescape.streamanything.data.history

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Its own DataStore file, separate from settings, so backup rules (which include only
 * `settings.preferences_pb`) keep it on this device. */
private val Context.historyDataStore by preferencesDataStore(name = "watch_history")

/** Persists [WatchHistory]. See there for what's stored — and what isn't. */
object WatchHistoryRepository {

    private val ENTRIES_KEY = stringPreferencesKey("entries")

    /** Writes outlive the player screen that triggers them (the last save happens as it
     * closes), so they run in their own scope rather than a ViewModel's. */
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun entriesFlow(context: Context): Flow<List<WatchEntry>> =
        context.historyDataStore.data.map { WatchHistory.decode(it[ENTRIES_KEY]) }

    suspend fun resumePositionMs(context: Context, videoKey: String): Long? =
        WatchHistory.resumePositionMs(entriesFlow(context).first(), WatchHistory.idFor(videoKey))

    /** @param link stored only when non-null — i.e. while "Show history" is on. */
    fun record(context: Context, videoKey: String, positionMs: Long, durationMs: Long, link: String?) {
        val entry = WatchEntry(WatchHistory.idFor(videoKey), positionMs, durationMs, System.currentTimeMillis(), link)
        update(context) { WatchHistory.record(it, entry) }
    }

    /** Keeps every resume position but forgets which links they belong to. */
    fun stripLinks(context: Context) = update(context, WatchHistory::withoutLinks)

    fun clear(context: Context) = update(context) { emptyList() }

    /** One video, from Recent history's delete button. */
    fun remove(context: Context, id: String) = update(context) { WatchHistory.remove(it, id) }

    private fun update(context: Context, transform: (List<WatchEntry>) -> List<WatchEntry>) {
        val app = context.applicationContext
        writeScope.launch {
            app.historyDataStore.edit { prefs ->
                val updated = transform(WatchHistory.decode(prefs[ENTRIES_KEY]))
                if (updated.isEmpty()) prefs.remove(ENTRIES_KEY) else prefs[ENTRIES_KEY] = WatchHistory.encode(updated)
            }
        }
    }
}
