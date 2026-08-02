package com.lotterynet.pro.core.servicesgames

import org.json.JSONObject

data class ServicesGamesQueryRequest(
    val clientRequestId: String,
    val module: ServicesGamesModule,
    val providerId: String,
    val productId: String,
    val adminKey: String,
    val cashierKey: String,
    val customerInput: JSONObject,
    val serviceType: String = "",
    val providerPayload: JSONObject = JSONObject(),
)

data class ServicesGamesConfirmRequest(
    val clientRequestId: String,
    val module: ServicesGamesModule,
    val providerId: String,
    val productId: String,
    val adminKey: String,
    val cashierKey: String,
    val quotedPrice: Double,
    val customerInput: JSONObject,
    val serviceType: String = "",
    val amount: Double? = null,
    val providerPayload: JSONObject = JSONObject(),
)

internal fun buildServicesGamesQueryPayload(request: ServicesGamesQueryRequest): JSONObject {
    return JSONObject().apply {
        put("action", "query")
        put("clientRequestId", request.clientRequestId.requireNonBlank("clientRequestId"))
        put("module", request.module.wireValue)
        put("providerId", request.providerId.requireNonBlank("providerId"))
        put("productId", request.productId.requireNonBlank("productId"))
        put("adminKey", request.adminKey.requireNonBlank("adminKey"))
        put("cashierKey", request.cashierKey.requireNonBlank("cashierKey"))
        put("customerInput", request.customerInput)
        put("serviceType", request.serviceType.trim())
        put("providerPayload", request.providerPayload)
    }
}

internal fun buildServicesGamesConfirmPayload(request: ServicesGamesConfirmRequest): JSONObject {
    require(request.quotedPrice > 0.0) { "quotedPrice debe ser mayor que cero" }
    return JSONObject().apply {
        put("action", "confirm")
        put("clientRequestId", request.clientRequestId.requireNonBlank("clientRequestId"))
        put("module", request.module.wireValue)
        put("providerId", request.providerId.requireNonBlank("providerId"))
        put("productId", request.productId.requireNonBlank("productId"))
        put("adminKey", request.adminKey.requireNonBlank("adminKey"))
        put("cashierKey", request.cashierKey.requireNonBlank("cashierKey"))
        put("quotedPrice", request.quotedPrice)
        put("customerInput", request.customerInput)
        put("serviceType", request.serviceType.trim())
        put("amount", request.amount)
        put("providerPayload", request.providerPayload)
    }
}

private fun String?.requireNonBlank(field: String): String {
    return this.orEmpty().trim().takeIf { it.isNotBlank() }
        ?: error("$field es requerido")
}
