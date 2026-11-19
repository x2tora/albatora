package com.albatora.app.domain

import com.albatora.app.model.AlbaToraDataState
import com.albatora.app.model.ShiftStatus
import com.albatora.app.model.TaxMode
import java.time.YearMonth
import kotlin.math.roundToLong

data class MonthlyTrendPoint(
    val month: YearMonth,
    val label: String,
    val grossPay: Long,
    val netPay: Long,
    val workMinutes: Int,
    val effectiveHourlyWon: Long,
    val shiftCount: Int
)

data class WorkplaceShare(
    val key: String,
    val title: String,
    val colorHex: Long,
    val netPay: Long,
    val workMinutes: Int,
    val shareFraction: Float,
    val effectiveHourlyWon: Long
)

data class PayBreakdownSummary(
    val basePay: Long,
    val weeklyHolidayPay: Long,
    val allowancePay: Long,
    val siteBonus: Long,
    val taxAndFeeDeductions: Long,
    val expenses: Long,
    val netPay: Long
)

data class GoalAndThresholdInsight(
    val monthTargetWon: Long,
    val monthCurrentWon: Long,
    val monthProgressFraction: Float,
    val estimatedRemainingHours: Double?,
    val annualNetWon: Long,
    val annualFreelanceGrossWon: Long,
    val annualThresholdWon: Long,
    val annualThresholdFraction: Float
)

data class AnalyticsDashboardSnapshot(
    val months: List<MonthlyTrendPoint>,
    val workplaceShares: List<WorkplaceShare>,
    val payBreakdown: PayBreakdownSummary,
    val goalInsight: GoalAndThresholdInsight,
    val totalNetWon: Long,
    val totalWorkMinutes: Int,
    val averageEffectiveHourlyWon: Long
)

object AnalyticsCalculator {

