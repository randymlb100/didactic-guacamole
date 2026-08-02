package com.lotterynet.pro.core.servicesgames

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import org.json.JSONObject

/** Separate provider bridge. It never calls the existing Recargas sale function. */
class ServicesGamesBackendClient(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.KEY,
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(baseUrl, apiKey),
) {
    fun catalog(
        module: ServicesGamesModule,
        adminKey: String,
        cashierKey: String,
        clientRequestId: String,
        bearerToken: String? = null,
    ): JSONObject {
        return edgeClient.invokeAuthenticated(
            "recargas-rapidas-services-games",
            JSONObject()
                .put("action", "catalog")
                .put("module", module.wireValue)
                .put("providerId", "recargas_rapidas")
                .put("productId", "catalog")
                .put("adminKey", adminKey)
                .put("cashierKey", cashierKey)
                .put("clientRequestId", clientRequestId),
            bearerToken,
        )
    }

    fun query(request: ServicesGamesQueryRequest, bearerToken: String? = null): JSONObject {
        return edgeClient.invokeAuthenticated(
            "recargas-rapidas-services-games",
            buildServicesGamesQueryPayload(request),
            bearerToken,
        )
    }

    fun confirm(request: ServicesGamesConfirmRequest, bearerToken: String? = null): JSONObject {
        return edgeClient.invokeAuthenticated(
            "recargas-rapidas-services-games",
            buildServicesGamesConfirmPayload(request),
            bearerToken,
        )
    }

    fun report(
        module: ServicesGamesModule? = null,
        from: String? = null,
        to: String? = null,
        bearerToken: String? = null,
    ): JSONObject {
        return edgeClient.invokeAuthenticated(
            "get-services-games-report",
            JSONObject().apply {
                put("module", module?.wireValue)
                put("from", from)
                put("to", to)
            },
            bearerToken,
        )
    }
}
