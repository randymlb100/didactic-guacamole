package com.lotterynet.pro.ui.report

import com.lotterynet.pro.core.finance.OperationalReportRemoteLoadResult
import com.lotterynet.pro.core.finance.OperationalReportSyncStatus
import com.lotterynet.pro.core.finance.resolveOperationalReportSyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class OperationalReportSyncContractsTest {

    @Test
    fun `fresh remote load is marked updated`() {
        assertEquals(
            OperationalReportSyncStatus.UPDATED,
            resolveOperationalReportSyncStatus(
                OperationalReportRemoteLoadResult(remoteSucceeded = true, cacheAvailable = true),
            ),
        )
    }

    @Test
    fun `remote failure with cache is clearly marked as cached copy`() {
        assertEquals(
            OperationalReportSyncStatus.CACHED_COPY,
            resolveOperationalReportSyncStatus(
                OperationalReportRemoteLoadResult(remoteSucceeded = false, cacheAvailable = true),
            ),
        )
    }

    @Test
    fun `remote failure without cache is marked server failed`() {
        assertEquals(
            OperationalReportSyncStatus.SERVER_FAILED,
            resolveOperationalReportSyncStatus(
                OperationalReportRemoteLoadResult(remoteSucceeded = false, cacheAvailable = false),
            ),
        )
    }
}
