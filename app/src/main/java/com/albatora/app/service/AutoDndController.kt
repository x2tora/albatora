package com.albatora.app.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.albatora.app.domain.LiveDashboardSnapshot
import com.albatora.app.model.AppSettings

/**
 * Automatically toggles system Do Not Disturb while at least one shift is actively running
 * (not paused for a break), and restores the user's prior interruption filter afterward.
 */
object AutoDndController {
    private const val PREFS_NAME = "albatora_dnd_prefs"
    private const val KEY_MANAGED = "dnd_managed_by_app"
    private const val KEY_PREV_FILTER = "dnd_previous_filter"

    fun hasPolicyAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun openPolicyAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun sync(
        context: Context,
        settings: AppSettings,
        live: LiveDashboardSnapshot
    ) {
        val shouldActivate = settings.autoDndEnabled && live.activeShifts.any { !it.isPaused }
        if (!hasPolicyAccess(context)) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isManaged = prefs.getBoolean(KEY_MANAGED, false)

        if (shouldActivate) {
            if (!isManaged) {
                val currentFilter = nm.currentInterruptionFilter
                if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    prefs.edit()
                        .putBoolean(KEY_MANAGED, true)
                        .putInt(KEY_PREV_FILTER, currentFilter)
                        .apply()
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                }
            }
        } else if (isManaged) {
            val prevFilter = prefs.getInt(KEY_PREV_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
            prefs.edit()
                .putBoolean(KEY_MANAGED, false)
                .remove(KEY_PREV_FILTER)
                .apply()
            nm.setInterruptionFilter(prevFilter)
        }
    }
}
