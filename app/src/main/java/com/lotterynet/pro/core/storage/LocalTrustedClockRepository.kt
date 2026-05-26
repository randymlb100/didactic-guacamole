package com.lotterynet.pro.core.storage

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import com.lotterynet.pro.core.calendar.LotteryTimeZones
import com.lotterynet.pro.core.model.ClockSource
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.model.TrustedClockSnapshot
import com.lotterynet.pro.core.repository.TrustedClockRepository

class LocalTrustedClockRepository(
    context: Context,
) : TrustedClockRepository {
    private val prefs = context.getSharedPreferences(TrustedClockStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    override fun getTrustedUtcMs(): Long {
        ensureInitialized()
        val baseUtc = prefs.getLong(TrustedClockStorageKeys.BASE_UTC_MS, System.currentTimeMillis())
        val baseElapsed = prefs.getLong(TrustedClockStorageKeys.BASE_ELAPSED_MS, SystemClock.elapsedRealtime())
        val nowElapsed = SystemClock.elapsedRealtime()
        val source = ClockSource.valueOf(
            prefs.getString(TrustedClockStorageKeys.CLOCK_SOURCE, ClockSource.DEVICE.name)
                ?: ClockSource.DEVICE.name
        )
        val read = resolveTrustedClockRead(
            baseUtcMs = baseUtc,
            baseElapsedMs = baseElapsed,
            nowElapsedMs = nowElapsed,
            deviceUtcMs = System.currentTimeMillis(),
            source = source,
        )
        if (read.shouldPersistDeviceBase) {
            syncFromUtc(read.utcMs, ClockSource.DEVICE)
        }
        return read.utcMs
    }

    override fun getSnapshot(territory: LotteryTerritory): TrustedClockSnapshot {
        ensureInitialized()
        return TrustedClockSnapshot(
            trustedUtcMs = getTrustedUtcMs(),
            source = ClockSource.valueOf(
                prefs.getString(TrustedClockStorageKeys.CLOCK_SOURCE, ClockSource.DEVICE.name)
                    ?: ClockSource.DEVICE.name
            ),
            syncedAtDeviceEpochMs = prefs.getLong(
                TrustedClockStorageKeys.SYNCED_AT_DEVICE_MS,
                System.currentTimeMillis(),
            ),
            operationTerritory = territory,
        )
    }

    override fun syncFromUtc(utcMs: Long, source: ClockSource): Boolean {
        if (utcMs <= 0L) return false
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowDevice = System.currentTimeMillis()
        prefs.edit {
            putLong(TrustedClockStorageKeys.BASE_UTC_MS, utcMs)
            putLong(TrustedClockStorageKeys.BASE_ELAPSED_MS, nowElapsed)
            putString(TrustedClockStorageKeys.CLOCK_SOURCE, source.name)
            putLong(TrustedClockStorageKeys.SYNCED_AT_DEVICE_MS, nowDevice)
        }
        return true
    }

    override fun getOperationTimeZone(territory: LotteryTerritory): String {
        return LotteryTimeZones.zoneId(territory)
    }

    private fun ensureInitialized() {
        if (!prefs.contains(TrustedClockStorageKeys.BASE_UTC_MS) || !prefs.contains(TrustedClockStorageKeys.BASE_ELAPSED_MS)) {
            syncFromUtc(System.currentTimeMillis(), ClockSource.DEVICE)
        }
    }
}

internal data class TrustedClockRead(
    val utcMs: Long,
    val shouldPersistDeviceBase: Boolean,
)

internal fun resolveTrustedClockRead(
    baseUtcMs: Long,
    baseElapsedMs: Long,
    nowElapsedMs: Long,
    deviceUtcMs: Long,
    source: ClockSource,
    maxDeviceDriftMs: Long = 5 * 60 * 1000L,
): TrustedClockRead {
    val delta = nowElapsedMs - baseElapsedMs
    if (delta < 0L) {
        return TrustedClockRead(deviceUtcMs, shouldPersistDeviceBase = true)
    }
    val calculated = baseUtcMs + delta
    val drift = kotlin.math.abs(calculated - deviceUtcMs)
    if (source == ClockSource.DEVICE && drift > maxDeviceDriftMs) {
        return TrustedClockRead(deviceUtcMs, shouldPersistDeviceBase = true)
    }
    return TrustedClockRead(calculated, shouldPersistDeviceBase = false)
}
