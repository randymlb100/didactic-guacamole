package com.lotterynet.pro.core.servicesgames

import org.json.JSONObject

/** Payloads that mirror the provider portal contracts. */
internal object ServicesGamesProviderContracts {
    fun billLookup(customerId: String, providerId: String): JSONObject {
        require(customerId.isNotBlank()) { "El identificador del cliente es requerido" }
        require(providerId.isNotBlank()) { "El proveedor es requerido" }
        return JSONObject()
            .put("customerInput", customerId.trim())
            .put("provider", providerId.trim())
    }

    fun billPaymentAmount(amount: Double): JSONObject {
        require(amount > 0.0) { "El monto de la factura debe ser mayor que cero" }
        return JSONObject().put("amount", amount)
    }

    fun videoGame(
        categoryId: String,
        productId: String,
        playerId: String,
        zoneId: String,
        clientName: String,
        notes: String,
    ): JSONObject {
        require(categoryId.isNotBlank()) { "La categoría del juego es requerida" }
        require(productId.isNotBlank()) { "El producto del juego es requerido" }
        require(playerId.isNotBlank()) { "El ID del jugador es requerido" }
        return JSONObject()
            .put("categoryId", categoryId.trim())
            .put("productId", productId.trim())
            .put("playerId", playerId.trim())
            .put("zoneId", zoneId.trim())
            .put("clientName", clientName.trim())
            .put("notes", notes.trim())
    }

    fun insurance(fields: JSONObject): JSONObject {
        return requireFields(
            fields,
            "name", "lastname", "address", "documentId", "phone", "cellphone",
            "make", "model", "year", "duration", "amount", "paymentType", "company",
            "type", "chasis", "cedulaImageBase64", "matriculaImageBase64",
        )
    }

    fun simActivation(fields: JSONObject): JSONObject {
        return requireFields(
            fields,
            "company", "name", "documentId", "birthday", "fathersName", "mothersName", "ICCID",
        )
    }

    fun remittanceCalculation(fields: JSONObject): JSONObject {
        return requireFields(fields, "serviceName", "amountSent", "remittanceType")
    }

    fun remittanceSend(fields: JSONObject): JSONObject {
        return requireFields(
            fields,
            "serviceName", "amountSent", "senderName", "senderPhone", "senderAddress",
            "recipientName", "recipientPhone", "recipientAddress", "remittanceType",
        )
    }

    private fun requireFields(fields: JSONObject, vararg names: String): JSONObject {
        names.forEach { name ->
            val value = fields.opt(name)
            require(value != null && value.toString().trim().isNotEmpty()) {
                "Falta el campo del servicio: $name"
            }
        }
        return fields
    }
}
