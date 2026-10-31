package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * BroadcastReceiver capable of receiving hardware media button intents (headsets, Bluetooth,
 * Samsung SoundAssistant, Android Auto, steering wheel controls) and dispatching them to
 * [RadioPlaybackService].
 */
class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            val serviceIntent = Intent(context, RadioPlaybackService::class.java).apply {
                action = Intent.ACTION_MEDIA_BUTTON
                intent.extras?.let { putExtras(it) }
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                // Ignore service start restrictions if restricted by background battery rules
            }
        }
    }
}
