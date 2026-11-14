package com.albatora.app.domain

import com.albatora.app.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

data class ShiftFinancialBreakdown(
    val rawDurationMinutes: Int,
    val paidWorkMinutes: Int,
    val nightMinutes: Int,
    val overtimeMinutes: Int,
    val holidayMinutes: Int,
    val basePay: Long,
    val allowancePay: Long,
    val siteBonus: Long,
    val grossPay: Long,
    val agencyFee: Long,
    val taxAmount: Long,
    val transportExpense: Long,
    val mealExpense: Long,
    val netPay: Long
)

data class WeeklyHolidaySummary(
    val weekStartDate: LocalDate,
    val weekEndDate: LocalDate,
    val paidRegularMinutes: Int,
    val eligible: Boolean,
    val excludedByAbsenceOrEarlyLeave: Boolean,
    val weeklyHolidayPay: Long
)

data class LiveShiftSnapshot(
    val runtimeKey: String,
    val isManualStopwatch: Boolean,
    val stopwatchId: String?,
    val jobId: String?,
    val title: String,
    val kind: JobKind,
    val colorHex: Long,
    val startEpochMillis: Long,
    val plannedEndEpochMillis: Long?,
    val liveEarnedWon: Double,
    val perSecondRateWon: Double,
    val perMinuteRateWon: Double,
    val progressFraction: Float?,
    val remainingMillis: Long?,
    val elapsedWorkMillis: Long,
    val isPaused: Boolean,
    val isManualPaused: Boolean,
    val pauseReason: String?,
    val isBoostActive: Boolean,
    val boostMultiplier: Double,
    val boostReasons: List<String>,
    val payType: PayType,
    val unitAmount: Long
)

data class LiveDashboardSnapshot(
    val activeShifts: List<LiveShiftSnapshot> = emptyList(),
    val totalEarnedWon: Double = 0.0,
    val totalPerSecondRateWon: Double = 0.0,
    val totalPerMinuteRateWon: Double = 0.0,
    val anyBoostActive: Boolean = false,
    val maxBoostMultiplier: Double = 1.0
)

data class ResolvedRecordItem(
    val record: WorkRecord,
    val job: Job?,
    val isVirtualSchedule: Boolean,
    val breakdown: ShiftFinancialBreakdown
)

data class CalendarDayScheduleItem(
    val title: String,
    val colorHex: Long,
    val jobId: String?,
    val record: WorkRecord?,
    val scheduleSlot: ScheduleSlot?,
    val status: ShiftStatus,
    val isVirtualFuture: Boolean,
    val isCurrentlyLive: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    val estimatedNetPay: Long,
    val paymentStatus: PaymentStatus?
)

data class SettlementSummary(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val totalWorkMinutes: Int,
    val shiftCount: Int,
    val totalBasePay: Long,
    val totalAllowancePay: Long,
    val totalWeeklyHolidayPay: Long,
    val totalSiteBonus: Long,
    val totalGrossPay: Long,
    val totalAgencyFee: Long,
    val totalTaxAmount: Long,
    val totalExpenses: Long,
    val totalNetPay: Long,
    val unpaidAmount: Long,
    val unpaidCount: Int,
    val items: List<ResolvedRecordItem>,
    val weeklyHolidayBreakdown: List<WeeklyHolidaySummary>
)

data class PaydayCountdownInfo(
    val jobId: String,
    val jobName: String,
    val colorHex: Long,
    val nominalPayday: LocalDate,
    val adjustedPayday: LocalDate,
    val dDay: Long,
    val wasAdjusted: Boolean,
    val estimatedPeriodNetPay: Long
)

object PayrollCalculator {

    val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val KOR_ZONE: ZoneId = ZoneId.systemDefault()

    fun parseDate(dateStr: String, fallback: LocalDate = LocalDate.now()): LocalDate =
        runCatching { LocalDate.parse(dateStr, DATE_FMT) }.getOrDefault(fallback)

    fun formatDate(date: LocalDate): String = date.format(DATE_FMT)

    fun formatMinutes(minuteOfDay: Int): String {
        val m = ((minuteOfDay % 1440) + 1440) % 1440
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
    }

    fun rawDurationMinutes(startMinute: Int, endMinute: Int): Int {
        val diff = endMinute - startMinute
        return if (diff > 0) diff else (1440 - startMinute) + endMinute
    }

    fun recommendedBreakMinutes(totalMinutes: Int): Int {
        if (totalMinutes < 240) return 0
        return (totalMinutes / 240) * 30
    }

    fun effectiveJobWage(job: Job, date: LocalDate): Long {
        if (job.wageHistory.isEmpty()) return job.baseAmount
        val dateIso = formatDate(date)
        val matched = job.wageHistory
            .filter { it.effectiveFromDate <= dateIso }
            .maxByOrNull { it.effectiveFromDate }
        return matched?.hourlyOrBaseAmount ?: job.baseAmount
    }

    private val variableHolidays: Set<LocalDate> = buildSet {
        // 2025 KR lunar & substitute holidays
        listOf(
            "2025-01-27", "2025-01-28", "2025-01-29", "2025-01-30",
            "2025-03-03", "2025-05-05", "2025-05-06", "2025-06-03",
            "2025-10-05", "2025-10-06", "2025-10-07", "2025-10-08",
            // 2026 KR lunar & substitute holidays
            "2026-02-16", "2026-02-17", "2026-02-18",
            "2026-03-02", "2026-05-24", "2026-05-25",
            "2026-06-03", "2026-08-17",
            "2026-09-24", "2026-09-25", "2026-09-26",
            "2026-10-05",
            // 2027 KR lunar & substitute holidays
            "2027-02-06", "2027-02-07", "2027-02-08", "2027-02-09",
            "2027-05-13", "2027-08-16",
            "2027-09-14", "2027-09-15", "2027-09-16",
            "2027-10-04", "2027-10-11", "2027-12-27"
        ).forEach { add(LocalDate.parse(it)) }
    }

    fun isKoreanPublicHoliday(date: LocalDate): Boolean {
        val m = date.monthValue
        val d = date.dayOfMonth
        val fixed = (m == 1 && d == 1) ||
            (m == 3 && d == 1) ||
            (m == 5 && d == 5) ||
            (m == 6 && d == 6) ||
            (m == 7 && d == 17) ||
            (m == 8 && d == 15) ||
            (m == 10 && d == 3) ||
            (m == 10 && d == 9) ||
            (m == 12 && d == 25)
        return fixed || date in variableHolidays
    }

    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    fun isNonWorkingDayForPayday(date: LocalDate): Boolean =
        isWeekend(date) || isKoreanPublicHoliday(date)

