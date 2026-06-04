package com.lotterynet.pro.core.results

import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class SupabaseResultsRemoteStore(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.KEY,
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(baseUrl, apiKey),
    private val renderClient: RenderResultsRemoteClient = RenderResultsRemoteClient(),
    private val adminClient: ResultsAdminRemoteClient = ResultsAdminRemoteClient(),
    private val edgePayloadFetcher: ((String) -> Any?)? = null,
    private val renderPayloadFetcher: ((String, Boolean) -> Any?)? = null,
) {
    fun fetchResultsPayload(
        date: String,
        expectedResultIds: Set<String> = emptySet(),
        forceLive: Boolean = false,
    ): Any? {
        if (forceLive) {
            val renderPayload = fetchRenderPayload(date, forceLive = true)
            if (payloadHasRows(renderPayload) && payloadHasExpectedCoverage(renderPayload, expectedResultIds)) {
                return renderPayload
            }
            return renderPayload
        }

        val edgePayload = runCatching {
            fetchEdgePayload(date)
        }.getOrNull()
        if (payloadHasRows(edgePayload) && payloadHasExpectedCoverage(edgePayload, expectedResultIds)) {
            return edgePayload
        }

        val renderPayload = fetchRenderPayload(date, forceLive = false)
        val mergedPayload = mergeResultPayloads(edgePayload, renderPayload)
        if (payloadHasRows(mergedPayload) && payloadHasExpectedCoverage(mergedPayload, expectedResultIds)) {
            return mergedPayload
        }
        if (payloadHasRows(renderPayload) && payloadHasExpectedCoverage(renderPayload, expectedResultIds)) {
            return renderPayload
        }

        return mergedPayload
            ?: renderPayload
            ?: edgePayload
    }

    private fun fetchEdgePayload(date: String): Any? {
        return if (edgePayloadFetcher != null) {
            edgePayloadFetcher.invoke(date)
        } else {
            val response = edgeClient.invoke(
                "get-results-v2",
                JSONObject()
                    .put("date", date),
            )
            extractResultPayload(response)
        }
    }

    private fun fetchRenderPayload(date: String, forceLive: Boolean): Any? {
        return if (renderPayloadFetcher != null) {
            renderPayloadFetcher.invoke(date, forceLive)
        } else {
            renderClient.fetchResultsPayload(date, forceLive = forceLive)
        }
    }

    fun upsertManualOverride(
        session: ActiveSession,
        date: String,
        resultId: String,
        name: String,
        number: String,
        game: String,
    ): Boolean {
        if (session.role !in setOf(UserRole.ADMIN, UserRole.MASTER)) return false
        val payload = JSONObject()
            .put("role", session.role.name.lowercase(Locale.US))
            .put("editedBy", session.username)
            .put("date", toScraperDateKey(date))
            .put("resultId", resultId)
            .put("name", name)
            .put("number", number)
            .put("game", game)
        return adminClient.postManualOverride(payload)
    }

    fun deleteManualOverride(
        session: ActiveSession,
        date: String,
        resultId: String,
    ): Boolean {
        if (session.role !in setOf(UserRole.ADMIN, UserRole.MASTER)) return false
        val payload = JSONObject()
            .put("role", session.role.name.lowercase(Locale.US))
            .put("editedBy", session.username)
            .put("date", toScraperDateKey(date))
            .put("resultId", resultId)
        return adminClient.deleteManualOverride(payload)
    }

    companion object {
        internal fun extractResultPayload(value: Any?): Any? {
            return when (value) {
                is JSONObject -> value.optJSONArray("results")
                    ?: value.optJSONArray("rows")
                    ?: mergeSystemResultPayload(value)
                    ?: value.optJSONObject("payload")?.let(::extractResultPayload)
                    ?: value.opt("payload")
                is String -> runCatching { JSONObject(value) }.getOrNull()?.let(::extractResultPayload)
                    ?: runCatching { JSONArray(value) }.getOrNull()
                    ?: value
                else -> value
            }
        }

        internal fun payloadHasRows(value: Any?): Boolean {
            return payloadToArray(value)?.length()?.let { it > 0 } == true
        }

        internal fun payloadHasPickRows(value: Any?): Boolean {
            return payloadUsPickRowCount(value) > 0
        }

        internal fun payloadHasUsPickCoverage(value: Any?): Boolean {
            return payloadUsPickCanonicalKeys(value).containsAll(expectedUsPickCanonicalKeys)
        }

        internal fun payloadHasExpectedCoverage(value: Any?, expectedResultIds: Set<String>): Boolean {
            if (expectedResultIds.isEmpty()) return payloadHasRows(value)
            val available = payloadResultKeys(value)
            if (available.isEmpty()) return false
            val expected = expectedResultIds.mapTo(linkedSetOf(), PickResultIdentityResolver::canonicalKeyForExpectedId)
            return available.containsAll(expected)
        }

        internal fun payloadUsPickRowCount(value: Any?): Int {
            val rows = payloadToArray(value) ?: return 0
            var count = 0
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val identity = listOf(
                    item.optString("id"),
                    item.optString("lotteryId"),
                    item.optString("name"),
                    item.optString("lotteryName"),
                    item.optString("game"),
                ).joinToString(" ").lowercase()
                val compactIdentity = identity.filter(Char::isLetterOrDigit)
                if (
                    item.optString("pick3").isNotBlank() ||
                    item.optString("pick4").isNotBlank() ||
                    compactIdentity.contains("usp3") ||
                    compactIdentity.contains("usp4") ||
                    compactIdentity.contains("pick3") ||
                    compactIdentity.contains("pick4") ||
                    compactIdentity.contains("play3") ||
                    compactIdentity.contains("play4")
                ) {
                    count += 1
                }
            }
            return count
        }

        private val expectedUsPickCanonicalKeys: Set<String> by lazy {
            StaticLotteryCatalogRepository().getAllLotteries()
                .asSequence()
                .filter { lottery ->
                    lottery.id.startsWith("US-P3-") ||
                        lottery.id.startsWith("US-P4-") ||
                        lottery.id in setOf("19", "20", "21", "22")
                }
                .map { lottery -> PickResultIdentityResolver.canonicalKeyForLottery(lottery) }
                .toSet()
        }

        private fun payloadUsPickCanonicalKeys(value: Any?): Set<String> {
            val rows = payloadToArray(value) ?: return emptySet()
            return buildSet {
                for (index in 0 until rows.length()) {
                    val item = rows.optJSONObject(index) ?: continue
                    val id = item.optString("id").ifBlank { item.optString("lotteryId") }
                    val name = item.optString("name").ifBlank { item.optString("lotteryName") }
                    val result = LotteryResult(
                        lotteryId = id,
                        lotteryName = name.ifBlank { null },
                        date = item.optString("date"),
                        pick3 = item.optString("pick3").ifBlank {
                            item.optString("number").takeIf {
                                payloadRowLooksLikePick3(item)
                            }
                        },
                        pick4 = item.optString("pick4").ifBlank {
                            item.optString("number").takeIf {
                                payloadRowLooksLikePick4(item)
                            }
                        },
                    )
                    PickResultIdentityResolver.resolveResult(result)?.canonicalKey?.let(::add)
                }
            }
        }

        private fun payloadResultKeys(value: Any?): Set<String> {
            val rows = payloadToArray(value) ?: return emptySet()
            return buildSet {
                for (index in 0 until rows.length()) {
                    val item = rows.optJSONObject(index) ?: continue
                    val id = item.optString("id").ifBlank { item.optString("lotteryId") }.trim()
                    val name = item.optString("name").ifBlank { item.optString("lotteryName") }
                    val result = LotteryResult(
                        lotteryId = id,
                        lotteryName = name.ifBlank { null },
                        date = item.optString("date"),
                        pick3 = item.optString("pick3").ifBlank {
                            item.optString("number").takeIf {
                                payloadRowLooksLikePick3(item)
                            }
                        },
                        pick4 = item.optString("pick4").ifBlank {
                            item.optString("number").takeIf {
                                payloadRowLooksLikePick4(item)
                            }
                        },
                    )
                    PickResultIdentityResolver.resolveResult(result)?.canonicalKey?.let(::add)
                        ?: id.takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }

        private fun payloadRowLooksLikePick3(item: JSONObject): Boolean {
            val identity = listOf(
                item.optString("id"),
                item.optString("lotteryId"),
                item.optString("name"),
                item.optString("lotteryName"),
                item.optString("game"),
                item.optString("gameName"),
            ).joinToString(" ").lowercase()
            val compactIdentity = identity.filter(Char::isLetterOrDigit)
            return compactIdentity.contains("usp3") ||
                compactIdentity.contains("pick3") ||
                compactIdentity.contains("play3") ||
                compactIdentity.contains("cash3") ||
                compactIdentity.contains("daily3") ||
                compactIdentity.contains("numbers")
        }

        private fun payloadRowLooksLikePick4(item: JSONObject): Boolean {
            val identity = listOf(
                item.optString("id"),
                item.optString("lotteryId"),
                item.optString("name"),
                item.optString("lotteryName"),
                item.optString("game"),
                item.optString("gameName"),
            ).joinToString(" ").lowercase()
            val compactIdentity = identity.filter(Char::isLetterOrDigit)
            return compactIdentity.contains("usp4") ||
                compactIdentity.contains("pick4") ||
                compactIdentity.contains("play4") ||
                compactIdentity.contains("cash4") ||
                compactIdentity.contains("daily4") ||
                compactIdentity.contains("win4")
        }

        internal fun mergeResultPayloads(vararg payloads: Any?): JSONArray? {
            val arrays = payloads.mapNotNull(::payloadToArray)
            if (arrays.isEmpty()) return null

            val merged = linkedMapOf<String, JSONObject>()
            arrays.forEach { rows ->
                for (index in 0 until rows.length()) {
                    val item = rows.optJSONObject(index) ?: continue
                    val id = item.optString("id").ifBlank { item.optString("lotteryId") }
                    val key = id.ifBlank {
                        item.optString("name").ifBlank { item.optString("lotteryName") }
                    }
                    if (key.isBlank()) continue
                    merged[key] = item
                }
            }
            if (merged.isEmpty()) return null
            return JSONArray().apply {
                merged.values
                    .sortedWith(compareBy<JSONObject> { it.optString("id").toIntOrNull() ?: Int.MAX_VALUE }
                        .thenBy { it.optString("name").ifBlank { it.optString("lotteryName") } })
                    .forEach(::put)
            }
        }

        private fun payloadToArray(value: Any?): JSONArray? {
            return when (value) {
                is JSONArray -> value
                is JSONObject -> value.optJSONArray("rows")
                    ?: value.optJSONArray("results")
                    ?: mergeSystemResultPayload(value)
                    ?: JSONArray().put(value)
                is String -> runCatching { JSONArray(value) }.getOrElse {
                    runCatching { JSONObject(value) }.getOrNull()?.let { json ->
                        json.optJSONArray("rows")
                            ?: json.optJSONArray("results")
                            ?: mergeSystemResultPayload(json)
                            ?: JSONArray().put(json)
                    }
                }
                else -> null
            }
        }

        private fun mergeSystemResultPayload(value: JSONObject): JSONArray? {
            val lotteryRows = value.optJSONObject("lotteries")?.optJSONArray("results")
            val pickRows = value.optJSONObject("picks")?.optJSONArray("results")
            return mergeResultPayloads(lotteryRows, pickRows)
        }
    }
}

class RenderResultsRemoteClient(
    private val baseUrl: String = "https://didactic-guacamole.onrender.com",
    private val connectTimeoutMs: Int = 8000,
    private val readTimeoutMs: Int = 8000,
    private val requestFetcher: ((String, Array<out Pair<String, String>>, Int, Int) -> Any?)? = null,
) {
    fun fetchResultsPayload(date: String, forceLive: Boolean = false): Any? {
        val renderDate = toRenderDateKey(date)
        val systemQuery = if (forceLive) {
            arrayOf("date" to renderDate, "mode" to "both", "live" to "1")
        } else {
            arrayOf("date" to renderDate, "mode" to "both")
        }
        val primaryPayload = fetchJson(
            path = "/system-results",
            query = systemQuery,
        )
        if (!forceLive || SupabaseResultsRemoteStore.payloadHasRows(SupabaseResultsRemoteStore.extractResultPayload(primaryPayload))) {
            return primaryPayload ?: fetchJson(path = "/results", query = arrayOf("date" to renderDate))
        }
        return primaryPayload
    }

    private fun fetchJson(
        path: String,
        query: Array<out Pair<String, String>>,
        connectTimeoutMs: Int = this.connectTimeoutMs,
        readTimeoutMs: Int = this.readTimeoutMs,
    ): Any? {
        requestFetcher?.let { return it(path, query, connectTimeoutMs, readTimeoutMs) }
        return runCatching {
            val encodedQuery = query.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }
            val requestUrl = "${baseUrl.trimEnd('/')}/$path?$encodedQuery"
            val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299 || body.isBlank()) return@runCatching null
            runCatching { JSONObject(body) }.getOrElse { JSONArray(body) }
        }.getOrNull()
    }

    internal fun toRenderDateKey(date: String): String {
        return toScraperDateKey(date)
    }
}

class ResultsAdminRemoteClient(
    private val baseUrl: String = "https://didactic-guacamole.onrender.com",
    private val connectTimeoutMs: Int = 8000,
    private val readTimeoutMs: Int = 12000,
    private val requestSender: ((String, String, JSONObject, Int, Int) -> Int)? = null,
) {
    fun postManualOverride(payload: JSONObject): Boolean = send(
        path = "/admin/results/manual-override",
        method = "POST",
        payload = payload,
    ) in 200..299

    fun deleteManualOverride(payload: JSONObject): Boolean = send(
        path = "/admin/results/manual-override",
        method = "DELETE",
        payload = payload,
    ) in 200..299

    private fun send(path: String, method: String, payload: JSONObject): Int {
        requestSender?.let { return it(path, method, payload, connectTimeoutMs, readTimeoutMs) }
        val connection = (URL("${baseUrl.trimEnd('/')}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }
}

internal fun toScraperDateKey(date: String): String {
    val parts = date.split("-")
    return if (parts.size == 3 && parts[0].length == 4) {
        "${parts[2]}-${parts[1]}-${parts[0]}"
    } else {
        date
    }
}
