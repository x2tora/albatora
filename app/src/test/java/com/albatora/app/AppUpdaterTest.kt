package com.albatora.app

import com.albatora.app.update.AppUpdater
import com.albatora.app.update.ReleaseApkAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {

    @Test
    fun versionComparisonHandlesSemverCorrectly() {
        assertTrue(AppUpdater.isNewerVersion("v1.0.3", "1.0.2"))
        assertTrue(AppUpdater.isNewerVersion("v1.0.10", "1.0.9"))
        assertTrue(AppUpdater.isNewerVersion("1.1.0", "1.0.99"))
        assertTrue(AppUpdater.isNewerVersion("2.0.0", "1.9.9"))

        assertFalse(AppUpdater.isNewerVersion("v1.0.3", "1.0.3"))
        assertFalse(AppUpdater.isNewerVersion("v1.0.2", "1.0.3"))
        assertFalse(AppUpdater.isNewerVersion("invalid", "1.0.3"))
    }

    @Test
    fun selectsDeviceMatchingAbiAssetWithUniversalFallback() {
        val assets = listOf(
            ReleaseApkAsset("albatora-v1.0.3-armeabi-v7a.apk", "https://example.com/v7", 100L),
            ReleaseApkAsset("albatora-v1.0.3-arm64-v8a.apk", "https://example.com/v8", 100L),
            ReleaseApkAsset("albatora-v1.0.3-x86_64.apk", "https://example.com/x64", 100L),
            ReleaseApkAsset("albatora-v1.0.3-universal.apk", "https://example.com/uni", 100L)
        )

        val arm64 = AppUpdater.selectBestApkAsset(assets, listOf("arm64-v8a", "armeabi-v7a"))
        assertNotNull(arm64)
        assertEquals("albatora-v1.0.3-arm64-v8a.apk", arm64?.first?.name)
        assertEquals("arm64-v8a", arm64?.second)

        val fallback = AppUpdater.selectBestApkAsset(assets, listOf("mips64"))
        assertNotNull(fallback)
        assertEquals("albatora-v1.0.3-universal.apk", fallback?.first?.name)
        assertEquals("universal", fallback?.second)
    }
}
