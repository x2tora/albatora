package com.albatora.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.albatora.app.R
import com.albatora.app.domain.LiveDashboardSnapshot
import com.albatora.app.domain.PayrollCalculator
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

object LiveUpdateNotificationHelper {

    /**
     * Formats a compact status-bar chip string (~7 chars target) for Android 16 Live Updates.
     */
    fun formatChipText(earnedWon: Double, isPaused: Boolean): String {
        val won = earnedWon.roundToInt().coerceAtLeast(0)
        return if (isPaused) {
            if (won >= 10_000) {
                val man = won / 10_000.0
                String.format(Locale.US, "휴게 %.1f만", man)
            } else {
                "휴게 ${NumberFormat.getNumberInstance(Locale.KOREA).format(won)}"
            }
        } else {
            if (won >= 10_000) {
                val man = won / 10_000.0
                String.format(Locale.US, "%.1f만원", man)
            } else {
                "${NumberFormat.getNumberInstance(Locale.KOREA).format(won)}원"
            }
        }
    }

    fun canPostPromoted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
    }

    fun openPromotionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 36) return
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun build(
        context: Context,
        channelId: String,
        snapshot: LiveDashboardSnapshot,
        openAppIntent: PendingIntent,
        breakIntent: PendingIntent,
        clockOutIntent: PendingIntent
    ): Notification {
        val firstShift = snapshot.activeShifts.firstOrNull()
        val isPaused = snapshot.activeShifts.isNotEmpty() && snapshot.activeShifts.all { it.isPaused }

        val title = when {
            snapshot.activeShifts.size > 1 ->
                "알바토라 · ${snapshot.activeShifts.size}개 동시 근무 중"
            firstShift != null && isPaused ->
                "일시정지(휴게) · ${firstShift.title}"
            firstShift != null ->
                "근무 진행 중 · ${firstShift.title}"
            else -> "알바토라 실시간 근무 중"
        }

        val rateFormatted = String.format(Locale.US, "+%.2f원/초", snapshot.totalPerSecondRateWon)
        val boostTag = if (snapshot.anyBoostActive) " · ${snapshot.maxBoostMultiplier}배 가산" else ""
        val content = "지금까지 ${PayrollCalculator.formatWonDecimal(snapshot.totalEarnedWon)} ($rateFormatted$boostTag)"
        val breakLabel = if (isPaused) "휴게 종료" else "휴게"

        return if (Build.VERSION.SDK_INT >= 36) {
            buildApi36LiveUpdate(
                context = context,
                channelId = channelId,
                title = title,
                content = content,
                snapshot = snapshot,
                isPaused = isPaused,
                breakLabel = breakLabel,
                openAppIntent = openAppIntent,
                breakIntent = breakIntent,
                clockOutIntent = clockOutIntent
            )
        } else {
            buildLegacyNotification(
                context = context,
                channelId = channelId,
                title = title,
                content = content,
                breakLabel = breakLabel,
                openAppIntent = openAppIntent,
                breakIntent = breakIntent,
                clockOutIntent = clockOutIntent
            )
        }
    }

    @RequiresApi(36)
    private fun buildApi36LiveUpdate(
        context: Context,
        channelId: String,
        title: String,
        content: String,
        snapshot: LiveDashboardSnapshot,
        isPaused: Boolean,
        breakLabel: String,
        openAppIntent: PendingIntent,
        breakIntent: PendingIntent,
        clockOutIntent: PendingIntent
    ): Notification {
        val firstShift = snapshot.activeShifts.firstOrNull()
        val accentColorInt = (firstShift?.colorHex ?: 0xFF3482FFL).toInt()
        val elapsedMillis = firstShift?.elapsedWorkMillis ?: 0L
        val chronometerBase = System.currentTimeMillis() - elapsedMillis

        val progressStyle = Notification.ProgressStyle().apply {
            val fraction = firstShift?.progressFraction
            if (fraction != null) {
                val progress = (fraction * 1000f).roundToInt().coerceIn(0, 1000)
                addProgressSegment(
                    Notification.ProgressStyle.Segment(1000).setColor(accentColorInt)
                )
                setProgress(progress)
                setStyledByProgress(true)
            } else {
                setProgressIndeterminate(true)
            }
        }

        val breakAction = Notification.Action.Builder(
            null as Icon?,
            breakLabel,
            breakIntent
        ).build()

        val clockOutAction = Notification.Action.Builder(
            null as Icon?,
            "퇴근",
            clockOutIntent
        ).build()

        return Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_albatora)
            .setColor(accentColorInt)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(!isPaused)
            .setWhen(chronometerBase)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(formatChipText(snapshot.totalEarnedWon, isPaused))
            .setStyle(progressStyle)
            .addAction(breakAction)
            .addAction(clockOutAction)
            .build()
    }

    private fun buildLegacyNotification(
        context: Context,
        channelId: String,
        title: String,
        content: String,
        breakLabel: String,
        openAppIntent: PendingIntent,
        breakIntent: PendingIntent,
        clockOutIntent: PendingIntent
    ): Notification = NotificationCompat.Builder(context, channelId)
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
