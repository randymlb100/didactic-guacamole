package com.lotterynet.pro.core.config

import android.util.Log
import org.json.JSONObject
import java.net.URI
import java.net.UnknownHostException

object SupabaseConfig {
    const val URL = "https://unhoulkujbtsypccpirc.supabase.co"
    const val KEY = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK"

    fun toBridgeJson(): String = JSONObject().apply {
        put("url", URL)
        put("key", KEY)
        put("host", runCatching { URI(URL).host }.getOrNull().orEmpty())
    }.toString()

    fun logRuntimeFailure(tag: String, requestUrl: String, error: Throwable) {
        val host = runCatching { URI(requestUrl).host }.getOrNull().orEmpty()
        val prefix = when (error) {
            is UnknownHostException -> "Supabase DNS failed"
            else -> "Supabase request failed"
        }
        Log.w(tag, "$prefix host=$host url=$requestUrl", error)
    }
}
