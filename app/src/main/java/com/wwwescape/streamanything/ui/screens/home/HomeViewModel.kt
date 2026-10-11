package com.wwwescape.streamanything.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.streamanything.data.history.WatchEntry
import com.wwwescape.streamanything.data.history.WatchHistory
import com.wwwescape.streamanything.data.history.WatchHistoryRepository
import com.wwwescape.streamanything.data.settings.SettingsRepository
import com.wwwescape.streamanything.data.streams.StreamResolvers
import com.wwwescape.streamanything.data.tvlink.TvLinkStore
import com.wwwescape.streamanything.ui.tvsend.LinkSendController
import com.wwwescape.streamanything.util.isTelevision
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    /** Recent history for the home screen — empty unless Settings → Experimental → Show
     * history (and Resume Playback, which records it) is on. Only observed while Home is
     * visible, so it costs nothing during playback. */
    val recentHistory: StateFlow<List<WatchEntry>> = combine(
        SettingsRepository.settingsFlow(application),
        WatchHistoryRepository.entriesFlow(application),
    ) { settings, entries ->
        if (settings.experimental.showHistory && settings.playback.resumePlayback) WatchHistory.recent(entries) else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Sending from Home to the other device: a phone sends to TVs (Play on TV), a TV to its
     * paired phones (Play on phone). Only with the option on, and devices are only looked for
     * while Home is visible, so the button can appear — discovery stops shortly after. */
    val linkSend = LinkSendController(
        application,
        viewModelScope,
        discoverAlways = SettingsRepository.settingsFlow(application).map { it.experimental.playOnTv },
    )

    /** Whether tapping a Recent history entry should ask "here or there?": only with a device
     * paired *and* reachable (found, or with a remembered address) — otherwise it just plays
     * here. */
    val offerOtherDeviceForHistory: StateFlow<Boolean> = combine(
        TvLinkStore.pairedFlow(application, if (application.isTelevision()) TvLinkStore.Role.PHONE else TvLinkStore.Role.TV),
        linkSend.devices,
    ) { paired, found -> paired.isNotEmpty() && found.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun removeFromHistory(entry: WatchEntry) {
        WatchHistoryRepository.remove(getApplication(), entry.id)
    }

    /** The link in the field, carrying on from where this device stopped (if it did). */
    fun playOnOtherDevice(link: String) {
        viewModelScope.launch { linkSend.request(link, savedPosition(link)) }
    }

    /** A Recent history entry: Resume carries on from its position, Rewatch (or one barely
     * started) starts over — the same as tapping it to play here. */
    fun playOnOtherDevice(entry: WatchEntry, link: String) {
        linkSend.request(link, if (entry.isResumable) entry.positionMs else 0L)
    }

    /** Where this device stopped in the video, so the other one carries on from there. */
    private suspend fun savedPosition(link: String): Long? {
        if (!SettingsRepository.settingsFlow(getApplication()).first().playback.resumePlayback) return null
        val key = StreamResolvers.videoKey(link) ?: return null
        return WatchHistoryRepository.resumePositionMs(getApplication(), key)
    }
}
