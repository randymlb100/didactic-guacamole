package com.lotterynet.pro.ui.update

import com.lotterynet.pro.BuildConfig
import com.lotterynet.pro.core.update.OtaCheckResult
import com.lotterynet.pro.core.update.OtaUpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateUiContractsTest {
    @Test
    fun `optional update shows later button`() {
        val info = OtaUpdateInfo(
            updateAvailable = true,
            forceUpdate = false,
            minimumVersion = BuildConfig.VERSION_CODE,
            versionCode = BuildConfig.VERSION_CODE + 1,
        )

        assertTrue(shouldShowLaterButton(info))
    }

    @Test
    fun `forced update hides later button`() {
        val info = OtaUpdateInfo(
            updateAvailable = true,
            forceUpdate = true,
            minimumVersion = BuildConfig.VERSION_CODE,
            versionCode = BuildConfig.VERSION_CODE + 1,
        )

        assertFalse(shouldShowLaterButton(info))
    }

    @Test
    fun `size and speed labels are stable`() {
        assertEquals("1.0 MB", formatApkSize(1024L * 1024L))
        assertEquals("512 KB/s", formatDownloadSpeed(512L * 1024L))
        assertEquals("1.5 MB/s", formatDownloadSpeed((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun `fresh update response replaces cached apk url before download`() {
        val cached = OtaUpdateInfo(
            updateAvailable = true,
            versionCode = BuildConfig.VERSION_CODE + 1,
            apkUrl = "https://example.com/old.apk",
        )
        val fresh = cached.copy(apkUrl = "https://example.com/fresh.apk")

        val resolved = resolveDownloadInfo(cached, OtaCheckResult.Success(fresh))

        assertEquals("https://example.com/fresh.apk", resolved.apkUrl)
    }

    @Test
    fun `failed refresh keeps current download info`() {
        val cached = OtaUpdateInfo(
            updateAvailable = true,
            versionCode = BuildConfig.VERSION_CODE + 1,
            apkUrl = "https://example.com/old.apk",
        )

        val resolved = resolveDownloadInfo(cached, OtaCheckResult.Error(null, "Internet lento"))

        assertEquals(cached, resolved)
    }
}
