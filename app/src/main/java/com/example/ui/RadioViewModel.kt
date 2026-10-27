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
    val dlnaManager = app.dlnaManager

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
    val dlnaVolume: StateFlow<Int> = dlnaManager.volume

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
    val skipStreamReload: StateFlow<Boolean> = preferences.skipStreamReload
    val artworkResolution: StateFlow<com.example.data.ArtworkResolution> = preferences.artworkResolution
    val totalListeningSeconds: StateFlow<Long> = preferences.totalListeningSeconds

    fun resetListeningTime() {
        preferences.resetListeningTime()
    }

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

    fun setSkipStreamReload(enabled: Boolean) {
        preferences.setSkipStreamReload(enabled)
    }

    fun setArtworkResolution(resolution: com.example.data.ArtworkResolution) {
        preferences.setArtworkResolution(resolution)
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

    val isDlnaActive: Boolean
        get() = dlnaManager.isDlnaActive

    fun togglePlayPause() {
        val currentSelected = selectedStation.value
        val activePlaying = playerState.value.currentStation

        if (activePlaying != null || dlnaManager.isDlnaActive) {
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
            val resolvedUrl = withContext(Dispatchers.IO) {
                StreamUrlResolver.resolveStreamUrl(station.streamUrl)
            }

            // Extraction prioritaire des métadonnées du flux en direct (titre et artiste)
            val streamMeta = withContext(Dispatchers.IO) {
                try {
                    kotlinx.coroutines.withTimeoutOrNull(1200) {
                        com.example.service.IcyMetadataExtractor.fetchStreamMetadata(resolvedUrl)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            val stateTrack = playerState.value.currentTrackTitle.takeIf { it.isNotBlank() }
            val stateArtist = playerState.value.currentTrackArtist.takeIf { it.isNotBlank() }

            val rawStreamTitle = streamMeta?.title?.trim().orEmpty()
            val (parsedArtist, parsedTrack) = if (rawStreamTitle.isNotBlank()) {
                com.example.service.IcyMetadataExtractor.parseArtistAndTitle(rawStreamTitle)
            } else {
                Pair(stateArtist ?: "", stateTrack ?: "")
            }

            val finalTrack = if (parsedTrack.isNotBlank()) parsedTrack else (stateTrack ?: "")
            val finalArtist = if (parsedArtist.isNotBlank()) parsedArtist else (stateArtist ?: "")

            val dlnaStreamTitle = if (finalTrack.isNotBlank()) {
                if (finalArtist.isNotBlank()) "$finalArtist - $finalTrack" else finalTrack
            } else {
                station.name
            }

            // Mute / pause local phone player first
            RadioPlaybackService.sendAction(getApplication(), RadioPlaybackService.ACTION_PAUSE)

            val success = dlnaManager.castStream(
                device = device,
                streamUrl = resolvedUrl,
                title = dlnaStreamTitle,
                artist = finalArtist,
                stationName = station.name,
                artworkUrl = playerState.value.artworkUrl
            )
            if (success) {
                // Synchronize service with active DLNA playback
                RadioPlaybackService.sendAction(getApplication(), RadioPlaybackService.ACTION_SYNC_DLNA)
            }
            onComplete(success)
        }
    }

    fun setDlnaVolume(vol: Int) {
        dlnaManager.setVolumeAsync(vol)
    }

    fun adjustDlnaVolume(delta: Int) {
        dlnaManager.adjustVolume(delta)
    }

    fun disconnectDlna() {
        viewModelScope.launch {
            val wasPlaying = playerState.value.status is PlaybackStatus.Playing
            val currentStation = playerState.value.currentStation ?: selectedStation.value
            dlnaManager.stopCasting()
            if (wasPlaying && currentStation != null) {
                RadioPlaybackService.playStation(getApplication(), currentStation)
            } else {
                RadioPlaybackService.sendAction(getApplication(), RadioPlaybackService.ACTION_PAUSE)
            }
        }
    }

    suspend fun exportDataJson(): String = withContext(Dispatchers.IO) {
        val stations = repository.getAllStationsSync().map {
            com.example.data.ExportedStation(
                name = it.name,
                streamUrl = it.streamUrl,
                changeSongUrl = it.changeSongUrl
            )
        }
        val backup = com.example.data.BackupData(
            totalListeningSeconds = preferences.totalListeningSeconds.value,
            stations = stations
        )
        backup.toJsonString()
    }

    suspend fun importData(
        backupData: com.example.data.BackupData,
        replaceExisting: Boolean
    ) = withContext(Dispatchers.IO) {
        val stationsToInsert = backupData.stations.map {
            RadioStation(
                name = it.name,
                streamUrl = it.streamUrl,
                changeSongUrl = it.changeSongUrl
            )
        }

        if (replaceExisting) {
            repository.replaceAllStations(stationsToInsert)
            preferences.setTotalListeningSeconds(backupData.totalListeningSeconds)
        } else {
            repository.mergeStations(stationsToInsert)
            if (backupData.totalListeningSeconds > 0) {
                val updatedDuration = maxOf(preferences.totalListeningSeconds.value, backupData.totalListeningSeconds)
                preferences.setTotalListeningSeconds(updatedDuration)
            }
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
                .header("User-Agent", "Ocarina/1.0 (VLC Compatible)")
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
