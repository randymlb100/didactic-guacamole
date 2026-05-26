package com.lotterynet.pro.ui.recharge

import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.remote.SupabaseEdgeException
import com.lotterynet.pro.core.storage.RechargeLimitSettings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class RecargasUiContractsTest {

    @Test
    fun `phone recargas merges status into header and reduces quick rows`() {
        val contract = resolveRechargeLayout(LotteryNetWindowMode.POS)

        assertFalse(contract.showHeaderMetrics)
        assertTrue(contract.mergeStatusIntoHeader)
        assertTrue(contract.useDenseRows)
        assertTrue(contract.inlineTotals)
        assertEquals(1, contract.quickAmountRows)
        assertFalse(contract.showSummaryCard)
        assertFalse(contract.showInlineLimitSettings)
        assertTrue(contract.formPaddingVerticalDp <= 8)
        assertTrue(contract.providerCardPaddingVerticalDp <= 8)
        assertTrue(contract.historyRowSpacingDp <= 6)
    }

    @Test
    fun `wide recargas keeps richer header metrics`() {
        val contract = resolveRechargeLayout(LotteryNetWindowMode.WIDE)

        assertTrue(contract.showHeaderMetrics)
        assertFalse(contract.mergeStatusIntoHeader)
        assertEquals(2, contract.quickAmountRows)
        assertTrue(contract.showSummaryCard)
        assertFalse(contract.showInlineLimitSettings)
        assertTrue(contract.formPaddingVerticalDp >= 10)
        assertTrue(contract.historyRowSpacingDp >= 8)
    }

    @Test
    fun `master cannot open recharge selling screen`() {
        assertFalse(canOpenRechargeForRole(UserRole.MASTER))
        assertTrue(canOpenRechargeForRole(UserRole.ADMIN))
        assertTrue(canOpenRechargeForRole(UserRole.CASHIER))
    }

    @Test
    fun `history panel uses parent scroll instead of nested lazy list`() {
        assertEquals(RechargeHistoryLayout.EMBEDDED_ROWS, resolveRechargeHistoryLayout())
    }

    @Test
    fun `recargas screen exposes recarga and paquetico modes`() {
        assertEquals(listOf(RechargeSellMode.RECARGA, RechargeSellMode.PAQUETICO), rechargeSellModes())
    }

    @Test
    fun `recargas providers use local logo assets for supported companies`() {
        val providers = rechargeProviderContracts()

        assertEquals(listOf("claro", "altice", "viva", "moun", "digicel", "natcom"), providers.map { it.id })
        assertEquals("logo_claro.svg", providers.first { it.id == "claro" }.logoAsset)
        assertEquals("logo_altice.svg", providers.first { it.id == "altice" }.logoAsset)
        assertEquals("logo_viva.svg", providers.first { it.id == "viva" }.logoAsset)
        assertEquals("logo_moun.svg", providers.first { it.id == "moun" }.logoAsset)
        assertEquals("logo_digicel.svg", providers.first { it.id == "digicel" }.logoAsset)
        assertEquals("logo_natcom.svg", providers.first { it.id == "natcom" }.logoAsset)
    }

    @Test
    fun `paquetico providers include Wind with local logo`() {
        val providers = paqueticoProviderContracts()

        assertEquals(listOf("claro", "altice", "viva", "wind"), providers.map { it.id })
        assertEquals("logo_wind.svg", providers.first { it.id == "wind" }.logoAsset)
    }

    @Test
    fun `quick amounts respect provider minimums`() {
        assertEquals(listOf(25.0, 50.0, 100.0, 200.0), resolveRechargeQuickAmounts("claro"))
        assertEquals(listOf(30.0, 50.0, 100.0, 200.0), resolveRechargeQuickAmounts("altice"))
        assertEquals(listOf(20.0, 50.0, 100.0, 200.0), resolveRechargeQuickAmounts("viva"))
        assertEquals(listOf(50.0, 100.0, 200.0, 500.0), resolveRechargeQuickAmounts("moun"))
        assertEquals(listOf(50.0, 100.0, 200.0, 500.0), resolveRechargeQuickAmounts("natcom"))
    }

    @Test
    fun `paquetico sale creates local pending record with sanitized phone`() {
        val record = buildRechargeSaleRecord(
            provider = rechargeProviderContracts().first { it.id == "claro" },
            mode = RechargeSellMode.PAQUETICO,
            phone = "809-555-0000",
            amount = 125.0,
            userId = "cashier-1",
            userName = "Caja 1",
            adminId = "admin-1",
            adminUser = "Admin",
            now = 1_777_000_000_000,
            id = "rec-test",
        )

        assertEquals("rec-test", record.id)
        assertEquals("claro", record.providerId)
        assertEquals("Claro", record.providerName)
        assertEquals("8095550000", record.phoneNumber)
        assertEquals(125.0, record.amount, 0.0)
        assertEquals("paquetico", record.productType)
        assertEquals("pending", record.status)
        assertEquals(null, record.providerReference)
        assertEquals("cashier-1", record.userId)
        assertEquals("admin-1", record.adminId)
    }

    @Test
    fun `paquetico mode requires consulting and choosing a provider plan`() {
        val state = RechargeFormState(
            mode = RechargeSellMode.PAQUETICO,
            phone = "8092201111",
            amountText = "",
            selectedPaqueticoPlan = null,
        )

        assertEquals("Consulta y elige un paquetico primero.", validateRechargeForm(state))
    }

    @Test
    fun `selected paquetico plan controls registered amount`() {
        val plan = RechargePaqueticoPlanContract(
            id = 44,
            description = "1GB por 1 dia",
            price = 75.0,
        )
        val state = RechargeFormState(
            mode = RechargeSellMode.PAQUETICO,
            phone = "8092201111",
            amountText = "",
            selectedPaqueticoPlan = plan,
        )

        assertEquals(null, validateRechargeForm(state))
        assertEquals(75.0, resolveRechargeFormAmount(state), 0.0)
    }

    @Test
    fun `required provider logos are stored locally in app assets`() {
        val assetsDir = File("src/main/assets")

        (rechargeProviderContracts() + paqueticoProviderContracts()).distinctBy { it.id }.forEach { provider ->
            val logo = assetsDir.resolve(provider.logoAsset)
            assertTrue("${provider.logoAsset} must exist locally", logo.isFile)
            assertTrue("${provider.logoAsset} must not be empty", logo.length() > 0)
        }
    }

    @Test
    fun `master recharge balance is the pool shared by admin and all cashiers`() {
        val admin = UserAccount(
            id = "ADM-1",
            user = "admin01",
            role = UserRole.ADMIN,
            balance = 75_000.0,
            rechargesEnabled = true,
            rechargesAssignedBalance = 10_000.0,
            rechargesBalance = 10_000.0,
        )
        val state = resolveRechargeAccessState(ownerAccount = admin, cashierAccount = null, fallbackLabel = "admin01")

        assertTrue(state.enabled)
        assertEquals(10_000.0, state.assignedBalance, 0.0)
        assertEquals(10_000.0, state.availableBalance, 0.0)
        assertEquals("admin01", state.ownerLabel)
    }

    @Test
    fun `master can block recharges for an admin even when money exists`() {
        val admin = UserAccount(
            id = "ADM-1",
            user = "admin01",
            role = UserRole.ADMIN,
            balance = 75_000.0,
            rechargesEnabled = false,
            rechargesBalance = 10_000.0,
        )
        val state = resolveRechargeAccessState(ownerAccount = admin, cashierAccount = null, fallbackLabel = "admin01")

        assertFalse(state.enabled)
        assertEquals(
            "Recargas bloqueadas por Master para admin01.",
            validateRechargeSubmission(
                amount = 100.0,
                balanceState = state,
                limitSettings = RechargeLimitSettings(),
            ),
        )
    }

    @Test
    fun `blocked recharge access hides recharge section for admin and cashier`() {
        val blockedAdmin = UserAccount(
            id = "ADM-1",
            user = "admin01",
            role = UserRole.ADMIN,
            rechargesEnabled = false,
            rechargesBalance = 10_000.0,
        )
        val activeAdmin = blockedAdmin.copy(rechargesEnabled = true)

        assertFalse(canShowRechargeAccess(ownerAccount = blockedAdmin))
        assertTrue(canShowRechargeAccess(ownerAccount = activeAdmin))
    }

    @Test
    fun `recharge access says master blocked admin account before money checks`() {
        val admin = UserAccount(
            id = "ADM-1",
            user = "admin01",
            role = UserRole.ADMIN,
            active = false,
            rechargesEnabled = true,
            rechargesBalance = 10_000.0,
        )
        val state = resolveRechargeAccessState(ownerAccount = admin, cashierAccount = null, fallbackLabel = "admin01")

        assertFalse(state.enabled)
        assertEquals(
            "Recargas bloqueadas por Master para admin01.",
            validateRechargeSubmission(
                amount = 100.0,
                balanceState = state,
                limitSettings = RechargeLimitSettings(),
            ),
        )
    }

    @Test
    fun `recharge debit consumes discounted cost from the admin recharge pool`() {
        val admin = UserAccount(
            id = "ADM-1",
            user = "admin01",
            role = UserRole.ADMIN,
            balance = 75_000.0,
            rechargesEnabled = true,
            rechargesAssignedBalance = 10_000.0,
            rechargesBalance = 10_000.0,
        )
        val updated = debitRechargeOwnerBalance(admin, 1_250.0)

        assertEquals(75_000.0, updated.balance, 0.0)
        assertEquals(10_000.0, updated.rechargesAssignedBalance, 0.0)
        assertEquals(8_812.5, updated.rechargesBalance, 0.0)
    }

    @Test
    fun `recharge fund discount consumes ninety five percent while sale keeps full amount`() {
        val admin = UserAccount(
            id = "ADM-1",
            user = "admin01",
            role = UserRole.ADMIN,
            balance = 75_000.0,
            rechargesEnabled = true,
            rechargesAssignedBalance = 1_000.0,
            rechargesBalance = 95.0,
        )
        val state = resolveRechargeAccessState(ownerAccount = admin, cashierAccount = null, fallbackLabel = "admin01")

        assertEquals(95.0, resolveRechargeFundDebitAmount(100.0), 0.0)
        assertEquals(
            null,
            validateRechargeSubmission(
                amount = 100.0,
                balanceState = state,
                limitSettings = RechargeLimitSettings(),
            ),
        )
        val updated = debitRechargeOwnerBalance(admin, 100.0)

        assertEquals(0.0, updated.rechargesBalance, 0.0)
    }

    @Test
    fun `recharge balance sync publishes latest users payload`() {
        var publishedPayload: String? = null
        val ok = publishRechargeBalancePayload(
            exportPayload = { """{"admins":[{"id":"ADM-1","recargasBalance":8750}],"cajeros":[]}""" },
            publishPayload = { payload -> publishedPayload = payload },
        )

        assertTrue(ok)
        assertEquals("""{"admins":[{"id":"ADM-1","recargasBalance":8750}],"cajeros":[]}""", publishedPayload)
    }

    @Test
    fun `recharge backend sale payload does not include Recargas Rapidas credentials`() {
        val request = buildRecargasRapidasSaleRequestJson(
            session = ActiveSession(
                userId = "CAJ-1",
                username = "caja01",
                role = UserRole.CASHIER,
                adminId = "ADM-1",
                adminUser = "admin01",
                banca = "Banca Yuniel",
            ),
            ownerAccountId = "ADM-1",
            provider = rechargeProviderContracts().first { it.id == "altice" },
            mode = RechargeSellMode.RECARGA,
            phone = "829-252-3956",
            amount = 100.0,
            paqueticoPlan = null,
            clientRequestId = "req-1",
        )

        assertEquals("recarga", request.getString("mode"))
        assertEquals("altice", request.getString("providerId"))
        assertEquals("ADM-1", request.getString("ownerAccountId"))
        assertEquals("caja01", request.getString("username"))
        assertFalse(request.has("recargasRapidasUsername"))
        assertFalse(request.has("credentialUsername"))
        assertFalse(request.has("password"))
        assertFalse(request.toString().contains("rr_pass"))
    }

    @Test
    fun `completed recharge voucher shows only customer safe proof details`() {
        val record = buildRechargeSaleRecord(
            provider = rechargeProviderContracts().first { it.id == "altice" },
            mode = RechargeSellMode.RECARGA,
            phone = "829-252-3956",
            amount = 30.0,
            userId = "cashier-1",
            userName = "Caja 1",
            adminId = "admin-1",
            adminUser = "Admin",
            now = 1_777_985_481_330,
            id = "rec-proof",
            status = "completed",
            providerReference = "RR-7788",
        )
        val voucher = RechargeVoucherState(
            record = record,
            bancaName = "Banca Yuniel",
            operatorName = "Caja 1",
            saleLabel = "Recarga",
            providerNewBalance = 471.5,
            providerBillNumber = "0",
            printerAvailable = true,
        )

        val text = buildRechargeVoucherText(voucher)

        assertTrue(text.contains("RECARGA RAPIDA"))
        assertTrue(text.contains("Banca Yuniel"))
        assertTrue(text.contains("Compania: Altice"))
        assertTrue(text.contains("Telefono: 8292523956"))
        assertTrue(text.contains("Monto $ 30"))
        assertTrue(text.contains("Referencia: RR-7788"))
        assertFalse(text.contains("Operador"))
        assertFalse(text.contains("Admin"))
        assertFalse(text.contains("Factura API"))
        assertFalse(text.contains("Saldo proveedor"))
        assertFalse(text.contains("ID local"))
    }

    @Test
    fun `shared recharge voucher is clean customer text without printer markers`() {
        val record = buildRechargeSaleRecord(
            provider = rechargeProviderContracts().first { it.id == "altice" },
            mode = RechargeSellMode.RECARGA,
            phone = "829-252-3956",
            amount = 30.0,
            userId = "cashier-1",
            userName = "Caja 1",
            adminId = "admin-1",
            adminUser = "Admin",
            now = 1_777_985_481_330,
            id = "rec-proof",
            status = "completed",
            providerReference = "RR-7788",
        )
        val voucher = RechargeVoucherState(
            record = record,
            bancaName = "Banca Yuniel",
            operatorName = "Caja 1",
            saleLabel = "Recarga",
            providerNewBalance = 471.5,
            providerBillNumber = "0",
            printerAvailable = true,
        )

        val text = buildRechargeVoucherShareText(voucher)

        assertTrue(text.contains("Recarga Rapida - Banca Yuniel"))
        assertTrue(text.contains("Compania: Altice"))
        assertTrue(text.contains("Telefono: 8292523956"))
        assertTrue(text.contains("Monto: $ 30"))
        assertTrue(text.contains("Referencia: RR-7788"))
        assertTrue(text.contains("Gracias por su compra"))
        assertFalse(text.contains("[["))
        assertFalse(text.contains("]]"))
        assertFalse(text.contains("Operador"))
        assertFalse(text.contains("Factura API"))
        assertFalse(text.contains("Saldo proveedor"))
        assertFalse(text.contains("ID local"))
    }

    @Test
    fun `voucher print prompt follows available printer target`() {
        assertEquals(
            RechargeVoucherPrintTarget.BLUETOOTH,
            resolveRechargeVoucherPrintTarget(integratedAvailable = false, selectedBluetoothAddress = "00:11:22"),
        )
        assertEquals(
            RechargeVoucherPrintTarget.INTEGRATED,
            resolveRechargeVoucherPrintTarget(integratedAvailable = true, selectedBluetoothAddress = ""),
        )
        assertEquals(
            RechargeVoucherPrintTarget.NONE,
            resolveRechargeVoucherPrintTarget(integratedAvailable = false, selectedBluetoothAddress = ""),
        )
    }

    @Test
    fun `recargas rapidas response exposes voucher balance and bill number`() {
        val response = JSONObject("""{"newBalance":471.5,"billNumber":0}""")

        assertEquals(471.5, extractRecargasRapidasNewBalance(response) ?: 0.0, 0.0)
        assertEquals("0", extractRecargasRapidasBillNumber(response))
    }

    @Test
    fun `recargas sale only records completed when edge confirms or does not send failure flag`() {
        assertEquals(null, validateRecargasRapidasSaleResponse(JSONObject("""{"ok":true,"reference":"RR-1"}""")))
        assertEquals(null, validateRecargasRapidasSaleResponse(JSONObject("""{"reference":"RR-1"}""")))
        assertEquals("Recarga no confirmada por proveedor", validateRecargasRapidasSaleResponse(JSONObject("""{"success":false}""")))
        assertEquals("Recarga no confirmada por proveedor", validateRecargasRapidasSaleResponse(JSONObject("""{"confirmed":false}""")))
        assertEquals("Recarga fallida", validateRecargasRapidasSaleResponse(JSONObject("""{"status":"failed","message":"Recarga fallida"}""")))
    }

    @Test
    fun `recargas shows safe edge error message to operator`() {
        val error = SupabaseEdgeException(
            userMessage = "Saldo insuficiente",
            technicalMessage = "provider rejected: stack details",
        )

        assertEquals("Saldo insuficiente", resolveRechargeSaleErrorMessage(error))
        assertEquals("Error procesando recarga", resolveRechargeSaleErrorMessage(IllegalStateException()))
    }
}
