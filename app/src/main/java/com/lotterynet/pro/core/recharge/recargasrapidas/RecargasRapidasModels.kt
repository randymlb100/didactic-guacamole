package com.lotterynet.pro.core.recharge.recargasrapidas

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONObject

data class RecargasRapidasProvider(
    val id: String,
    val label: String,
    val apiValue: String,
    val minimumAmount: Double,
    val logoAsset: String,
)

object RecargasRapidasEndpoints {
    const val baseUrl = "http://198.23.59.27:4000"
    const val login = "oauth/token"
    const val addRefill = "refill/add"
    const val paqueticoInfo = "refill/paquetico/info"
    const val paqueticoBuy = "refill/paquetico/add"
    const val history = "refill"
    const val cancel = "refill/cancel"
}

data class RecargasRapidasCredentials(
    val username: String,
    val password: String,
) {
    val isConfigured: Boolean = username.isNotBlank() && password.isNotBlank()
}

fun resolveRecargasRapidasCredentials(
    ownerCredentials: RecargasRapidasCredentials,
    fallbackCredentials: RecargasRapidasCredentials,
): RecargasRapidasCredentials {
    return if (ownerCredentials.isConfigured) ownerCredentials else fallbackCredentials
}

data class RecargasRapidasPaqueticoPlan(
    val id: Int,
    val description: String,
    val price: Double,
)

data class RecargasRapidasPaqueticoInfo(
    val error: Boolean,
    val message: String,
    val plans: List<RecargasRapidasPaqueticoPlan>,
)

data class RecargasRapidasWalletBalance(
    val amount: Double,
    val currency: String? = null,
)

sealed class RecargasRapidasCredentialScope {
    abstract val wireValue: String

    data object Default : RecargasRapidasCredentialScope() {
        override val wireValue: String = "default"
    }

    data class Admin(val adminId: String) : RecargasRapidasCredentialScope() {
        override val wireValue: String = "admin:${adminId.trim()}"
    }
}

data class RecargasRapidasCredentialStatus(
    val configured: Boolean,
    val usernameHint: String? = null,
    val scope: String = RecargasRapidasCredentialScope.Default.wireValue,
    val updatedAt: String? = null,
) {
    val maskedUsername: String
        get() {
            val clean = usernameHint.orEmpty().trim()
            if (clean.isBlank()) return "sin usuario"
            return if (clean.length <= 9) clean else "${clean.take(9)}..."
        }

    fun toDisplayLabel(): String {
        return if (configured) {
            "Configurada: $maskedUsername"
        } else {
            "Pendiente"
        }
    }
}

fun buildRecargasRapidasCredentialSavePayload(
    scope: RecargasRapidasCredentialScope,
    username: String,
    password: String,
    updatedBy: String,
): JSONObject {
    return JSONObject()
        .put("scope", scope.wireValue)
        .put("username", username.trim())
        .put("password", password.trim())
        .put("updatedBy", updatedBy.trim())
}

fun buildRecargasRapidasStatusPayload(
    adminId: String? = null,
): JSONObject {
    return JSONObject().apply {
        adminId?.trim()?.takeIf { it.isNotBlank() }?.let { put("adminId", it) }
    }
}

fun parseRecargasRapidasCredentialStatus(rawJson: String): RecargasRapidasCredentialStatus {
    val root = JSONObject(rawJson)
    return RecargasRapidasCredentialStatus(
        configured = root.optBoolean("configured", false),
        usernameHint = root.optString("usernameHint").takeIf { it.isNotBlank() },
        scope = root.optString("scope").ifBlank { RecargasRapidasCredentialScope.Default.wireValue },
        updatedAt = root.optString("updatedAt").takeIf { it.isNotBlank() },
    )
}

fun normalizeRecargasRapidasMode(raw: String): String {
    return raw.trim().lowercase(Locale.US)
}

fun recargasRapidasProviders(): List<RecargasRapidasProvider> {
    return listOf(
        RecargasRapidasProvider("claro", "Claro", "Claro", 25.0, "logo_claro.svg"),
        RecargasRapidasProvider("altice", "Altice", "Orange", 30.0, "logo_altice.svg"),
        RecargasRapidasProvider("viva", "Viva", "Viva", 20.0, "logo_viva.svg"),
        RecargasRapidasProvider("moun", "Moun", "Moun", 50.0, "logo_moun.svg"),
        RecargasRapidasProvider("digicel", "Digicel", "DigiCel", 0.0, "logo_digicel.svg"),
        RecargasRapidasProvider("natcom", "Natcom", "Natcom", 50.0, "logo_natcom.svg"),
    )
}

fun recargasRapidasPaqueticoProviders(): List<RecargasRapidasProvider> {
    val rechargeProviders = recargasRapidasProviders().associateBy { it.id }
    return listOf(
        rechargeProviders.getValue("claro"),
        rechargeProviders.getValue("altice"),
        rechargeProviders.getValue("viva"),
        RecargasRapidasProvider("wind", "Wind", "Wind", 0.0, "logo_wind.svg"),
    )
}

fun isRecargasRapidasAmountAllowed(
    provider: RecargasRapidasProvider,
    amount: Double,
): Boolean {
    return amount >= provider.minimumAmount
}

fun sanitizeRecargasRapidasPhone(raw: String): String {
    return raw.filter(Char::isDigit)
}

fun validateRecargasRapidasPhoneForProvider(
    provider: RecargasRapidasProvider,
    phone: String,
): String? {
    val clean = sanitizeRecargasRapidasPhone(phone)
    val minLength = if (provider.apiValue == "DigiCel" || provider.apiValue == "Natcom") 8 else 10
    return if (clean.length < minLength) "Favor introduzca un numero valido" else null
}

