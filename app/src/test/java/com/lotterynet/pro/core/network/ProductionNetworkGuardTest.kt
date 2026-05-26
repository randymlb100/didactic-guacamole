package com.lotterynet.pro.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionNetworkGuardTest {

    @Test
    fun `critical operations only proceed when app sees an internet network`() {
        assertTrue(ProductionNetworkGuard.canRunCriticalOperation(hasValidatedInternet = true))
        assertFalse(ProductionNetworkGuard.canRunCriticalOperation(hasValidatedInternet = false))
    }

    @Test
    fun `offline block message is explicit for production actions`() {
        assertEquals(
            "Sin internet. No se puede ejecutar esta opción.",
            ProductionNetworkGuard.NO_INTERNET_ACTION_MESSAGE,
        )
    }
}
