package com.lotterynet.pro.core.storage

internal class UsersPayloadCache<T> {
    private var cachedRaw: String? = null
    private var cachedPayload: T? = null

    @Synchronized
    fun getOrParse(
        raw: String?,
        emptyPayload: T,
        parser: (String) -> T,
    ): T {
        if (raw.isNullOrBlank()) return emptyPayload
        cachedPayload?.let { payload ->
            if (cachedRaw == raw) return payload
        }
        return parser(raw).also { payload ->
            cachedRaw = raw
            cachedPayload = payload
        }
    }

    @Synchronized
    fun invalidate() {
        cachedRaw = null
        cachedPayload = null
    }
}
