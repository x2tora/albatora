package com.albatora.app

import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PayrollCalculatorTest {

    @Test
    fun `initial empty state has zero jobs, empty tangibles, and 2026 minimum wage`() {
        val empty = AlbaToraDataState.initialEmptyState()
        assertTrue(empty.jobs.isEmpty())
        assertTrue(empty.records.isEmpty())
        assertTrue(empty.templates.isEmpty())
        assertTrue(empty.wishlistGoals.isEmpty())
        assertTrue(empty.tangibleItems.isEmpty())
        assertEquals(10320L, empty.settings.defaultMinWage)
    }

    @Test
    fun `recommended break calculation follows 4h 30m and 8h 60m`() {
        assertEquals(0, PayrollCalculator.recommendedBreakMinutes(180))
        assertEquals(30, PayrollCalculator.recommendedBreakMinutes(240))
        assertEquals(30, PayrollCalculator.recommendedBreakMinutes(420))
        assertEquals(60, PayrollCalculator.recommendedBreakMinutes(480))
        assertEquals(90, PayrollCalculator.recommendedBreakMinutes(720))
    }

    @Test
    fun `daily worker withholding applies 150k exemption and 2_7 or 2_97 percent`() {
        assertEquals(0L, PayrollCalculator.computeTaxAmount(150_000L, TaxMode.DAILY_WORKER, 0.0, true))
        assertEquals(297L, PayrollCalculator.computeTaxAmount(160_000L, TaxMode.DAILY_WORKER, 0.0, true))
        assertEquals(270L, PayrollCalculator.computeTaxAmount(160_000L, TaxMode.DAILY_WORKER, 0.0, false))
        assertEquals(1_485L, PayrollCalculator.computeTaxAmount(200_000L, TaxMode.DAILY_WORKER, 0.0, true))
    }

    @Test
    fun `short term net profit includes site bonus minus tax agency fee and expenses`() {
        val record = WorkRecord(
            id = "r1",
            title = "물류센터",
            kind = JobKind.SHORT_TERM,
            date = "2026-11-10",
            startMinute = 9 * 60,
            endMinute = 18 * 60,
            payType = PayType.DAILY,
            unitAmount = 200_000L,
            breakMinutes = 60,
            breakPayType = BreakPayType.UNPAID,
            transportExpense = 3_000L,
            mealExpense = 9_000L,
            siteBonus = 20_000L,
            agencyFeeValue = 10.0,
            agencyFeeType = FeeType.PERCENT,
            taxMode = TaxMode.DAILY_WORKER,
            dailyWorkerIncludeLocalTax = true
        )
        val br = PayrollCalculator.calculateShiftFinancials(record)
        // gross = 200,000 + 20,000 = 220,000
        assertEquals(220_000L, br.grossPay)
        // agency 10% = 22,000
        assertEquals(22_000L, br.agencyFee)
        // daily tax on (220,000 - 150,000) * 0.0297 = 2,079
        assertEquals(2_079L, br.taxAmount)
        // net = 220,000 - 22,000 - 2,079 - 3,000 - 9,000 = 183,921
        assertEquals(183_921L, br.netPay)
    }

    @Test
    fun `wage revision history preserves earlier shifts at old hourly rate`() {
        val job = Job(
            id = "j1",
            name = "카페",
            payType = PayType.HOURLY,
            baseAmount = 10_000L,
            wageHistory = listOf(
                WageRevision("2026-01-01", 10_320L),
                WageRevision("2026-11-01", 11_000L)
            )
        )
        assertEquals(10_320L, PayrollCalculator.effectiveJobWage(job, LocalDate.of(2026, 10, 31)))
        assertEquals(11_000L, PayrollCalculator.effectiveJobWage(job, LocalDate.of(2026, 11, 1)))
        assertEquals(10_000L, PayrollCalculator.effectiveJobWage(job, LocalDate.of(2025, 12, 31)))
    }

    @Test
    fun `weekly holiday pay calculates with 40h cap and absence exclusion`() {
        val job = Job(
            id = "j1",
            name = "편의점",
            payType = PayType.HOURLY,
            baseAmount = 10_000L,
            weeklyHoliday = WeeklyHolidayRule(
                enabled = true,
                excludeOnAbsenceOrEarlyLeave = true,
                forceIncludeUnder15h = false
            )
        )
        val weekRecords = listOf(
            WorkRecord(
                id = "1", jobId = "j1", title = "편의점", date = "2026-11-09",
                startMinute = 9 * 60, endMinute = 18 * 60, breakMinutes = 60,
                breakPayType = BreakPayType.UNPAID, unitAmount = 10_000L
            ),
            WorkRecord(
                id = "2", jobId = "j1", title = "편의점", date = "2026-11-10",
                startMinute = 9 * 60, endMinute = 18 * 60, breakMinutes = 60,
                breakPayType = BreakPayType.UNPAID, unitAmount = 10_000L
            ),
            WorkRecord(
                id = "3", jobId = "j1", title = "편의점", date = "2026-11-11",
                startMinute = 9 * 60, endMinute = 18 * 60, breakMinutes = 60,
                breakPayType = BreakPayType.UNPAID, unitAmount = 10_000L
            ),
            WorkRecord(
                id = "4", jobId = "j1", title = "편의점", date = "2026-11-12",
                startMinute = 9 * 60, endMinute = 18 * 60, breakMinutes = 60,
                breakPayType = BreakPayType.UNPAID, unitAmount = 10_000L
            ),
            WorkRecord(
                id = "5", jobId = "j1", title = "편의점", date = "2026-11-13",
                startMinute = 9 * 60, endMinute = 18 * 60, breakMinutes = 60,
                breakPayType = BreakPayType.UNPAID, unitAmount = 10_000L
            )
        )
        // 5 * 8h = 40h -> 8h * 10,000 = 80,000
        val summaries = PayrollCalculator.calculateWeeklyHolidaySummaries(job, weekRecords)
        assertEquals(1, summaries.size)
        assertEquals(80_000L, summaries.first().weeklyHolidayPay)

        // Adding absence removes weekly holiday pay when excludeOnAbsenceOrEarlyLeave = true
        val withAbsence = weekRecords.dropLast(1) + weekRecords.last().copy(status = ShiftStatus.ABSENT)
        val excludedSummaries = PayrollCalculator.calculateWeeklyHolidaySummaries(job, withAbsence)
        assertFalse(excludedSummaries.first().eligible)
        assertEquals(0L, excludedSummaries.first().weeklyHolidayPay)
    }

    @Test
    fun `night boost only applies when enabled on job and after 22 00`() {
        val baseRecord = WorkRecord(
            id = "n1",
            title = "야간편의점",
            date = "2026-11-13",
            startMinute = 20 * 60,
            endMinute = 24 * 60,
            payType = PayType.HOURLY,
            unitAmount = 10_000L,
            breakMinutes = 0,
            allowance = AllowanceSettings(nightAllowanceEnabled = false)
        )
        val withoutBoost = PayrollCalculator.calculateShiftFinancials(baseRecord)
        assertEquals(40_000L, withoutBoost.basePay)
        assertEquals(0L, withoutBoost.allowancePay)

        val withBoost = PayrollCalculator.calculateShiftFinancials(
            baseRecord.copy(allowance = AllowanceSettings(nightAllowanceEnabled = true))
        )
        // 20~22 (2h * 10,000) + 22~24 (2h * 10,000 * 1.5) = 40,000 + 10,000
        assertEquals(40_000L, withBoost.basePay)
        assertEquals(10_000L, withBoost.allowancePay)
        assertEquals(50_000L, withBoost.netPay)
    }

    @Test
    fun `payday adjustment shifts weekend and Korean holidays`() {
        // 2026-10-10 is Saturday -> prev business day is 2026-10-08? Wait: 2026-10-09 is Hangul Day (Fri)!
        // So prev business day before Oct 10 (Sat) skips Oct 9 (Hangul Day) -> Oct 8 (Thu)!
        val nominal = LocalDate.of(2026, 10, 10)
        assertEquals(LocalDate.of(2026, 10, 8), PayrollCalculator.adjustPayday(nominal, PaydayHolidayRule.PREV_BUSINESS_DAY))
        // Next business day after Oct 10 (Sat) skips Oct 11 (Sun) -> Oct 12 (Mon)!
        assertEquals(LocalDate.of(2026, 10, 12), PayrollCalculator.adjustPayday(nominal, PaydayHolidayRule.NEXT_BUSINESS_DAY))
        assertEquals(nominal, PayrollCalculator.adjustPayday(nominal, PaydayHolidayRule.EXACT_DAY))
    }

    @Test
    fun `concurrent live shifts combine earnings and trigger boost badges`() {
        val zone = ZoneId.systemDefault()
        // 2026-11-13 22:30 (Friday night -> triggers night boost if enabled!)
        val nowMs = LocalDateTime.of(2026, 11, 13, 22, 30)
            .atZone(zone).toInstant().toEpochMilli()
        val sessionStartMs = LocalDateTime.of(2026, 11, 13, 22, 0)
            .atZone(zone).toInstant().toEpochMilli()

        val state = AlbaToraDataState.initialEmptyState().copy(
            jobs = listOf(
                Job(
                    id = "reg1",
                    name = "편의점",
                    kind = JobKind.REGULAR,
                    payType = PayType.HOURLY,
                    baseAmount = 10_000L,
                    scheduleStartDate = "2026-11-01",
                    schedules = listOf(
                        ScheduleSlot(dayOfWeek = 5, startMinute = 20 * 60, endMinute = 24 * 60)
                    ),
                    allowance = AllowanceSettings(nightAllowanceEnabled = true)
                )
            ),
            activeStopwatches = listOf(
                ActiveStopwatchSession(
                    id = "sw1",
                    title = "배달 대타",
                    kind = JobKind.SHORT_TERM,
                    payType = PayType.HOURLY,
                    unitAmount = 12_000L,
                    startEpochMillis = sessionStartMs,
                    allowance = AllowanceSettings(nightAllowanceEnabled = false)
                )
            )
        )
        val live = PayrollCalculator.evaluateLiveDashboard(state, nowMs)
        assertEquals(2, live.activeShifts.size)
        val regularLive = live.activeShifts.first { !it.isManualStopwatch }
        assertTrue(regularLive.isBoostActive)
        assertEquals(1.5, regularLive.boostMultiplier, 0.01)
        val manualLive = live.activeShifts.first { it.isManualStopwatch }
        assertFalse(manualLive.isBoostActive)
        assertTrue(live.totalEarnedWon > regularLive.liveEarnedWon)
    }

    @Test
    fun `unpaid alerts wait until scheduled payday on Dec 10 for Nov 14 shift`() {
        val job = Job(
            id = "j_cafe",
            name = "카페",
            kind = JobKind.REGULAR,
            payType = PayType.HOURLY,
            baseAmount = 10_320L,
            scheduleStartDate = "2026-11-14",
            settlementMode = SettlementPeriodMode.FIRST_TO_END,
            settlementPrevMonthCycle = true,
            paydayDay = 10,
            paydayHolidayRule = PaydayHolidayRule.PREV_BUSINESS_DAY
        )
        val nov14Record = WorkRecord(
            id = "rec_nov14",
            jobId = "j_cafe",
            title = "카페",
            kind = JobKind.REGULAR,
            date = "2026-11-14",
            startMinute = 9 * 60,
            endMinute = 14 * 60,
            payType = PayType.HOURLY,
            unitAmount = 10_320L,
            paymentStatus = PaymentStatus.UNPAID
        )
        val standaloneDayLabor = WorkRecord(
            id = "rec_daily",
            jobId = null,
            title = "단기 물류",
            kind = JobKind.SHORT_TERM,
            date = "2026-11-14",
            startMinute = 9 * 60,
            endMinute = 18 * 60,
            payType = PayType.DAILY,
            unitAmount = 120_000L,
            paymentStatus = PaymentStatus.UNPAID
        )

        assertEquals(
            LocalDate.of(2026, 12, 10),
            PayrollCalculator.expectedPaydayForRecord(job, LocalDate.of(2026, 11, 14))
        )
        // Even if settlementPrevMonthCycle was false on FIRST_TO_END with payday 10th, Nov 14 cannot be due on Nov 10
        assertEquals(
            LocalDate.of(2026, 12, 10),
            PayrollCalculator.expectedPaydayForRecord(
                job.copy(settlementPrevMonthCycle = false),
                LocalDate.of(2026, 11, 14)
            )
        )

        val stateWithJobOnly = AlbaToraDataState.initialEmptyState().copy(
            jobs = listOf(job),
            records = listOf(nov14Record)
        )
        assertTrue(PayrollCalculator.calculateGlobalUnpaidSummary(stateWithJobOnly, LocalDate.of(2026, 11, 15)).second.isEmpty())
        assertTrue(PayrollCalculator.calculateGlobalUnpaidSummary(stateWithJobOnly, LocalDate.of(2026, 12, 10)).second.isEmpty())
        assertEquals(1, PayrollCalculator.calculateGlobalUnpaidSummary(stateWithJobOnly, LocalDate.of(2026, 12, 11)).second.size)

        val stateWithStandalone = AlbaToraDataState.initialEmptyState().copy(
            jobs = listOf(job),
            records = listOf(nov14Record, standaloneDayLabor)
        )
        val nov15Unpaid = PayrollCalculator.calculateGlobalUnpaidSummary(stateWithStandalone, LocalDate.of(2026, 11, 15)).second
        assertEquals(1, nov15Unpaid.size)
        assertEquals("rec_daily", nov15Unpaid.first().record.id)
    }

    @Test
    fun findsRecoverableClockedOutShiftOnlyWhenWithinWindowAndRecordDeleted() {
        val shiftDate = LocalDate.of(2026, 11, 15) // Sunday = 7
        val job = Job(
            id = "job_recover",
            name = "편의점 야간",
            kind = JobKind.REGULAR,
            payType = PayType.HOURLY,
            baseAmount = 12_000L,
            scheduleStartDate = "2026-11-01",
            schedules = listOf(ScheduleSlot(dayOfWeek = 7, startMinute = 9 * 60, endMinute = 18 * 60))
        )
        val korZone = java.time.ZoneId.of("Asia/Seoul")
        val startMs = LocalDateTime.of(shiftDate, java.time.LocalTime.of(9, 0))
            .atZone(korZone).toInstant().toEpochMilli()
        val inShiftNowMs = LocalDateTime.of(shiftDate, java.time.LocalTime.of(13, 30))
            .atZone(korZone).toInstant().toEpochMilli()
        val afterShiftNowMs = LocalDateTime.of(shiftDate, java.time.LocalTime.of(18, 30))
            .atZone(korZone).toInstant().toEpochMilli()

        val runtimeKey = "${job.id}_$startMs"
        val clockedOutState = AlbaToraDataState.initialEmptyState().copy(
            jobs = listOf(job),
            scheduledRuntimes = listOf(
                ScheduledShiftRuntime(
                    key = runtimeKey,
                    jobId = job.id,
                    clockedOutAtEpochMillis = inShiftNowMs - 600_000L
                )
            )
        )

        // 1. Inside shift window & record deleted -> recoverable
        val found = PayrollCalculator.findRecoverableClockedOutShifts(clockedOutState, inShiftNowMs)
        assertEquals(1, found.size)
        assertEquals(runtimeKey, found.first().runtimeKey)

        // 2. Outside shift window -> not recoverable
        assertTrue(PayrollCalculator.findRecoverableClockedOutShifts(clockedOutState, afterShiftNowMs).isEmpty())

        // 3. Record still exists -> not recoverable
        val withRecord = clockedOutState.copy(
            records = listOf(
                WorkRecord(
                    id = "rec_saved",
                    jobId = job.id,
                    title = job.name,
                    kind = JobKind.REGULAR,
                    date = "2026-11-15",
                    startMinute = 9 * 60,
                    endMinute = 13 * 60 + 20,
                    payType = PayType.HOURLY,
                    unitAmount = 12_000L
                )
            )
        )
        assertTrue(PayrollCalculator.findRecoverableClockedOutShifts(withRecord, inShiftNowMs).isEmpty())
    }
}