    fun adjustPayday(nominalDate: LocalDate, rule: PaydayHolidayRule): LocalDate {
        if (rule == PaydayHolidayRule.EXACT_DAY) return nominalDate
        var cursor = nominalDate
        var guard = 0
        while (isNonWorkingDayForPayday(cursor) && guard < 14) {
            cursor = when (rule) {
                PaydayHolidayRule.PREV_BUSINESS_DAY -> cursor.minusDays(1)
                PaydayHolidayRule.NEXT_BUSINESS_DAY -> cursor.plusDays(1)
                PaydayHolidayRule.EXACT_DAY -> cursor
            }
            guard++
        }
        return cursor
    }

    private fun isHolidayMinute(date: LocalDate, allowance: AllowanceSettings, forceHolidayPay: Boolean): Boolean {
        if (!allowance.holidayAllowanceEnabled) return false
        if (forceHolidayPay) return true
        if (isKoreanPublicHoliday(date)) return true
        return allowance.holidayIncludesWeekends && isWeekend(date)
    }

    private fun isNightMinute(minuteOfDay: Int, allowance: AllowanceSettings): Boolean {
        if (!allowance.nightAllowanceEnabled) return false
        val m = ((minuteOfDay % 1440) + 1440) % 1440
        return m >= 22 * 60 || m < 6 * 60
    }

    private fun isInsideFixedBreak(
        minuteOfDay: Int,
        fixedStart: Int?,
        fixedEnd: Int?
    ): Boolean {
        if (fixedStart == null || fixedEnd == null || fixedStart == fixedEnd) return false
        val m = ((minuteOfDay % 1440) + 1440) % 1440
        val s = ((fixedStart % 1440) + 1440) % 1440
        val e = ((fixedEnd % 1440) + 1440) % 1440
        return if (s < e) m in s until e else (m >= s || m < e)
    }

    fun computeTaxAmount(
        taxableGross: Long,
        taxMode: TaxMode,
        customTaxRatePercent: Double,
        dailyWorkerIncludeLocalTax: Boolean
    ): Long {
        if (taxableGross <= 0L) return 0L
        return when (taxMode) {
            TaxMode.NONE -> 0L
            TaxMode.FREELANCE_3_3 -> (taxableGross * 0.033).roundToLong()
            TaxMode.INSURANCE_4 -> (taxableGross * 0.094).roundToLong()
            TaxMode.DAILY_WORKER -> {
                val excess = (taxableGross - 150_000L).coerceAtLeast(0L)
                val rate = if (dailyWorkerIncludeLocalTax) 0.0297 else 0.027
                (excess * rate).roundToLong()
            }
            TaxMode.CUSTOM -> (taxableGross * (customTaxRatePercent.coerceIn(0.0, 99.0) / 100.0)).roundToLong()
        }
    }

    fun computeAgencyFee(gross: Long, value: Double, feeType: FeeType): Long {
        if (gross <= 0L || value <= 0.0) return 0L
        return when (feeType) {
            FeeType.PERCENT -> (gross * (value.coerceIn(0.0, 99.0) / 100.0)).roundToLong()
            FeeType.FIXED -> value.roundToLong().coerceAtLeast(0L)
        }
    }

    fun calculateShiftFinancials(record: WorkRecord): ShiftFinancialBreakdown {
        if (record.status == ShiftStatus.ABSENT) {
            return ShiftFinancialBreakdown(
                rawDurationMinutes = 0,
                paidWorkMinutes = 0,
                nightMinutes = 0,
                overtimeMinutes = 0,
                holidayMinutes = 0,
                basePay = 0L,
                allowancePay = 0L,
                siteBonus = 0L,
                grossPay = 0L,
                agencyFee = 0L,
                taxAmount = 0L,
                transportExpense = record.transportExpense,
                mealExpense = record.mealExpense,
                netPay = -(record.transportExpense + record.mealExpense)
            )
        }

        val rawMinutes = rawDurationMinutes(record.startMinute, record.endMinute)
        val hasFixedUnpaidBreak = record.breakPayType == BreakPayType.UNPAID &&
            record.fixedBreakStartMinute != null && record.fixedBreakEndMinute != null &&
            record.fixedBreakStartMinute != record.fixedBreakEndMinute

        val startDate = parseDate(record.date)
        val shiftStartDt = LocalDateTime.of(
            startDate,
            LocalTime.of((record.startMinute / 60).coerceIn(0, 23), (record.startMinute % 60).coerceIn(0, 59))
        )

        var paidMinuteUnits = 0.0
        var nightMinCount = 0.0
        var overtimeMinCount = 0.0
        var holidayMinCount = 0.0
        var basePayExact = 0.0
        var allowanceExact = 0.0

        val nonFixedUnpaidRatio = if (!hasFixedUnpaidBreak && record.breakPayType == BreakPayType.UNPAID && rawMinutes > 0) {
            (rawMinutes - record.breakMinutes).coerceAtLeast(0).toDouble() / rawMinutes.toDouble()
        } else {
            1.0
        }

        val perMinBaseRate = when (record.payType) {
            PayType.HOURLY -> record.unitAmount / 60.0
            PayType.DAILY, PayType.PER_TASK -> 0.0
        }

        var accumulatedPaidUnits = 0.0
        var fixedBreakSkippedCount = 0

        for (offset in 0 until rawMinutes) {
            val currentDt = shiftStartDt.plusMinutes(offset.toLong())
            val minuteOfDay = currentDt.hour * 60 + currentDt.minute
            if (hasFixedUnpaidBreak && isInsideFixedBreak(minuteOfDay, record.fixedBreakStartMinute, record.fixedBreakEndMinute)) {
                fixedBreakSkippedCount++
                continue
            }

            val unitWeight = if (hasFixedUnpaidBreak) 1.0 else nonFixedUnpaidRatio
            accumulatedPaidUnits += unitWeight
            paidMinuteUnits += unitWeight

            val night = isNightMinute(minuteOfDay, record.allowance)
            val overtime = record.allowance.overtimeAllowanceEnabled &&
                (accumulatedPaidUnits > 480.0 || record.status == ShiftStatus.OVERTIME)
            val holiday = isHolidayMinute(currentDt.toLocalDate(), record.allowance, record.forceHolidayPay)

            if (night) nightMinCount += unitWeight
            if (overtime) overtimeMinCount += unitWeight
            if (holiday) holidayMinCount += unitWeight

            if (record.payType == PayType.HOURLY) {
                var extraMult = 0.0
                if (night) extraMult += 0.5
                if (overtime) extraMult += 0.5
                if (holiday) extraMult += 0.5
                basePayExact += perMinBaseRate * unitWeight
                allowanceExact += perMinBaseRate * unitWeight * extraMult
            }
        }

        val effectivePaidMinutes = if (hasFixedUnpaidBreak) {
            (rawMinutes - fixedBreakSkippedCount).coerceAtLeast(0)
        } else if (record.breakPayType == BreakPayType.UNPAID) {
            (rawMinutes - record.breakMinutes).coerceAtLeast(0)
        } else {
            rawMinutes
        }

        val basePay: Long
        val allowancePay: Long
        when (record.payType) {
            PayType.HOURLY -> {
                basePay = basePayExact.roundToLong()
                allowancePay = allowanceExact.roundToLong()
            }
            PayType.DAILY, PayType.PER_TASK -> {
                basePay = record.unitAmount
                allowancePay = if (record.allowance.overtimeAllowanceEnabled && record.status == ShiftStatus.OVERTIME) {
                    (record.unitAmount * 0.5).roundToLong()
                } else {
                    0L
                }
            }
        }

        val grossPay = basePay + allowancePay + record.siteBonus
        val agencyFee = computeAgencyFee(grossPay, record.agencyFeeValue, record.agencyFeeType)
        val taxAmount = computeTaxAmount(
            taxableGross = grossPay,
            taxMode = record.taxMode,
            customTaxRatePercent = record.customTaxRatePercent,
            dailyWorkerIncludeLocalTax = record.dailyWorkerIncludeLocalTax
        )
        val netPay = grossPay - agencyFee - taxAmount - record.transportExpense - record.mealExpense

        return ShiftFinancialBreakdown(
            rawDurationMinutes = rawMinutes,
            paidWorkMinutes = effectivePaidMinutes,
            nightMinutes = nightMinCount.roundToLong().toInt(),
            overtimeMinutes = overtimeMinCount.roundToLong().toInt(),
            holidayMinutes = holidayMinCount.roundToLong().toInt(),
            basePay = basePay,
            allowancePay = allowancePay,
            siteBonus = record.siteBonus,
            grossPay = grossPay,
            agencyFee = agencyFee,
            taxAmount = taxAmount,
            transportExpense = record.transportExpense,
            mealExpense = record.mealExpense,
            netPay = netPay
        )
    }

