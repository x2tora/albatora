package com.albatora.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.albatora.app.data.AlbaToraStateHolder

class TrackerNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        AlbaToraStateHolder.getInstance(context).handleNotificationAction(action)
    }
}
