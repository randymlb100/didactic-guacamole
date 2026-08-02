package com.lotterynet.pro.core.printing

import java.lang.reflect.InvocationTargetException

internal fun Throwable.printerRootCause(): Throwable {
    var current: Throwable = this
    val seen = HashSet<Throwable>()
    while (true) {
        if (!seen.add(current)) return current
        val next = when (current) {
            is InvocationTargetException -> current.targetException ?: current.cause
            else -> current.cause
        } ?: return current
        current = next
    }
}

internal fun Throwable.printerSafeMessage(fallback: String): String {
    val root = printerRootCause()
    return root.message?.takeIf { it.isNotBlank() } ?: fallback
}
