package com.lotterynet.pro.core.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lotterynet.pro.core.diagnostics.NativeCrashReporter

class LotteryNetBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action.orEmpty()
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        runCatching {
            LotteryNetCatchUpScheduler.schedulePeriodic(context)
            LotteryNetCatchUpScheduler.enqueueImmediate(context, reason = SyncReason.APP_START)
        }.onFailure { error ->
            NativeCrashReporter(context.applicationContext).recordHandled("LotteryNetBootReceiver", error)
        }
    }
}
