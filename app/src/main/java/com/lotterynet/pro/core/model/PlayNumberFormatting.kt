package com.lotterynet.pro.core.model

fun formatPlayDisplayNumber(number: String, playType: String): String {
    if (number.isBlank()) return number
    return when (playType.uppercase()) {
        "P", "SP" -> {
            if (number.contains("/")) {
                number
            } else if (number.contains("-")) {
                number.split("-").joinToString("/")
            } else {
                val cleaned = number.filter(Char::isDigit)
                when {
                    cleaned.length >= 4 -> listOf(cleaned.take(2), cleaned.drop(2).take(2)).joinToString("/")
                    cleaned.length >= 2 -> listOf(cleaned.take(2), cleaned.drop(2)).filter { it.isNotBlank() }.joinToString("/")
                    else -> number
                }
            }
        }

        else -> number
    }
}
