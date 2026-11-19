package com.albatora.app.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.albatora.app.MainActivity
import com.albatora.app.R
import com.albatora.app.data.AlbaToraStateHolder
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.AllowanceSettings
import com.albatora.app.model.BreakPayType
import com.albatora.app.model.JobKind
import com.albatora.app.model.PayType
import java.time.LocalDate

class ShiftReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val holder = AlbaToraStateHolder.getInstance(context)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                ShiftReminderScheduler.sync(context, holder.state.value)
            }
            ACTION_TRIGGER_REMINDER -> {
                showReminderNotification(context, intent)
                // Keep rolling 7-day schedule fresh
                ShiftReminderScheduler.sync(context, holder.state.value)
            }
            ACTION_REMINDER_START_SHIFT -> {
                val jobId = intent.getStringExtra(EXTRA_JOB_ID)
                val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                val job = holder.state.value.jobs.find { it.id == jobId }
                if (job != null) {
                    val wage = PayrollCalculator.effectiveJobWage(job, LocalDate.now())
                    holder.startManualStopwatch(
                        title = job.name,
                        emoji = job.emoji,
                        kind = job.kind,
                        payType = job.payType,
                        unitAmount = wage,
                        jobId = job.id,
                        colorHex = job.colorHex,
                        plannedDurationMinutes = null,
                        unpaidBreakMinutes = 0,
                        breakPayType = BreakPayType.PAID,
                        allowance = job.allowance
                    )
                } else {
                    holder.startManualStopwatch(
                        title = intent.getStringExtra(EXTRA_JOB_NAME) ?: "근무",
                        kind = JobKind.SHORT_TERM,
                        payType = PayType.HOURLY,
                        unitAmount = holder.state.value.settings.defaultMinWage,
                        allowance = AllowanceSettings()
                    )
                }
                dismissNotification(context, notifId)
            }
            ACTION_REMINDER_CONFIRM_SHIFT -> {
                val jobId = intent.getStringExtra(EXTRA_JOB_ID)
                val dateIso = intent.getStringExtra(EXTRA_DATE_ISO)
                val dow = intent.getIntExtra(EXTRA_DAY_OF_WEEK, 1)
                val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                if (jobId != null && dateIso != null) {
                    val job = holder.state.value.jobs.find { it.id == jobId }
                    val slot = job?.schedules?.firstOrNull { it.dayOfWeek == dow }
                    if (slot != null) {
                        holder.confirmVirtualScheduleAsRecord(jobId, dateIso, slot)
                    }
                }
                dismissNotification(context, notifId)
            }
            ACTION_REMINDER_MARK_PAYDAY_PAID -> {
                val jobId = intent.getStringExtra(EXTRA_JOB_ID)
                val dateIso = intent.getStringExtra(EXTRA_DATE_ISO)
                val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                if (jobId != null && dateIso != null) {
                    holder.markJobPaydayRecordsPaid(jobId, dateIso)
                }
                dismissNotification(context, notifId)
            }
        }
    }

    private fun showReminderNotification(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ShiftReminderScheduler.ensureChannel(context)
        val kindName = intent.getStringExtra(EXTRA_KIND) ?: return
        val kind = runCatching { ReminderKind.valueOf(kindName) }.getOrNull() ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "알바토라 알림"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 7200)
        val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: ""
        val jobName = intent.getStringExtra(EXTRA_JOB_NAME) ?: ""
        val dateIso = intent.getStringExtra(EXTRA_DATE_ISO) ?: ""
        val dow = intent.getIntExtra(EXTRA_DAY_OF_WEEK, 1)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, ShiftReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openPending)
            .setAutoCancel(true)

        when (kind) {
            ReminderKind.PRE_SHIFT -> {
                val startAction = Intent(context, ShiftReminderReceiver::class.java).apply {
                    action = ACTION_REMINDER_START_SHIFT
                    putExtra(EXTRA_JOB_ID, jobId)
                    putExtra(EXTRA_JOB_NAME, jobName)
                    putExtra(EXTRA_NOTIF_ID, notifId)
                }
                val startPi = PendingIntent.getBroadcast(
                    context,
                    notifId + 100,
                    startAction,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, "지금 근무 시작", startPi)
            }
            ReminderKind.POST_SHIFT_CONFIRM -> {
                val confirmAction = Intent(context, ShiftReminderReceiver::class.java).apply {
                    action = ACTION_REMINDER_CONFIRM_SHIFT
                    putExtra(EXTRA_JOB_ID, jobId)
                    putExtra(EXTRA_DATE_ISO, dateIso)
                    putExtra(EXTRA_DAY_OF_WEEK, dow)
                    putExtra(EXTRA_NOTIF_ID, notifId)
                }
                val confirmPi = PendingIntent.getBroadcast(
                    context,
                    notifId + 200,
                    confirmAction,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, "정상 근무로 확정", confirmPi)
            }
            ReminderKind.PAYDAY -> {
                val paidAction = Intent(context, ShiftReminderReceiver::class.java).apply {
                    action = ACTION_REMINDER_MARK_PAYDAY_PAID
                    putExtra(EXTRA_JOB_ID, jobId)
                    putExtra(EXTRA_DATE_ISO, dateIso)
                    putExtra(EXTRA_NOTIF_ID, notifId)
                }
                val paidPi = PendingIntent.getBroadcast(
                    context,
                    notifId + 300,
                    paidAction,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, "입금 완료 처리", paidPi)
            }
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, builder.build())
    }

    private fun dismissNotification(context: Context, notifId: Int) {
        if (notifId >= 0) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notifId)
        }
    }

    companion object {
        const val ACTION_TRIGGER_REMINDER = "com.albatora.app.ACTION_TRIGGER_REMINDER"
        const val ACTION_REMINDER_START_SHIFT = "com.albatora.app.ACTION_REMINDER_START_SHIFT"
        const val ACTION_REMINDER_CONFIRM_SHIFT = "com.albatora.app.ACTION_REMINDER_CONFIRM_SHIFT"
        const val ACTION_REMINDER_MARK_PAYDAY_PAID = "com.albatora.app.ACTION_REMINDER_MARK_PAYDAY_PAID"

        const val EXTRA_KIND = "extra_kind"
        const val EXTRA_JOB_ID = "extra_job_id"
        const val EXTRA_JOB_NAME = "extra_job_name"
        const val EXTRA_DATE_ISO = "extra_date_iso"
        const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
        const val EXTRA_START_MINUTE = "extra_start_minute"
        const val EXTRA_END_MINUTE = "extra_end_minute"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }
}
