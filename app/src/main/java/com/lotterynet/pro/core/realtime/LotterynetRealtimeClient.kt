package com.lotterynet.pro.core.realtime

import android.util.Log
import com.lotterynet.pro.core.config.SupabaseConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class LotterynetRealtimeConfig(
    val url: String,
    val publishableKey: String,
) {
    companion object {
        fun fromSupabaseConfig(): LotterynetRealtimeConfig = LotterynetRealtimeConfig(
            url = SupabaseConfig.URL,
            publishableKey = SupabaseConfig.KEY,
        )
    }
}

class LotterynetRealtimeClient(
    private val config: LotterynetRealtimeConfig = LotterynetRealtimeConfig.fromSupabaseConfig(),
) {
    interface SubscriptionHandle {
        fun close()
    }

    companion object {
        private const val TAG = "LotteryNetRealtime"
    }

    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val client by lazy {
        createSupabaseClient(
            supabaseUrl = config.url,
            supabaseKey = config.publishableKey,
        ) {
            install(Postgrest)
            install(Realtime)
        }
    }

    fun isConfigured(): Boolean {
        return config.url.isNotBlank() && config.publishableKey.isNotBlank()
    }

    fun scope(): CoroutineScope = clientScope

    fun subscribe(
        subscription: LotterynetRealtimeSubscription,
        onEvent: (LotterynetRealtimeEvent) -> Unit,
    ): SubscriptionHandle {
        if (!isConfigured()) {
            return object : SubscriptionHandle {
                override fun close() = Unit
            }
        }

        val channel = client.channel("${subscription.channelName}-${System.nanoTime()}")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = subscription.schema) {
            table = subscription.table
            subscription.filter?.let(::applyRealtimeFilter)
        }
        val job = clientScope.launch {
            try {
                launch {
                    changes.collect { action ->
                        onEvent(
                            LotterynetRealtimeEvent(
                                type = action.toLotterynetType(),
                                table = subscription.table,
                                filterValue = subscription.filterValue(),
                                payloadJson = action.toString(),
                            )
                        )
                    }
                }
                channel.subscribe()
            } catch (error: Throwable) {
                Log.w(TAG, "Realtime subscription failed for ${subscription.table}", error)
            }
        }

        return object : SubscriptionHandle {
            override fun close() {
                job.cancel()
                clientScope.launch {
                    try {
                        client.realtime.removeChannel(channel)
                    } catch (error: Throwable) {
                        Log.w(TAG, "Realtime channel close failed for ${subscription.table}", error)
                    }
                }
            }
        }
    }

    fun shutdown() {
        clientScope.cancel()
    }

    private fun PostgresAction.toLotterynetType(): LotterynetRealtimeEventType = when (this) {
        is PostgresAction.Delete -> LotterynetRealtimeEventType.DELETE
        is PostgresAction.Insert -> LotterynetRealtimeEventType.INSERT
        is PostgresAction.Update -> LotterynetRealtimeEventType.UPDATE
        is PostgresAction.Select -> LotterynetRealtimeEventType.UPDATE
    }

}

private fun io.github.jan.supabase.realtime.PostgresChangeFilter.applyRealtimeFilter(raw: String) {
    val match = Regex("""^([^=]+)=eq\.(.+)$""").matchEntire(raw.trim()) ?: return
    filter(match.groupValues[1], FilterOperator.EQ, match.groupValues[2])
}
