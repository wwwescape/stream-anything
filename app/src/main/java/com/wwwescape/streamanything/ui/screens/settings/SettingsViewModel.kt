package com.wwwescape.streamanything.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.streamanything.data.history.WatchHistoryRepository
import com.wwwescape.streamanything.data.settings.AppSettings
import com.wwwescape.streamanything.data.settings.ColorTheme
import com.wwwescape.streamanything.data.settings.SeekIncrement
import com.wwwescape.streamanything.data.settings.SettingsRepository
import com.wwwescape.streamanything.data.settings.ThemeContrast
import com.wwwescape.streamanything.data.settings.ThemeMode
import com.wwwescape.streamanything.data.settings.VideoScaling
import com.wwwescape.streamanything.data.tvlink.PairedDevice
import com.wwwescape.streamanything.data.tvlink.TvLinkStore
import com.wwwescape.streamanything.ui.tvsend.LinkSendController
import com.wwwescape.streamanything.util.isTelevision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    val settings: StateFlow<AppSettings> = SettingsRepository.settingsFlow(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** A TV lists the phones allowed to send to it; a phone lists the TVs it can send to. */
    private val pairedRole = if (application.isTelevision()) TvLinkStore.Role.PHONE else TvLinkStore.Role.TV

    val pairedDevices: StateFlow<List<PairedDevice>> = TvLinkStore.pairedFlow(application, pairedRole)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Phone only: "Pair with a TV" is open, so look for TVs on the network. */
    val pairDialogOpen = MutableStateFlow(false)

    /** Phone only: pairing from Experimental, by a TV found on the network or a typed address. */
    val linkSend = LinkSendController(application, viewModelScope, discoverAlways = pairDialogOpen)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { SettingsRepository.setThemeMode(getApplication(), mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { SettingsRepository.setDynamicColor(getApplication(), enabled) }
    }

    fun setColorTheme(theme: ColorTheme) {
        viewModelScope.launch { SettingsRepository.setColorTheme(getApplication(), theme) }
    }

    fun setThemeContrast(contrast: ThemeContrast) {
        viewModelScope.launch { SettingsRepository.setThemeContrast(getApplication(), contrast) }
    }

    fun setPureDark(enabled: Boolean) {
        viewModelScope.launch { SettingsRepository.setPureDark(getApplication(), enabled) }
    }

    fun setAbsoluteDark(enabled: Boolean) {
        viewModelScope.launch { SettingsRepository.setAbsoluteDark(getApplication(), enabled) }
    }

    fun setSeekIncrement(increment: SeekIncrement) {
        viewModelScope.launch { SettingsRepository.setSeekIncrement(getApplication(), increment) }
    }

    fun setVideoScaling(scaling: VideoScaling) {
        viewModelScope.launch { SettingsRepository.setVideoScaling(getApplication(), scaling) }
    }

    /** Turning resume off also forgets every saved position — off means nothing is kept. */
    fun setResumePlayback(enabled: Boolean) {
        viewModelScope.launch { SettingsRepository.setResumePlayback(getApplication(), enabled) }
        if (!enabled) WatchHistoryRepository.clear(getApplication())
    }

    /** Turning it off strips the stored links straight away; resume positions are kept. */
    fun setShowHistory(enabled: Boolean) {
        viewModelScope.launch { SettingsRepository.setShowHistory(getApplication(), enabled) }
        if (!enabled) WatchHistoryRepository.stripLinks(getApplication())
    }

    fun setPlayOnTv(enabled: Boolean) {
        viewModelScope.launch { SettingsRepository.setPlayOnTv(getApplication(), enabled) }
    }

    fun forgetPairedDevice(id: String) {
        viewModelScope.launch { TvLinkStore.forget(getApplication(), pairedRole, id) }
    }

    fun clearWatchHistory() {
        WatchHistoryRepository.clear(getApplication())
    }
}
