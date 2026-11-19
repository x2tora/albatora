package com.albatora.app.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.AlbaToraDataState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class ReminderKind {
    PRE_SHIFT,
    POST_SHIFT_CONFIRM,
    PAYDAY
}

data class ScheduledReminder(
    val requestCode: Int,
    val kind: ReminderKind,
    val triggerAtMillis: Long,
    val jobId: String,
    val jobName: String,
    val dateIso: String,
    val dayOfWeek: Int = 1,
    val startMinute: Int = 0,
    val endMinute: Int = 0,
    val title: String,
    val message: String
)

object ShiftReminderScheduler {

    const val CHANNEL_ID = "albatora_smart_reminders"
    private const val MAX_SCHEDULED_ALARMS = 12

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "출퇴근 · 월급날 스마트 알림",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "근무 시작 전, 퇴근 후 기록 확정, 월급날 입금 확인 알림"
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun sync(context: Context, state: AlbaToraDataState, nowMillis: Long = System.currentTimeMillis()) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        ensureChannel(context)

        // Cancel previously registered slots cleanly
        for (slotCode in 0 until MAX_SCHEDULED_ALARMS) {
            val cancelIntent = Intent(context, ShiftReminderReceiver::class.java).apply {
                action = ShiftReminderReceiver.ACTION_TRIGGER_REMINDER
            }
            val pi = PendingIntent.getBroadcast(
                context,
                BASE_REQUEST_CODE + slotCode,
                cancelIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }

        val reminders = computeUpcomingReminders(
            state = state,
            nowMillis = nowMillis,
            zoneId = ZoneId.systemDefault()
        ).take(MAX_SCHEDULED_ALARMS)

        reminders.forEachIndexed { index, reminder ->
            val intent = Intent(context, ShiftReminderReceiver::class.java).apply {
                action = ShiftReminderReceiver.ACTION_TRIGGER_REMINDER
                putExtra(ShiftReminderReceiver.EXTRA_KIND, reminder.kind.name)
                putExtra(ShiftReminderReceiver.EXTRA_JOB_ID, reminder.jobId)
                putExtra(ShiftReminderReceiver.EXTRA_JOB_NAME, reminder.jobName)
                putExtra(ShiftReminderReceiver.EXTRA_DATE_ISO, reminder.dateIso)
                putExtra(ShiftReminderReceiver.EXTRA_DAY_OF_WEEK, reminder.dayOfWeek)
                putExtra(ShiftReminderReceiver.EXTRA_START_MINUTE, reminder.startMinute)
                putExtra(ShiftReminderReceiver.EXTRA_END_MINUTE, reminder.endMinute)
                putExtra(ShiftReminderReceiver.EXTRA_TITLE, reminder.title)
                putExtra(ShiftReminderReceiver.EXTRA_MESSAGE, reminder.message)
                putExtra(ShiftReminderReceiver.EXTRA_NOTIF_ID, BASE_NOTIF_ID + index)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                BASE_REQUEST_CODE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pi)
        }
    }