    fun evaluate(
        state: AlbaToraDataState,
        anchorMonth: YearMonth = YearMonth.now(),
        monthCount: Int = 6
    ): AnalyticsDashboardSnapshot {
        val safeCount = monthCount.coerceIn(1, 12)
        val monthRange = (safeCount - 1 downTo 0).map { offset ->
            anchorMonth.minusMonths(offset.toLong())
        }

        val monthlyPoints = mutableListOf<MonthlyTrendPoint>()
        var totalBase = 0L
        var totalWeeklyHoliday = 0L
        var totalAllowance = 0L
        var totalBonus = 0L
        var totalDeductions = 0L
        var totalExpenses = 0L
        var totalNet = 0L
        var totalMinutes = 0

        val workplaceAcc = linkedMapOf<String, MutableWorkplaceAcc>()

        for (ym in monthRange) {
            val summary = PayrollCalculator.calculateSettlementSummary(
                state = state,
                fromDate = ym.atDay(1),
                toDate = ym.atEndOfMonth(),
                filterJobId = null,
                onlyUnpaid = false
            )
            val effHourly = computeEffectiveHourly(summary.totalNetPay, summary.totalWorkMinutes)
            monthlyPoints += MonthlyTrendPoint(
                month = ym,
                label = "${ym.monthValue}월",
                grossPay = summary.totalGrossPay,
                netPay = summary.totalNetPay,
                workMinutes = summary.totalWorkMinutes,
                effectiveHourlyWon = effHourly,
                shiftCount = summary.shiftCount
            )
            totalBase += summary.totalBasePay
            totalWeeklyHoliday += summary.totalWeeklyHolidayPay
            totalAllowance += summary.totalAllowancePay
            totalBonus += summary.totalSiteBonus
            totalDeductions += (summary.totalTaxAmount + summary.totalAgencyFee)
            totalExpenses += summary.totalExpenses
            totalNet += summary.totalNetPay
            totalMinutes += summary.totalWorkMinutes

            for (item in summary.items) {
                val rec = item.record
                if (rec.status == ShiftStatus.ABSENT) continue
                val key = rec.jobId ?: "short_${rec.title}"
                val acc = workplaceAcc.getOrPut(key) {
                    MutableWorkplaceAcc(
                        key = key,
                        title = rec.title,
                        colorHex = rec.colorHex
                    )
                }
                acc.netPay += item.breakdown.netPay
                acc.workMinutes += item.breakdown.paidWorkMinutes
            }
        }

        // Include weekly holiday pay in workplace shares for jobs in window
        val totalWorkplaceNet = workplaceAcc.values.sumOf { it.netPay.coerceAtLeast(0L) }
        val shares = workplaceAcc.values
            .sortedByDescending { it.netPay }
            .map { acc ->
                val share = if (totalWorkplaceNet > 0L) {
                    (acc.netPay.coerceAtLeast(0L).toFloat() / totalWorkplaceNet.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                WorkplaceShare(
                    key = acc.key,
                    title = acc.title,
                    colorHex = acc.colorHex,
                    netPay = acc.netPay,
                    workMinutes = acc.workMinutes,
                    shareFraction = share,
                    effectiveHourlyWon = computeEffectiveHourly(acc.netPay, acc.workMinutes)
                )
            }

        val avgHourly = computeEffectiveHourly(totalNet, totalMinutes)

        // Current anchor month & annual year-to-date insights
        val currentMonthSummary = monthlyPoints.lastOrNull()
        val currentMonthNet = currentMonthSummary?.netPay ?: 0L
        val monthGoal = state.settings.monthlyIncomeGoalWon.coerceAtLeast(0L)
        val monthProgress = if (monthGoal > 0L) {
            (currentMonthNet.toFloat() / monthGoal.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val remainingHours = if (monthGoal > currentMonthNet && avgHourly > 0L) {
            (monthGoal - currentMonthNet).toDouble() / avgHourly.toDouble()
        } else {
            null
        }

        val yearStart = YearMonth.of(anchorMonth.year, 1).atDay(1)
        val yearEnd = YearMonth.of(anchorMonth.year, 12).atEndOfMonth()
        val annualSummary = PayrollCalculator.calculateSettlementSummary(
            state = state,
            fromDate = yearStart,
            toDate = yearEnd,
            filterJobId = null,
            onlyUnpaid = false
        )
        val annualFreelanceGross = annualSummary.items
            .filter { it.record.taxMode == TaxMode.FREELANCE_3_3 && it.record.status != ShiftStatus.ABSENT }
            .sumOf { it.breakdown.grossPay }
        val annualThreshold = state.settings.annualIncomeThresholdWon.coerceAtLeast(0L)
        val thresholdFraction = if (annualThreshold > 0L) {
            (annualSummary.totalNetPay.toFloat() / annualThreshold.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        return AnalyticsDashboardSnapshot(
            months = monthlyPoints,
            workplaceShares = shares,
            payBreakdown = PayBreakdownSummary(
                basePay = totalBase,
                weeklyHolidayPay = totalWeeklyHoliday,
                allowancePay = totalAllowance,
                siteBonus = totalBonus,
                taxAndFeeDeductions = totalDeductions,
                expenses = totalExpenses,
                netPay = totalNet
            ),
            goalInsight = GoalAndThresholdInsight(
                monthTargetWon = monthGoal,
                monthCurrentWon = currentMonthNet,
                monthProgressFraction = monthProgress,
                estimatedRemainingHours = remainingHours,
                annualNetWon = annualSummary.totalNetPay,
                annualFreelanceGrossWon = annualFreelanceGross,
                annualThresholdWon = annualThreshold,
                annualThresholdFraction = thresholdFraction
            ),
            totalNetWon = totalNet,
            totalWorkMinutes = totalMinutes,
            averageEffectiveHourlyWon = avgHourly
        )
    }

    private fun computeEffectiveHourly(netPay: Long, workMinutes: Int): Long {
        if (workMinutes <= 0 || netPay <= 0L) return 0L
        return (netPay.toDouble() / (workMinutes.toDouble() / 60.0)).roundToLong()
    }

    private class MutableWorkplaceAcc(
        val key: String,
        val title: String,
        val colorHex: Long,
        var netPay: Long = 0L,
        var workMinutes: Int = 0
    )
}