    fun calculateWeeklyHolidaySummaries(
        job: Job,
        recordsForJob: List<WorkRecord>
    ): List<WeeklyHolidaySummary> {
        if (job.payType != PayType.HOURLY || !job.weeklyHoliday.enabled || recordsForJob.isEmpty()) {
            return emptyList()
        }
        val weekFields = WeekFields.ISO
        val grouped = recordsForJob.groupBy { rec ->
            val d = parseDate(rec.date)
            d.get(weekFields.weekBasedYear()) to d.get(weekFields.weekOfWeekBasedYear())
        }
        return grouped.entries.sortedBy { it.key.first * 100 + it.key.second }.map { (_, weekRecords) ->
            val firstDate = weekRecords.minOf { parseDate(it.date) }
            val weekStart = firstDate.with(DayOfWeek.MONDAY)
            val weekEnd = weekStart.plusDays(6)
            val hasAbsOrEarly = weekRecords.any {
                it.status == ShiftStatus.ABSENT || it.status == ShiftStatus.EARLY_LEAVE
            }
            val totalPaidMinutes = weekRecords.sumOf { rec ->
                if (rec.status == ShiftStatus.ABSENT) 0
                else if (rec.breakPayType == BreakPayType.UNPAID) (rawDurationMinutes(rec.startMinute, rec.endMinute) - rec.breakMinutes).coerceAtLeast(0)
                else rawDurationMinutes(rec.startMinute, rec.endMinute)
            }
            val excludedByRule = job.weeklyHoliday.excludeOnAbsenceOrEarlyLeave && hasAbsOrEarly
            val meetsHours = totalPaidMinutes >= 15 * 60 || (job.weeklyHoliday.forceIncludeUnder15h && totalPaidMinutes > 0)
            val eligible = !excludedByRule && meetsHours
            val cappedHours = (totalPaidMinutes.coerceAtMost(40 * 60)) / 60.0
            val avgWage = effectiveJobWage(job, weekEnd)
            val weeklyHolidayPay = if (eligible) {
                ((cappedHours / 40.0) * 8.0 * avgWage).roundToLong()
            } else {
                0L
            }
            WeeklyHolidaySummary(
                weekStartDate = weekStart,
                weekEndDate = weekEnd,
                paidRegularMinutes = totalPaidMinutes,
                eligible = eligible,
                excludedByAbsenceOrEarlyLeave = excludedByRule,
                weeklyHolidayPay = weeklyHolidayPay
            )
        }
    }

    fun getSettlementPeriod(
        job: Job,
        anchorMonth: YearMonth
    ): Pair<LocalDate, LocalDate> {
        val targetBaseMonth = if (job.settlementPrevMonthCycle) anchorMonth.minusMonths(1) else anchorMonth
        return when (job.settlementMode) {
            SettlementPeriodMode.FIRST_TO_END -> {
                targetBaseMonth.atDay(1) to targetBaseMonth.atEndOfMonth()
            }
            SettlementPeriodMode.PREV_N_PLUS_1_TO_N -> {
                val n = job.settlementCutoffDay.coerceIn(1, 28)
                val startMonth = targetBaseMonth.minusMonths(1)
                val startDay = (n + 1).coerceAtMost(startMonth.lengthOfMonth())
                val endDay = n.coerceAtMost(targetBaseMonth.lengthOfMonth())
                startMonth.atDay(startDay) to targetBaseMonth.atDay(endDay)
            }
            SettlementPeriodMode.CUSTOM_RANGE -> {
                val sDay = job.settlementCustomStartDay.coerceIn(1, 31)
                val eDay = job.settlementCustomEndDay.coerceIn(1, 31)
                if (sDay <= eDay) {
                    val actualS = sDay.coerceAtMost(targetBaseMonth.lengthOfMonth())
                    val actualE = eDay.coerceAtMost(targetBaseMonth.lengthOfMonth())
                    targetBaseMonth.atDay(actualS) to targetBaseMonth.atDay(actualE)
                } else {
                    val prevM = targetBaseMonth.minusMonths(1)
                    val actualS = sDay.coerceAtMost(prevM.lengthOfMonth())
                    val actualE = eDay.coerceAtMost(targetBaseMonth.lengthOfMonth())
                    prevM.atDay(actualS) to targetBaseMonth.atDay(actualE)
                }
            }
        }
    }

