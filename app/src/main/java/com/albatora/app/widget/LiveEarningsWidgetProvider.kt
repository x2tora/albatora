package com.albatora.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class LiveEarningsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        AlbaToraWidgetUpdater.updateLiveEarningsWidgets(context, appWidgetManager, appWidgetIds)
    }
}
