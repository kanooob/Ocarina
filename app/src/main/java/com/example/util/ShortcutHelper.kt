package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R
import com.example.data.RadioStation

object ShortcutHelper {

    private const val TAG = "ShortcutHelper"

    fun updateDynamicShortcuts(context: Context, stations: List<RadioStation>) {
        try {
            val maxShortcuts = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
            val shortcuts = stations.take(maxShortcuts).map { station ->
                createShortcutInfo(context, station)
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
            Log.d(TAG, "Updated ${shortcuts.size} dynamic shortcuts")
        } catch (e: Exception) {
            Log.w(TAG, "Error updating dynamic shortcuts: ${e.message}")
        }
    }

    fun pinStationShortcut(context: Context, station: RadioStation): Boolean {
        return try {
            if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                val shortcut = createShortcutInfo(context, station)
                ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error pinning shortcut: ${e.message}")
            false
        }
    }

    private fun createShortcutInfo(context: Context, station: RadioStation): ShortcutInfoCompat {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_STATION_ID, station.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return ShortcutInfoCompat.Builder(context, "shortcut_station_${station.id}")
            .setShortLabel(station.name.take(15))
            .setLongLabel("Écouter ${station.name}")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher_foreground))
            .setIntent(launchIntent)
            .build()
    }
}
