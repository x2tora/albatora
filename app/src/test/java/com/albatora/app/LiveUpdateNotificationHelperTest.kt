package com.albatora.app

import com.albatora.app.service.LiveUpdateNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveUpdateNotificationHelperTest {

    @Test
    fun formatChipTextHandlesZeroUnderTenThousandAndManWonUnits() {
        assertEquals("0원", LiveUpdateNotificationHelper.formatChipText(0.0, isPaused = false))
        assertEquals("8,450원", LiveUpdateNotificationHelper.formatChipText(8450.2, isPaused = false))
        assertEquals("1.0만원", LiveUpdateNotificationHelper.formatChipText(10_000.0, isPaused = false))
        assertEquals("12.5만원", LiveUpdateNotificationHelper.formatChipText(125_400.0, isPaused = false))
    }

    @Test
    fun formatChipTextHandlesPausedStateCompactly() {
        assertEquals("휴게 8,450", LiveUpdateNotificationHelper.formatChipText(8450.0, isPaused = true))
        assertEquals("휴게 1.2만", LiveUpdateNotificationHelper.formatChipText(12_300.0, isPaused = true))
    }
}
