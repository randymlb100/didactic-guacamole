package com.lotterynet.pro.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lotterynet.pro.core.diagnostics.NativeCrashReporter
import com.lotterynet.pro.core.sync.LotteryNetCatchUpScheduler
import java.util.Locale
import kotlin.concurrent.thread

class LotteryNetFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        thread(name = "lotterynet-push-token-register") {
            runCatching { PushTokenRegistrar(applicationContext).register(token) }
                .onFailure { error ->
                    NativeCrashReporter(applicationContext).recordHandled("PushTokenRegistrar.register", error)
                }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"].orEmpty().lowercase(Locale.US)
        LotteryNetCatchUpScheduler.enqueuePushTriggered(
            context = applicationContext,
            forceTickets = type == "ticket" || type == "winner",
            forceResults = type == "result" || type == "winner",
            forceFinance = type == "ticket" || type == "winner",
        )
    }
}
