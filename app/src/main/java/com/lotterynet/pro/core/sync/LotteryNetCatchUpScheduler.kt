package com.lotterynet.pro.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object LotteryNetCatchUpScheduler {
    private const val PERIODIC_WORK_NAME = "lotterynet-catch-up-periodic"
    private const val IMMEDIATE_WORK_NAME = "lotterynet-catch-up-now"
    private const val PUSH_WORK_NAME = "lotterynet-catch-up-push"
    private const val MIN_IMMEDIATE_ENQUEUE_INTERVAL_MS = 90_000L
    private val lastImmediateEnqueuedAt = AtomicLong(0L)

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<LotteryNetCatchUpWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .addTag(PERIODIC_WORK_NAME)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueImmediate(
        context: Context,
        reason: SyncReason = SyncReason.FOREGROUND,
        forceTickets: Boolean = false,
        forceResults: Boolean = false,
        forceFinance: Boolean = false,
        forceConfig: Boolean = false,
        forceSports: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        val previous = lastImmediateEnqueuedAt.get()
        val forced = forceTickets || forceResults || forceFinance || forceConfig || forceSports
        if (!forced && now - previous < MIN_IMMEDIATE_ENQUEUE_INTERVAL_MS) return
        if (!lastImmediateEnqueuedAt.compareAndSet(previous, now)) return

        val request = OneTimeWorkRequestBuilder<LotteryNetCatchUpWorker>()
            .setConstraints(networkConstraints())
            .setInputData(
                Data.Builder()
                    .putString(LotteryNetCatchUpWorker.KEY_REASON, reason.name)
                    .putBoolean(LotteryNetCatchUpWorker.KEY_FORCE_TICKETS, forceTickets)
                    .putBoolean(LotteryNetCatchUpWorker.KEY_FORCE_RESULTS, forceResults)
                    .putBoolean(LotteryNetCatchUpWorker.KEY_FORCE_FINANCE, forceFinance)
                    .putBoolean(LotteryNetCatchUpWorker.KEY_FORCE_CONFIG, forceConfig)
                    .putBoolean(LotteryNetCatchUpWorker.KEY_FORCE_SPORTS, forceSports)
                    .build(),
            )
            .addTag(IMMEDIATE_WORK_NAME)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueuePushTriggered(
        context: Context,
        forceTickets: Boolean,
        forceResults: Boolean,
        forceFinance: Boolean,
    ) {
        val request = OneTimeWorkRequestBuilder<LotteryNetCatchUpWorker>()
            .setConstraints(networkConstraints())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(
                Data.Builder()
                    .putString(LotteryNetCatchUpWorker.KEY_REASON, SyncReason.FCM.name)
                    .putBoolean(LotteryNetCatchUpWorker.KEY_FORCE_TICKETS, forceTickets)
                    .putBoolean(LotteryNetCatchUpWorker.KEY_FORCE_RESULTS, forceResults)
                    .putBoolean(LotteryNetCatchUpWorker.KEY_FORCE_FINANCE, forceFinance)
                    .build(),
            )
            .addTag(PUSH_WORK_NAME)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            PUSH_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun networkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
