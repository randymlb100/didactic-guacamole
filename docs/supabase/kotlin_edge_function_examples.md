# Kotlin Compose Edge Function Examples

```kotlin
class LotteryNetBackend(
    private val supabaseUrl: String,
    private val anonKey: String,
    private val accessTokenProvider: () -> String,
) {
    fun callFunction(name: String, payload: JSONObject): JSONObject {
        val url = URL("$supabaseUrl/functions/v1/$name")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 35_000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("apikey", anonKey)
        conn.setRequestProperty("Authorization", "Bearer ${accessTokenProvider()}")
        conn.outputStream.writer(Charsets.UTF_8).use { it.write(payload.toString()) }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()
        if (code !in 200..299) {
            val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
            error(message?.takeIf { it.isNotBlank() } ?: "Servidor no disponible")
        }
        return JSONObject(body.ifBlank { "{}" })
    }

    fun createTicket(sorteoId: String, drawDate: String, jugadas: JSONArray): JSONObject {
        return callFunction(
            "create-ticket",
            JSONObject()
                .put("sorteoId", sorteoId)
                .put("drawDate", drawDate)
                .put("jugadas", jugadas),
        )
    }

    fun voidTicket(ticketId: String, reason: String): JSONObject {
        return callFunction("void-ticket", JSONObject().put("ticketId", ticketId).put("reason", reason))
    }

    fun payTicket(ticketId: String): JSONObject {
        return callFunction("pay-ticket", JSONObject().put("ticketId", ticketId))
    }

    fun rechargeUser(toUserId: String, amount: Double, reference: String): JSONObject {
        return callFunction(
            "recharge-user",
            JSONObject()
                .put("toUserId", toUserId)
                .put("amount", amount)
                .put("reference", reference),
        )
    }

    fun getReport(fromDate: String, toDate: String, cashierId: String? = null): JSONObject {
        return callFunction(
            "get-admin-report",
            JSONObject()
                .put("fromDate", fromDate)
                .put("toDate", toDate)
                .put("cashierId", cashierId),
        )
    }
}
```

Cliente Compose recomendado:

```kotlin
scope.launch {
    loading = true
    val result = withContext(Dispatchers.IO) {
        runCatching { backend.createTicket(sorteoId, drawDate, jugadas) }
    }
    loading = false
    result.onSuccess { response ->
        ticketCode = response.getJSONObject("ticket").getString("ticket_code")
    }.onFailure { error ->
        snackbarHostState.showSnackbar(error.message ?: "No se pudo completar")
    }
}
```

