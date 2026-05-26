package com.lotterynet.pro.core.catalog

import java.util.Locale

object UsPickScheduleResolver {
    data class Schedule(
        val drawTime: String,
        val timeZoneId: String,
    )

    fun resolve(id: String, name: String?): Schedule? {
        val upperId = id.uppercase(Locale.US)
        val stateCode = upperId.split("-").getOrNull(2) ?: return null
        val period = resolvePeriod(upperId, name.orEmpty().uppercase(Locale.US)) ?: return null
        if (stateCode == "CA" && isCaliforniaDaily4(upperId, name.orEmpty().uppercase(Locale.US))) {
            return Schedule(drawTime = "6:30 PM", timeZoneId = timeZonesByState[stateCode] ?: "America/Los_Angeles")
        }
        val drawTime = drawTimesByStatePeriod[stateCode to period]
            ?: (if (period == "DAY") drawTimesByStatePeriod[stateCode to "MIDDAY"] else null)
            ?: return null
        val zone = timeZonesByState[stateCode] ?: "America/New_York"
        return Schedule(drawTime = drawTime, timeZoneId = zone)
    }

    private fun resolvePeriod(id: String, name: String): String? {
        resolvePeriodFromText(id)?.let { return it }
        return resolvePeriodFromText(name)
    }

    private fun resolvePeriodFromText(text: String): String? {
        parseHyphenClockPeriod(text)?.let { return it }
        parseColonClockPeriod(text)?.let { return it }
        return when {
            "MORNING" in text -> "MORNING"
            "MIDDAY" in text || hasDrawToken(text, "DIA") -> "MIDDAY"
            "EVENING" in text || "TARDE" in text -> "EVENING"
            "NIGHT" in text || "NOCHE" in text -> "NIGHT"
            hasDrawToken(text, "DAY") -> "DAY"
            "DRAW" in text -> "DRAW"
            else -> null
        }
    }

    private fun hasDrawToken(text: String, token: String): Boolean {
        return text
            .split('-', '_', ' ', ':', '/', '.', ',', '(', ')')
            .any { it == token }
    }

    private fun parseHyphenClockPeriod(text: String): String? {
        val tokens = text.split('-', '_', ' ')
        for (index in 0 until tokens.lastIndex) {
            val hour = tokens[index].toIntOrNull()?.takeIf { it in 1..12 } ?: continue
            val minuteToken = tokens[index + 1]
            val minute = minuteToken.take(2).toIntOrNull()?.takeIf { it in 0..59 } ?: continue
            val meridiem = when {
                minuteToken.endsWith("AM") -> "AM"
                minuteToken.endsWith("PM") -> "PM"
                index + 2 < tokens.size && tokens[index + 2] == "AM" -> "AM"
                index + 2 < tokens.size && tokens[index + 2] == "PM" -> "PM"
                else -> null
            } ?: continue
            return "$hour:${minute.toString().padStart(2, '0')} $meridiem"
        }
        return null
    }

    private fun parseColonClockPeriod(text: String): String? {
        val colonIndex = text.indexOf(':')
        if (colonIndex <= 0) return null
        val hourStart = (colonIndex - 2).coerceAtLeast(0)
        val hourText = text.substring(hourStart, colonIndex).dropWhile { !it.isDigit() }
        val hour = hourText.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val minuteText = text.drop(colonIndex + 1).take(2)
        val minute = minuteText.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        val suffix = text.drop(colonIndex + 3).trimStart().take(2)
        val meridiem = when (suffix) {
            "AM", "PM" -> suffix
            else -> return null
        }
        return "$hour:${minute.toString().padStart(2, '0')} $meridiem"
    }

    private fun isCaliforniaDaily4(id: String, name: String): Boolean {
        val text = "$id $name"
        return "US-P4-CA-" in id && ("DAILY 4" in text || "DAILY-4" in text || "DAILY4" in text)
    }

    private val timeZonesByState = mapOf(
        "AR" to "America/Chicago",
        "AZ" to "America/Phoenix",
        "CA" to "America/Los_Angeles",
        "CO" to "America/Denver",
        "CT" to "America/New_York",
        "DC" to "America/New_York",
        "DE" to "America/New_York",
        "FL" to "America/New_York",
        "GA" to "America/New_York",
        "IA" to "America/Chicago",
        "ID" to "America/Boise",
        "IL" to "America/Chicago",
        "IN" to "America/New_York",
        "KS" to "America/Chicago",
        "KY" to "America/New_York",
        "LA" to "America/Chicago",
        "MA" to "America/New_York",
        "MD" to "America/New_York",
        "ME" to "America/New_York",
        "MI" to "America/New_York",
        "MN" to "America/Chicago",
        "MO" to "America/Chicago",
        "MS" to "America/Chicago",
        "NC" to "America/New_York",
        "NE" to "America/Chicago",
        "NH" to "America/New_York",
        "NJ" to "America/New_York",
        "NM" to "America/Denver",
        "NY" to "America/New_York",
        "OH" to "America/New_York",
        "OK" to "America/Chicago",
        "OR" to "America/Los_Angeles",
        "PA" to "America/New_York",
        "RI" to "America/New_York",
        "SC" to "America/New_York",
        "TN" to "America/Chicago",
        "TX" to "America/Chicago",
        "VA" to "America/New_York",
        "VT" to "America/New_York",
        "WA" to "America/Los_Angeles",
        "WI" to "America/Chicago",
        "WV" to "America/New_York",
    )

