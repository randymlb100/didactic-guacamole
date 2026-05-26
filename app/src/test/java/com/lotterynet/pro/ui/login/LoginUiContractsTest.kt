package com.lotterynet.pro.ui.login

import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.SavedLogin
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginUiContractsTest {

    @Test
    fun `tight login removes supporting copy and primary panel`() {
        val contract = resolveLoginLayoutContract(LotteryNetWindowMode.POS_TIGHT)

        assertFalse(contract.usePanel)
        assertFalse(contract.showSupportingText)
        assertEquals(6, contract.panelPaddingDp)
    }

    @Test
    fun `phone login keeps compact panel spacing without legacy secondary action`() {
        val contract = resolveLoginLayoutContract(LotteryNetWindowMode.POS)

        assertFalse(contract.usePanel)
        assertFalse(contract.showSupportingText)
        assertEquals(8, contract.panelPaddingDp)
    }

    @Test
    fun `non sales redesign keeps login compact without extra copy on pos`() {
        val contract = resolveLoginLayoutContract(LotteryNetWindowMode.POS)

        assertTrue(contract.compactPanel)
        assertFalse(contract.showSupportingText)
        assertTrue(contract.panelPaddingDp <= 8)
    }

    @Test
    fun `wide login can still use panel layout`() {
        val contract = resolveLoginLayoutContract(LotteryNetWindowMode.WIDE)

        assertTrue(contract.usePanel)
    }

    @Test
    fun `login with cached users does not wait for bootstrap to enable entry`() {
        val gate = resolveLoginBootstrapGate(hasCachedUsers = true)

        assertFalse(gate.blockEntryWhileBootstrapping)
    }

    @Test
    fun `login without cached users waits for bootstrap before entry`() {
        val gate = resolveLoginBootstrapGate(hasCachedUsers = false)

        assertTrue(gate.blockEntryWhileBootstrapping)
    }

    @Test
    fun `login submit is disabled while authenticating`() {
        val gate = resolveLoginSubmitGate(bootstrapBusy = false, loginBusy = true)

        assertFalse(gate.enabled)
        assertEquals("Verificando credenciales...", gate.status)
    }

    @Test
    fun `login submit is disabled while bootstrap is busy`() {
        val gate = resolveLoginSubmitGate(bootstrapBusy = true, loginBusy = false)

        assertFalse(gate.enabled)
    }

    @Test
    fun `login copy shows product slogan`() {
        val copy = resolveLoginCopyContract()

        assertEquals("Venta rapida, caja clara, tickets seguros.", copy.slogan)
        assertTrue(copy.showSlogan)
    }

    @Test
    fun `password visibility contract toggles label and masking`() {
        val hidden = resolvePasswordVisibilityContract(showPassword = false)
        val visible = resolvePasswordVisibilityContract(showPassword = true)

        assertEquals("Mostrar contraseña", hidden.actionLabel)
        assertTrue(hidden.maskPassword)
        assertEquals("Ocultar contraseña", visible.actionLabel)
        assertFalse(visible.maskPassword)
    }

    @Test
    fun `remember login can prefill saved password`() {
        val saved = SavedLogin(username = "cajero01", password = "1234", remember = true)

        assertEquals("1234", resolveInitialLoginPassword(saved))
        assertEquals("", resolveInitialLoginPassword(saved.copy(remember = false)))
        assertEquals("", resolveInitialLoginPassword(null))
    }

    @Test
    fun `login explains admin blocked by master`() {
        val message = resolveBlockedLoginStatusMessage(
            username = "admin01",
            accounts = listOf(UserAccount(id = "admin-1", user = "admin01", role = UserRole.ADMIN, active = false)),
        )

        assertEquals("Esta cuenta está bloqueada por Master.", message)
    }

    @Test
    fun `login explains cashier blocked by admin`() {
        val message = resolveBlockedLoginStatusMessage(
            username = "cajero01",
            accounts = listOf(UserAccount(id = "cashier-1", user = "cajero01", role = UserRole.CASHIER, active = false)),
        )

        assertEquals("Tus credenciales están bloqueadas por admin.", message)
    }
}