    fun nominalPaydayForMonth(job: Job, month: YearMonth): LocalDate {
        val day = job.paydayDay.coerceIn(1, month.lengthOfMonth())
        return month.atDay(day)
    }

    fun resolveEarnedRecords(
        state: AlbaToraDataState,
        fromDate: LocalDate,
        toDate: LocalDate,
        now: LocalDateTime = LocalDateTime.now()
    ): List<ResolvedRecordItem> {
        val jobsById = state.jobs.associateBy { it.id }
        val explicitInRange = state.records.filter {
            val d = parseDate(it.date)
            !d.isBefore(fromDate) && !d.isAfter(toDate)
        }
        val explicitKeys = explicitInRange.mapNotNull { rec -> rec.jobId?.let { "$it|${rec.date}" } }.toSet()

        val items = mutableListOf<ResolvedRecordItem>()
        explicitInRange.forEach { rec ->
            val job = rec.jobId?.let { jobsById[it] }
            items += ResolvedRecordItem(
                record = rec,
                job = job,
                isVirtualSchedule = false,
                breakdown = calculateShiftFinancials(rec)
            )
        }

        val today = now.toLocalDate()
        val effectiveEnd = if (toDate.isAfter(today)) today else toDate
        if (!effectiveEnd.isBefore(fromDate)) {
            state.jobs.filter { !it.isArchived && it.kind == JobKind.REGULAR && it.schedules.isNotEmpty() }.forEach { job ->
                val startGate = if (job.scheduleStartDate.isNotBlank()) parseDate(job.scheduleStartDate, today) else today
                val loopStart = if (fromDate.isAfter(startGate)) fromDate else startGate
                var cursor = loopStart
                while (!cursor.isAfter(effectiveEnd)) {
                    val dateStr = formatDate(cursor)
                    if ("${job.id}|$dateStr" !in explicitKeys) {
                        val matchingSlots = job.schedules.filter { it.dayOfWeek == cursor.dayOfWeek.value }
                        matchingSlots.forEachIndexed { idx, slot ->
                            val shiftStartDt = LocalDateTime.of(cursor, LocalTime.of(slot.startMinute / 60, slot.startMinute % 60))
                            val dur = rawDurationMinutes(slot.startMinute, slot.endMinute)
                            val shiftEndDt = shiftStartDt.plusMinutes(dur.toLong())
                            // Only include virtual scheduled shifts in settled records once shift ended (unless runtime clocked out earlier)
                            if (!shiftEndDt.isAfter(now)) {
                                val breakMins = if (slot.autoRecommendBreak) recommendedBreakMinutes(dur) else slot.breakMinutes
                                val wage = effectiveJobWage(job, cursor)
                                val virtualRec = WorkRecord(
                                    id = "virtual_${job.id}_${dateStr}_$idx",
                                    jobId = job.id,
                                    title = job.name,
                                    kind = JobKind.REGULAR,
                                    colorHex = job.colorHex,
                                    date = dateStr,
                                    startMinute = slot.startMinute,
                                    endMinute = slot.endMinute,
                                    status = ShiftStatus.NORMAL,
                                    payType = job.payType,
                                    unitAmount = wage,
                                    breakMinutes = breakMins,
                                    breakPayType = slot.breakPayType,
                                    fixedBreakStartMinute = slot.fixedBreakStartMinute,
                                    fixedBreakEndMinute = slot.fixedBreakEndMinute,
                                    taxMode = job.taxMode,
                                    customTaxRatePercent = job.customTaxRatePercent,
                                    dailyWorkerIncludeLocalTax = job.dailyWorkerIncludeLocalTax,
                                    allowance = job.allowance,
                                    paymentStatus = PaymentStatus.PAID
                                )
                                items += ResolvedRecordItem(
                                    record = virtualRec,
                                    job = job,
                                    isVirtualSchedule = true,
                                    breakdown = calculateShiftFinancials(virtualRec)
                                )
                            }
                        }
                    }
                    cursor = cursor.plusDays(1)
                }
            }
        }
        return items.sortedWith(compareByDescending<ResolvedRecordItem> { it.record.date }.thenByDescending { it.record.startMinute })
    }

