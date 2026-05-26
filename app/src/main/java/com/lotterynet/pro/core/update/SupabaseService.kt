package com.lotterynet.pro.core.update

import com.lotterynet.pro.core.config.SupabaseConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SupabaseService(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.KEY,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build(),
) {
    fun invoke(functionSlug: String, payload: JSONObject): JSONObject {
        val url = "${baseUrl.trimEnd('/')}/functions/v1/${functionSlug.trim().trim('/')}"
        val body = payload.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("apikey", apiKey)
            .header("Authorization", "Bearer $apiKey")
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(raw).optString("message")
                        .ifBlank { JSONObject(raw).optString("error") }
                }.getOrNull().orEmpty()
                throw IOException(message.ifBlank { "Servidor OTA no disponible." })
            }
            return JSONObject(raw.ifBlank { "{}" })
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
