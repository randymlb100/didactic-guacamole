package com.lotterynet.pro.core.sportsbook

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.model.SportsbookBoardGame
import com.lotterynet.pro.core.model.SportsbookBoardSnapshot
import com.lotterynet.pro.core.model.SportsbookEvent
import com.lotterynet.pro.core.model.SportsbookMarket
import com.lotterynet.pro.core.model.SportsbookMarketKey
import com.lotterynet.pro.core.model.SportsbookOdd
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import org.json.JSONArray
import org.json.JSONObject

class SportsbookBoardRemoteStore(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.KEY,
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(baseUrl, apiKey),
) {
    fun fetchBoard(
        sportKey: String? = null,
        bearerToken: String? = null,
    ): SportsbookBoardSnapshot {
        val response = edgeClient.invoke(
            "sports-get-board",
            JSONObject()
                .put("action", "fetch")
                .put("sportKey", sportKey.orEmpty()),
            bearerToken,
        )
        return parseSportsbookBoardSnapshot(response.optJSONObject("payload") ?: response)
    }
}

fun parseSportsbookBoardSnapshot(raw: String): SportsbookBoardSnapshot {
    return parseSportsbookBoardSnapshot(JSONObject(raw.ifBlank { "{}" }))
}

fun parseSportsbookBoardSnapshot(payload: JSONObject): SportsbookBoardSnapshot {
    val gamesArray = payload.optJSONArray("games") ?: JSONArray()
    return SportsbookBoardSnapshot(
        games = buildList {
            for (index in 0 until gamesArray.length()) {
                gamesArray.optJSONObject(index)?.let(::parseSportsbookBoardGame)?.let(::add)
            }
        },
        fetchedAtEpochMs = parseSportsbookEpochMs(payload.opt("fetchedAt")),
        source = payload.optString("source", "cache"),
    )
}

private fun parseSportsbookBoardGame(json: JSONObject): SportsbookBoardGame? {
    val eventJson = json.optJSONObject("event") ?: return null
    val event = SportsbookEvent(
        id = eventJson.optString("id"),
        sportKey = eventJson.optString("sportKey"),
        sportTitle = eventJson.optString("sportTitle"),
        leagueTitle = eventJson.optString("leagueTitle").ifBlank { null },
        homeTeam = eventJson.optString("homeTeam"),
        awayTeam = eventJson.optString("awayTeam"),
        homeTeamLogoUrl = eventJson.optString("homeTeamLogoUrl").ifBlank { null },
        awayTeamLogoUrl = eventJson.optString("awayTeamLogoUrl").ifBlank { null },
        commenceTimeEpochMs = parseSportsbookEpochMs(eventJson.opt("commenceTime")),
        status = eventJson.optString("status", "scheduled"),
    )
    val markets = parseSportsbookMarkets(json.optJSONArray("markets"), event.id)
    return SportsbookBoardGame(
        event = event,
        markets = markets,
        odds = parseSportsbookOdds(json.optJSONArray("odds")),
    )
}

private fun parseSportsbookMarkets(array: JSONArray?, eventId: String): List<SportsbookMarket> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val key = sportsbookMarketKeyFromWire(json.optString("marketKey")) ?: continue
            add(
                SportsbookMarket(
                    id = json.optString("id"),
                    eventId = json.optString("eventId").ifBlank { eventId },
                    key = key,
                    title = json.optString("marketTitle").ifBlank { key.label },
                    status = json.optString("status", "open"),
                    line = json.optDoubleOrNull("line"),
                ),
            )
        }
    }
}

private fun parseSportsbookOdds(array: JSONArray?): List<SportsbookOdd> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val decimalOdds = json.optDouble("decimalOdds", 0.0)
            if (decimalOdds <= 1.0) continue
            add(
                SportsbookOdd(
                    id = json.optString("id"),
                    marketId = json.optString("marketId"),
                    selectionKey = json.optString("selectionKey"),
                    selectionLabel = json.optString("selectionLabel"),
                    decimalOdds = decimalOdds,
                    americanOdds = json.optIntOrNull("americanOdds"),
                    point = json.optDoubleOrNull("point"),
                    status = json.optString("status", "open"),
                    lastUpdatedEpochMs = parseSportsbookEpochMs(json.opt("lastUpdated")),
                ),
            )
        }
    }
}

fun sportsbookMarketKeyFromWire(raw: String?): SportsbookMarketKey? {
    val clean = raw?.trim().orEmpty()
    return SportsbookMarketKey.entries.firstOrNull { it.wireValue == clean }
}

private fun JSONObject.optDoubleOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val value = optDouble(key, Double.NaN)
    return value.takeIf { it.isFinite() }
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    val value = optInt(key, Int.MIN_VALUE)
    return value.takeIf { it != Int.MIN_VALUE }
}

private fun parseSportsbookEpochMs(value: Any?): Long {
    return when (value) {
        is Number -> value.toLong()
        is String -> runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
        else -> 0L
    }.coerceAtLeast(0L)
}
