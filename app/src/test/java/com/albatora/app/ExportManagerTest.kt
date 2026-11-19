package com.albatora.app

import com.albatora.app.domain.ExportManager
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.domain.ResolvedRecordItem
import com.albatora.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExportManagerTest {

    @Test
    fun csvIncludesUtf8BomAndEscapesCommasQuotesAndNewlines() {
        val record = WorkRecord(
            id = "r1",
            title = "카페, \"본점\"",
            kind = JobKind.SHORT_TERM,
            date = "2026-11-20",
            startMinute = 9 * 60,
            endMinute = 13 * 60,
            payType = PayType.HOURLY,
            unitAmount = 12000L,
            extraDeduction = 2000L,
            paymentStatus = PaymentStatus.UNPAID,
            memo = "첫째줄\n둘째줄"
        )
        val breakdown = PayrollCalculator.calculateShiftFinancials(record)
        assertEquals(46000L, breakdown.netPay)

        val csv = ExportManager.buildCsvContent(
            listOf(ResolvedRecordItem(record = record, job = null, isVirtualSchedule = false, breakdown = breakdown))
        )
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"카페, \"\"본점\"\"\""))
        assertTrue(csv.contains("\"첫째줄\n둘째줄\""))
        assertTrue(csv.contains(",46000,"))
    }

    @Test
    fun korean2028HolidaysAreRecognized() {
        assertTrue(PayrollCalculator.isKoreanPublicHoliday(LocalDate.of(2028, 1, 27)))
        assertTrue(PayrollCalculator.isKoreanPublicHoliday(LocalDate.of(2028, 10, 3)))
    }
}
