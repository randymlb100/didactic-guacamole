package com.lotterynet.pro.core.recharge.recargasrapidas

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import org.json.JSONObject

class RecargasRapidasBackendClient(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.KEY,
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(baseUrl, apiKey),
) {
    fun executeSale(payload: JSONObject, bearerToken: String? = null): JSONObject {
        return edgeClient.invoke("recargas-rapidas-sale", payload.put("action", "sell"), bearerToken)
    }

    fun getPaqueticosInfo(payload: JSONObject, bearerToken: String? = null): RecargasRapidasPaqueticoInfo {
        val response = edgeClient.invoke("recargas-rapidas-sale", payload.put("action", "paquetico-info"), bearerToken)
        return parseRecargasRapidasPaqueticoInfo(response.toString())
    }

    fun getWalletBalance(
        scope: RecargasRapidasCredentialScope = RecargasRapidasCredentialScope.Default,
        bearerToken: String? = null,
    ): RecargasRapidasWalletBalance? {
        val response = edgeClient.invoke(
            "recargas-rapidas-sale",
            JSONObject()
                .put("action", "wallet")
                .put("scope", scope.wireValue),
            bearerToken,
        )
        return parseRecargasRapidasWalletBalance(response.toString())
    }

    fun saveCredentials(
        scope: RecargasRapidasCredentialScope,
        username: String,
        password: String,
        updatedBy: String,
        bearerToken: String? = null,
    ): RecargasRapidasCredentialStatus {
        val response = edgeClient.invoke(
            "recargas-rapidas-credentials",
            buildRecargasRapidasCredentialSavePayload(scope, username, password, updatedBy).put("action", "save"),
            bearerToken,
        )
        return parseRecargasRapidasCredentialStatus(response.toString())
    }

    fun fetchCredentialStatus(adminId: String? = null, bearerToken: String? = null): RecargasRapidasCredentialStatus {
        val response = edgeClient.invoke(
            "recargas-rapidas-credentials",
            buildRecargasRapidasStatusPayload(adminId).put("action", "status"),
            bearerToken,
        )
        return parseRecargasRapidasCredentialStatus(response.toString())
    }
}
