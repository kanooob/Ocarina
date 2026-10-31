package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.PlaybackStatus
import com.example.ui.LocalAppStrings
import com.example.ui.RadioViewModel
import com.example.ui.resolveStrings
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

enum class AppScreen {
    HOME,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: RadioViewModel by viewModels()
    private var volumeObserver: android.database.ContentObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC

        handleLaunchIntent(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val language by viewModel.language.collectAsStateWithLifecycle()
            val strings = resolveStrings(language)

            val dlnaVolume by viewModel.dlnaVolume.collectAsStateWithLifecycle()
            val activeDlnaDevice by viewModel.activeDlnaDevice.collectAsStateWithLifecycle()
            var showVolumeOverlay by remember { mutableStateOf(false) }

            // Flash volume indicator when DLNA volume is adjusted
            LaunchedEffect(dlnaVolume) {
                if (activeDlnaDevice != null) {
                    showVolumeOverlay = true
                    delay(2200)
                    showVolumeOverlay = false
                }
            }

            CompositionLocalProvider(LocalAppStrings provides strings) {
                MyApplicationTheme(themeMode = themeMode) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            RequestNotificationPermission()

                            var currentScreen by remember { mutableStateOf(AppScreen.HOME) }

                            AnimatedContent(
                                targetState = currentScreen,
                                transitionSpec = {
                                    if (targetState == AppScreen.SETTINGS) {
                                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                            slideOutHorizontally { width -> -width / 2 } + fadeOut()
                                        )
                                    } else {
                                        (slideInHorizontally { width -> -width / 2 } + fadeIn()).togetherWith(
                                            slideOutHorizontally { width -> width } + fadeOut()
                                        )
                                    }
                                },
                                label = "screen_transition"
                            ) { screen ->
                                when (screen) {
                                    AppScreen.HOME -> {
                                        HomeScreen(
                                            viewModel = viewModel,
                                            onNavigateToSettings = { currentScreen = AppScreen.SETTINGS }
                                        )
                                    }
                                    AppScreen.SETTINGS -> {
                                        SettingsScreen(
                                            viewModel = viewModel,
                                            onNavigateBack = { currentScreen = AppScreen.HOME }
                                        )
                                    }
                                }
                            }

                            // Capsule flottante de notification de volume DLNA (action des touches du téléphone)
                            AnimatedVisibility(
                                visible = showVolumeOverlay && activeDlnaDevice != null,
                                enter = fadeIn() + slideInVertically { -it },
                                exit = fadeOut() + slideOutVertically { -it },
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding()
                                    .padding(top = 16.dp)
                                    .zIndex(100f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.inverseSurface,
                                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                dlnaVolume == 0 -> androidx.compose.material.icons.Icons.AutoMirrored.Filled.VolumeMute
                                                dlnaVolume < 50 -> androidx.compose.material.icons.Icons.AutoMirrored.Filled.VolumeDown
                                                else -> androidx.compose.material.icons.Icons.AutoMirrored.Filled.VolumeUp
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.inversePrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.widthIn(min = 130.dp, max = 220.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = activeDlnaDevice?.name ?: "DLNA",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$dlnaVolume%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = { dlnaVolume / 100f },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp)),
                                                color = MaterialTheme.colorScheme.inversePrimary,
                                                trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.2f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setupVolumeObserver()
    }

    override fun onStop() {
        super.onStop()
        volumeObserver?.let { contentResolver.unregisterContentObserver(it) }
        volumeObserver = null
    }

    private fun setupVolumeObserver() {
        val audioManager = getSystemService(android.media.AudioManager::class.java) ?: return
        var lastPhoneVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        volumeObserver = object : android.database.ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                val dlnaManager = (application as RadioApp).dlnaManager
                if (dlnaManager.isDlnaActive) {
                    val currentPhoneVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                    if (currentPhoneVol != lastPhoneVolume) {
                        lastPhoneVolume = currentPhoneVol
                        val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                        if (maxVol > 0) {
                            val percentage = ((currentPhoneVol.toFloat() / maxVol) * 100).toInt().coerceIn(0, 100)
                            dlnaManager.setVolumeAsync(percentage)
                        }
                    }
                }
            }
        }
        try {
            contentResolver.registerContentObserver(
                android.provider.Settings.System.CONTENT_URI,
                true,
                volumeObserver!!
            )
        } catch (e: Exception) {
            // Ignored if observer not permitted
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val dlnaManager = (application as RadioApp).dlnaManager
        if (dlnaManager.isDlnaActive) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                    dlnaManager.adjustVolume(5)
                    return true
                }
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    dlnaManager.adjustVolume(-5)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val dlnaManager = (application as RadioApp).dlnaManager
        if (dlnaManager.isDlnaActive) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP,
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        val stationId = intent?.getLongExtra(EXTRA_STATION_ID, -1L) ?: -1L
        if (stationId != -1L) {
            viewModel.playStationById(stationId)
            return
        }

        val action = intent?.action
        if (action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            val query = intent.getStringExtra(android.app.SearchManager.QUERY)
            if (!query.isNullOrBlank()) {
                viewModel.playStationBySearchQuery(query)
            } else if (viewModel.playerState.value.status !is PlaybackStatus.Playing) {
                viewModel.togglePlayPause()
            }
        } else if (action == "android.intent.action.MUSIC_PLAYER") {
            if (viewModel.playerState.value.status !is PlaybackStatus.Playing) {
                viewModel.togglePlayPause()
            }
        }
    }

    @Composable
    private fun RequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val permissionGranted = ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED

            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* Permission granted or denied handled gracefully */ }

            LaunchedEffect(Unit) {
                if (!permissionGranted) {
                    launcher.launch(permission)
                }
            }
        }
    }

    companion object {
        const val EXTRA_STATION_ID = "EXTRA_STATION_ID"
    }
}
