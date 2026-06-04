package com.lotterynet.pro.ui.users

import com.lotterynet.pro.core.finance.FinanceActorPeriodRow
import com.lotterynet.pro.core.finance.FinanceSummary
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAccountsFormattingTest {

    @Test
    fun `commission input shows whole percent instead of decimal fraction`() {
        assertEquals("10", formatCommissionInput(0.10))
        assertEquals("5", formatCommissionInput(0.05))
    }

    @Test
    fun `commission label shows trimmed percent`() {
        assertEquals("10%", formatCommissionLabel(0.10))
    }

    @Test
    fun `user accounts layout compacts phone screens`() {
        val tight = resolveUserAccountsLayout(LotteryNetWindowMode.POS_TIGHT)
        val wide = resolveUserAccountsLayout(LotteryNetWindowMode.WIDE)

        assertEquals(2, tight.tabsColumns)
        assertTrue(tight.compactReadOnlyHint)
        assertTrue(tight.useCompactRows)
        assertFalse(tight.showLargeCards)
        assertTrue(tight.cardSpacingDp < wide.cardSpacingDp)
        assertTrue(tight.cardPaddingVerticalDp <= 7)
        assertTrue(tight.filterPaddingVerticalDp <= 7)
        assertFalse(wide.compactReadOnlyHint)
        assertTrue(wide.cardPaddingVerticalDp >= 9)
    }

    @Test
    fun `user role labels stay human readable`() {
        assertEquals("Admin", presentUserRoleLabel(UserRole.ADMIN))
        assertEquals("Supervisor", presentUserRoleLabel(UserRole.SUPERVISOR))
        assertEquals("Cajero", presentUserRoleLabel(UserRole.CASHIER))
        assertEquals("Usuario", presentUserRoleLabel(UserRole.UNKNOWN))
    }

    @Test
    fun `admin filters include operational buckets`() {
        assertEquals(
            listOf("Todos", "Activos", "Bloqueados", "Cajeros", "Supervisores", "Admin"),
            userAccountFilterOptions().map { it.label },
        )
    }

    @Test
    fun `admin filter can show supervisors apart from cashiers`() {
        val accounts = listOf(
            UserAccount(id = "admin", user = "admin1", role = UserRole.ADMIN, displayName = "Banca Central"),
            UserAccount(id = "sup", user = "sup01", role = UserRole.SUPERVISOR, displayName = "Supervisor Norte"),
            UserAccount(id = "ana", user = "ana01", role = UserRole.CASHIER, displayName = "Ana Gomez"),
        )

        val filtered = filterUserAccountsForAdmin(accounts, UserAccountFilter.SUPERVISORS, "sup")

        assertEquals(listOf("sup"), filtered.map { it.id })
    }

    @Test
    fun `supervisor section labels expose creation and assignment`() {
        assertEquals(
            listOf(
                "Crear supervisor",
                "Asignar cajeros",
                "Comision supervisor",
                "Guardar clave",
                "Compartir credencial",
                "Bloquear supervisor",
                "Desbloquear supervisor",
                "Eliminar supervisor",
                "Guardar grupo",
            ),
            supervisorSectionActionLabels(),
        )
    }

    @Test
    fun `supervisor admin view labels split crowded operations`() {
        assertEquals(
            listOf("Crear", "Grupo", "Credenciales"),
            supervisorAdminViewLabels(),
        )
    }

    @Test
    fun `cashier system mode labels stay cashier friendly`() {
        assertEquals("Solo Lotería", com.lotterynet.pro.core.storage.cashierSystemModeOverrideLabel(null))
        assertEquals("Solo Lotería", com.lotterynet.pro.core.storage.cashierSystemModeOverrideLabel("admin"))
        assertEquals("Solo Lotería", com.lotterynet.pro.core.storage.cashierSystemModeOverrideLabel("lottery"))
        assertEquals("Solo Pick", com.lotterynet.pro.core.storage.cashierSystemModeOverrideLabel("pick"))
        assertEquals("Lotería + Pick", com.lotterynet.pro.core.storage.cashierSystemModeOverrideLabel("both"))
    }

    @Test
    fun `supervisor menu opens supervisor admin section first`() {
        assertEquals("SUPERVISORS", resolveInitialAdminSectionName("SUPERVISORS"))
        assertEquals("ACCOUNTS", resolveInitialAdminSectionName(null))
    }

    @Test
    fun `cashier console hides supervisor administration tabs`() {
        assertEquals(listOf("Cajeros", "Límites", "Modo venta", "Premios"), cashierAdminSectionLabels(supervisorConsole = false))
        assertEquals(listOf("Supervisores"), cashierAdminSectionLabels(supervisorConsole = true))
    }

    @Test
    fun `cashier admin tasks keep account cards separate from limit editing`() {
        val contract = resolveCashierAdminTaskContract("ACCOUNTS")
        val limits = resolveCashierAdminTaskContract("LIMITS")

        assertTrue(contract.showsAccountCards)
        assertFalse(contract.showsLimitEditor)
        assertFalse(limits.showsAccountCards)
        assertTrue(limits.showsLimitEditor)
        assertEquals("Cajeros", contract.title)
        assertEquals("Límites", limits.title)
    }

    @Test
    fun `supervisor detail rows expose id user status and assigned group`() {
        val supervisor = UserAccount(
            id = "SUP-7",
            user = "sup07",
            role = UserRole.SUPERVISOR,
            displayName = "Supervisor Sur",
            active = false,
            commissionRate = 0.12,
        )

        val rows = supervisorDetailRows(supervisor, assignedCashiers = 3)

        assertEquals(
            listOf("ID: SUP-7", "Usuario: sup07", "Estado: Bloqueado", "Comision: 12%", "Grupo: 3 cajero(s)"),
            rows,
        )
    }

    @Test
    fun `admin creates supervisor with login secret and assigned cashiers`() {
        val admin = UserAccount(id = "ADM-1", user = "admin01", role = UserRole.ADMIN, banca = "Banca Norte")
        val cashiers = listOf(
            UserAccount(id = "CAJ-1", user = "cajero01", role = UserRole.CASHIER, adminId = "ADM-1"),
            UserAccount(id = "CAJ-2", user = "cajero02", role = UserRole.CASHIER, adminId = "ADM-1"),
        )

        val result = buildSupervisorCreateResult(
            admin = admin,
            existingAccounts = listOf(admin) + cashiers,
            cashiers = cashiers,
            rawUser = " sup01 ",
            rawName = " Supervisor Norte ",
            rawPassword = "123456",
            assignedCashierIds = setOf("CAJ-1"),
            active = true,
        )

        assertEquals("sup01", result.supervisor.user)
        assertEquals(UserRole.SUPERVISOR, result.supervisor.role)
        assertEquals("ADM-1", result.supervisor.adminId)
        assertEquals("admin01", result.supervisor.adminUser)
        assertEquals("Banca Norte", result.supervisor.banca)
        assertTrue(result.supervisor.passwordSalt?.isNotBlank() == true)
        assertTrue(result.supervisor.passwordHash?.isNotBlank() == true)
        assertEquals("123456", result.password)
        assertEquals(listOf("SUP-1"), result.cashiers.first { it.id == "CAJ-1" }.supervisorIds)
        assertEquals(emptyList<String>(), result.cashiers.first { it.id == "CAJ-2" }.supervisorIds)
    }

    @Test
    fun `supervisor commission stays on supervisor and does not replace cashier commission`() {
        val supervisor = UserAccount(id = "SUP-1", user = "sup01", role = UserRole.SUPERVISOR)
        val cashiers = listOf(
            UserAccount(id = "CAJ-1", user = "cajero01", role = UserRole.CASHIER, commissionRate = 0.10),
            UserAccount(id = "CAJ-2", user = "cajero02", role = UserRole.CASHIER, commissionRate = 0.07),
        )

        val updated = applySupervisorAssignments(
            cashiers = cashiers,
            supervisor = supervisor,
            assignedCashierIds = setOf("CAJ-1"),
            groupCommissionRate = parseSupervisorGroupCommission("12"),
        )

        assertEquals(0.10, updated.first { it.id == "CAJ-1" }.commissionRate ?: 0.0, 0.0001)
        assertEquals(0.07, updated.first { it.id == "CAJ-2" }.commissionRate ?: 0.0, 0.0001)
    }

    @Test
    fun `supervisor assignment never links cashiers from another admin`() {
        val supervisor = UserAccount(
            id = "SUP-1",
            user = "sup01",
            role = UserRole.SUPERVISOR,
            adminId = "ADM-1",
            adminUser = "admin01",
            banca = "Banca Uno",
        )
        val ownCashier = UserAccount(
            id = "CAJ-1",
            user = "cajero01",
            role = UserRole.CASHIER,
            adminId = "ADM-1",
            adminUser = "admin01",
            banca = "Banca Uno",
        )
        val otherAdminCashier = UserAccount(
            id = "CAJ-2",
            user = "cajero02",
            role = UserRole.CASHIER,
            adminId = "ADM-2",
            adminUser = "admin02",
            banca = "Banca Dos",
        )

        val updated = applySupervisorAssignments(
            cashiers = listOf(ownCashier, otherAdminCashier),
            supervisor = supervisor,
            assignedCashierIds = setOf("CAJ-1", "CAJ-2"),
        )

        assertEquals(listOf("SUP-1"), updated.first { it.id == "CAJ-1" }.supervisorIds)
        assertEquals(emptyList<String>(), updated.first { it.id == "CAJ-2" }.supervisorIds)
        assertEquals(emptyList<String>(), updated.first { it.id == "CAJ-2" }.supervisorUsers)
    }

    @Test
    fun `cashiers assigned to another supervisor are hidden from supervisor picker`() {
        val selected = UserAccount(id = "SUP-1", user = "sup01", role = UserRole.SUPERVISOR, adminId = "ADM-1")
        val ownAssigned = UserAccount(
            id = "CAJ-1",
            user = "cajero01",
            role = UserRole.CASHIER,
            adminId = "ADM-1",
            supervisorIds = listOf("SUP-1"),
        )
        val free = UserAccount(id = "CAJ-2", user = "cajero02", role = UserRole.CASHIER, adminId = "ADM-1")
        val assignedToOtherSupervisor = UserAccount(
            id = "CAJ-3",
            user = "cajero03",
            role = UserRole.CASHIER,
            adminId = "ADM-1",
            supervisorIds = listOf("SUP-2"),
            supervisorUsers = listOf("sup02"),
        )
        val otherAdmin = UserAccount(id = "CAJ-4", user = "cajero04", role = UserRole.CASHIER, adminId = "ADM-2")

        val visible = supervisorAssignableCashiers(
            cashiers = listOf(ownAssigned, free, assignedToOtherSupervisor, otherAdmin),
            selectedSupervisor = selected,
        )

        assertEquals(listOf("CAJ-1", "CAJ-2"), visible.map { it.id })
    }

    @Test
    fun `creating supervisor only offers free cashiers from the same admin in natural order`() {
        val admin = UserAccount(id = "ADM-1", user = "admin01", role = UserRole.ADMIN, banca = "Banca Uno")
        val cajero10 = UserAccount(id = "CAJ-10", user = "cajero10", role = UserRole.CASHIER, adminId = "ADM-1")
        val cajero2 = UserAccount(id = "CAJ-2", user = "cajero2", role = UserRole.CASHIER, adminId = "ADM-1")
        val cajero1 = UserAccount(id = "CAJ-1", user = "cajero1", role = UserRole.CASHIER, adminId = "ADM-1")
        val assignedToOtherSupervisor = UserAccount(
            id = "CAJ-3",
            user = "cajero3",
            role = UserRole.CASHIER,
            adminId = "ADM-1",
            supervisorIds = listOf("SUP-9"),
        )
        val otherAdmin = UserAccount(id = "CAJ-4", user = "cajero4", role = UserRole.CASHIER, adminId = "ADM-2")

        val visible = supervisorAvailableCashiersForCreate(
            cashiers = listOf(cajero10, assignedToOtherSupervisor, otherAdmin, cajero2, cajero1),
            admin = admin,
        )

        assertEquals(listOf("CAJ-1", "CAJ-2", "CAJ-10"), visible.map { it.id })
    }

    @Test
    fun `admin deleting supervisor removes supervisor and clears cashier designations`() {
        val supervisor = UserAccount(id = "SUP-1", user = "sup01", role = UserRole.SUPERVISOR)
        val otherSupervisor = UserAccount(id = "SUP-2", user = "sup02", role = UserRole.SUPERVISOR)
        val cashiers = listOf(
            UserAccount(
                id = "CAJ-1",
                user = "cajero01",
                role = UserRole.CASHIER,
                supervisorIds = listOf("SUP-1"),
                supervisorUsers = listOf("sup01"),
            ),
            UserAccount(
                id = "CAJ-2",
                user = "cajero02",
                role = UserRole.CASHIER,
                supervisorIds = listOf("SUP-2"),
                supervisorUsers = listOf("sup02"),
            ),
        )

        val result = deleteSupervisorAndClearAssignments(
            supervisors = listOf(supervisor, otherSupervisor),
            cashiers = cashiers,
            supervisor = supervisor,
        )

        assertEquals(listOf("SUP-2"), result.supervisors.map { it.id })
        assertEquals(emptyList<String>(), result.cashiers.first { it.id == "CAJ-1" }.supervisorIds)
        assertEquals(emptyList<String>(), result.cashiers.first { it.id == "CAJ-1" }.supervisorUsers)
        assertEquals(listOf("SUP-2"), result.cashiers.first { it.id == "CAJ-2" }.supervisorIds)
    }

    @Test
    fun `admin creates supervisor with supervisor commission rate`() {
        val admin = UserAccount(id = "ADM-1", user = "admin01", role = UserRole.ADMIN, banca = "Banca Norte")
        val result = buildSupervisorCreateResult(
            admin = admin,
            existingAccounts = listOf(admin),
            cashiers = emptyList(),
            rawUser = "sup01",
            rawName = "Supervisor Norte",
            rawPassword = "123456",
            assignedCashierIds = emptySet(),
            active = true,
            groupCommissionRate = parseSupervisorGroupCommission("8"),
        )

        assertEquals(0.08, result.supervisor.commissionRate ?: 0.0, 0.0001)
    }

    @Test
    fun `supervisor credential share text stays clean and hides technical secrets`() {
        val supervisor = UserAccount(
            id = "SUP-1",
            user = "sup01",
            role = UserRole.SUPERVISOR,
            displayName = "Supervisor Norte",
            banca = "Banca Norte",
            passwordHash = "secret-hash",
            passwordSalt = "secret-salt",
        )

        val text = buildSupervisorCredentialShareText(supervisor, "123456")

        assertTrue(text.contains("Banca: Banca Norte"))
        assertTrue(text.contains("Usuario: sup01"))
        assertTrue(text.contains("Clave: 123456"))
        assertFalse(text.contains("secret-hash"))
        assertFalse(text.contains("secret-salt"))
    }

    @Test
    fun `admin can reset supervisor password without storing clear text`() {
        val supervisor = UserAccount(
            id = "SUP-1",
            user = "sup01",
            role = UserRole.SUPERVISOR,
            passwordHash = "old-hash",
            passwordSalt = "old-salt",
        )

        val result = resetSupervisorPassword(supervisor, "nueva123")

        assertEquals("nueva123", result.password)
        assertEquals("sup01", result.supervisor.user)
        assertTrue(result.supervisor.passwordHash?.isNotBlank() == true)
        assertTrue(result.supervisor.passwordSalt?.isNotBlank() == true)
        assertFalse(result.supervisor.passwordHash == "old-hash")
        assertFalse(result.supervisor.passwordSalt == "old-salt")
        assertFalse(result.supervisor.passwordHash?.contains("nueva123") == true)
    }

    @Test
    fun `admin can block and unblock supervisor without changing account identity`() {
        val supervisor = UserAccount(
            id = "SUP-1",
            user = "sup01",
            role = UserRole.SUPERVISOR,
            displayName = "Supervisor Norte",
            active = true,
            updatedAtEpochMs = 100L,
        )

        val blocked = toggleSupervisorActive(supervisor, nowEpochMs = 200L)
        val unblocked = toggleSupervisorActive(blocked, nowEpochMs = 300L)

        assertEquals("SUP-1", blocked.id)
        assertEquals("sup01", blocked.user)
        assertEquals("Supervisor Norte", blocked.displayName)
        assertFalse(blocked.active)
        assertEquals(200L, blocked.updatedAtEpochMs)
        assertTrue(unblocked.active)
        assertEquals(300L, unblocked.updatedAtEpochMs)
    }

    @Test
    fun `admin filter applies query and cashier bucket`() {
        val accounts = listOf(
            UserAccount(id = "admin", user = "admin1", role = UserRole.ADMIN, displayName = "Banca Central"),
            UserAccount(id = "ana", user = "ana01", role = UserRole.CASHIER, displayName = "Ana Gomez", phone = "8091112222"),
            UserAccount(id = "luis", user = "luis01", role = UserRole.CASHIER, displayName = "Luis", active = false),
        )

        val filtered = filterUserAccountsForAdmin(accounts, UserAccountFilter.CASHIERS, "ana")

        assertEquals(listOf("ana"), filtered.map { it.id })
    }

    @Test
    fun `admin can edit cashier display name without changing login user`() {
        val account = UserAccount(id = "c1", user = "cajero01", role = UserRole.CASHIER, displayName = "Cajero 1")

        val renamed = applyEditableCashierDisplayName(account, "  Ventanilla Norte  ")
        val cleared = applyEditableCashierDisplayName(renamed, "   ")

        assertEquals("cajero01", renamed.user)
        assertEquals("Ventanilla Norte", renamed.displayName)
        assertEquals("cajero01", cleared.user)
        assertEquals(null, cleared.displayName)
    }

    @Test
    fun `admin account list uses natural cashier order`() {
        val accounts = listOf(
            UserAccount(id = "ten", user = "cajero10", role = UserRole.CASHIER, displayName = "Cajero 10"),
            UserAccount(id = "two", user = "cajero2", role = UserRole.CASHIER, displayName = "Cajero 2"),
            UserAccount(id = "admin", user = "admin1", role = UserRole.ADMIN, displayName = "Admin"),
            UserAccount(id = "one", user = "cajero1", role = UserRole.CASHIER, displayName = "Cajero 1"),
            UserAccount(id = "ana", user = "ana", role = UserRole.CASHIER, displayName = "Ana"),
        )

        val filtered = filterUserAccountsForAdmin(accounts, UserAccountFilter.ALL, "")

        assertEquals(listOf("admin", "one", "two", "ten", "ana"), filtered.map { it.id })
    }

    @Test
    fun `admin cashier selector starts with global values and then admins plus cashiers`() {
        val accounts = listOf(
            UserAccount(id = "admin", user = "admin1", role = UserRole.ADMIN, displayName = "Banca Central"),
            UserAccount(id = "luis", user = "cajero10", role = UserRole.CASHIER, displayName = "Cajero 10"),
            UserAccount(id = "ana", user = "cajero2", role = UserRole.CASHIER, displayName = "Cajero 2"),
            UserAccount(id = "zoe", user = "zoe", role = UserRole.CASHIER, displayName = "Zoe Mesa"),
        )

        val options = cashierSelectorOptions(accounts)
        val selected = resolveSelectedCashierAccount(accounts, selectedCashierId = "luis")
        val selectedAdmin = resolveSelectedCashierAccount(accounts, selectedCashierId = "admin")

        assertEquals(listOf(ALL_CASHIER_LIMITS_ID, "admin", "ana", "luis", "zoe"), options.map { it.id })
        assertEquals("Valores globales", options.first().label)
        assertEquals("luis", selected?.id)
        assertEquals("admin", selectedAdmin?.id)
    }

    @Test
    fun `admin and master edit their own sales limit scope`() {
        assertTrue(accountUsesAdminSelfSalesLimits(UserAccount(id = "admin", user = "admin1", role = UserRole.ADMIN)))
        assertTrue(accountUsesAdminSelfSalesLimits(UserAccount(id = "master", user = "master1", role = UserRole.MASTER)))
        assertFalse(accountUsesAdminSelfSalesLimits(UserAccount(id = "cashier", user = "cajero1", role = UserRole.CASHIER)))
    }

    @Test
    fun `cashier selector uses natural number order before names`() {
        val accounts = listOf(
            UserAccount(id = "ten", user = "cajero10", role = UserRole.CASHIER, displayName = "Cajero 10"),
            UserAccount(id = "ana", user = "ana", role = UserRole.CASHIER, displayName = "Ana"),
            UserAccount(id = "two", user = "cajero2", role = UserRole.CASHIER, displayName = "Cajero 2"),
            UserAccount(id = "one", user = "cajero1", role = UserRole.CASHIER, displayName = "Cajero 1"),
        )

        assertEquals(
            listOf(ALL_CASHIER_LIMITS_ID, "one", "two", "ten", "ana"),
            cashierSelectorOptions(accounts).map { it.id },
        )
    }

    @Test
    fun `admin visible cashier details never show all cashier cards at once`() {
        val accounts = listOf(
            UserAccount(id = "admin", user = "admin1", role = UserRole.ADMIN, displayName = "Banca Central"),
            UserAccount(id = "one", user = "cajero1", role = UserRole.CASHIER, displayName = "Cajero 1"),
            UserAccount(id = "two", user = "cajero2", role = UserRole.CASHIER, displayName = "Cajero 2"),
        )

        assertEquals(emptyList<String>(), resolveAdminVisibleCashierDetailAccounts(accounts, ALL_CASHIER_LIMITS_ID).map { it.id })
        assertEquals(listOf("two"), resolveAdminVisibleCashierDetailAccounts(accounts, "two").map { it.id })
        assertEquals(listOf("admin"), resolveAdminVisibleCashierDetailAccounts(accounts, "admin").map { it.id })
        assertEquals(listOf("admin"), resolveAdminVisibleCashierDetailAccounts(accounts, null).map { it.id })
    }

    @Test
    fun `cashier admin server action uses service wording`() {
        assertEquals("Actualizar servidor", cashierAdminServerActionLabel())
        assertEquals("Guardar servidor", cashierAdminSaveServerActionLabel())
        assertEquals("Guardar servidor", cashierAccountSaveServerActionLabel())
        assertEquals("Actualizado desde servidor", cashierAdminServerStatusLabel(success = true))
        assertEquals("No se pudo actualizar", cashierAdminServerStatusLabel(success = false))
    }

    @Test
    fun `cashier payout label separates global from individual adjustment`() {
        assertEquals("Tope pago premios todos", cashierPayoutLimitLabel(selectedAllCashiers = true))
        assertEquals("Tope pago premios cajero", cashierPayoutLimitLabel(selectedAllCashiers = false))
    }

    @Test
    fun `cashier prize section label explains that premios are payout settings`() {
        assertEquals("Pago premios", cashierPrizeSectionLabel())
        assertTrue(cashierPrizeSectionPurpose().contains("paga", ignoreCase = true))
    }

    @Test
    fun `cashier admin fields separate sales limits from prize payouts`() {
        assertEquals(
            listOf("Estado", "Límite diario de venta", "Tope pago premios", "Quiniela venta diaria", "Pale venta diaria", "Super Pale venta diaria", "Tripleta venta diaria", "Pick 3 Straight venta", "Pick 3 Box venta", "Pick 4 Straight venta", "Pick 4 Box venta"),
            cashierAdminFieldLabels(),
        )
        assertFalse(cashierAdminFieldLabels().any { it.contains("Comisi", ignoreCase = true) })
        assertFalse(cashierAdminFieldLabels().any { it.contains("way", ignoreCase = true) })
    }

    @Test
    fun `cashier performance period selector stays hidden`() {
        assertFalse(cashierPerformancePeriodSelectorVisible())
    }

    @Test
    fun `cashier prize payout fields expose only simple pick settings`() {
        val labels = cashierPrizePayoutFieldLabels()

        assertTrue(labels.containsAll(listOf("P3 directo", "P3 box", "P4 directo", "P4 box")))
        assertFalse(labels.any { it.contains("box 3", ignoreCase = true) })
        assertFalse(labels.any { it.contains("box 6", ignoreCase = true) })
        assertFalse(labels.any { it.contains("box 12", ignoreCase = true) })
        assertFalse(labels.any { it.contains("box 24", ignoreCase = true) })
    }

    @Test
    fun `cashier prize payout inputs collapse pick box variants into one value`() {
        val config = prizeConfigFromInputs(
            mapOf(
                "pick3Straight" to "500",
                "pick3Box" to "80",
                "pick4Straight" to "5000",
                "pick4Box" to "200",
            ),
        )

        assertEquals(500, config.pick3Straight)
        assertEquals(80, config.pick3Box3)
        assertEquals(80, config.pick3Box6)
        assertEquals(5000, config.pick4Straight)
        assertEquals(200, config.pick4Box4)
        assertEquals(200, config.pick4Box6)
        assertEquals(200, config.pick4Box12)
        assertEquals(200, config.pick4Box24)
    }

    @Test
    fun `cashier metric flags negative net as loss`() {
        val metric = buildCashierAccountMetric(
            row = FinanceActorPeriodRow(
                actorKey = "ana",
                actorDisplay = "Ana",
                summary = FinanceSummary(
                    ventas = 100.0,
                    comision = 10.0,
                    premiosPagados = 160.0,
                    cajaDisponible = -70.0,
                ),
            ),
            maxVentas = 100.0,
        )

        assertTrue(metric.isLoss)
        assertEquals(1.0f, metric.salesRatio, 0.001f)
        assertEquals(0.1f, metric.commissionRatio, 0.001f)
    }
}
