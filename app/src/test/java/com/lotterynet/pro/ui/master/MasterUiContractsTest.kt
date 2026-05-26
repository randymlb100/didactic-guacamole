package com.lotterynet.pro.ui.master

import com.lotterynet.pro.core.master.IssuedCredential
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterUiContractsTest {

    @Test
    fun `phone master dashboard compacts summary and shortens bank actions`() {
        val contract = resolveMasterDashboardLayout(LotteryNetWindowMode.POS)

        assertTrue(contract.compactSummary)
        assertTrue(contract.compactBanks)
        assertTrue(contract.useCompactRows)
        assertFalse(contract.showLargeCards)
        assertTrue(contract.splitServerActions)
        assertTrue(contract.shortBankActionLabels)
    }

    @Test
    fun `wide master create bank keeps richer credential rows`() {
        val contract = resolveMasterCreateBankLayout(LotteryNetWindowMode.WIDE)

        assertFalse(contract.compactCredentials)
        assertFalse(contract.shortenSectionMeta)
    }

    @Test
    fun `master create bank follows compact provision segments`() {
        assertEquals(listOf("Banca", "Usuarios", "Resumen"), masterCreateBankSegmentOptions().map { it.label })
        assertEquals(listOf("RD", "USA"), masterCreateBankTerritoryOptions().map { it.label })
        assertEquals("Pendiente", masterCreateBankStatusLabel(created = false))
        assertEquals("Credenciales listas", masterCreateBankStatusLabel(created = true))
    }

    @Test
    fun `master dashboard labels generated keys as a reset action`() {
        assertEquals("Gen.", masterCredentialResetActionLabel(short = true))
        assertEquals("Generar claves", masterCredentialResetActionLabel(short = false))
    }

    @Test
    fun `master dashboard labels cashier dropdown and group password action`() {
        assertEquals("Caj.", masterCashierDropdownActionLabel(short = true))
        assertEquals("Cajeros", masterCashierDropdownActionLabel(short = false))
        assertEquals("Todos", masterCashierGroupPasswordActionLabel(short = true))
        assertEquals("Clave a todos", masterCashierGroupPasswordActionLabel(short = false))
    }

    @Test
    fun `master dashboard groups sections by operational intent`() {
        assertEquals(
            listOf("Bancas", "Credenciales", "Servidor/Nube", "Recargas Master", "Auditoría"),
            masterDashboardSectionTitles(),
        )
    }

    @Test
    fun `master dashboard exposes compact bank filters`() {
        assertEquals(
            listOf("Todas", "Activas", "Bloqueadas", "Con problemas"),
            masterBankFilterOptions().map { it.label },
        )
    }

    @Test
    fun `master dashboard separates dangerous actions`() {
        assertEquals(listOf("Bloq.", "Borra", "Gen."), masterDangerActionLabels(short = true))
        assertEquals(listOf("Bloquear", "Borrar", "Generar claves"), masterDangerActionLabels(short = false))
    }

    @Test
    fun `master admin selector filters by name and orders by creation`() {
        val newest = UserAccount(
            id = "adm-new",
            user = "dueno02",
            role = UserRole.ADMIN,
            displayName = "Carlos",
            banca = "Banca Nueva",
            createdLabel = "08/05/2026 09:10 AM",
        )
        val oldest = UserAccount(
            id = "adm-old",
            user = "dueno01",
            role = UserRole.ADMIN,
            displayName = "Ana",
            banca = "Banca Vieja",
            createdLabel = "01/05/2026 08:00 AM",
        )

        val ordered = sortMasterAdminsByCreation(listOf(newest, oldest))
        val filtered = filterMasterAdminsForSelector(listOf(newest, oldest), "ana")

        assertEquals(listOf("adm-old", "adm-new"), ordered.map { it.id })
        assertEquals(listOf("adm-old"), filtered.map { it.id })
    }

    @Test
    fun `master bank filters handle large bank lists without opening every bank`() {
        val active = UserAccount(
            id = "adm-active",
            user = "dueno01",
            role = UserRole.ADMIN,
            displayName = "Ana",
            banca = "Banca Activa",
            phone = "8091112222",
            active = true,
            rechargesEnabled = true,
            recargasRapidasUsername = "rr_active",
            createdLabel = "01/05/2026 08:00 AM",
        )
        val blocked = active.copy(
            id = "adm-blocked",
            user = "dueno02",
            displayName = "Bruno",
            banca = "Banca Bloqueada",
            active = false,
            createdLabel = "02/05/2026 08:00 AM",
        )
        val issue = active.copy(
            id = "adm-issue",
            user = "dueno03",
            displayName = "Carla",
            banca = "Banca Sin RR",
            recargasRapidasUsername = null,
            createdLabel = "03/05/2026 08:00 AM",
        )
        val banks = listOf(issue, blocked, active)

        assertEquals(listOf("adm-active"), filterMasterBanksForDashboard(banks, "809111", MasterBankFilter.ACTIVE).map { it.id })
        assertEquals(listOf("adm-blocked"), filterMasterBanksForDashboard(banks, "", MasterBankFilter.BLOCKED).map { it.id })
        assertEquals(listOf("adm-blocked", "adm-issue"), filterMasterBanksForDashboard(banks, "", MasterBankFilter.ISSUES).map { it.id })
    }

    @Test
    fun `master dashboard distinguishes changed password from generated credentials`() {
        assertEquals("Clave actualizada", MasterIssuedCredentialsMode.PASSWORD_CHANGED.title)
        assertEquals("Claves nuevas generadas", MasterIssuedCredentialsMode.CREDENTIALS_REGENERATED.title)
    }

    @Test
    fun `master dashboard share text includes issued cashier credentials`() {
        val text = buildMasterIssuedCredentialsShareText(
            title = "Claves nuevas generadas",
            credentials = listOf(
                IssuedCredential(
                    displayName = "Cajero 2 - Banca yuniel",
                    username = "yuniel02",
                    password = "abc123",
                    role = UserRole.CASHIER,
                )
            ),
        )

        assertTrue(text.contains("Total usuarios: 1"))
        assertTrue(text.contains("Usuario: yuniel02"))
        assertTrue(text.contains("Clave: abc123"))
    }

    @Test
    fun `master can assign recharge access and pool to one admin`() {
        val admin = UserAccount(
            id = "adm-1",
            user = "dueno01",
            role = UserRole.ADMIN,
            balance = 50_000.0,
            rechargesEnabled = false,
            rechargesBalance = 0.0,
        )

        val updated = updateMasterRechargeAccess(admin, enabled = true, amount = 10_000.0)

        assertTrue(updated.rechargesEnabled)
        assertEquals(10_000.0, updated.rechargesAssignedBalance, 0.0)
        assertEquals(10_000.0, updated.rechargesBalance, 0.0)
        assertEquals(50_000.0, updated.balance, 0.0)
    }

    @Test
    fun `master recharge status label shows blocked or assigned pool`() {
        assertEquals("Bloqueada", masterRechargeAccessLabel(enabled = false, assigned = 10_000.0, available = 8_500.0))
        assertEquals("Fondo $10,000 · queda $8,500", masterRechargeAccessLabel(enabled = true, assigned = 10_000.0, available = 8_500.0))
    }

    @Test
    fun `master recharge status keeps assigned and available separated`() {
        val assigned = masterRechargeFundSummary(assigned = 500.0, available = 350.0)

        assertEquals("Fondo $500", assigned.assignedLabel)
        assertEquals("Disponible $350", assigned.availableLabel)
        assertEquals("Vendido $150", assigned.soldLabel)
    }

    @Test
    fun `master stores Recargas Rapidas backend status per admin without local password`() {
        val admin = UserAccount(
            id = "adm-1",
            user = "dueno01",
            role = UserRole.ADMIN,
            rechargesEnabled = true,
            rechargesBalance = 10_000.0,
        )

        val updated = updateMasterRecargasRapidasCredentialStatus(
            admin = admin,
            usernameHint = "rr_admin",
        )

        assertEquals("rr_admin", updated.recargasRapidasUsername)
        assertNull(updated.recargasRapidasPassword)
        assertTrue(updated.rechargesEnabled)
        assertEquals(10_000.0, updated.rechargesBalance, 0.0)
    }

    @Test
    fun `master recharge provider label names Recargas Rapidas and not Reloadly`() {
        val label = masterRechargeProviderLabel()

        assertTrue(label.contains("Recargas Rapidas"))
        assertFalse(label.contains("Reloadly", ignoreCase = true))
    }

    @Test
    fun `master explains default Recargas Rapidas credentials versus admin override`() {
        val pending = UserAccount(id = "adm-1", user = "admin01", role = UserRole.ADMIN)
        val configured = pending.copy(
            recargasRapidasUsername = "rr_admin",
            recargasRapidasPassword = "rr_pass",
        )

        assertEquals("Usa cuenta default backend", masterRecargasRapidasCredentialLabel(pending))
        assertEquals("Cuenta propia en backend", masterRecargasRapidasCredentialLabel(configured))
        assertTrue(masterRecargasRapidasCredentialHelpText().contains("backend", ignoreCase = true))
    }
}
