package com.lotterynet.pro.core.finance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalReportLoadPolicyContractsTest {
    @Test
    fun `historical report calls endpoint even with local data`() {
        assertTrue(
            shouldFetchOperationalReportEndpoint(
                refreshDecision = FinanceRemoteRefreshDecision(
                    shouldRefreshRemote = false,
                    initialMessage = "Datos locales listos",
                ),
                hasLocalReport = true,
            ),
        )
    }

    @Test
    fun `empty local report calls endpoint`() {
        assertTrue(
            shouldFetchOperationalReportEndpoint(
                refreshDecision = FinanceRemoteRefreshDecision(
                    shouldRefreshRemote = false,
                    initialMessage = "Datos locales listos",
                ),
                hasLocalReport = false,
            ),
        )
    }

    @Test
    fun `manual refresh calls endpoint even with cache`() {
        assertTrue(
            shouldFetchOperationalReportEndpoint(
                refreshDecision = FinanceRemoteRefreshDecision(
                    shouldRefreshRemote = true,
                    initialMessage = "Cargando desde servidor...",
                ),
                hasLocalReport = true,
            ),
        )
    }

    @Test
    fun `older response cannot replace active report request`() {
        assertFalse(isOperationalReportRequestCurrent(activeRequestId = 8L, completedRequestId = 7L))
        assertTrue(isOperationalReportRequestCurrent(activeRequestId = 8L, completedRequestId = 8L))
    }
}