    private val drawTimesByStatePeriod = mapOf(
        ("AR" to "MIDDAY") to "12:59 PM",
        ("AR" to "EVENING") to "6:59 PM",
        ("AZ" to "DRAW") to "7:00 PM",
        ("CA" to "DAY") to "1:00 PM",
        ("CA" to "MIDDAY") to "1:00 PM",
        ("CA" to "EVENING") to "6:30 PM",
        ("CO" to "MIDDAY") to "1:30 PM",
        ("CO" to "EVENING") to "7:30 PM",
        ("CT" to "DAY") to "1:57 PM",
        ("CT" to "MIDDAY") to "1:57 PM",
        ("CT" to "NIGHT") to "10:29 PM",
        ("CT" to "EVENING") to "10:29 PM",
        ("DC" to "MIDDAY") to "1:50 PM",
        ("DC" to "EVENING") to "7:50 PM",
        ("DC" to "NIGHT") to "11:30 PM",
        ("DE" to "DAY") to "1:58 PM",
        ("DE" to "MIDDAY") to "1:58 PM",
        ("DE" to "NIGHT") to "7:57 PM",
        ("DE" to "EVENING") to "7:57 PM",
        ("FL" to "MIDDAY") to "1:30 PM",
        ("FL" to "EVENING") to "9:45 PM",
        ("GA" to "MIDDAY") to "12:29 PM",
        ("GA" to "EVENING") to "6:59 PM",
        ("GA" to "NIGHT") to "11:34 PM",
        ("IA" to "MIDDAY") to "12:20 PM",
        ("IA" to "EVENING") to "10:00 PM",
        ("ID" to "DAY") to "1:59 PM",
        ("ID" to "MIDDAY") to "1:59 PM",
        ("ID" to "NIGHT") to "7:59 PM",
        ("IL" to "MIDDAY") to "12:40 PM",
        ("IL" to "MORNING") to "12:40 PM",
        ("IL" to "EVENING") to "9:22 PM",
        ("IN" to "MIDDAY") to "1:20 PM",
        ("IN" to "EVENING") to "11:00 PM",
        ("KS" to "MIDDAY") to "1:10 PM",
        ("KS" to "EVENING") to "9:10 PM",
        ("KY" to "MIDDAY") to "1:20 PM",
        ("KY" to "EVENING") to "11:00 PM",
        ("LA" to "DAY") to "9:59 PM",
        ("MA" to "MIDDAY") to "2:00 PM",
        ("MA" to "EVENING") to "9:00 PM",
        ("MD" to "MIDDAY") to "12:27 PM",
        ("MD" to "EVENING") to "7:56 PM",
        ("ME" to "MIDDAY") to "1:10 PM",
        ("ME" to "EVENING") to "6:50 PM",
        ("MI" to "MIDDAY") to "12:59 PM",
        ("MI" to "EVENING") to "7:29 PM",
        ("MN" to "DAY") to "6:17 PM",
        ("MO" to "DAY") to "12:45 PM",
        ("MO" to "MIDDAY") to "12:45 PM",
        ("MO" to "EVENING") to "8:59 PM",
        ("MS" to "MIDDAY") to "2:30 PM",
        ("MS" to "EVENING") to "9:30 PM",
        ("NC" to "MIDDAY") to "3:00 PM",
        ("NC" to "EVENING") to "11:22 PM",
        ("NE" to "DAY") to "10:00 PM",
        ("NH" to "MIDDAY") to "1:10 PM",
        ("NH" to "EVENING") to "6:55 PM",
        ("NJ" to "MIDDAY") to "12:59 PM",
        ("NJ" to "EVENING") to "10:57 PM",
        ("NM" to "MIDDAY") to "1:00 PM",
        ("NM" to "EVENING") to "9:30 PM",
        ("NY" to "MIDDAY") to "2:30 PM",
        ("NY" to "EVENING") to "10:30 PM",
        ("OH" to "MIDDAY") to "12:29 PM",
        ("OH" to "EVENING") to "7:29 PM",
        ("OK" to "DAY") to "9:00 PM",
        ("OR" to "EVENING") to "7:00 PM",
        ("PA" to "DAY") to "1:35 PM",
        ("PA" to "MIDDAY") to "1:35 PM",
        ("PA" to "EVENING") to "6:59 PM",
        ("RI" to "MIDDAY") to "1:20 PM",
        ("RI" to "EVENING") to "6:59 PM",
        ("SC" to "MIDDAY") to "12:59 PM",
        ("SC" to "EVENING") to "6:59 PM",
        ("TN" to "MORNING") to "9:28 AM",
        ("TN" to "DAY") to "12:28 PM",
        ("TN" to "MIDDAY") to "12:28 PM",
        ("TN" to "EVENING") to "6:28 PM",
        ("TN" to "6:28 PM") to "6:28 PM",
        ("TX" to "MORNING") to "10:00 AM",
        ("TX" to "DAY") to "12:27 PM",
        ("TX" to "EVENING") to "6:00 PM",
        ("TX" to "NIGHT") to "10:12 PM",
        ("VA" to "DAY") to "1:59 PM",
        ("VA" to "MIDDAY") to "1:59 PM",
        ("VA" to "NIGHT") to "11:00 PM",
        ("VA" to "EVENING") to "11:00 PM",
        ("VT" to "MIDDAY") to "1:10 PM",
        ("VT" to "EVENING") to "6:59 PM",
        ("WA" to "DAY") to "8:00 PM",
        ("WI" to "MIDDAY") to "1:30 PM",
        ("WI" to "1:30 PM") to "1:30 PM",
        ("WI" to "EVENING") to "9:00 PM",
        ("WI" to "9:00 PM") to "9:00 PM",
        ("WV" to "DAY") to "6:59 PM",
        ("WV" to "EVENING") to "9:00 PM",
        ("WV" to "9:00 PM") to "9:00 PM",
    )
}
