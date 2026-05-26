package com.lotterynet.pro.core.format

import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

fun formatWholeAmount(value: Double): String {
    val cents = round(value * 100.0) / 100.0
    val whole = round(cents)
    return if (abs(cents - whole) < 0.005) {
        "%,.0f".format(Locale.US, whole)
    } else {
        "%,.2f".format(Locale.US, cents)
    }
}

fun formatWholeMoney(value: Double): String {
    return "$ ${formatWholeAmount(value)}"
}

fun formatSignedWholeMoney(value: Double): String {
    val sign = if (value >= 0.0) "+" else "-"
    return "$sign${formatWholeMoney(abs(value))}"
}