    fun computeUpcomingReminders(
        state: AlbaToraDataState,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<ScheduledReminder> {
        val settings = state.settings
        if (!settings.preShiftReminderEnabled &&
            !settings.postShiftReminderEnabled &&
            !settings.paydayReminderEnabled
        ) {
            return emptyList()
        }

        val nowZdt = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val today = nowZdt.toLocalDate()
        val result = mutableListOf<ScheduledReminder>()

        val activeJobs = state.jobs.filter { !it.isArchived }
        val explicitRecordKeys = state.records.mapNotNull { rec ->
            rec.jobId?.let { "${it}_${rec.date}" }
        }.toSet()

        if (settings.preShiftReminderEnabled || settings.postShiftReminderEnabled) {
            val leadMinutes = settings.preShiftReminderMinutes.coerceIn(5, 120)
            for (offsetDays in 0..6) {
                val date = today.plusDays(offsetDays.toLong())
                val dateIso = PayrollCalculator.formatDate(date)
                val dow = date.dayOfWeek.value

                for (job in activeJobs) {
                    if (job.scheduleStartDate.isNotBlank()) {
                        val startDate = PayrollCalculator.parseDate(job.scheduleStartDate, today)
                        if (date.isBefore(startDate)) continue
                    }
                    if ("${job.id}_$dateIso" in explicitRecordKeys) continue

                    val slots = job.schedules.filter { it.dayOfWeek == dow }
                    for (slot in slots) {
                        val startTime = LocalTime.of(slot.startMinute / 60, slot.startMinute % 60)
                        val startZdt = ZonedDateTime.of(date, startTime, zoneId)

                        if (settings.preShiftReminderEnabled) {
                            val triggerMillis = startZdt.minusMinutes(leadMinutes.toLong()).toInstant().toEpochMilli()
                            if (triggerMillis > nowMillis) {
                                result += ScheduledReminder(
                                    requestCode = 0,
                                    kind = ReminderKind.PRE_SHIFT,
                                    triggerAtMillis = triggerMillis,
                                    jobId = job.id,
                                    jobName = job.name,
                                    dateIso = dateIso,
                                    dayOfWeek = dow,
                                    startMinute = slot.startMinute,
                                    endMinute = slot.endMinute,
                                    title = "${job.name} 출근 ${leadMinutes}분 전",
                                    message = "${PayrollCalculator.formatMinutes(slot.startMinute)} 근무 예정입니다. 바로 타이머를 시작할까요?"
                                )
                            }
                        }

                        if (settings.postShiftReminderEnabled) {
                            val endDate = if (slot.endMinute <= slot.startMinute) date.plusDays(1) else date
                            val endTime = LocalTime.of(slot.endMinute / 60, slot.endMinute % 60)
                            val endZdt = ZonedDateTime.of(endDate, endTime, zoneId)
                            val triggerMillis = endZdt.plusMinutes(15L).toInstant().toEpochMilli()
                            if (triggerMillis > nowMillis) {
                                result += ScheduledReminder(
                                    requestCode = 0,
                                    kind = ReminderKind.POST_SHIFT_CONFIRM,
                                    triggerAtMillis = triggerMillis,
                                    jobId = job.id,
                                    jobName = job.name,
                                    dateIso = dateIso,
                                    dayOfWeek = dow,
                                    startMinute = slot.startMinute,
                                    endMinute = slot.endMinute,
                                    title = "${job.name} 퇴근 기록 확정",
                                    message = "오늘 근무(${PayrollCalculator.formatMinutes(slot.startMinute)}~${PayrollCalculator.formatMinutes(slot.endMinute)})를 정상 근무로 확정할까요?"
                                )
                            }
                        }
                    }
                }
            }
        }

        if (settings.paydayReminderEnabled) {
            val paydays = PayrollCalculator.calculateNextPaydays(state, today)
            for (info in paydays) {
                if (info.dDay in 0..7) {
                    val triggerZdt = ZonedDateTime.of(info.adjustedPayday, LocalTime.of(10, 0), zoneId)
                    val triggerMillis = triggerZdt.toInstant().toEpochMilli()
                    if (triggerMillis > nowMillis) {
                        val dateIso = PayrollCalculator.formatDate(info.adjustedPayday)
                        result += ScheduledReminder(
                            requestCode = 0,
                            kind = ReminderKind.PAYDAY,
                            triggerAtMillis = triggerMillis,
                            jobId = info.jobId,
                            jobName = info.jobName,
                            dateIso = dateIso,
                            title = "오늘은 ${info.jobName} 월급날",
                            message = "입금 여부를 확인하고 미수금을 입금 완료로 정리해 보세요."
                        )
                    }
                }
            }
        }

        return result.sortedBy { it.triggerAtMillis }
    }

    private const val BASE_REQUEST_CODE = 7100
    private const val BASE_NOTIF_ID = 7200
}
