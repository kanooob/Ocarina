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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.LocalAppStrings
import com.example.ui.RadioViewModel
import com.example.ui.resolveStrings
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

enum class AppScreen {
    HOME,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleLaunchIntent(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val language by viewModel.language.collectAsStateWithLifecycle()
            val strings = resolveStrings(language)

            CompositionLocalProvider(LocalAppStrings provides strings) {
                MyApplicationTheme(themeMode = themeMode) {
                    Surface(modifier = Modifier.fillMaxSize()) {
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
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val dlnaManager = (application as RadioApp).dlnaManager
        if (dlnaManager.isDlnaActive && dlnaManager.isCasting.value) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        val stationId = intent?.getLongExtra(EXTRA_STATION_ID, -1L) ?: -1L
        if (stationId != -1L) {
            viewModel.playStationById(stationId)
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
