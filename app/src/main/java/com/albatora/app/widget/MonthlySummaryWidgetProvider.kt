package com.albatora.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class MonthlySummaryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        AlbaToraWidgetUpdater.updateMonthlySummaryWidgets(context, appWidgetManager, appWidgetIds)
    }
}
