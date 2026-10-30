package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.example.service.RadioPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CircleRadioWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        OcarinaWidgetHelper.updateWidgets(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        OcarinaWidgetHelper.updateWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            OcarinaWidgetHelper.ACTION_TOGGLE -> {
                handleCircleTap(context)
            }
        }
    }

    companion object {
        private var lastTapTime: Long = 0L
        private var singleTapJob: Job? = null
        private val tapScope = CoroutineScope(Dispatchers.Main + Job())
        private const val DOUBLE_TAP_TIMEOUT_MS = 380L

        private fun handleCircleTap(context: Context) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastTapTime

            if (elapsed in 1 until DOUBLE_TAP_TIMEOUT_MS) {
                // Double tap détecté : annuler le toggle et passer à la station suivante
                singleTapJob?.cancel()
                singleTapJob = null
                lastTapTime = 0L
                RadioPlaybackService.sendAction(context, RadioPlaybackService.ACTION_NEXT)
            } else {
                // Premier tap : démarrer le compte à rebours pour le toggle
                lastTapTime = now
                singleTapJob?.cancel()
                val appContext = context.applicationContext
                singleTapJob = tapScope.launch {
                    delay(DOUBLE_TAP_TIMEOUT_MS)
                    RadioPlaybackService.sendAction(appContext, RadioPlaybackService.ACTION_TOGGLE)
                    lastTapTime = 0L
                }
            }
        }
    }
}