    fun calculateSettlementSummary(
        state: AlbaToraDataState,
        fromDate: LocalDate,
        toDate: LocalDate,
        filterJobId: String? = null,
        onlyUnpaid: Boolean = false,
        now: LocalDateTime = LocalDateTime.now()
    ): SettlementSummary {
        var resolved = resolveEarnedRecords(state, fromDate, toDate, now)
        if (filterJobId != null) {
            resolved = resolved.filter { it.record.jobId == filterJobId }
        }
        if (onlyUnpaid) {
            resolved = resolved.filter { it.record.paymentStatus == PaymentStatus.UNPAID }
        }

        val jobsById = state.jobs.associateBy { it.id }
        val weeklySummaries = mutableListOf<WeeklyHolidaySummary>()
        val byJob = resolved.filter { it.record.jobId != null }.groupBy { it.record.jobId!! }
        byJob.forEach { (jobId, jobItems) ->
            val job = jobsById[jobId] ?: return@forEach
            if (!onlyUnpaid) {
                weeklySummaries += calculateWeeklyHolidaySummaries(job, jobItems.map { it.record })
            }
        }
        val weeklyHolidayTotal = weeklySummaries.sumOf { it.weeklyHolidayPay }
        val taxOnWeeklyHoliday = if (weeklyHolidayTotal > 0L) {
            // Apply each job's tax rate to its weekly holiday pay
            byJob.entries.sumOf { (jobId, _) ->
                val job = jobsById[jobId] ?: return@sumOf 0L
                val jobWh = calculateWeeklyHolidaySummaries(
                    job,
                    byJob[jobId].orEmpty().map { it.record }
                ).sumOf { it.weeklyHolidayPay }
                computeTaxAmount(
                    taxableGross = jobWh,
                    taxMode = job.taxMode,
                    customTaxRatePercent = job.customTaxRatePercent,
                    dailyWorkerIncludeLocalTax = job.dailyWorkerIncludeLocalTax
                )
            }
        } else 0L

        val baseSum = resolved.sumOf { it.breakdown.basePay }
        val allowanceSum = resolved.sumOf { it.breakdown.allowancePay }
        val bonusSum = resolved.sumOf { it.breakdown.siteBonus }
        val grossSum = resolved.sumOf { it.breakdown.grossPay } + weeklyHolidayTotal
        val agencySum = resolved.sumOf { it.breakdown.agencyFee }
        val taxSum = resolved.sumOf { it.breakdown.taxAmount } + taxOnWeeklyHoliday
        val expenseSum = resolved.sumOf { it.breakdown.transportExpense + it.breakdown.mealExpense }
        val netSum = resolved.sumOf { it.breakdown.netPay } + weeklyHolidayTotal - taxOnWeeklyHoliday
        val workMinutesSum = resolved.sumOf { it.breakdown.paidWorkMinutes }
        val unpaidItems = resolved.filter { it.record.paymentStatus == PaymentStatus.UNPAID }

        return SettlementSummary(
            periodStart = fromDate,
            periodEnd = toDate,
            totalWorkMinutes = workMinutesSum,
            shiftCount = resolved.count { it.record.status != ShiftStatus.ABSENT },
            totalBasePay = baseSum,
            totalAllowancePay = allowanceSum,
            totalWeeklyHolidayPay = weeklyHolidayTotal,
            totalSiteBonus = bonusSum,
            totalGrossPay = grossSum,
            totalAgencyFee = agencySum,
            totalTaxAmount = taxSum,
            totalExpenses = expenseSum,
            totalNetPay = netSum,
            unpaidAmount = unpaidItems.sumOf { it.breakdown.netPay },
            unpaidCount = unpaidItems.size,
            items = resolved,
            weeklyHolidayBreakdown = weeklySummaries
        )
    }

    fun calculateGlobalUnpaidSummary(state: AlbaToraDataState): Pair<Long, List<ResolvedRecordItem>> {
        val unpaid = state.records
            .filter { it.paymentStatus == PaymentStatus.UNPAID }
            .map { rec ->
                ResolvedRecordItem(
                    record = rec,
                    job = rec.jobId?.let { id -> state.jobs.find { it.id == id } },
                    isVirtualSchedule = false,
                    breakdown = calculateShiftFinancials(rec)
                )
            }
            .sortedByDescending { it.record.date }
        return unpaid.sumOf { it.breakdown.netPay } to unpaid
    }

    fun calculateNextPaydays(state: AlbaToraDataState, today: LocalDate = LocalDate.now()): List<PaydayCountdownInfo> {
        val currentMonth = YearMonth.from(today)
        return state.jobs.filter { !it.isArchived }.map { job ->
            val nomThis = nominalPaydayForMonth(job, currentMonth)
            val adjThis = adjustPayday(nomThis, job.paydayHolidayRule)
            val (targetMonth, nomDate, adjDate) = if (!adjThis.isBefore(today)) {
                Triple(currentMonth, nomThis, adjThis)
            } else {
                val nextM = currentMonth.plusMonths(1)
                val nomNext = nominalPaydayForMonth(job, nextM)
                Triple(nextM, nomNext, adjustPayday(nomNext, job.paydayHolidayRule))
            }
            val (pStart, pEnd) = getSettlementPeriod(job, targetMonth)
            val estSummary = calculateSettlementSummary(state, pStart, pEnd, filterJobId = job.id)
            PaydayCountdownInfo(
                jobId = job.id,
                jobName = job.name,
                colorHex = job.colorHex,
                nominalPayday = nomDate,
                adjustedPayday = adjDate,
                dDay = ChronoUnit.DAYS.between(today, adjDate),
                wasAdjusted = nomDate != adjDate,
                estimatedPeriodNetPay = estSummary.totalNetPay
            )
        }.sortedBy { it.dDay }
    }

    fun resolveCalendarDayItems(
        state: AlbaToraDataState,
        date: LocalDate,
        now: LocalDateTime = LocalDateTime.now()
    ): List<CalendarDayScheduleItem> {
        val dateStr = formatDate(date)
        val jobsById = state.jobs.associateBy { it.id }
        val explicit = state.records.filter { it.date == dateStr }
        val explicitJobIds = explicit.mapNotNull { it.jobId }.toSet()
        val result = mutableListOf<CalendarDayScheduleItem>()

        explicit.forEach { rec ->
            val job = rec.jobId?.let { jobsById[it] }
            val br = calculateShiftFinancials(rec)
            result += CalendarDayScheduleItem(
                title = rec.title,
                colorHex = rec.colorHex,
                jobId = job?.id,
                record = rec,
                scheduleSlot = null,
                status = rec.status,
                isVirtualFuture = date.isAfter(now.toLocalDate()),
                isCurrentlyLive = false,
                startMinute = rec.startMinute,
                endMinute = rec.endMinute,
                estimatedNetPay = br.netPay,
                paymentStatus = rec.paymentStatus
            )
        }

        val today = now.toLocalDate()
        state.jobs.filter { !it.isArchived && it.kind == JobKind.REGULAR }.forEach { job ->
            if (job.id in explicitJobIds) return@forEach
            val startGate = if (job.scheduleStartDate.isNotBlank()) parseDate(job.scheduleStartDate, today) else today
            if (date.isBefore(startGate)) return@forEach

            job.schedules.filter { it.dayOfWeek == date.dayOfWeek.value }.forEach { slot ->
                val dur = rawDurationMinutes(slot.startMinute, slot.endMinute)
                val breakMins = if (slot.autoRecommendBreak) recommendedBreakMinutes(dur) else slot.breakMinutes
                val wage = effectiveJobWage(job, date)
                val virtualRec = WorkRecord(
                    id = "cal_${job.id}_$dateStr",
                    jobId = job.id,
                    title = job.name,
                    kind = JobKind.REGULAR,
                    colorHex = job.colorHex,
                    date = dateStr,
                    startMinute = slot.startMinute,
                    endMinute = slot.endMinute,
                    status = ShiftStatus.NORMAL,
                    payType = job.payType,
                    unitAmount = wage,
                    breakMinutes = breakMins,
                    breakPayType = slot.breakPayType,
                    fixedBreakStartMinute = slot.fixedBreakStartMinute,
                    fixedBreakEndMinute = slot.fixedBreakEndMinute,
                    taxMode = job.taxMode,
                    customTaxRatePercent = job.customTaxRatePercent,
                    dailyWorkerIncludeLocalTax = job.dailyWorkerIncludeLocalTax,
                    allowance = job.allowance
                )
                val startDt = LocalDateTime.of(date, LocalTime.of(slot.startMinute / 60, slot.startMinute % 60))
                val endDt = startDt.plusMinutes(dur.toLong())
                val isLive = !now.isBefore(startDt) && now.isBefore(endDt)
                val br = calculateShiftFinancials(virtualRec)
                result += CalendarDayScheduleItem(
                    title = job.name,
                    colorHex = job.colorHex,
                    jobId = job.id,
                    record = virtualRec,
                    scheduleSlot = slot,
                    status = ShiftStatus.NORMAL,
                    isVirtualFuture = endDt.isAfter(now),
                    isCurrentlyLive = isLive,
                    startMinute = slot.startMinute,
                    endMinute = slot.endMinute,
                    estimatedNetPay = br.netPay,
                    paymentStatus = null
                )
            }
        }
        return result.sortedBy { it.startMinute }
    }

