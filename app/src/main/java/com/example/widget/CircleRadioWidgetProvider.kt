package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.example.service.RadioPlaybackService

class CircleRadioWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        OcarinaWidgetHelper.updateWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            OcarinaWidgetHelper.ACTION_TOGGLE -> {
                RadioPlaybackService.sendAction(context, RadioPlaybackService.ACTION_TOGGLE)
            }
        }
    }
}
