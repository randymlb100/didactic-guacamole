package com.lotterynet.pro.ui.master

import org.junit.Assert.assertEquals
import org.junit.Test

class MasterDashboardNavigationTest {

    @Test
    fun `master center exposes five administrative areas`() {
        assertEquals(
            listOf("Resumen", "Bancas", "Módulos", "Sistema", "Seguridad"),
            masterPrimaryDestinations().map { it.label },
        )
    }

    @Test
    fun `master modules destination exposes services games and sportsbook`() {
        assertEquals(
            setOf("services", "video_games", "sportsbook"),
            masterModuleEntries().map { it.id }.toSet(),
        )
    }

    @Test
    fun `master bank detail separates responsibilities`() {
        assertEquals(
            listOf("Resumen", "Cajeros", "Fondos", "Seguridad"),
            MasterBankDetailArea.entries.map { it.label },
        )
    }

    @Test
    fun `master back clears bank detail before leaving its section`() {
        assertEquals(
            MasterBackResult.CLEAR_BANK_SELECTION,
            masterBackDestination(
                current = MasterDestination.BANKS,
                hasSelectedAdmin = true,
            ),
        )
    }

    @Test
    fun `master back returns primary sections to overview`() {
        assertEquals(
            MasterBackResult.GO_TO_OVERVIEW,
            masterBackDestination(
                current = MasterDestination.MODULES,
                hasSelectedAdmin = false,
            ),
        )
    }

    @Test
    fun `master back exits only from overview`() {
        assertEquals(
            MasterBackResult.EXIT,
            masterBackDestination(
                current = MasterDestination.OVERVIEW,
                hasSelectedAdmin = false,
            ),
        )
    }

    @Test
    fun `master status never claims synchronization without evidence`() {
        assertEquals(
            MasterStatusBadge("Estado local listo", MasterStatusKind.NEUTRAL),
            resolveMasterStatusBadge(busy = false, statusMessage = null),
        )
        assertEquals(
            MasterStatusBadge("Cambio confirmado", MasterStatusKind.SUCCESS),
            resolveMasterStatusBadge(busy = false, statusMessage = "Servidor confirmó el cambio."),
        )
        assertEquals(
            MasterStatusBadge("Cambios pendientes", MasterStatusKind.WARNING),
            resolveMasterStatusBadge(busy = false, statusMessage = "Guardado local; servidor pendiente."),
        )
    }
}