    /**
     * Hybrid Live Earnings Engine evaluated at [nowEpochMillis] (ticks every 100ms in UI).
     * Combines active scheduled shifts (Regular jobs) + active manual stopwatch sessions.
     */
    fun evaluateLiveDashboard(
        state: AlbaToraDataState,
        nowEpochMillis: Long
    ): LiveDashboardSnapshot {
        val nowInstant = Instant.ofEpochMilli(nowEpochMillis)
        val nowDt = LocalDateTime.ofInstant(nowInstant, KOR_ZONE)
        val today = nowDt.toLocalDate()
        val runtimeByKey = state.scheduledRuntimes.associateBy { it.key }
        val explicitKeysToday = state.records.mapNotNull { rec -> rec.jobId?.let { "$it|${rec.date}" } }.toSet()

        val activeList = mutableListOf<LiveShiftSnapshot>()

        // 1. Auto-scheduled shifts from REGULAR jobs (check yesterday for overnight shifts + today)
        val candidateDates = listOf(today.minusDays(1), today)
        state.jobs.filter { !it.isArchived && it.kind == JobKind.REGULAR }.forEach { job ->
            val startGate = if (job.scheduleStartDate.isNotBlank()) parseDate(job.scheduleStartDate, today) else today
            candidateDates.forEach { shiftDate ->
                if (shiftDate.isBefore(startGate)) return@forEach
                val dateIso = formatDate(shiftDate)
                if ("${job.id}|$dateIso" in explicitKeysToday) return@forEach

                job.schedules.filter { it.dayOfWeek == shiftDate.dayOfWeek.value }.forEach { slot ->
                    val shiftStartDt = LocalDateTime.of(
                        shiftDate,
                        LocalTime.of(slot.startMinute / 60, slot.startMinute % 60)
                    )
                    val durMins = rawDurationMinutes(slot.startMinute, slot.endMinute)
                    val shiftEndDt = shiftStartDt.plusMinutes(durMins.toLong())
                    val startMs = shiftStartDt.atZone(KOR_ZONE).toInstant().toEpochMilli()
                    val endMs = shiftEndDt.atZone(KOR_ZONE).toInstant().toEpochMilli()
                    val key = "${job.id}_$startMs"
                    val runtime = runtimeByKey[key]
                    if (runtime?.clockedOutAtEpochMillis != null) return@forEach

                    if (nowEpochMillis in startMs until endMs) {
                        val breakMins = if (slot.autoRecommendBreak) recommendedBreakMinutes(durMins) else slot.breakMinutes
                        val wage = effectiveJobWage(job, shiftDate)
                        activeList += evaluateSingleLiveShift(
                            runtimeKey = key,
                            isManualStopwatch = false,
                            stopwatchId = null,
                            jobId = job.id,
                            title = job.name,
                            kind = JobKind.REGULAR,
                            colorHex = job.colorHex,
                            payType = job.payType,
                            unitAmount = wage,
                            startEpochMillis = startMs,
                            plannedEndEpochMillis = endMs,
                            nowEpochMillis = nowEpochMillis,
                            isManualPaused = runtime?.isManualPaused == true,
                            manualPausedAtEpochMillis = runtime?.manualPausedAtEpochMillis,
                            accumulatedManualPauseMillis = runtime?.accumulatedManualPauseMillis ?: 0L,
                            unpaidBreakMinutes = if (slot.breakPayType == BreakPayType.UNPAID) breakMins else 0,
                            breakPayType = slot.breakPayType,
                            fixedBreakStartMinute = slot.fixedBreakStartMinute,
                            fixedBreakEndMinute = slot.fixedBreakEndMinute,
                            allowance = job.allowance
                        )
                    }
                }
            }
        }

        // 2. Manual Stopwatch sessions ([즉시 출근(스톱워치)])
        state.activeStopwatches.forEach { session ->
            activeList += evaluateSingleLiveShift(
                runtimeKey = session.id,
                isManualStopwatch = true,
                stopwatchId = session.id,
                jobId = session.jobId,
                title = session.title,
                kind = session.kind,
                colorHex = session.colorHex,
                payType = session.payType,
                unitAmount = session.unitAmount,
                startEpochMillis = session.startEpochMillis,
                plannedEndEpochMillis = session.plannedEndEpochMillis,
                nowEpochMillis = nowEpochMillis,
                isManualPaused = session.isPaused,
                manualPausedAtEpochMillis = session.pausedAtEpochMillis,
                accumulatedManualPauseMillis = session.accumulatedManualPauseMillis,
                unpaidBreakMinutes = if (session.breakPayType == BreakPayType.UNPAID) session.unpaidBreakMinutes else 0,
                breakPayType = session.breakPayType,
                fixedBreakStartMinute = session.fixedBreakStartMinute,
                fixedBreakEndMinute = session.fixedBreakEndMinute,
                allowance = session.allowance
            )
        }

        val totalEarned = activeList.sumOf { it.liveEarnedWon }
        val totalRateSec = activeList.sumOf { it.perSecondRateWon }
        val totalRateMin = activeList.sumOf { it.perMinuteRateWon }
        val anyBoost = activeList.any { it.isBoostActive }
        val maxBoost = activeList.maxOfOrNull { it.boostMultiplier } ?: 1.0

        return LiveDashboardSnapshot(
            activeShifts = activeList,
            totalEarnedWon = totalEarned,
            totalPerSecondRateWon = totalRateSec,
            totalPerMinuteRateWon = totalRateMin,
            anyBoostActive = anyBoost,
            maxBoostMultiplier = maxBoost
        )
    }