fun buildRecargasRapidasLoginPayload(credentials: RecargasRapidasCredentials): JSONObject {
    return JSONObject()
        .put("username", credentials.username.trim().lowercase())
        .put("password", credentials.password.trim())
}

fun buildPaqueticoInfoPayload(
    provider: RecargasRapidasProvider,
    phone: String,
): JSONObject {
    return JSONObject()
        .put("phoneNumber", sanitizeRecargasRapidasPhone(phone))
        .put("company", provider.apiValue)
}

fun buildPaqueticoBuyPayload(
    provider: RecargasRapidasProvider,
    phone: String,
    paqueticoId: Int,
): JSONObject {
    return JSONObject()
        .put("phoneNumber", sanitizeRecargasRapidasPhone(phone))
        .put("paqueticoId", paqueticoId)
        .put("company", provider.apiValue)
}

fun buildRecargaBuyPayload(
    provider: RecargasRapidasProvider,
    phone: String,
    amount: Double,
): JSONObject {
    return JSONObject()
        .put("phoneNumber", sanitizeRecargasRapidasPhone(phone))
        .put("amount", amount)
        .put("company", provider.apiValue)
}

fun parseRecargasRapidasPaqueticoInfo(rawJson: String): RecargasRapidasPaqueticoInfo {
    val root = JSONObject(rawJson)
    val plansJson = root.optJSONArray("paqueticosInfo")
    val plans = buildList {
        if (plansJson != null) {
            for (index in 0 until plansJson.length()) {
                val item = plansJson.optJSONObject(index) ?: continue
                add(
                    RecargasRapidasPaqueticoPlan(
                        id = item.optInt("id"),
                        description = item.optString("description"),
                        price = item.optDouble("price"),
                    ),
                )
            }
        }
    }
    val error = root.optBoolean("error", false) || plans.isEmpty()
    val message = root.optString(
        "message",
        if (plans.isEmpty()) {
            "No se encontraron Ofertas de Paqueticos disponibles para ese numero, favor verifique e intentelo de nuevo"
        } else {
            ""
        },
    )
    return RecargasRapidasPaqueticoInfo(
        error = error,
        message = message,
        plans = plans,
    )
}

fun parseRecargasRapidasWalletBalance(rawJson: String): RecargasRapidasWalletBalance? {
    val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
    return findWalletBalance(root)?.let { amount ->
        RecargasRapidasWalletBalance(
            amount = amount,
            currency = root.optString("currency").takeIf { it.isNotBlank() }
                ?: root.optString("moneda").takeIf { it.isNotBlank() },
        )
    }
}

private fun findWalletBalance(json: JSONObject): Double? {
    listOf("balance", "saldo", "walletBalance", "wallet", "cartera", "availableBalance").forEach { key ->
        if (json.has(key)) {
            val direct = json.optDouble(key, Double.NaN)
            if (!direct.isNaN()) return direct
            val nested = json.optJSONObject(key)?.let(::findWalletBalance)
            if (nested != null) return nested
        }
    }
    listOf("user", "account", "data", "result").forEach { key ->
        val nested = json.optJSONObject(key)?.let(::findWalletBalance)
        if (nested != null) return nested
    }
    return null
}

class RecargasRapidasClient(
    private val credentials: RecargasRapidasCredentials,
    private val baseUrl: String = RecargasRapidasEndpoints.baseUrl,
) {
    fun getPaqueticosInfo(
        provider: RecargasRapidasProvider,
        phone: String,
    ): RecargasRapidasPaqueticoInfo {
        val token = login()
        val payload = buildPaqueticoInfoPayload(provider, phone)
        return parseRecargasRapidasPaqueticoInfo(
            postJson(
                path = RecargasRapidasEndpoints.paqueticoInfo,
                payload = payload,
                bearerToken = token,
            ),
        )
    }

    fun buyPaquetico(
        provider: RecargasRapidasProvider,
        phone: String,
        paqueticoId: Int,
    ): JSONObject {
        val token = login()
        val payload = buildPaqueticoBuyPayload(provider, phone, paqueticoId)
        return JSONObject(
            postJson(
                path = RecargasRapidasEndpoints.paqueticoBuy,
                payload = payload,
                bearerToken = token,
            ),
        )
    }

    fun buyRecarga(
        provider: RecargasRapidasProvider,
        phone: String,
        amount: Double,
    ): JSONObject {
        val token = login()
        val payload = buildRecargaBuyPayload(provider, phone, amount)
        return JSONObject(
            postJson(
                path = RecargasRapidasEndpoints.addRefill,
                payload = payload,
                bearerToken = token,
            ),
        )
    }

    fun getWalletBalance(): RecargasRapidasWalletBalance? {
        return parseRecargasRapidasWalletBalance(loginResponse().toString())
    }

    private fun login(): String {
        return loginResponse().optString("token").ifBlank {
            error("Recargas Rapidas no devolvio token.")
        }
    }

    private fun loginResponse(): JSONObject {
        check(credentials.isConfigured) { "Credenciales de Recargas Rapidas no configuradas." }
        return JSONObject(
            postJson(
                path = RecargasRapidasEndpoints.login,
                payload = buildRecargasRapidasLoginPayload(credentials),
                bearerToken = null,
            ),
        )
    }

    private fun postJson(
        path: String,
        payload: JSONObject,
        bearerToken: String?,
    ): String {
        val connection = URL("$baseUrl/$path").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 25_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/json")
        bearerToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload.toString())
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        }.orEmpty()
        if (status !in 200..299) {
            error("Recargas Rapidas HTTP $status: $body")
        }
        return body
    }
}
