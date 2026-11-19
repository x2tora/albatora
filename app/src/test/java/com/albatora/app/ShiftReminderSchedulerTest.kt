package com.albatora.app

import com.albatora.app.model.*
import com.albatora.app.service.ReminderKind
import com.albatora.app.service.ShiftReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ShiftReminderSchedulerTest {

    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun disabledRemindersProduceEmptyList() {
        val state = AlbaToraDataState(
            jobs = listOf(
                Job(
                    id = "job_1",
                    name = "카페",
                    schedules = listOf(ScheduleSlot(dayOfWeek = 1, startMinute = 9 * 60, endMinute = 15 * 60))
                )
            ),
            settings = AppSettings(
                preShiftReminderEnabled = false,
                postShiftReminderEnabled = false,
                paydayReminderEnabled = false
            )
        )
        val now = ZonedDateTime.of(LocalDate.of(2026, 11, 23), LocalTime.of(8, 0), zone).toInstant().toEpochMilli()
        val reminders = ShiftReminderScheduler.computeUpcomingReminders(state, now, zone)
        assertTrue(reminders.isEmpty())
    }

    @Test
    fun preShiftAndOvernightPostShiftRemindersComputeExactTriggerTimes() {
        // 2026-11-23 is Monday (dayOfWeek = 1)
        val monday = LocalDate.of(2026, 11, 23)
        val state = AlbaToraDataState(
            jobs = listOf(
                Job(
                    id = "job_night",
                    name = "야간 편의점",
                    schedules = listOf(
                        ScheduleSlot(dayOfWeek = 1, startMinute = 22 * 60, endMinute = 6 * 60)
                    )
                )
            ),
            settings = AppSettings(
                preShiftReminderEnabled = true,
                preShiftReminderMinutes = 15,
                postShiftReminderEnabled = true,
                paydayReminderEnabled = false
            )
        )
        val now = ZonedDateTime.of(monday, LocalTime.of(21, 0), zone).toInstant().toEpochMilli()
        val reminders = ShiftReminderScheduler.computeUpcomingReminders(state, now, zone)

        val firstPre = reminders.first { it.kind == ReminderKind.PRE_SHIFT }
        val expectedPre = ZonedDateTime.of(monday, LocalTime.of(21, 45), zone).toInstant().toEpochMilli()
        assertEquals(expectedPre, firstPre.triggerAtMillis)

        val firstPost = reminders.first { it.kind == ReminderKind.POST_SHIFT_CONFIRM }
        val expectedPost = ZonedDateTime.of(monday.plusDays(1), LocalTime.of(6, 15), zone).toInstant().toEpochMilli()
        assertEquals(expectedPost, firstPost.triggerAtMillis)
    }

    @Test
    fun explicitRecordSuppressesScheduledPreShiftReminderOnSameDate() {
        val monday = LocalDate.of(2026, 11, 23)
        val state = AlbaToraDataState(
            jobs = listOf(
                Job(
                    id = "job_1",
                    name = "카페",
                    schedules = listOf(
                        ScheduleSlot(dayOfWeek = 1, startMinute = 10 * 60, endMinute = 14 * 60)
                    )
                )
            ),
            records = listOf(
                WorkRecord(
                    id = "rec_override",
                    jobId = "job_1",
                    title = "카페",
                    date = "2026-11-23",
                    startMinute = 10 * 60,
                    endMinute = 14 * 60
                )
            ),
            settings = AppSettings(preShiftReminderEnabled = true, preShiftReminderMinutes = 10)
        )
        val now = ZonedDateTime.of(monday, LocalTime.of(8, 0), zone).toInstant().toEpochMilli()
        val reminders = ShiftReminderScheduler.computeUpcomingReminders(state, now, zone)
        assertTrue(reminders.none { it.dateIso == "2026-11-23" })
    }
}
