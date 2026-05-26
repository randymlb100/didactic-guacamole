package com.lotterynet.pro.core.results

import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.model.LotteryResult
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.Normalizer
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class ResultsSupabaseStore(
    private val remoteStore: SupabaseResultsRemoteStore = SupabaseResultsRemoteStore(),
    private val expectedResultIds: Set<String> = emptySet(),
    private val expectedResultIdsForDate: ((String) -> Set<String>)? = null,
    private val catalogRepository: StaticLotteryCatalogRepository = StaticLotteryCatalogRepository(),
) : ResultsRemoteStore {
    override fun fetchResultsForDate(date: String, forceLive: Boolean): List<LotteryResult> {
        try {
            val value = remoteStore.fetchResultsPayload(
                date = date,
                expectedResultIds = expectedResultIdsForDate?.invoke(date) ?: expectedResultIds,
                forceLive = forceLive,
            ) ?: return emptyList()
            return parseResultsValue(value, date)
        } catch (error: Exception) {
            if (isTransientNetworkFailure(error)) {
                return emptyList()
            }
            throw error
        }
    }

    internal fun parseResultsValue(value: Any, date: String): List<LotteryResult> {
        val jsonValue = when (value) {
            is JSONArray -> value
            is JSONObject -> SupabaseResultsRemoteStore.extractResultPayload(value) as? JSONArray
                ?: value.optJSONArray("rows")
                ?: value.optJSONArray("results")
                ?: JSONArray().put(value)
            is String -> {
                runCatching { JSONArray(value) }.getOrElse {
                    val json = runCatching { JSONObject(value) }.getOrDefault(JSONObject())
                    SupabaseResultsRemoteStore.extractResultPayload(json) as? JSONArray
                        ?: json.optJSONArray("rows")
                        ?: json.optJSONArray("results")
                        ?: JSONArray()
                }
            }
            else -> JSONArray()
        }
        return buildList {
            for (index in 0 until jsonValue.length()) {
                val item = jsonValue.optJSONObject(index) ?: continue
                val numbers = item.optJSONArray("n")
                val rawId = item.optString("id").ifBlank { item.optString("lotteryId") }
                val rawName = item.optString("name").ifBlank { item.optString("lotteryName") }
                val normalizedId = normalizeRemoteLotteryId(rawId, rawName)
                val compactNumber = item.optString("number")
                    .ifBlank { item.optString("numero") }
                    .ifBlank { item.optString("numbers") }
                    .ifBlank { null }
                val normalizedStatus = normalizeResultStatus(
                    item.optString("status").ifBlank { item.optString("estado") },
                )
                val parsedNumber = parseCompactNumber(
                    rawNumber = compactNumber,
                    lotteryName = rawName,
                    lotteryId = rawId,
                    game = item.optString("game").ifBlank { null },
                )
                val explicitPick3 = item.optString("pick3").ifBlank { null }
                val explicitPick4 = item.optString("pick4").ifBlank { null }
                val isPickResult = !parsedNumber.pick3.isNullOrBlank() ||
                    !parsedNumber.pick4.isNullOrBlank() ||
                    !explicitPick3.isNullOrBlank() ||
                    !explicitPick4.isNullOrBlank()
                add(
                        LotteryResult(
                        lotteryId = normalizedId,
                        lotteryName = rawName.ifBlank { null },
                        date = normalizeResultDate(item.optString("date").ifBlank { date }, date),
                        first = if (isPickResult) null else firstPresentString(item, "first", "primera", "1ra", "1era", "firstPrize", "premio1").ifBlank {
                            numbers?.optString(0)?.takeIf { it.isNotBlank() } ?: parsedNumber.first
                        },
                        second = if (isPickResult) null else firstPresentString(item, "second", "segunda", "2da", "secondPrize", "premio2").ifBlank {
                            numbers?.optString(1)?.takeIf { it.isNotBlank() } ?: parsedNumber.second
                        },
                        third = if (isPickResult) null else firstPresentString(item, "third", "tercera", "3ra", "thirdPrize", "premio3").ifBlank {
                            numbers?.optString(2)?.takeIf { it.isNotBlank() } ?: parsedNumber.third
                        },
                        pick3 = explicitPick3 ?: parsedNumber.pick3,
                        pick4 = explicitPick4 ?: parsedNumber.pick4,
                        source = item.optString("source").ifBlank {
                            if (normalizedStatus == RESULT_STATUS_NO_DRAW) RESULT_STATUS_NO_DRAW else "supabase"
                        },
                        status = normalizedStatus,
                        fetchedAtEpochMs = System.currentTimeMillis(),
                        isManualOverride = item.optBoolean("isManualOverride", false),
                        manualEditedBy = item.optString("manualEditedBy").ifBlank { null },
                        manualEditedAt = item.optString("manualEditedAt").ifBlank { null },
                    ),
                )
            }
        }.filter { result -> resultBelongsToDate(result, date) }
    }

    internal fun normalizeResultDate(rawDate: String?, fallback: String): String {
        val value = rawDate.orEmpty().trim().ifBlank { fallback }
        val parts = value.split("-")
        return if (parts.size == 3 && parts[0].length == 2 && parts[2].length == 4) {
            "${parts[2]}-${parts[1]}-${parts[0]}"
        } else {
            value
        }
    }

    private fun firstPresentString(item: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            item.optString(key).trim().takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    private fun normalizeResultStatus(raw: String?): String? {
        val text = raw.orEmpty().trim()
        if (text.isBlank()) return null
        val normalized = normalizeResultIdentity(text)
            .replace("_", " ")
            .replace("-", " ")
        return when {
            normalized == "no draw" ||
                normalized == "no sorteo" ||
                normalized == "sin sorteo" ||
                ("sorteo" in normalized && ("no" in normalized || "sin" in normalized)) -> RESULT_STATUS_NO_DRAW
            normalized == "pending" || normalized == "pendiente" -> RESULT_STATUS_PENDING
            normalized == "missing from sources" || normalized == "missing_from_sources" -> RESULT_STATUS_MISSING_FROM_SOURCES
            else -> text
        }
    }

    internal fun normalizeRemoteLotteryId(
        rawId: String?,
        rawName: String?,
    ): String {
        val id = rawId.orEmpty().trim()
        val normalized = normalizeResultIdentity("${rawId.orEmpty()} ${rawName.orEmpty()}")
        if ("king" in normalized && "lottery" in normalized) {
            return when {
                "12:30" in normalized || "1230" in normalized || "12 30" in normalized ||
                    "dia" in normalized || "day" in normalized || "midday" in normalized -> "23"
                "7:30" in normalized || "730" in normalized || "7 30" in normalized ||
                    "noche" in normalized || "night" in normalized || "evening" in normalized -> "24"
                else -> id
            }
        }
        if ("haiti" in normalized && "bolet" in normalized) {
            return when {
                "9:30" in normalized || "930" in normalized || "9 30" in normalized -> "40"
                "10:30" in normalized || "1030" in normalized || "10 30" in normalized -> "41"
                "11:30" in normalized || "1130" in normalized || "11 30" in normalized -> "27"
                "5:30" in normalized || "530" in normalized || "5 30" in normalized -> "42"
                "6:30" in normalized || "630" in normalized || "6 30" in normalized -> "28"
                "7:30" in normalized || "730" in normalized || "7 30" in normalized -> "43"
                else -> id
            }
        }
        if ("primera" in normalized) {
            return when {
                "noche" in normalized || "night" in normalized || "7:00" in normalized || "700" in normalized -> "16"
                "manana" in normalized || "dia" in normalized || "day" in normalized ||
                    "12:00" in normalized || "1200" in normalized -> "1"
                else -> id
            }
        }
        if ("anguilla" in normalized || "anguila" in normalized) {
            return when {
                "8am" in normalized || "8 am" in normalized || "8:00" in normalized || "800" in normalized -> "29"
                "9am" in normalized || "9 am" in normalized || "9:00" in normalized || "900" in normalized -> "30"
                "10am" in normalized || "10 am" in normalized || "10:00" in normalized || "1000" in normalized ||
                    "manana" in normalized -> "2"
                "11am" in normalized || "11 am" in normalized || "11:00" in normalized || "1100" in normalized -> "31"
                "12pm" in normalized || "12 pm" in normalized || "12:00" in normalized || "1200" in normalized -> "32"
                "1pm" in normalized || "1 pm" in normalized || "1:00" in normalized || "100" in normalized ||
                    "medio dia" in normalized || "mediodia" in normalized -> "4"
                "2pm" in normalized || "2 pm" in normalized || "2:00" in normalized || "200" in normalized -> "33"
                "3pm" in normalized || "3 pm" in normalized || "3:00" in normalized || "300" in normalized -> "34"
                "4pm" in normalized || "4 pm" in normalized || "4:00" in normalized || "400" in normalized -> "35"
                "5pm" in normalized || "5 pm" in normalized || "5:00" in normalized || "500" in normalized -> "36"
                "6pm" in normalized || "6 pm" in normalized || "6:00" in normalized || "600" in normalized ||
                    "tarde" in normalized -> "11"
                "7pm" in normalized || "7 pm" in normalized || "7:00" in normalized || "700" in normalized -> "37"
                "8pm" in normalized || "8 pm" in normalized || "8:00" in normalized || "800" in normalized -> "38"
                "9pm" in normalized || "9 pm" in normalized || "9:00" in normalized || "900" in normalized ||
                    "noche" in normalized -> "14"
                "10pm" in normalized || "10 pm" in normalized || "10:00" in normalized || "1000" in normalized -> "39"
                else -> id
            }
        }
        if ("georgia" in normalized) {
            if (id.uppercase(Locale.US).startsWith("US-P3-") || id.uppercase(Locale.US).startsWith("US-P4-")) {
                return id
            }
            return when {
                "dia" in normalized || "day" in normalized -> "44"
                "tarde" in normalized || "evening" in normalized -> "45"
                "noche" in normalized || "night" in normalized -> "46"
                else -> id
            }
        }
        if (("new jersey" in normalized || " nj " in " $normalized ") && "pick" in normalized) {
            val isPick4 = "pick 4" in normalized || "pick4" in normalized
            val isPick3 = "pick 3" in normalized || "pick3" in normalized
            val isDay = "dia" in normalized || "day" in normalized || "midday" in normalized ||
                "tarde" in normalized || "am" in normalized
            val isNight = "noche" in normalized || "night" in normalized || "evening" in normalized ||
                "pm" in normalized
            return when {
                isPick3 && isDay -> "19"
                isPick3 && isNight -> "20"
                isPick4 && isDay -> "21"
                isPick4 && isNight -> "22"
                else -> id
            }
        }
        if ("new jersey" in normalized && "pick" !in normalized) {
            return when {
                "tarde" in normalized || "am" in normalized || "midday" in normalized -> "25"
                "noche" in normalized || "pm" in normalized || "evening" in normalized -> "26"
                else -> id
            }
        }
        catalogRepository.getLotteryByName(rawName.orEmpty())?.id?.let { return it }
        return id
    }

    internal fun parseCompactNumber(
        rawNumber: String?,
        lotteryName: String?,
        lotteryId: String? = null,
        game: String? = null,
    ): ParsedResultNumber {
        val parts = rawNumber
            ?.split(Regex("""\D+"""))
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (parts.isEmpty()) {
            return ParsedResultNumber()
        }
        val normalizedIdentity = normalizeResultIdentity(
            listOfNotNull(lotteryId, lotteryName, game).joinToString(" "),
        )
        val compactIdentity = normalizedIdentity.filter(Char::isLetterOrDigit)
        val isPick4 = "pick 4" in normalizedIdentity ||
            "pick4" in compactIdentity ||
            "play 4" in normalizedIdentity ||
            "play4" in compactIdentity ||
            "cash 4" in normalizedIdentity ||
            "cash4" in compactIdentity ||
            "daily 4" in normalizedIdentity ||
            "daily4" in compactIdentity ||
            "p4" in compactIdentity ||
            compactIdentity.contains("usp4")
        val isPick3 = "pick 3" in normalizedIdentity ||
            "pick3" in compactIdentity ||
            "play 3" in normalizedIdentity ||
            "play3" in compactIdentity ||
            "cash 3" in normalizedIdentity ||
            "cash3" in compactIdentity ||
            "daily 3" in normalizedIdentity ||
            "daily3" in compactIdentity ||
            "p3" in compactIdentity ||
            compactIdentity.contains("usp3")
        return when {
            isPick4 || parts.size == 4 -> {
                ParsedResultNumber(pick4 = rawNumber)
            }

            isPick3 -> {
                ParsedResultNumber(pick3 = rawNumber)
            }

            else -> ParsedResultNumber(
                first = parts.getOrNull(0),
                second = parts.getOrNull(1),
                third = parts.getOrNull(2),
            )
        }
    }

    private fun normalizeResultIdentity(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .trim()
    }

    internal data class ParsedResultNumber(
        val first: String? = null,
        val second: String? = null,
        val third: String? = null,
        val pick3: String? = null,
        val pick4: String? = null,
    )

    internal fun isTransientNetworkFailure(error: Throwable): Boolean {
        return error is UnknownHostException ||
            error is SocketTimeoutException ||
            error is SocketException ||
            error is IOException
    }
}

const val RESULT_STATUS_NO_DRAW = "no_draw"
const val RESULT_STATUS_PENDING = "pending"
const val RESULT_STATUS_MISSING_FROM_SOURCES = "missing_from_sources"
