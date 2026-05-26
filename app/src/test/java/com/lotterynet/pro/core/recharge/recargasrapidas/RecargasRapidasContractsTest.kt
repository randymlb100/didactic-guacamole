package com.lotterynet.pro.core.recharge.recargasrapidas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecargasRapidasContractsTest {

    @Test
    fun `provider catalog maps local companies to Recargas Rapidas api values and local logos`() {
        val providers = recargasRapidasProviders()

        assertEquals(listOf("claro", "altice", "viva", "moun", "digicel", "natcom"), providers.map { it.id })
        assertEquals("Claro", providers.first { it.id == "claro" }.apiValue)
        assertEquals("Orange", providers.first { it.id == "altice" }.apiValue)
        assertEquals("Viva", providers.first { it.id == "viva" }.apiValue)
        assertEquals("Moun", providers.first { it.id == "moun" }.apiValue)
        assertEquals("DigiCel", providers.first { it.id == "digicel" }.apiValue)
        assertEquals("Natcom", providers.first { it.id == "natcom" }.apiValue)
        assertEquals("logo_claro.svg", providers.first { it.id == "claro" }.logoAsset)
        assertEquals("logo_altice.svg", providers.first { it.id == "altice" }.logoAsset)
        assertEquals("logo_viva.svg", providers.first { it.id == "viva" }.logoAsset)
        assertEquals("logo_moun.svg", providers.first { it.id == "moun" }.logoAsset)
        assertEquals("logo_digicel.svg", providers.first { it.id == "digicel" }.logoAsset)
        assertEquals("logo_natcom.svg", providers.first { it.id == "natcom" }.logoAsset)
    }

    @Test
    fun `provider minimums match Recargas Rapidas selling rules`() {
        val providers = recargasRapidasProviders().associateBy { it.id }

        assertEquals(25.0, providers.getValue("claro").minimumAmount, 0.0)
        assertEquals(30.0, providers.getValue("altice").minimumAmount, 0.0)
        assertEquals(20.0, providers.getValue("viva").minimumAmount, 0.0)
        assertEquals(50.0, providers.getValue("moun").minimumAmount, 0.0)
        assertEquals(0.0, providers.getValue("digicel").minimumAmount, 0.0)
        assertEquals(50.0, providers.getValue("natcom").minimumAmount, 0.0)
        assertFalse(isRecargasRapidasAmountAllowed(providers.getValue("claro"), 24.0))
        assertTrue(isRecargasRapidasAmountAllowed(providers.getValue("claro"), 25.0))
    }

    @Test
    fun `paquetico catalog matches Recargas Rapidas page providers`() {
        val providers = recargasRapidasPaqueticoProviders()

        assertEquals(listOf("claro", "altice", "viva", "wind"), providers.map { it.id })
        assertEquals("Claro", providers.first { it.id == "claro" }.apiValue)
        assertEquals("Orange", providers.first { it.id == "altice" }.apiValue)
        assertEquals("Viva", providers.first { it.id == "viva" }.apiValue)
        assertEquals("Wind", providers.first { it.id == "wind" }.apiValue)
        assertEquals("logo_wind.svg", providers.first { it.id == "wind" }.logoAsset)
    }

    @Test
    fun `endpoint paths stay aligned with Recargas Rapidas frontend`() {
        assertEquals("http://198.23.59.27:4000", RecargasRapidasEndpoints.baseUrl)
        assertEquals("oauth/token", RecargasRapidasEndpoints.login)
        assertEquals("refill/add", RecargasRapidasEndpoints.addRefill)
        assertEquals("refill/paquetico/info", RecargasRapidasEndpoints.paqueticoInfo)
        assertEquals("refill/paquetico/add", RecargasRapidasEndpoints.paqueticoBuy)
        assertEquals("refill", RecargasRapidasEndpoints.history)
        assertEquals("refill/cancel", RecargasRapidasEndpoints.cancel)
    }

    @Test
    fun `cleartext is limited to Recargas Rapidas host only`() {
        val networkConfig = java.io.File("src/main/res/xml/network_security_config.xml").readText()

        assertTrue(RecargasRapidasEndpoints.baseUrl.startsWith("http://198.23.59.27:4000"))
        assertTrue(networkConfig.contains("""<base-config cleartextTrafficPermitted="false">"""))
        assertTrue(networkConfig.contains("""<domain-config cleartextTrafficPermitted="true">"""))
        assertTrue(networkConfig.contains("""<domain includeSubdomains="false">198.23.59.27</domain>"""))
    }

    @Test
    fun `phone numbers are sanitized before provider calls`() {
        assertEquals("8095550000", sanitizeRecargasRapidasPhone("809-555-0000"))
        assertEquals("18095550000", sanitizeRecargasRapidasPhone("+1 (809) 555-0000"))
    }

    @Test
    fun `paquetico info request matches Recargas Rapidas page contract`() {
        val provider = recargasRapidasPaqueticoProviders().first { it.id == "altice" }
        val payload = buildPaqueticoInfoPayload(provider, "809-859-6555")

        assertEquals("8098596555", payload.getString("phoneNumber"))
        assertEquals("Orange", payload.getString("company"))
        assertFalse(payload.has("amount"))
    }

    @Test
    fun `paquetico buy request uses selected paquetico id and not free amount`() {
        val provider = recargasRapidasPaqueticoProviders().first { it.id == "claro" }
        val payload = buildPaqueticoBuyPayload(
            provider = provider,
            phone = "809-220-1111",
            paqueticoId = 93,
        )

        assertEquals("8092201111", payload.getString("phoneNumber"))
        assertEquals("Claro", payload.getString("company"))
        assertEquals(93, payload.getInt("paqueticoId"))
        assertFalse(payload.has("amount"))
    }

    @Test
    fun `recarga buy request uses amount and company expected by Recargas Rapidas`() {
        val provider = recargasRapidasProviders().first { it.id == "claro" }
        val payload = buildRecargaBuyPayload(
            provider = provider,
            phone = "829-741-2063",
            amount = 100.0,
        )

        assertEquals("8297412063", payload.getString("phoneNumber"))
        assertEquals("Claro", payload.getString("company"))
        assertEquals(100.0, payload.getDouble("amount"), 0.0)
        assertFalse(payload.has("paqueticoId"))
    }

    @Test
    fun `credential status masks configured Recargas Rapidas account`() {
        val status = RecargasRapidasCredentialStatus(
            configured = true,
            usernameHint = "cuenta_admin_larga",
            scope = "admin:ADM-1",
            updatedAt = "2026-05-06T12:00:00Z",
        )

        assertEquals("cuenta_ad...", status.maskedUsername)
        assertFalse(status.toDisplayLabel().contains("password"))
    }

    @Test
    fun `credential save payload supports default and per admin scopes without exposing saved status`() {
        val defaultPayload = buildRecargasRapidasCredentialSavePayload(
            scope = RecargasRapidasCredentialScope.Default,
            username = "master_rr",
            password = "master_pass",
            updatedBy = "master01",
        )
        val adminPayload = buildRecargasRapidasCredentialSavePayload(
            scope = RecargasRapidasCredentialScope.Admin("ADM-1"),
            username = "admin_rr",
            password = "admin_pass",
            updatedBy = "master01",
        )

        assertEquals("default", defaultPayload.getString("scope"))
        assertEquals("admin:ADM-1", adminPayload.getString("scope"))
        assertEquals("master_rr", defaultPayload.getString("username"))
        assertEquals("admin_pass", adminPayload.getString("password"))
    }

    @Test
    fun `paquetico info response parses plans returned by Recargas Rapidas`() {
        val response = """
            {
              "message": "Ofertas disponibles",
              "paqueticosInfo": [
                {"id": 11, "price": 75, "description": "1GB por 1 dia"},
                {"id": 12, "price": "150", "description": "3GB por 3 dias"}
              ]
            }
        """.trimIndent()

        val info = parseRecargasRapidasPaqueticoInfo(response)

        assertFalse(info.error)
        assertEquals("Ofertas disponibles", info.message)
        assertEquals(2, info.plans.size)
        assertEquals(11, info.plans[0].id)
        assertEquals(75.0, info.plans[0].price, 0.0)
        assertEquals("1GB por 1 dia", info.plans[0].description)
        assertEquals(150.0, info.plans[1].price, 0.0)
    }

    @Test
    fun `wallet balance parser accepts common Recargas Rapidas account shapes`() {
        val direct = parseRecargasRapidasWalletBalance("""{"balance":1250.5,"currency":"DOP"}""")
        val nested = parseRecargasRapidasWalletBalance("""{"user":{"wallet":{"saldo":875.0}}}""")

        assertEquals(1250.5, direct?.amount ?: 0.0, 0.0)
        assertEquals("DOP", direct?.currency)
        assertEquals(875.0, nested?.amount ?: 0.0, 0.0)
    }

    @Test
    fun `paquetico phone validation follows Recargas Rapidas page rules`() {
        val providers = recargasRapidasProviders().associateBy { it.id }

        assertEquals(null, validateRecargasRapidasPhoneForProvider(providers.getValue("claro"), "8092201111"))
        assertEquals("Favor introduzca un numero valido", validateRecargasRapidasPhoneForProvider(providers.getValue("claro"), "80922011"))
        assertEquals(null, validateRecargasRapidasPhoneForProvider(providers.getValue("digicel"), "55550000"))
        assertEquals(null, validateRecargasRapidasPhoneForProvider(providers.getValue("natcom"), "55550000"))
    }
}