    private fun evaluateSingleLiveShift(
        runtimeKey: String,
        isManualStopwatch: Boolean,
        stopwatchId: String?,
        jobId: String?,
        title: String,
        kind: JobKind,
        colorHex: Long,
        payType: PayType,
        unitAmount: Long,
        startEpochMillis: Long,
        plannedEndEpochMillis: Long?,
        nowEpochMillis: Long,
        isManualPaused: Boolean,
        manualPausedAtEpochMillis: Long?,
        accumulatedManualPauseMillis: Long,
        unpaidBreakMinutes: Int,
        breakPayType: BreakPayType,
        fixedBreakStartMinute: Int?,
        fixedBreakEndMinute: Int?,
        allowance: AllowanceSettings
    ): LiveShiftSnapshot {
        val currentEffectiveNow = if (isManualPaused && manualPausedAtEpochMillis != null) {
            min(nowEpochMillis, manualPausedAtEpochMillis)
        } else {
            nowEpochMillis
        }

        val rawElapsedSinceStart = (currentEffectiveNow - startEpochMillis).coerceAtLeast(0L)
        val hasFixedBreak = breakPayType == BreakPayType.UNPAID &&
            fixedBreakStartMinute != null && fixedBreakEndMinute != null &&
            fixedBreakStartMinute != fixedBreakEndMinute

        // Proration factor for proportional unpaid break (Q16=A) when no fixed break or manual pause
        val plannedTotalMs = if (plannedEndEpochMillis != null && plannedEndEpochMillis > startEpochMillis) {
            plannedEndEpochMillis - startEpochMillis
        } else null

        val proportionalUnpaidFactor = if (!hasFixedBreak && breakPayType == BreakPayType.UNPAID && unpaidBreakMinutes > 0 && plannedTotalMs != null) {
            val netPlannedMs = (plannedTotalMs - unpaidBreakMinutes * 60_000L).coerceAtLeast(0L)
            netPlannedMs.toDouble() / plannedTotalMs.toDouble()
        } else {
            1.0
        }

        // Integrate minute by minute up to currentEffectiveNow minus manual pauses
        val effectiveElapsedNonManualMs = (rawElapsedSinceStart - accumulatedManualPauseMillis).coerceAtLeast(0L)
        val nowLocal = LocalDateTime.ofInstant(Instant.ofEpochMilli(currentEffectiveNow), KOR_ZONE)
        val currentMinuteOfDay = nowLocal.hour * 60 + nowLocal.minute
        val inFixedBreakNow = hasFixedBreak && isInsideFixedBreak(currentMinuteOfDay, fixedBreakStartMinute, fixedBreakEndMinute)

        // Step across 60s chunks (and final fractional seconds chunk) for accurate real-time allowance & smooth 100ms updates
        var earnedWon = 0.0
        var activeWorkMsAccumulated = 0L

        val stepMs = 60_000L
        var cursorMs = 0L
        while (cursorMs < effectiveElapsedNonManualMs) {
            val chunkEnd = min(cursorMs + stepMs, effectiveElapsedNonManualMs)
            val chunkDurationMs = chunkEnd - cursorMs
            val wallDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(startEpochMillis + cursorMs), KOR_ZONE)
            val wallMin = wallDt.hour * 60 + wallDt.minute
            val isChunkFixedBreak = hasFixedBreak && isInsideFixedBreak(wallMin, fixedBreakStartMinute, fixedBreakEndMinute)
            if (!isChunkFixedBreak) {
                val weightedMs = (chunkDurationMs * proportionalUnpaidFactor).roundToLong()
                activeWorkMsAccumulated += weightedMs
                val night = isNightMinute(wallMin, allowance)
                val overtime = allowance.overtimeAllowanceEnabled && activeWorkMsAccumulated > 8L * 3_600_000L
                val holiday = isHolidayMinute(wallDt.toLocalDate(), allowance, forceHolidayPay = false)
                var mult = 1.0
                if (night) mult += 0.5
                if (overtime) mult += 0.5
                if (holiday) mult += 0.5

                val chunkEarned = when (payType) {
                    PayType.HOURLY -> (unitAmount / 3_600_000.0) * weightedMs * mult
                    PayType.DAILY, PayType.PER_TASK -> {
                        val denomMs = if (plannedTotalMs != null) (plannedTotalMs * proportionalUnpaidFactor).coerceAtLeast(1.0) else (8.0 * 3_600_000.0)
                        (unitAmount / denomMs) * weightedMs
                    }
                }
                earnedWon += chunkEarned
            }
            cursorMs = chunkEnd
        }

        // Instantaneous current boost check
        val nightNow = isNightMinute(currentMinuteOfDay, allowance)
        val overtimeNow = allowance.overtimeAllowanceEnabled && activeWorkMsAccumulated > 8L * 3_600_000L
        val holidayNow = isHolidayMinute(nowLocal.toLocalDate(), allowance, forceHolidayPay = false)
        val boostReasons = buildList {
            if (nightNow) add("야간 1.5x")
            if (overtimeNow) add("연장 1.5x")
            if (holidayNow) add("휴일 1.5x")
        }
        var currentMult = 1.0
        if (nightNow) currentMult += 0.5
        if (overtimeNow) currentMult += 0.5
        if (holidayNow) currentMult += 0.5

        val effectivelyPaused = isManualPaused || inFixedBreakNow
        val pauseReason = when {
            isManualPaused -> "수동 휴게 중"
            inFixedBreakNow -> "고정 휴게 시간 (${formatMinutes(fixedBreakStartMinute ?: 0)}~${formatMinutes(fixedBreakEndMinute ?: 0)})"
            else -> null
        }

        val perSecRate = if (effectivelyPaused) {
            0.0
        } else {
            when (payType) {
                PayType.HOURLY -> (unitAmount / 3600.0) * proportionalUnpaidFactor * currentMult
                PayType.DAILY, PayType.PER_TASK -> {
                    val denomSec = if (plannedTotalMs != null) (plannedTotalMs / 1000.0) else (8.0 * 3600.0)
                    unitAmount / max(1.0, denomSec)
                }
            }
        }

