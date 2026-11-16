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

        assertTrue(AppUpdater.isNewerVersion("v1.1.4_hotfix", "1.1.4"))
        assertTrue(AppUpdater.isNewerVersion("v1.1.4.1_hotfix", "1.1.4"))
        assertTrue(AppUpdater.isNewerVersion("1.1.5", "1.1.4_hotfix"))
        assertTrue(AppUpdater.isNewerVersion("v1.1.4_hotfix2", "1.1.4_hotfix"))
        assertTrue(AppUpdater.isNewerVersion("v1.1.4.2_hotfix", "1.1.4_hotfix"))
        assertTrue(AppUpdater.isNewerVersion("v1.1.4.2_hotfix", "1.1.4.1_hotfix"))
        assertTrue(AppUpdater.isNewerVersion("v1.1.4.2_hotfix", "1.1.4"))
        assertTrue(AppUpdater.isNewerVersion("v1.1.4.3_hotfix", "1.1.4.2_hotfix"))
        assertTrue(AppUpdater.isNewerVersion("v1.1.5.1_hotfix", "1.1.5"))
        assertFalse(AppUpdater.isNewerVersion("v1.1.4_hotfix", "1.1.4_hotfix"))
        assertFalse(AppUpdater.isNewerVersion("v1.1.4.1_hotfix", "1.1.4_hotfix"))
        assertFalse(AppUpdater.isNewerVersion("v1.1.4", "1.1.4_hotfix"))

        // Hyphenated hotfix tags & 1.5.1.1_hotfix -> 1.6.0 progression
        assertTrue(AppUpdater.isNewerVersion("v1.5.1-hotfix", "1.5.1"))
        assertTrue(AppUpdater.isNewerVersion("v1.5.1-hotfix2", "1.5.1-hotfix"))
        assertFalse(AppUpdater.isNewerVersion("1.5.1", "1.5.1-hotfix"))
        assertTrue(AppUpdater.isNewerVersion("v1.6.0", "1.5.1.1_hotfix"))
        assertFalse(AppUpdater.isNewerVersion("v1.5.1", "1.5.1.1_hotfix"))
        assertTrue(AppUpdater.isNewerVersion("v1.6.2.1_hotfix", "1.6.2"))
        assertTrue(AppUpdater.isNewerVersion("v1.6.2.2_hotfix", "1.6.2.1_hotfix"))
        assertTrue(AppUpdater.isNewerVersion("v1.6.2.3_hotfix", "1.6.2.2_hotfix"))
        assertTrue(AppUpdater.isNewerVersion("v1.6.3", "1.6.2.3_hotfix"))
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
