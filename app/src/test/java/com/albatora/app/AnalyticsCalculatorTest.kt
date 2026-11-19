package com.albatora.app

import com.albatora.app.domain.AnalyticsCalculator
import com.albatora.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class AnalyticsCalculatorTest {

    @Test
    fun emptyStateProducesZeroAnalyticsSnapshot() {
        val snapshot = AnalyticsCalculator.evaluate(
            state = AlbaToraDataState.initialEmptyState(),
            anchorMonth = YearMonth.of(2026, 11),
            monthCount = 6
        )

        assertEquals(6, snapshot.months.size)
        assertEquals(0L, snapshot.totalNetWon)
        assertEquals(0, snapshot.totalWorkMinutes)
        assertEquals(0L, snapshot.averageEffectiveHourlyWon)
        assertTrue(snapshot.workplaceShares.isEmpty())
        assertNull(snapshot.goalInsight.estimatedRemainingHours)
    }

    @Test
    fun multiWorkplaceRecordsComputeSharesEffectiveHourlyAndGoalProgress() {
        val state = AlbaToraDataState(
            records = listOf(
                WorkRecord(
                    id = "r1",
                    title = "카페 A",
                    kind = JobKind.REGULAR,
                    date = "2026-11-05",
                    startMinute = 9 * 60,
                    endMinute = 13 * 60, // 4h = 240m
                    payType = PayType.HOURLY,
                    unitAmount = 12000L, // 48,000
                    taxMode = TaxMode.NONE
                ),
                WorkRecord(
                    id = "r2",
                    title = "단기 B",
                    kind = JobKind.SHORT_TERM,
                    date = "2026-11-10",
                    startMinute = 10 * 60,
                    endMinute = 16 * 60, // 6h = 360m
                    payType = PayType.HOURLY,
                    unitAmount = 15000L, // 90,000
                    taxMode = TaxMode.NONE
                )
            ),
            settings = AppSettings(
                monthlyIncomeGoalWon = 276000L,
                annualIncomeThresholdWon = 1_000_000L
            )
        )

        val snapshot = AnalyticsCalculator.evaluate(
            state = state,
            anchorMonth = YearMonth.of(2026, 11),
            monthCount = 6
        )

        assertEquals(138000L, snapshot.totalNetWon)
        assertEquals(600, snapshot.totalWorkMinutes)
        assertEquals(13800L, snapshot.averageEffectiveHourlyWon)
        assertEquals(2, snapshot.workplaceShares.size)
        assertEquals("단기 B", snapshot.workplaceShares[0].title)
        assertEquals(90000L, snapshot.workplaceShares[0].netPay)
        assertEquals(0.5f, snapshot.goalInsight.monthProgressFraction, 0.001f)
        // Remaining 138,000 / 13,800 = 10.0 hours
        assertEquals(10.0, snapshot.goalInsight.estimatedRemainingHours!!, 0.05)
    }
}
