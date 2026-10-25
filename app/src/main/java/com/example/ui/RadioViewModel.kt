package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.RadioApp
import com.example.data.AppLanguage
import com.example.data.RadioStation
import com.example.data.ThemeMode
import com.example.service.PlaybackStatus
import com.example.service.PlayerState
import com.example.service.RadioPlaybackService
import com.example.service.StreamUrlResolver
import com.example.util.ShortcutHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as RadioApp
    private val repository = app.repository
    private val preferences = app.preferences
    val dlnaManager = com.example.dlna.DlnaManager(application)

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val dlnaDevices: StateFlow<List<com.example.dlna.DlnaDevice>> = dlnaManager.devices
    val isDlnaScanning: StateFlow<Boolean> = dlnaManager.isScanning
    val activeDlnaDevice: StateFlow<com.example.dlna.DlnaDevice?> = dlnaManager.activeDevice
    val isDlnaCasting: StateFlow<Boolean> = dlnaManager.isCasting
    val dlnaError: StateFlow<String?> = dlnaManager.lastError

    val stations: StateFlow<List<RadioStation>> = repository.allStations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val selectedStation: StateFlow<RadioStation?> = repository.selectedStation
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val playerState: StateFlow<PlayerState> = RadioPlaybackService.playerState

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
    val language: StateFlow<AppLanguage> = preferences.language

    init {
        // Automatically sync launcher app shortcuts whenever stations are modified
        viewModelScope.launch {
            repository.allStations.collect { list ->
                ShortcutHelper.updateDynamicShortcuts(getApplication(), list)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        preferences.setThemeMode(mode)
    }

    fun setLanguage(lang: AppLanguage) {
        preferences.setLanguage(lang)
    }

    fun addStation(name: String, streamUrl: String, changeSongUrl: String, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val station = RadioStation(
                name = name.trim(),
                streamUrl = streamUrl.trim(),
                changeSongUrl = changeSongUrl.trim()
            )
            val newId = repository.insertStation(station)
            onResult(newId)
        }
    }

    fun updateStation(station: RadioStation) {
        viewModelScope.launch {
            repository.updateStation(station)
        }
    }

    fun deleteStation(station: RadioStation) {
        viewModelScope.launch {
            if (playerState.value.currentStation?.id == station.id) {
                stopPlayback()
            }
            repository.deleteStation(station)
        }
    }

    fun selectAndPlayStation(station: RadioStation) {
        viewModelScope.launch {
            repository.selectStation(station.id)
            RadioPlaybackService.playStation(getApplication(), station)
        }
    }

    fun playStationById(stationId: Long) {
        viewModelScope.launch {
            val station = repository.getStationById(stationId)
            if (station != null) {
                selectAndPlayStation(station)
            }
        }
    }

    fun pinStationShortcut(station: RadioStation): Boolean {
        return ShortcutHelper.pinStationShortcut(getApplication(), station)
    }

    fun togglePlayPause() {
        val currentSelected = selectedStation.value
        val activePlaying = playerState.value.currentStation

        if (activePlaying != null) {
            RadioPlaybackService.sendAction(getApplication(), RadioPlaybackService.ACTION_TOGGLE)
        } else if (currentSelected != null) {
            RadioPlaybackService.playStation(getApplication(), currentSelected)
        }
    }

    fun nextSong() {
        RadioPlaybackService.sendAction(getApplication(), RadioPlaybackService.ACTION_NEXT)
    }

    fun stopPlayback() {
        RadioPlaybackService.sendAction(getApplication(), RadioPlaybackService.ACTION_STOP)
    }

    fun startDlnaScan() {
        dlnaManager.startDiscovery()
    }

    fun castToDevice(device: com.example.dlna.DlnaDevice, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val station = playerState.value.currentStation ?: selectedStation.value
            if (station == null) {
                onComplete(false)
                return@launch
            }
            val title = if (playerState.value.currentTrackTitle.isNotBlank()) {
                "${station.name} - ${playerState.value.currentTrackTitle}"
            } else {
                station.name
            }
            val resolvedUrl = withContext(Dispatchers.IO) {
                StreamUrlResolver.resolveStreamUrl(station.streamUrl)
            }
            val success = dlnaManager.castStream(device, resolvedUrl, title)
            if (success) {
                // Pause phone local playback to avoid echo with network speaker/TV
                RadioPlaybackService.sendAction(getApplication(), RadioPlaybackService.ACTION_PAUSE)
            }
            onComplete(success)
        }
    }

    fun disconnectDlna() {
        viewModelScope.launch {
            dlnaManager.stopCasting()
        }
    }

    suspend fun testStreamUrl(rawUrl: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return@withContext Pair(false, "L'URL ne peut pas être vide")

        val effective = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            if (trimmed.contains(".") && !trimmed.contains(" ")) "http://$trimmed" else return@withContext Pair(false, "URL invalide (protocole http:// ou https:// requis)")
        } else trimmed

        try {
            val resolved = StreamUrlResolver.resolveStreamUrl(effective)

            val request = Request.Builder()
                .url(resolved)
                .header("User-Agent", "OndeLive/1.0 (VLC Compatible)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val contentType = response.header("Content-Type") ?: "audio"
                    val info = if (resolved != trimmed) "Playlist résolue ($contentType)" else "Flux accessible ($contentType)"
                    return@withContext Pair(true, info)
                } else {
                    return@withContext Pair(false, "Code HTTP: ${response.code}")
                }
            }
        } catch (e: Exception) {
            return@withContext Pair(false, "Erreur: ${e.localizedMessage ?: "serveur injoignable"}")
        }
    }
}
