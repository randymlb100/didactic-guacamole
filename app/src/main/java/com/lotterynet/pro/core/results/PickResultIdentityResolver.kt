package com.lotterynet.pro.core.results

import com.lotterynet.pro.core.catalog.UsPickScheduleResolver
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryResult
import java.util.Locale

data class PickResultIdentity(
    val canonicalKey: String,
    val stateCode: String,
    val gameType: String,
    val drawTime: String,
)

object PickResultIdentityResolver {
    fun resolveResult(result: LotteryResult): PickResultIdentity? {
        return resolve(
            id = result.lotteryId,
            name = result.lotteryName,
            game = null,
            drawTime = null,
            hasPick3 = !result.pick3.isNullOrBlank(),
            hasPick4 = !result.pick4.isNullOrBlank(),
        )
    }

    fun resolveLottery(lottery: LotteryCatalogItem): PickResultIdentity? {
        return resolve(
            id = lottery.id,
            name = lottery.name,
            game = lottery.type,
            drawTime = lottery.baseDrawTime,
            hasPick3 = false,
            hasPick4 = false,
        )
    }

    fun canonicalKeyForResult(result: LotteryResult): String {
        return resolveResult(result)?.canonicalKey
            ?: result.lotteryId.ifBlank { result.lotteryName.orEmpty() }
    }

    fun canonicalKeyForLottery(lottery: LotteryCatalogItem): String {
        return resolveLottery(lottery)?.canonicalKey ?: lottery.id
    }

    fun canonicalKeyForExpectedId(id: String): String {
        if (id.count { it == '|' } == 2) return id
        return resolve(
            id = id,
            name = null,
            game = null,
            drawTime = null,
            hasPick3 = false,
            hasPick4 = false,
        )?.canonicalKey ?: id
    }

    private fun resolve(
        id: String,
        name: String?,
        game: String?,
        drawTime: String?,
        hasPick3: Boolean,
        hasPick4: Boolean,
    ): PickResultIdentity? {
        val upperId = id.uppercase(Locale.US)
        val text = listOf(id, name.orEmpty(), game.orEmpty()).joinToString(" ").uppercase(Locale.US)
        val gameType = resolveGameType(upperId, text, hasPick3, hasPick4) ?: return null
        val stateCode = resolveStateCode(upperId, text) ?: return null
        val resolvedDrawTime = drawTime
            ?: legacyNewJerseyDrawTime(upperId)
            ?: extractExplicitDrawTime(text)
            ?: UsPickScheduleResolver.resolve(id, name)?.drawTime
            ?: return null
        val minuteKey = formatDrawMinuteKey(resolvedDrawTime) ?: return null
        return PickResultIdentity(
            canonicalKey = "$gameType|$stateCode|$minuteKey",
            stateCode = stateCode,
            gameType = gameType,
            drawTime = resolvedDrawTime,
        )
    }

    private fun resolveGameType(
        upperId: String,
        text: String,
        hasPick3: Boolean,
        hasPick4: Boolean,
    ): String? {
        return when {
            hasPick4 || upperId.startsWith("US-P4-") || upperId in setOf("21", "22") ||
                "PICK 4" in text || "PICK-4" in text || "PICK4" in text ||
                "PLAY 4" in text || "PLAY4" in text || "CASH 4" in text ||
                "DAILY 4" in text || "WIN 4" in text -> "P4"
            hasPick3 || upperId.startsWith("US-P3-") || upperId in setOf("19", "20") ||
                "PICK 3" in text || "PICK-3" in text || "PICK3" in text ||
                "PLAY 3" in text || "PLAY3" in text || "CASH 3" in text ||
                "DAILY 3" in text || "NUMBERS" in text -> "P3"
            else -> null
        }
    }

    private fun resolveStateCode(upperId: String, text: String): String? {
        if (upperId in setOf("19", "20", "21", "22")) return "NJ"
        val idState = upperId.split("-").getOrNull(2)
            ?.takeIf { it.matches(Regex("[A-Z]{2}|DC")) }
        if (idState != null) return idState
        return stateNames.firstNotNullOfOrNull { (token, code) ->
            code.takeIf { token in text }
        }
    }

    private fun legacyNewJerseyDrawTime(upperId: String): String? {
        return when (upperId) {
            "19", "21" -> "12:59 PM"
            "20", "22" -> "10:57 PM"
            else -> null
        }
    }

    private fun extractExplicitDrawTime(text: String): String? {
        Regex("""(\d{1,2})-(\d{2})-?(AM|PM)""").find(text)?.let { match ->
            return "${match.groupValues[1].toInt()}:${match.groupValues[2]} ${match.groupValues[3]}"
        }
        Regex("""(\d{1,2}):(\d{2})\s*(AM|PM)""").find(text)?.let { match ->
            return "${match.groupValues[1].toInt()}:${match.groupValues[2]} ${match.groupValues[3]}"
        }
        return null
    }

    private fun formatDrawMinuteKey(raw: String): String? {
        val match = Regex("""(\d{1,2}):(\d{2})(?:\s*(AM|PM))?""")
            .find(raw.trim().uppercase(Locale.US))
            ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val suffix = match.groupValues.getOrNull(3).orEmpty()
        if (suffix == "AM" && hour == 12) hour = 0
        if (suffix == "PM" && hour < 12) hour += 12
        return "%02d:%02d".format(Locale.US, hour, minute)
    }

    private val stateNames = listOf(
        "NEW JERSEY" to "NJ",
        "FLORIDA" to "FL",
        "TEXAS" to "TX",
        "NEW YORK" to "NY",
        "ARIZONA" to "AZ",
        "CALIFORNIA" to "CA",
        "CONNECTICUT" to "CT",
        "WASHINGTON DC" to "DC",
        "DISTRICT OF COLUMBIA" to "DC",
        "INDIANA" to "IN",
        "GEORGIA" to "GA",
        "MARYLAND" to "MD",
        "OHIO" to "OH",
        "PENNSYLVANIA" to "PA",
        "VIRGINIA" to "VA",
        "NORTH CAROLINA" to "NC",
        "SOUTH CAROLINA" to "SC",
        "TENNESSEE" to "TN",
        "KENTUCKY" to "KY",
        "ILLINOIS" to "IL",
        "MICHIGAN" to "MI",
        "WISCONSIN" to "WI",
        "MISSOURI" to "MO",
        "MINNESOTA" to "MN",
        "OREGON" to "OR",
        "WASHINGTON" to "WA",
    )
}
