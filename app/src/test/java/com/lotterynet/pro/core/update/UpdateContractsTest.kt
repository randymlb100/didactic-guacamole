package com.lotterynet.pro.core.update

import com.lotterynet.pro.BuildConfig
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateContractsTest {
    @Test
    fun `parse response detects available update`() {
        val info = parseUpdateResponse(
            JSONObject().apply {
                put("updateAvailable", true)
                put("forceUpdate", false)
                put("minimumVersion", BuildConfig.VERSION_CODE)
                put("versionCode", BuildConfig.VERSION_CODE + 1)
                put("versionName", "1.0.3")
                put("title", "Nueva actualizacion")
                put("changelog", JSONArray(listOf("Mas velocidad")))
                put("apkUrl", "https://example.com/app.apk")
                put("apkSha256", "a".repeat(64))
                put("apkSizeBytes", 1000)
            },
        )

        assertTrue(info.updateAvailable)
        assertTrue(info.shouldInstall)
        assertFalse(info.blocksCurrentBuild)
        assertEquals("Mas velocidad", info.changelog.single())
    }

    @Test
    fun `minimum version forces update even without force flag`() {
        val info = OtaUpdateInfo(
            updateAvailable = true,
            forceUpdate = false,
            minimumVersion = BuildConfig.VERSION_CODE + 1,
            versionCode = BuildConfig.VERSION_CODE + 1,
        )

        assertTrue(info.blocksCurrentBuild)
        assertTrue(info.shouldInstall)
    }

    @Test
    fun `downgrade does not install`() {
        val info = OtaUpdateInfo(
            updateAvailable = true,
            forceUpdate = false,
            minimumVersion = 1,
            versionCode = BuildConfig.VERSION_CODE - 1,
        )

        assertFalse(info.shouldInstall)
    }

    @Test
    fun `sha256 validation rejects invalid hash`() {
        assertFalse(isValidSha256("abc"))
        assertFalse(isValidSha256("g".repeat(64)))
        assertTrue(isValidSha256("f".repeat(64)))
    }

    @Test
    fun `file sha256 matches expected`() {
        val file = File.createTempFile("ota-test", ".apk")
        try {
            file.writeText("lotterynet")
            assertEquals(
                "5cbb18bca838735066b0d98b05aa38a6a99f6f09985d1c88f7a6b26c7f769952",
                file.sha256Hex(),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `queued download is stale only after timeout with no bytes`() {
        assertFalse(isQueuedDownloadStale(OtaDownloadStatus.QUEUED, bytesDownloaded = 0L, queuedForMs = OTA_QUEUE_STALL_TIMEOUT_MS - 1))
        assertTrue(isQueuedDownloadStale(OtaDownloadStatus.QUEUED, bytesDownloaded = 0L, queuedForMs = OTA_QUEUE_STALL_TIMEOUT_MS))
        assertFalse(isQueuedDownloadStale(OtaDownloadStatus.QUEUED, bytesDownloaded = 1L, queuedForMs = OTA_QUEUE_STALL_TIMEOUT_MS))
        assertFalse(isQueuedDownloadStale(OtaDownloadStatus.DOWNLOADING, bytesDownloaded = 0L, queuedForMs = OTA_QUEUE_STALL_TIMEOUT_MS))
    }
}
