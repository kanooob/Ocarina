package com.example.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.R
import com.example.RadioApp
import com.example.data.RadioStation
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RadioPlaybackService : MediaSessionService(), Player.Listener {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private lateinit var networkMonitor: NetworkMonitor

    private var reconnectJob: Job? = null
    private var metadataJob: Job? = null
    private var nextSongJob: Job? = null
    private var artworkJob: Job? = null
    private var currentArtworkBitmap: android.graphics.Bitmap? = null
    private var hasPlayedSuccessfully = false
    private var activeStreamUrl: String? = null
    private var notifyMetadataListener: ((MediaMetadata) -> Unit)? = null

    private val dlnaManager: com.example.dlna.DlnaManager
        get() = (application as RadioApp).dlnaManager

    val isDlnaActive: Boolean
        get() = dlnaManager.isDlnaActive

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()

        networkMonitor = NetworkMonitor(
            context = this,
            onNetworkAvailable = {
                serviceScope.launch(Dispatchers.Main) {
                    handleNetworkRestored()
                }
            },
            onNetworkLost = {
                serviceScope.launch(Dispatchers.Main) {
                    handleNetworkLost()
                }
            }
        )
        networkMonitor.startMonitoring()

        // Enable Icecast and Shoutcast metadata parsing in ExoPlayer HTTP requests
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Ocarina/1.0 (Android; VLC Compatible)")
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(10000)
            .setAllowCrossProtocolRedirects(true)

        val loadErrorHandlingPolicy = DefaultLoadErrorHandlingPolicy(/* minimumLoadableRetryCount= */ 1)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)
            .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus= */ true
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(this)
        exoPlayer = player

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextCustomCommand = SessionCommand(CUSTOM_ACTION_NEXT, Bundle.EMPTY)
        val nextButton = CommandButton.Builder()
            .setDisplayName("Passer / Next")
            .setIconResId(android.R.drawable.ic_media_next)
            .setSessionCommand(nextCustomCommand)
            .build()

        val forwardingPlayer = object : ForwardingPlayer(player) {
            private val playerListeners = java.util.concurrent.CopyOnWriteArraySet<Player.Listener>()

            override fun addListener(listener: Player.Listener) {
                playerListeners.add(listener)
                super.addListener(listener)
            }

            override fun removeListener(listener: Player.Listener) {
                playerListeners.remove(listener)
                super.removeListener(listener)
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return command == Player.COMMAND_PLAY_PAUSE ||
                       command == Player.COMMAND_SEEK_TO_NEXT ||
                       command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                       super.isCommandAvailable(command)
            }

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .build()
            }

            override fun play() {
                if (isDlnaActive) {
                    onPlay()
                } else {
                    super.play()
                }
            }

            override fun pause() {
                if (isDlnaActive) {
                    onPause()
                } else {
                    super.pause()
                }
            }

            override fun stop() {
                if (isDlnaActive) {
                    onStop()
                } else {
                    super.stop()
                }
            }

            override fun isPlaying(): Boolean {
                if (isDlnaActive) {
                    return _playerState.value.status is PlaybackStatus.Playing
                }
                return super.isPlaying()
            }

            override fun getPlayWhenReady(): Boolean {
                if (isDlnaActive) {
                    return _playerState.value.status is PlaybackStatus.Playing
                }
                return super.getPlayWhenReady()
            }

            override fun getPlaybackState(): Int {
                if (isDlnaActive) {
                    return when (_playerState.value.status) {
                        is PlaybackStatus.Buffering -> Player.STATE_BUFFERING
                        is PlaybackStatus.Playing, is PlaybackStatus.Paused -> Player.STATE_READY
                        is PlaybackStatus.Idle, is PlaybackStatus.NoNetwork, is PlaybackStatus.Error -> Player.STATE_IDLE
                        else -> Player.STATE_READY
                    }
                }
                return super.getPlaybackState()
            }

            override fun seekToNext() {
                triggerNextSong()
            }

            override fun seekToNextMediaItem() {
                triggerNextSong()
            }

            override fun getMediaMetadata(): MediaMetadata {
                val state = _playerState.value
                val stationName = state.currentStation?.name ?: getString(R.string.app_name)
                val trackTitle = state.currentTrackTitle
                val trackArtist = state.currentTrackArtist

                val displayTitle = if (trackTitle.isNotBlank()) trackTitle else stationName
                val displayArtist = if (trackArtist.isNotBlank()) trackArtist else stationName
                val artUri = state.artworkUrl?.let { Uri.parse(it) }

                return MediaMetadata.Builder()
                    .setTitle(displayTitle)
                    .setDisplayTitle(displayTitle)
                    .setArtist(displayArtist)
                    .setStation(stationName)
                    .apply {
                        if (artUri != null) {
                            setArtworkUri(artUri)
                        }
                    }
                    .build()
            }

            fun notifyListenersMetadata(newMetadata: MediaMetadata) {
                for (listener in playerListeners) {
                    try {
                        listener.onMediaMetadataChanged(newMetadata)
                        listener.onPlaylistMetadataChanged(newMetadata)
                    } catch (e: Exception) {
                        Log.d(TAG, "Listener metadata dispatch error: ${e.message}")
                    }
                }
            }
        }

        notifyMetadataListener = { meta ->
            forwardingPlayer.notifyListenersMetadata(meta)
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCustomLayout(listOf(nextButton))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(nextCustomCommand)
                        .build()
                    val availablePlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .add(Player.COMMAND_STOP)
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .build()

                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(availableSessionCommands)
                        .setAvailablePlayerCommands(availablePlayerCommands)
                        .setCustomLayout(listOf(nextButton))
                        .build()
                }

                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int
                ): Int {
                    if (isDlnaActive) {
                        when (playerCommand) {
                            Player.COMMAND_PLAY_PAUSE -> {
                                if (_playerState.value.status is PlaybackStatus.Playing) {
                                    onPause()
                                } else {
                                    onPlay()
                                }
                                return SessionResult.RESULT_INFO_SKIPPED
                            }
                            Player.COMMAND_STOP -> {
                                onStop()
                                return SessionResult.RESULT_INFO_SKIPPED
                            }
                            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                                triggerNextSong()
                                return SessionResult.RESULT_INFO_SKIPPED
                            }
                        }
                    }
                    if (playerCommand == Player.COMMAND_SEEK_TO_NEXT || playerCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
                        triggerNextSong()
                        return SessionResult.RESULT_INFO_SKIPPED
                    }
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }

                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    if (keyEvent != null && keyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                onPlay()
                                return true
                            }
                            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                onPause()
                                return true
                            }
                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                togglePlayback()
                                return true
                            }
                            android.view.KeyEvent.KEYCODE_MEDIA_STOP -> {
                                onStop()
                                return true
                            }
                            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                triggerNextSong()
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(session, controller, intent)
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == CUSTOM_ACTION_NEXT) {
                        triggerNextSong()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()

        // Observer for user artwork resolution setting changes
        serviceScope.launch {
            com.example.data.AppPreferences.getInstance(this@RadioPlaybackService)
                .artworkResolution.collect { res ->
                    val currentTrack = _playerState.value.currentTrackTitle
                    val currentArtist = _playerState.value.currentTrackArtist
                    if (currentTrack.isNotBlank()) {
                        val stationName = _playerState.value.currentStation?.name ?: getString(R.string.app_name)
                        val effectiveArtist = if (currentArtist.isNotBlank()) currentArtist else stationName
                        val searchQuery = if (currentArtist.isNotBlank()) "$currentArtist $currentTrack" else currentTrack
                        fetchArtworkForTrack(searchQuery, currentTrack, effectiveArtist, res)
                    }
                }
        }

        // Keep local player silenced whenever DLNA casting is active
        serviceScope.launch {
            dlnaManager.isCasting.collect { casting ->
                if (casting && exoPlayer?.isPlaying == true) {
                    exoPlayer?.stop()
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null) {
            when (action) {
                ACTION_PLAY -> onPlay()
                ACTION_PAUSE -> onPause()
                ACTION_TOGGLE -> togglePlayback()
                ACTION_NEXT -> triggerNextSong()
                ACTION_STOP -> onStop()
                ACTION_SYNC_DLNA -> handleSyncDlna()
                ACTION_PLAY_STATION -> {
                    val stationId = intent.getLongExtra(EXTRA_STATION_ID, -1L)
                    val stationName = intent.getStringExtra(EXTRA_STATION_NAME) ?: ""
                    val streamUrl = intent.getStringExtra(EXTRA_STATION_STREAM_URL) ?: ""
                    val changeSongUrl = intent.getStringExtra(EXTRA_STATION_CHANGE_SONG_URL) ?: ""

                    if (streamUrl.isNotBlank()) {
                        val station = RadioStation(
                            id = stationId,
                            name = stationName,
                            streamUrl = streamUrl,
                            changeSongUrl = changeSongUrl,
                            isSelected = true
                        )
                        playStation(station)
                    }
                }
            }
        }
        updateForegroundNotification()
        return super.onStartCommand(intent, flags, startId)
    }

    fun playStation(station: RadioStation) {
        cancelReconnect()
        cancelMetadataPolling()
        artworkJob?.cancel()
        currentArtworkBitmap = null
        hasPlayedSuccessfully = false

        val rawUrl = station.streamUrl.trim()
        if (rawUrl.isBlank()) {
            _playerState.update {
                it.copy(
                    currentStation = station,
                    status = PlaybackStatus.Error("URL vide"),
                    message = "L'adresse du flux est vide."
                )
            }
            updateForegroundNotification()
            return
        }

        // Validate and auto-prefix missing protocol if it's a domain/path
        val effectiveUrl = if (!rawUrl.startsWith("http://", ignoreCase = true) && !rawUrl.startsWith("https://", ignoreCase = true)) {
            if (rawUrl.contains(".") && !rawUrl.contains(" ")) {
                "http://$rawUrl"
            } else {
                _playerState.update {
                    it.copy(
                        currentStation = station,
                        status = PlaybackStatus.Error("URL invalide : $rawUrl"),
                        message = "L'URL doit commencer par http:// ou https://"
                    )
                }
                updateForegroundNotification()
                return
            }
        } else {
            rawUrl
        }

        val effectiveStation = if (effectiveUrl != station.streamUrl) station.copy(streamUrl = effectiveUrl) else station

        // Verify network availability before trying to connect
        if (!networkMonitor.isConnected()) {
            exoPlayer?.stop()
            _playerState.update {
                it.copy(
                    currentStation = effectiveStation,
                    status = PlaybackStatus.NoNetwork,
                    currentTrackTitle = "",
                    artworkUrl = null,
                    reconnectFailed = false,
                    message = "Aucune connexion Internet. Diffusion mise en attente du réseau."
                )
            }
            updateForegroundNotification()
            return
        }

        if (isDlnaActive) {
            val activeDev = dlnaManager.activeDevice.value!!
            // Silence local phone speaker immediately
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()

            _playerState.update {
                it.copy(
                    currentStation = effectiveStation,
                    status = PlaybackStatus.Buffering,
                    currentTrackTitle = "",
                    currentTrackArtist = "",
                    artworkUrl = null,
                    reconnectFailed = false,
                    message = "Diffusion vers ${activeDev.name}…"
                )
            }
            updateForegroundNotification()

            serviceScope.launch {
                try {
                    val resolvedUrl = StreamUrlResolver.resolveStreamUrl(effectiveStation.streamUrl)
                    activeStreamUrl = resolvedUrl
                    // Conserver l'URI du renderer DLNA distant lors du changement de source
                    // et transmettre la nouvelle URL directement au renderer distant sans couper la connexion UPnP
                    val success = dlnaManager.changeSource(resolvedUrl, effectiveStation.name)
                    if (success) {
                        hasPlayedSuccessfully = true
                        _playerState.update {
                            it.copy(
                                status = PlaybackStatus.Playing,
                                message = null
                            )
                        }
                        updateForegroundNotification()
                        startMetadataPolling(resolvedUrl)
                    } else {
                        _playerState.update {
                            it.copy(
                                status = PlaybackStatus.Error("Échec de diffusion sur ${activeDev.name}"),
                                message = dlnaManager.lastError.value ?: "Échec de connexion DLNA"
                            )
                        }
                        updateForegroundNotification()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to cast station to DLNA: ${e.message}")
                    _playerState.update {
                        it.copy(status = PlaybackStatus.Error("Erreur DLNA: ${e.message}"))
                    }
                    updateForegroundNotification()
                }
            }
            return
        }

        _playerState.update {
            it.copy(
                currentStation = effectiveStation,
                status = PlaybackStatus.Buffering,
                currentTrackTitle = "",
                currentTrackArtist = "",
                artworkUrl = null,
                reconnectFailed = false,
                message = null
            )
        }
        updateForegroundNotification()

        serviceScope.launch {
            try {
                // Smart VLC-like playlist resolution (.pls, .m3u, direct streams, hls)
                val resolvedUrl = StreamUrlResolver.resolveStreamUrl(effectiveStation.streamUrl)
                prepareAndPlayExoPlayer(resolvedUrl, effectiveStation.name)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve or play station: ${e.message}")
                handleStreamInterrupted("Flux temporairement indisponible")
            }
        }
    }

    private fun handleSyncDlna() {
        exoPlayer?.stop()
        val activeDev = dlnaManager.activeDevice.value
        val station = _playerState.value.currentStation
        val effectiveUrl = activeStreamUrl ?: station?.streamUrl
        activeStreamUrl = effectiveUrl

        _playerState.update {
            it.copy(
                currentStation = station ?: it.currentStation,
                status = PlaybackStatus.Playing,
                message = if (activeDev != null) "Diffusé sur ${activeDev.name}" else null
            )
        }
        updateForegroundNotification()
        if (!effectiveUrl.isNullOrBlank()) {
            startMetadataPolling(effectiveUrl)
        }
    }

    private fun prepareAndPlayExoPlayer(streamUrl: String, stationName: String) {
        val player = exoPlayer ?: return

        // Safety check: ensure streamUrl has a valid http/https scheme before passing to ExoPlayer
        val parsedUri = try {
            val uri = Uri.parse(streamUrl)
            val scheme = uri.scheme
            if (scheme.isNullOrBlank() || (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true))) {
                throw java.net.MalformedURLException("Protocole http/https manquant pour: $streamUrl")
            }
            uri
        } catch (e: Exception) {
            _playerState.update {
                it.copy(
                    status = PlaybackStatus.Error("URL invalide : $streamUrl"),
                    message = "L'adresse du flux n'a pas de protocole valide."
                )
            }
            updateForegroundNotification()
            return
        }

        activeStreamUrl = streamUrl

        try {
            val currentTrack = _playerState.value.currentTrackTitle
            val currentArtist = _playerState.value.currentTrackArtist
            val effectiveTitle = if (currentTrack.isNotBlank()) currentTrack else stationName
            val effectiveArtist = if (currentArtist.isNotBlank()) currentArtist else stationName

            val initialMetadata = MediaMetadata.Builder()
                .setTitle(effectiveTitle)
                .setDisplayTitle(effectiveTitle)
                .setArtist(effectiveArtist)
                .setStation(stationName)
                .build()

            // CRITICAL: Do NOT set static mediaMetadata on MediaItem.
            // When MediaItem has static mediaMetadata, ExoPlayer locks those values and ignores
            // dynamic updates from playlistMetadata. Leaving mediaItem free allows dynamic metadata
            // from stream and polling to propagate immediately to MediaSession and notification.
            val mediaItem = MediaItem.Builder()
                .setUri(parsedUri)
                .build()

            player.setMediaItem(mediaItem)
            player.playlistMetadata = initialMetadata
            notifyMetadataListener?.invoke(initialMetadata)
            player.prepare()
            player.playWhenReady = true

            _playerState.update { it.copy(status = PlaybackStatus.Buffering) }
            updateForegroundNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing ExoPlayer: ${e.message}", e)
            handleStreamInterrupted("Impossible d'initialiser le flux")
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                _playerState.update {
                    if (it.status !is PlaybackStatus.Reconnecting) {
                        it.copy(status = PlaybackStatus.Buffering)
                    } else it
                }
                updateForegroundNotification()
            }
            Player.STATE_READY -> {
                hasPlayedSuccessfully = true
                cancelReconnect()
                val isPlaying = exoPlayer?.playWhenReady == true
                _playerState.update {
                    it.copy(
                        status = if (isPlaying) PlaybackStatus.Playing else PlaybackStatus.Paused,
                        message = null
                    )
                }
                updateForegroundNotification()
                val currentUrl = activeStreamUrl
                if (isPlaying && !currentUrl.isNullOrBlank()) {
                    startMetadataPolling(currentUrl)
                }
            }
            Player.STATE_ENDED -> {
                handleStreamInterrupted("Flux terminé ou interrompu")
            }
            Player.STATE_IDLE -> {
                // Player idle
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            hasPlayedSuccessfully = true
            cancelReconnect()
            _playerState.update { it.copy(status = PlaybackStatus.Playing) }
            val currentUrl = activeStreamUrl
            if (!currentUrl.isNullOrBlank()) {
                startMetadataPolling(currentUrl)
            }
        } else {
            cancelMetadataPolling()
            if (_playerState.value.status is PlaybackStatus.Playing) {
                _playerState.update { it.copy(status = PlaybackStatus.Paused) }
            }
        }
        updateForegroundNotification()
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        // Built-in Icecast/Shoutcast ICY metadata extraction from stream
        val streamTitle = mediaMetadata.title?.toString()
        if (!streamTitle.isNullOrBlank() && streamTitle != _playerState.value.currentStation?.name) {
            updateTrackTitle(streamTitle)
        }
    }

    private fun updateTrackTitle(rawTitle: String) {
        val (parsedArtist, parsedTitle) = IcyMetadataExtractor.parseArtistAndTitle(rawTitle)
        val cleanTitle = if (parsedTitle.isNotBlank()) parsedTitle else IcyMetadataExtractor.sanitizeTitle(rawTitle)
        val cleanArtist = parsedArtist.trim()

        if (cleanTitle.isBlank() || 
            (cleanTitle == _playerState.value.currentTrackTitle && cleanArtist == _playerState.value.currentTrackArtist)) {
            return
        }

        val stationName = _playerState.value.currentStation?.name ?: getString(R.string.app_name)
        val effectiveArtist = if (cleanArtist.isNotBlank()) cleanArtist else stationName

        _playerState.update { 
            it.copy(
                currentTrackTitle = cleanTitle,
                currentTrackArtist = cleanArtist
            ) 
        }

        val updatedMetadata = MediaMetadata.Builder()
            .setTitle(cleanTitle)
            .setDisplayTitle(cleanTitle)
            .setArtist(effectiveArtist)
            .setStation(stationName)
            .build()
        exoPlayer?.playlistMetadata = updatedMetadata
        notifyMetadataListener?.invoke(updatedMetadata)
        updateForegroundNotification()

        val searchQuery = if (cleanArtist.isNotBlank()) "$cleanArtist $cleanTitle" else cleanTitle
        val resolution = com.example.data.AppPreferences.getInstance(this).artworkResolution.value
        fetchArtworkForTrack(searchQuery, cleanTitle, effectiveArtist, resolution)
    }

    private fun fetchArtworkForTrack(
        searchQuery: String,
        cleanTitle: String,
        effectiveArtist: String,
        resolution: com.example.data.ArtworkResolution
    ) {
        artworkJob?.cancel()
        val stationName = _playerState.value.currentStation?.name ?: getString(R.string.app_name)

        if (resolution == com.example.data.ArtworkResolution.DISABLED) {
            currentArtworkBitmap = null
            _playerState.update { it.copy(artworkUrl = null) }
            val updatedMetadata = MediaMetadata.Builder()
                .setTitle(cleanTitle)
                .setDisplayTitle(cleanTitle)
                .setArtist(effectiveArtist)
                .setStation(stationName)
                .build()
            exoPlayer?.playlistMetadata = updatedMetadata
            notifyMetadataListener?.invoke(updatedMetadata)
            updateForegroundNotification()
            return
        }

        artworkJob = serviceScope.launch {
            val artUrl = ArtworkRepository.findArtworkUrl(searchQuery, resolution)
            _playerState.update { it.copy(artworkUrl = artUrl) }

            if (!artUrl.isNullOrBlank()) {
                val bitmap = ArtworkRepository.fetchArtworkBitmap(artUrl)
                currentArtworkBitmap = bitmap
                val updatedMetadata = MediaMetadata.Builder()
                    .setTitle(cleanTitle)
                    .setDisplayTitle(cleanTitle)
                    .setArtist(effectiveArtist)
                    .setStation(stationName)
                    .setArtworkUri(Uri.parse(artUrl))
                    .build()
                exoPlayer?.playlistMetadata = updatedMetadata
                notifyMetadataListener?.invoke(updatedMetadata)
            } else {
                currentArtworkBitmap = null
                val updatedMetadata = MediaMetadata.Builder()
                    .setTitle(cleanTitle)
                    .setDisplayTitle(cleanTitle)
                    .setArtist(effectiveArtist)
                    .setStation(stationName)
                    .build()
                exoPlayer?.playlistMetadata = updatedMetadata
                notifyMetadataListener?.invoke(updatedMetadata)
            }
            updateForegroundNotification()
        }
    }

    private fun updateForegroundNotification() {
        val session = mediaSession ?: return
        val state = _playerState.value
        val stationName = state.currentStation?.name ?: getString(R.string.app_name)

        val isDlna = isDlnaActive
        val activeDev = dlnaManager.activeDevice.value

        val statusText = when {
            isDlna && state.status is PlaybackStatus.Playing -> "Diffusé sur ${activeDev?.name ?: "DLNA"}"
            isDlna && state.status is PlaybackStatus.Paused -> "En pause (DLNA : ${activeDev?.name ?: ""})"
            state.status is PlaybackStatus.NoNetwork -> "Hors ligne - En attente du réseau…"
            state.status is PlaybackStatus.Reconnecting -> "Reconnexion auto… (${(state.status as PlaybackStatus.Reconnecting).secondsLeft}s)"
            state.status is PlaybackStatus.Buffering -> "Connexion au flux en direct…"
            state.status is PlaybackStatus.Playing -> "Diffusion en direct"
            state.status is PlaybackStatus.Paused -> "En pause"
            else -> "Prêt à diffuser"
        }

        // Title: track title if available, otherwise station name (nom de la radio)
        val notificationTitle = if (state.currentTrackTitle.isNotBlank()) {
            state.currentTrackTitle
        } else {
            stationName
        }

        // Subtitle: artist if available, otherwise station name
        val notificationText = when {
            state.currentTrackArtist.isNotBlank() -> state.currentTrackArtist
            state.currentTrackTitle.isNotBlank() -> stationName
            else -> stationName
        }

        // Subtext: station name + status when artist is available, otherwise statusText
        val notificationSubText = if (state.currentTrackArtist.isNotBlank()) {
            "$stationName • $statusText"
        } else {
            statusText
        }

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Actions: Toggle (Play/Pause) + Next (Skip)
        val isPlaying = state.status is PlaybackStatus.Playing
        val toggleIntent = Intent(this, RadioPlaybackService::class.java).apply { action = ACTION_TOGGLE }
        val togglePendingIntent = PendingIntent.getService(
            this, 101, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, RadioPlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(
            this, 102, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, RadioApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSubText(notificationSubText)
            .setSmallIcon(R.drawable.ic_ocarina_logo)
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1)
            )
            .setContentIntent(sessionActivityPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(
                state.status is PlaybackStatus.Playing ||
                state.status is PlaybackStatus.Buffering ||
                state.status is PlaybackStatus.Reconnecting ||
                state.status is PlaybackStatus.NoNetwork
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Lecture",
                togglePendingIntent
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Passer",
                nextPendingIntent
            )

        currentArtworkBitmap?.let {
            builder.setLargeIcon(it)
        }

        val notification = builder.build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(RadioApp.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(RadioApp.NOTIFICATION_ID, notification)
            }
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager?.notify(RadioApp.NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Error in startForeground: ${e.message}")
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.w(TAG, "ExoPlayer error: ${error.errorCodeName} (${error.errorCode}) ${error.message}")

        cancelMetadataPolling()

        var cause: Throwable? = error.cause
        var isMalformed = false
        var isUnknownHost = false
        var isConnectRefused = false
        var isTimeout = false
        var httpErrorCode: Int? = null
        var connectHost: String? = null

        while (cause != null) {
            if (cause is java.net.MalformedURLException || cause is IllegalArgumentException) {
                isMalformed = true
                break
            }
            if (cause is java.net.UnknownHostException) {
                isUnknownHost = true
                connectHost = cause.message
            }
            if (cause is java.net.ConnectException) {
                isConnectRefused = true
                connectHost = cause.message
            }
            if (cause is java.net.SocketTimeoutException) {
                isTimeout = true
            }
            if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                httpErrorCode = cause.responseCode
            }
            cause = cause.cause
        }

        // Case 1: Malformed URL (missing protocol, invalid characters)
        if (isMalformed) {
            cancelReconnect()
            exoPlayer?.stop()
            _playerState.update {
                it.copy(
                    status = PlaybackStatus.Error("URL invalide"),
                    message = "L'adresse du flux est invalide ou malformée."
                )
            }
            updateForegroundNotification()
            return
        }

        val isConnected = networkMonitor.isConnected()

        // Case 2: Device is offline or DNS resolution failed because device has no internet
        if (!isConnected || isUnknownHost) {
            Log.i(TAG, "Gracefully handling device offline state (connected=$isConnected, isUnknownHost=$isUnknownHost)")
            cancelReconnect()
            exoPlayer?.stop()
            _playerState.update {
                it.copy(
                    status = PlaybackStatus.NoNetwork,
                    message = "Aucune connexion Internet. En attente du réseau…"
                )
            }
            updateForegroundNotification()
            return
        }

        // Case 3: Connection refused by station host (e.g. bb.com:443 refused connection)
        if (isConnectRefused) {
            Log.i(TAG, "Station server connection refused ($connectHost). Aborting reconnect loop.")
            cancelReconnect()
            exoPlayer?.stop()
            val hostDisplay = connectHost?.let { " : $it" } ?: ""
            _playerState.update {
                it.copy(
                    status = PlaybackStatus.Error("Connexion refusée"),
                    message = "Le serveur de la station refuse la connexion$hostDisplay. Vérifiez l'adresse du flux."
                )
            }
            updateForegroundNotification()
            return
        }

        // Case 4: Remote HTTP error status (404, 403, 500, etc.)
        if (httpErrorCode != null) {
            Log.i(TAG, "Station server HTTP error: $httpErrorCode")
            cancelReconnect()
            exoPlayer?.stop()
            val errorLabel = when (httpErrorCode) {
                404 -> "Flux introuvable (404)"
                403 -> "Accès refusé au flux (403)"
                else -> "Erreur serveur HTTP $httpErrorCode"
            }
            _playerState.update {
                it.copy(
                    status = PlaybackStatus.Error(errorLabel),
                    message = "Le serveur de streaming a renvoyé une erreur $httpErrorCode."
                )
            }
            updateForegroundNotification()
            return
        }

        // Case 5: Stream was previously playing and was suddenly interrupted/timed out
        if (hasPlayedSuccessfully) {
            handleStreamInterrupted("Flux interrompu")
        } else {
            // Initial playback failed without explicit connection refused
            cancelReconnect()
            exoPlayer?.stop()
            val errorMsg = if (isTimeout) "Délai de connexion dépassé" else "Impossible de charger le flux (${error.errorCodeName})"
            _playerState.update {
                it.copy(
                    status = PlaybackStatus.Error(errorMsg),
                    message = "Impossible de se connecter à cette station. Vérifiez l'adresse du flux."
                )
            }
            updateForegroundNotification()
        }
    }

    private fun handleStreamInterrupted(reason: String) {
        val currentStation = _playerState.value.currentStation
        if (currentStation == null) {
            _playerState.update { it.copy(status = PlaybackStatus.Error(reason)) }
            return
        }

        exoPlayer?.stop()

        // Start auto-reconnect watchdog up to 5 minutes (300 seconds) if stream dropped mid-play
        if (reconnectJob?.isActive != true) {
            startReconnectWatchdog(currentStation)
        }
    }

    private fun handleNetworkRestored() {
        val state = _playerState.value
        val station = state.currentStation ?: return
        Log.i(TAG, "Network restored, checking active station ${station.name}")

        if (state.status is PlaybackStatus.NoNetwork || state.status is PlaybackStatus.Reconnecting) {
            _playerState.update {
                it.copy(
                    status = PlaybackStatus.Buffering,
                    message = "Connexion rétablie, reprise de la lecture…"
                )
            }
            updateForegroundNotification()
            playStation(station)
        }
    }

    private fun handleNetworkLost() {
        Log.i(TAG, "Network lost, pausing active player")
        val state = _playerState.value
        if (state.status is PlaybackStatus.Playing || state.status is PlaybackStatus.Buffering || state.status is PlaybackStatus.Reconnecting) {
            cancelReconnect()
            cancelMetadataPolling()
            exoPlayer?.stop()
            _playerState.update {
                it.copy(
                    status = PlaybackStatus.NoNetwork,
                    message = "Connexion Internet perdue. En attente du réseau…"
                )
            }
            updateForegroundNotification()
        }
    }

    private fun startReconnectWatchdog(station: RadioStation) {
        cancelReconnect()
        val totalSeconds = 300 // 5 minutes max
        var failedConsecutiveAttempts = 0

        reconnectJob = serviceScope.launch {
            var elapsed = 0
            while (isActive && elapsed < totalSeconds) {
                val remaining = totalSeconds - elapsed

                // Check connectivity BEFORE calling ExoPlayer to prevent UnknownHostException log spam
                val isOnline = networkMonitor.isConnected()
                if (!isOnline) {
                    _playerState.update {
                        it.copy(
                            status = PlaybackStatus.NoNetwork,
                            message = "En attente du retour de la connexion Internet…"
                        )
                    }
                    updateForegroundNotification()
                    delay(3000)
                    elapsed += 3
                    continue
                }

                _playerState.update {
                    it.copy(
                        status = PlaybackStatus.Reconnecting(remaining),
                        message = "Reconnexion automatique en cours ($remaining s restantes)…"
                    )
                }
                updateForegroundNotification()

                // Try reconnecting every 8 seconds
                delay(8000)
                elapsed += 8

                if (!isActive) break

                if (networkMonitor.isConnected()) {
                    try {
                        val resolvedUrl = StreamUrlResolver.resolveStreamUrl(station.streamUrl)
                        activeStreamUrl = resolvedUrl
                        prepareAndPlayExoPlayer(resolvedUrl, station.name)

                        delay(3500)
                        if (exoPlayer?.isPlaying == true) {
                            failedConsecutiveAttempts = 0
                            break
                        } else {
                            failedConsecutiveAttempts++
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Reconnect attempt failed: ${e.message}")
                        failedConsecutiveAttempts++
                    }
                }

                // If host keeps actively refusing connections (2 times in a row while online), abort watchdog
                if (failedConsecutiveAttempts >= 2 && networkMonitor.isConnected()) {
                    Log.i(TAG, "Station server connection failed repeatedly. Aborting auto-reconnect.")
                    _playerState.update {
                        it.copy(
                            status = PlaybackStatus.Error("Serveur de radio inaccessible"),
                            message = "La station ne répond pas. Vérifiez l'adresse ou réessayez plus tard."
                        )
                    }
                    updateForegroundNotification()
                    break
                }
            }

            if (elapsed >= totalSeconds && exoPlayer?.isPlaying != true) {
                _playerState.update {
                    it.copy(
                        status = PlaybackStatus.Error("Délai de reconnexion automatique dépassé (5 min)"),
                        message = "La station n'a pas pu être reconnectée après 5 minutes.",
                        reconnectFailed = true
                    )
                }
                updateForegroundNotification()
                showReconnectFailedAlertNotification(station)
            }
        }
    }

    private fun showReconnectFailedAlertNotification(station: RadioStation) {
        val retryIntent = Intent(this, RadioPlaybackService::class.java).apply {
            action = ACTION_PLAY_STATION
            putExtra(EXTRA_STATION_ID, station.id)
            putExtra(EXTRA_STATION_NAME, station.name)
            putExtra(EXTRA_STATION_STREAM_URL, station.streamUrl)
            putExtra(EXTRA_STATION_CHANGE_SONG_URL, station.changeSongUrl)
        }
        val retryPendingIntent = PendingIntent.getService(
            this, 201, retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 202, sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alertNotification = NotificationCompat.Builder(this, RadioApp.ALERT_CHANNEL_ID)
            .setContentTitle(getString(R.string.reconnect_failed_title))
            .setContentText("${station.name} : ${getString(R.string.reconnect_failed_desc)}")
            .setSmallIcon(R.drawable.ic_ocarina_logo)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_media_play, getString(R.string.action_retry), retryPendingIntent)
            .build()

        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        notificationManager?.notify(RadioApp.ALERT_NOTIFICATION_ID, alertNotification)
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    fun triggerNextSong() {
        val currentStation = _playerState.value.currentStation ?: return

        nextSongJob?.cancel()
        nextSongJob = serviceScope.launch {
            val stationName = currentStation.name
            _playerState.update { 
                it.copy(
                    isChangingSong = true,
                    currentTrackTitle = "",
                    currentTrackArtist = "",
                    artworkUrl = null,
                    message = "Changement de morceau en cours…"
                ) 
            }
            currentArtworkBitmap = null

            val pendingMetadata = MediaMetadata.Builder()
                .setTitle(stationName)
                .setDisplayTitle(stationName)
                .setArtist(stationName)
                .setStation(stationName)
                .build()
            exoPlayer?.playlistMetadata = pendingMetadata
            notifyMetadataListener?.invoke(pendingMetadata)
            updateForegroundNotification()

            if (currentStation.changeSongUrl.isNotBlank()) {
                try {
                    val rawChange = currentStation.changeSongUrl.trim()
                    val effectiveChange = if (!rawChange.startsWith("http://", ignoreCase = true) && !rawChange.startsWith("https://", ignoreCase = true)) {
                        if (rawChange.contains(".") && !rawChange.contains(" ")) "http://$rawChange" else null
                    } else rawChange

                    if (effectiveChange != null) {
                        val request = Request.Builder()
                            .url(effectiveChange)
                            .header("User-Agent", "Ocarina/1.0")
                            .build()

                        withContext(Dispatchers.IO) {
                            okHttpClient.newCall(request).execute().use { response ->
                                Log.d(TAG, "Change song response: ${response.code}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to call changeSongUrl: ${e.message}")
                }
            }

            val shouldReloadStream = com.example.data.AppPreferences.getInstance(this@RadioPlaybackService).skipStreamReload.value

            if (isDlnaActive) {
                val activeDev = dlnaManager.activeDevice.value!!
                val resolvedUrl = StreamUrlResolver.resolveStreamUrl(currentStation.streamUrl)
                activeStreamUrl = resolvedUrl

                if (shouldReloadStream) {
                    // Requirement 3: Envoi explicite STOP -> SET_AV_TRANSPORT_URI -> PLAY au renderer distant pour vider et resynchroniser le buffer
                    delay(300)
                    val resyncSuccess = dlnaManager.resyncStream(activeDev, resolvedUrl, currentStation.name)
                    _playerState.update { 
                        it.copy(
                            isChangingSong = false, 
                            message = if (resyncSuccess) "Morceau actualisé sur ${activeDev.name}" else "Échec de synchronisation sur ${activeDev.name}"
                        ) 
                    }
                } else {
                    _playerState.update { 
                        it.copy(
                            isChangingSong = false, 
                            message = "Morceau suivant demandé sur ${activeDev.name}"
                        ) 
                    }
                }

                // Progressive polling to catch new track metadata immediately upon stream restart
                for (pollDelay in listOf(600L, 1200L, 2200L)) {
                    delay(pollDelay)
                    val updatedTitle = IcyMetadataExtractor.fetchCurrentTrackTitle(resolvedUrl)
                    if (!updatedTitle.isNullOrBlank() && updatedTitle != currentStation.name) {
                        updateTrackTitle(updatedTitle)
                        break
                    }
                }
            } else {
                // Respect user preference for active stream reload on skip
                if (shouldReloadStream) {
                    // Immediately reload the audio stream to cut buffer lag and refresh to the new song
                    delay(400)
                    val resolvedUrl = StreamUrlResolver.resolveStreamUrl(currentStation.streamUrl)
                    prepareAndPlayExoPlayer(resolvedUrl, currentStation.name)
                    _playerState.update { it.copy(isChangingSong = false, message = "Morceau actualisé") }

                    // Progressive polling to catch new track metadata immediately upon stream restart
                    for (pollDelay in listOf(600L, 1200L, 2200L)) {
                        delay(pollDelay)
                        val updatedTitle = IcyMetadataExtractor.fetchCurrentTrackTitle(resolvedUrl)
                        if (!updatedTitle.isNullOrBlank() && updatedTitle != currentStation.name) {
                            updateTrackTitle(updatedTitle)
                            break
                        }
                    }
                } else {
                    _playerState.update { it.copy(isChangingSong = false, message = "Morceau suivant demandé") }
                    for (pollDelay in listOf(1000L, 1800L, 2500L)) {
                        delay(pollDelay)
                        val currentUrl = activeStreamUrl ?: currentStation.streamUrl
                        val updatedTitle = IcyMetadataExtractor.fetchCurrentTrackTitle(currentUrl)
                        if (!updatedTitle.isNullOrBlank() && updatedTitle != currentStation.name) {
                            updateTrackTitle(updatedTitle)
                            break
                        }
                    }
                }
            }
        }
    }

    fun onPlay() {
        Log.d(TAG, "onPlay: routing command (isDlnaActive=$isDlnaActive)")
        if (isDlnaActive) {
            resumePlayback()
        } else {
            if (exoPlayer != null && exoPlayer?.playbackState != Player.STATE_IDLE) {
                exoPlayer?.play()
                _playerState.update { it.copy(status = PlaybackStatus.Playing) }
                updateForegroundNotification()
            } else {
                _playerState.value.currentStation?.let { playStation(it) }
            }
        }
    }

    fun onPause() {
        Log.d(TAG, "onPause: routing command (isDlnaActive=$isDlnaActive)")
        if (isDlnaActive) {
            pausePlayback()
        } else {
            exoPlayer?.pause()
            _playerState.update { it.copy(status = PlaybackStatus.Paused) }
            updateForegroundNotification()
        }
    }

    fun onStop() {
        Log.d(TAG, "onStop: routing command (isDlnaActive=$isDlnaActive)")
        if (isDlnaActive) {
            stopDlnaPlayback()
        } else {
            exoPlayer?.stop()
            _playerState.update { it.copy(status = PlaybackStatus.Paused) }
            updateForegroundNotification()
        }
    }

    fun stopDlnaPlayback() {
        serviceScope.launch {
            dlnaManager.stopPlayback()
            _playerState.update { it.copy(status = PlaybackStatus.Paused) }
            updateForegroundNotification()
        }
    }

    fun resumePlayback() {
        val state = _playerState.value
        val station = state.currentStation

        if (!networkMonitor.isConnected()) {
            _playerState.update {
                it.copy(
                    status = PlaybackStatus.NoNetwork,
                    message = "Impossible de reprendre la lecture : aucune connexion Internet."
                )
            }
            updateForegroundNotification()
            return
        }

        if (isDlnaActive) {
            val activeDev = dlnaManager.activeDevice.value!!
            serviceScope.launch {
                _playerState.update { it.copy(status = PlaybackStatus.Buffering) }
                updateForegroundNotification()
                val success = dlnaManager.resume()
                if (success) {
                    _playerState.update { it.copy(status = PlaybackStatus.Playing) }
                    val currentUrl = activeStreamUrl ?: station?.streamUrl
                    if (!currentUrl.isNullOrBlank()) {
                        startMetadataPolling(currentUrl)
                    }
                } else if (station != null) {
                    val resolved = activeStreamUrl ?: StreamUrlResolver.resolveStreamUrl(station.streamUrl)
                    dlnaManager.changeSource(resolved, station.name)
                    _playerState.update { it.copy(status = PlaybackStatus.Playing) }
                }
                updateForegroundNotification()
            }
            return
        }

        if (exoPlayer != null && exoPlayer?.playbackState != Player.STATE_IDLE) {
            exoPlayer?.play()
            _playerState.update { it.copy(status = PlaybackStatus.Playing) }
            updateForegroundNotification()
        } else if (station != null) {
            playStation(station)
        }
    }

    fun pausePlayback() {
        if (isDlnaActive) {
            serviceScope.launch {
                dlnaManager.pause()
                _playerState.update { it.copy(status = PlaybackStatus.Paused) }
                updateForegroundNotification()
            }
            return
        }

        exoPlayer?.pause()
        _playerState.update { it.copy(status = PlaybackStatus.Paused) }
        updateForegroundNotification()
    }

    fun togglePlayback() {
        if (isDlnaActive) {
            if (_playerState.value.status is PlaybackStatus.Playing) {
                onPause()
            } else {
                onPlay()
            }
            return
        }

        val player = exoPlayer
        if (player != null && player.isPlaying) {
            onPause()
        } else if (player != null && (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)) {
            if (player.playWhenReady) {
                onPause()
            } else {
                onPlay()
            }
        } else if (_playerState.value.status is PlaybackStatus.Reconnecting || _playerState.value.status is PlaybackStatus.NoNetwork) {
            cancelReconnect()
            _playerState.update { it.copy(status = PlaybackStatus.Paused) }
            updateForegroundNotification()
        } else {
            _playerState.value.currentStation?.let { playStation(it) }
        }
    }

    private fun startMetadataPolling(streamUrl: String) {
        cancelMetadataPolling()
        metadataJob = serviceScope.launch {
            while (isActive) {
                val title = IcyMetadataExtractor.fetchCurrentTrackTitle(streamUrl)
                if (!title.isNullOrBlank()) {
                    updateTrackTitle(title)
                }
                delay(10000) // Poll ICY title every 10 seconds
            }
        }
    }

    private fun cancelMetadataPolling() {
        metadataJob?.cancel()
        metadataJob = null
    }

    fun stopPlaybackService() {
        cancelReconnect()
        cancelMetadataPolling()
        nextSongJob?.cancel()
        artworkJob?.cancel()
        currentArtworkBitmap = null
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()

        serviceScope.launch {
            if (dlnaManager.isCasting.value) {
                dlnaManager.stopCasting()
            }
        }

        _playerState.update {
            it.copy(
                status = PlaybackStatus.Idle,
                currentTrackTitle = "",
                artworkUrl = null,
                message = null
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        cancelReconnect()
        cancelMetadataPolling()
        nextSongJob?.cancel()
        artworkJob?.cancel()
        currentArtworkBitmap = null
        networkMonitor.stopMonitoring()

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RadioPlaybackService"

        const val CUSTOM_ACTION_NEXT = "com.example.service.CUSTOM_ACTION_NEXT"

        const val ACTION_PLAY = "com.example.service.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE"
        const val ACTION_TOGGLE = "com.example.service.ACTION_TOGGLE"
        const val ACTION_NEXT = "com.example.service.ACTION_NEXT"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_PLAY_STATION = "com.example.service.ACTION_PLAY_STATION"
        const val ACTION_SYNC_DLNA = "com.example.service.ACTION_SYNC_DLNA"

        const val EXTRA_STATION_ID = "EXTRA_STATION_ID"
        const val EXTRA_STATION_NAME = "EXTRA_STATION_NAME"
        const val EXTRA_STATION_STREAM_URL = "EXTRA_STATION_STREAM_URL"
        const val EXTRA_STATION_CHANGE_SONG_URL = "EXTRA_STATION_CHANGE_SONG_URL"

        private val _playerState = MutableStateFlow(PlayerState())
        val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

        fun playStation(context: Context, station: RadioStation) {
            val intent = Intent(context, RadioPlaybackService::class.java).apply {
                action = ACTION_PLAY_STATION
                putExtra(EXTRA_STATION_ID, station.id)
                putExtra(EXTRA_STATION_NAME, station.name)
                putExtra(EXTRA_STATION_STREAM_URL, station.streamUrl)
                putExtra(EXTRA_STATION_CHANGE_SONG_URL, station.changeSongUrl)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun sendAction(context: Context, action: String) {
            val intent = Intent(context, RadioPlaybackService::class.java).apply {
                this.action = action
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
