package com.albatora.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.albatora.app.MainActivity
import com.albatora.app.R
import com.albatora.app.data.AlbaToraStateHolder
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.widget.AlbaToraWidgetUpdater
import kotlinx.coroutines.*
import java.util.Locale

class LiveTrackerForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stateHolder = AlbaToraStateHolder.getInstance(applicationContext)
        val currentState = stateHolder.state.value
        val snapshot = PayrollCalculator.evaluateLiveDashboard(currentState, System.currentTimeMillis())

        if (!currentState.settings.ongoingNotificationEnabled || snapshot.activeShifts.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        startTicker()
        return START_STICKY
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            var tickCount = 0
            while (isActive) {
                val stateHolder = AlbaToraStateHolder.getInstance(applicationContext)
                val st = stateHolder.state.value
                val live = PayrollCalculator.evaluateLiveDashboard(st, System.currentTimeMillis())
                if (!st.settings.ongoingNotificationEnabled || live.activeShifts.isEmpty()) {
                    AlbaToraWidgetUpdater.updateAll(applicationContext)
                    stopSelf()
                    break
                }
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification())
                if (tickCount % 5 == 0) {
                    AlbaToraWidgetUpdater.updateLiveEarningsOnly(applicationContext)
                }
                tickCount++
                delay(1000L)
            }
        }
    }

    private fun buildNotification(): Notification {
        val st = AlbaToraStateHolder.getInstance(applicationContext).state.value
        val snapshot = PayrollCalculator.evaluateLiveDashboard(st, System.currentTimeMillis())
        val firstShift = snapshot.activeShifts.firstOrNull()
        val isPaused = snapshot.activeShifts.all { it.isPaused }

        val title = when {
            snapshot.activeShifts.size > 1 ->
                "알바토라 · ${snapshot.activeShifts.size}개 동시 근무 중"
            firstShift != null && isPaused ->
                "⏸ 휴게 중 · ${firstShift.title}"
            firstShift != null ->
                "🟢 근무 중 · ${firstShift.title}"
            else -> "알바토라 실시간 근무 중"
        }

        val rateFormatted = String.format(Locale.US, "+%.2f원/초", snapshot.totalPerSecondRateWon)
        val boostTag = if (snapshot.anyBoostActive) " · 🔥${snapshot.maxBoostMultiplier}배 부스트" else ""
        val content = "지금까지 ${PayrollCalculator.formatWonDecimal(snapshot.totalEarnedWon)} ($rateFormatted$boostTag)"

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val breakIntent = PendingIntent.getBroadcast(
            this,
            101,
            Intent(this, TrackerNotificationReceiver::class.java).apply {
                action = AlbaToraStateHolder.ACTION_TOGGLE_BREAK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clockOutIntent = PendingIntent.getBroadcast(
            this,
            102,
            Intent(this, TrackerNotificationReceiver::class.java).apply {
                action = AlbaToraStateHolder.ACTION_CLOCK_OUT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val breakLabel = if (isPaused) "휴게 종료" else "휴게"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_albatora)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, breakLabel, breakIntent)
            .addAction(0, "퇴근", clockOutIntent)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "실시간 수익 트래커",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "근무 중 실시간 누적 급여와 휴게/퇴근 빠른 제어 알림을 표시합니다."
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "albatora_live_tracker"
        const val NOTIFICATION_ID = 20261

        fun startOrUpdate(context: Context) {
            val intent = Intent(context, LiveTrackerForegroundService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, LiveTrackerForegroundService::class.java))
            }
        }
    }
}