        val progress = if (plannedTotalMs != null && plannedTotalMs > 0L) {
            ((nowEpochMillis - startEpochMillis).toDouble() / plannedTotalMs.toDouble()).toFloat().coerceIn(0f, 1f)
        } else null

        val remainingMs = plannedEndEpochMillis?.let { max(0L, it - nowEpochMillis) }

        return LiveShiftSnapshot(
            runtimeKey = runtimeKey,
            isManualStopwatch = isManualStopwatch,
            stopwatchId = stopwatchId,
            jobId = jobId,
            title = title,
            kind = kind,
            colorHex = colorHex,
            startEpochMillis = startEpochMillis,
            plannedEndEpochMillis = plannedEndEpochMillis,
            liveEarnedWon = earnedWon,
            perSecondRateWon = perSecRate,
            perMinuteRateWon = perSecRate * 60.0,
            progressFraction = progress,
            remainingMillis = remainingMs,
            elapsedWorkMillis = activeWorkMsAccumulated,
            isPaused = effectivelyPaused,
            isManualPaused = isManualPaused,
            pauseReason = pauseReason,
            isBoostActive = boostReasons.isNotEmpty() && payType == PayType.HOURLY,
            boostMultiplier = if (payType == PayType.HOURLY) currentMult else 1.0,
            boostReasons = boostReasons,
            payType = payType,
            unitAmount = unitAmount
        )
    }

    /**
     * Format payslip text for 1-tap clipboard copy (Q15-2).
     */
    fun buildPayslipText(
        title: String,
        summary: SettlementSummary
    ): String = buildString {
        appendLine("==============================")
        appendLine(" [알바토라] 급여 정산 명세서")
        appendLine(" $title")
        appendLine(" 기간: ${summary.periodStart} ~ ${summary.periodEnd}")
        appendLine("==============================")
        val totalHours = summary.totalWorkMinutes / 60
        val remMins = summary.totalWorkMinutes % 60
        appendLine("• 총 근무: ${summary.shiftCount}건 (${totalHours}시간 ${remMins}분)")
        appendLine("• 기본 급여: ${formatWon(summary.totalBasePay)}")
        if (summary.totalAllowancePay > 0L) {
            appendLine("• 가산 수당(야간/연장/휴일): +${formatWon(summary.totalAllowancePay)}")
        }
        if (summary.totalWeeklyHolidayPay > 0L) {
            appendLine("• 주휴수당: +${formatWon(summary.totalWeeklyHolidayPay)}")
        }
        if (summary.totalSiteBonus > 0L) {
            appendLine("• 현장 지원금/보너스: +${formatWon(summary.totalSiteBonus)}")
        }
        appendLine("------------------------------")
        appendLine("• 지급 총액(세전): ${formatWon(summary.totalGrossPay)}")
        if (summary.totalTaxAmount > 0L) {
            appendLine("• 세금 공제: -${formatWon(summary.totalTaxAmount)}")
        }
        if (summary.totalAgencyFee > 0L) {
            appendLine("• 플랫폼/인력 수수료: -${formatWon(summary.totalAgencyFee)}")
        }
        if (summary.totalExpenses > 0L) {
            appendLine("• 교통비/식비 지출: -${formatWon(summary.totalExpenses)}")
        }
        appendLine("==============================")
        appendLine("▶ 최종 실수령(순수익): ${formatWon(summary.totalNetPay)}")
        if (summary.unpaidAmount > 0L) {
            appendLine("▶ 미수금(입금 대기): ${formatWon(summary.unpaidAmount)} (${summary.unpaidCount}건)")
        }
        appendLine("==============================")
        if (summary.items.isNotEmpty()) {
            appendLine("[상세 근무 내역]")
            summary.items.sortedBy { it.record.date }.forEach { item ->
                val r = item.record
                val b = item.breakdown
                val statusTag = when (r.status) {
                    ShiftStatus.NORMAL -> ""
                    else -> " [${r.status.label}]"
                }
                val payTag = if (r.kind == JobKind.SHORT_TERM || r.paymentStatus == PaymentStatus.UNPAID) {
                    " (${r.paymentStatus.badgeEmoji}${r.paymentStatus.label})"
                } else ""
                appendLine(
                    "- ${r.date} ${formatMinutes(r.startMinute)}~${formatMinutes(r.endMinute)} " +
                        "${r.title}$statusTag: ${formatWon(b.netPay)}$payTag"
                )
            }
        }
    }

    /**
     * Korean retirement severance calculator (근로기준법/퇴직급여보장법: 평균 30일분 일급 * 총 계속근로기간/365).
     * Requires >= 365 days & weekly >= 15h.
     */
    fun calculateSeverancePay(
        monthlyAvgWage: Long,
        workedDays: Long,
        weeklyAvgHours: Double
    ): Triple<Boolean, String, Long> {
        if (workedDays < 365L) {
            return Triple(false, "계속근로기간 1년(365일) 미만으로 법정 퇴직금 대상이 아닙니다.", 0L)
        }
        if (weeklyAvgHours < 15.0) {
            return Triple(false, "4주 평균 주당 소정근로시간 15시간 미만으로 법정 퇴직금 대상이 아닙니다.", 0L)
        }
        val estimatedDailyWage = monthlyAvgWage / 30.0
        val severance = (estimatedDailyWage * 30.0 * (workedDays.toDouble() / 365.0)).roundToLong()
        return Triple(true, "법정 퇴직금 지급 요건 충족 (30일분 평균임금 × 근속연수)", severance)
    }

    fun formatWon(amount: Long): String {
        val sign = if (amount < 0L) "-" else ""
        val abs = kotlin.math.abs(amount)
        return "$sign${String.format(Locale.US, "%,d", abs)}원"
    }

    fun formatWonDecimal(amount: Double): String {
        val sign = if (amount < 0.0) "-" else ""
        val abs = kotlin.math.abs(amount)
        return "$sign${String.format(Locale.US, "%,.1f", abs)}원"
    }

    fun formatDurationKorean(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}시간 ${m}분"
            h > 0 -> "${h}시간"
            else -> "${m}분"
        }
    }

    fun formatCountdown(millis: Long): String {
        val totalSec = (millis / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }
}
