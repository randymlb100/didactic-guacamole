package com.lotterynet.pro.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lotterynet.pro.core.diagnostics.NativeCrashReporter

class LotteryNetCatchUpWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            val reason = syncReasonFromRaw(inputData.getString(KEY_REASON))
            val forceTickets = inputData.getBoolean(KEY_FORCE_TICKETS, false)
            val forceResults = inputData.getBoolean(KEY_FORCE_RESULTS, false)
            val forceFinance = inputData.getBoolean(KEY_FORCE_FINANCE, false)
            val forceConfig = inputData.getBoolean(KEY_FORCE_CONFIG, false)
            val forceSports = inputData.getBoolean(KEY_FORCE_SPORTS, false)
            val result = LotteryNetCatchUpCoordinator(applicationContext).catchUp(
                reason = reason,
                forceTickets = forceTickets,
                forceResults = forceResults,
                forceFinance = forceFinance,
                forceConfig = forceConfig,
                forceSports = forceSports,
            )
            if (result.ok) {
                Result.success()
            } else {
                Result.retry()
            }
        }.getOrElse { error ->
            NativeCrashReporter(applicationContext).recordHandled("LotteryNetCatchUpWorker", error)
            Result.retry()
        }
    }

    companion object {
        const val KEY_REASON = "syncReason"
        const val KEY_FORCE_TICKETS = "forceTickets"
        const val KEY_FORCE_RESULTS = "forceResults"
        const val KEY_FORCE_FINANCE = "forceFinance"
        const val KEY_FORCE_CONFIG = "forceConfig"
        const val KEY_FORCE_SPORTS = "forceSports"
    }
}
