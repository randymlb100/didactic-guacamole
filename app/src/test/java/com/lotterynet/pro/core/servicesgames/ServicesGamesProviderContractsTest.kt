package com.lotterynet.pro.core.servicesgames

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ServicesGamesProviderContractsTest {
    @Test
    fun billLookupUsesPortalFields() {
        val payload = ServicesGamesProviderContracts.billLookup(" 7230660 ", "edenorte")
        assertEquals("7230660", payload.getString("customerInput"))
        assertEquals("edenorte", payload.getString("provider"))
    }

    @Test
    fun billPaymentOnlyContainsAmount() {
        val payload = ServicesGamesProviderContracts.billPaymentAmount(95.0)
        assertEquals(95.0, payload.getDouble("amount"), 0.001)
        assertEquals(1, payload.length())
    }

    @Test
    fun videoGameUsesExactPortalFields() {
        val payload = ServicesGamesProviderContracts.videoGame(
            categoryId = "roblox",
            productId = "10",
            playerId = "player-1",
            zoneId = "1",
            clientName = "Cliente",
            notes = "",
        )
        assertEquals("roblox", payload.getString("categoryId"))
        assertEquals("10", payload.getString("productId"))
        assertEquals("player-1", payload.getString("playerId"))
    }

    @Test
    fun invalidBillAmountIsRejected() {
        expectIllegalArgument { ServicesGamesProviderContracts.billPaymentAmount(0.0) }
    }

    @Test
    fun remittanceCalculationAndSendRemainSeparate() {
        val calculation = org.json.JSONObject()
            .put("serviceName", "MONCASH")
            .put("amountSent", 500.0)
            .put("remittanceType", org.json.JSONObject().put("id", "haiti"))
        val send = org.json.JSONObject(calculation.toString())
            .put("senderName", "Juan")
            .put("senderPhone", "8095550000")
            .put("senderAddress", "Santo Domingo")
            .put("recipientName", "Pierre")
            .put("recipientPhone", "5095550000")
            .put("recipientAddress", "Port au Prince")

        assertEquals("MONCASH", ServicesGamesProviderContracts.remittanceCalculation(calculation).getString("serviceName"))
        assertEquals("Pierre", ServicesGamesProviderContracts.remittanceSend(send).getString("recipientName"))
    }

    @Test
    fun insuranceAndSimContractsRejectIncompletePayloads() {
        expectIllegalArgument { ServicesGamesProviderContracts.insurance(org.json.JSONObject().put("name", "Juan")) }
        expectIllegalArgument { ServicesGamesProviderContracts.simActivation(org.json.JSONObject().put("company", "Orange")) }
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Se esperaba IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected contract rejection.
        }
    }
}
