package com.lotterynet.pro.core.realtime

import android.util.Log
import com.lotterynet.pro.core.config.SupabaseConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonObject

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
        private const val PRIVATE_REALTIME_AUTH_REFRESH_INTERVAL_MS = 60 * 1000L
        private val sharedBroadcastSubscriptionLock = Any()
        private val sharedBroadcastSubscriptions = mutableMapOf<String, SharedBroadcastSubscription>()
        private val sharedUsersStateSubscriptionLock = Any()
        private var sharedUsersStateSubscription: SharedUsersStateSubscription? = null
        private val sharedUsersStateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val sharedBroadcastScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeAuthLock = Any()
    private val privateRealtimeAuthRefreshLock = Any()
    @Volatile private var lastAppliedRealtimeAuthToken: String? = null
    @Volatile private var privateRealtimeAuthProvider: (() -> String?)? = null
    @Volatile private var privateRealtimeAuthRefreshJob: Job? = null
    @Volatile private var realtimeChannelHealthy: Boolean = false

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

    /**
     * Realtime is considered healthy only after at least one channel has
     * completed its subscription handshake. Configuration alone is not enough:
     * a failed/private channel must allow the UI's bounded fallback sync to run.
     */
    fun isHealthy(): Boolean = isConfigured() && realtimeChannelHealthy

    fun shouldUsePollingFallback(): Boolean = !isConfigured() || !realtimeChannelHealthy

    fun scope(): CoroutineScope = clientScope

    fun subscribe(
        subscription: LotterynetRealtimeSubscription,
        bearerTokenProvider: (() -> String?)? = null,
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
                if (bearerTokenProvider != null) {
                    applyRealtimeAuth(bearerTokenProvider)
                    ensurePrivateRealtimeAuthRefreshLoop(bearerTokenProvider)
                }
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
                realtimeChannelHealthy = true
            } catch (error: Throwable) {
                realtimeChannelHealthy = false
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

    fun subscribeBroadcast(
        topic: String,
        event: String = "UPDATE",
        isPrivate: Boolean = true,
        bearerTokenProvider: (() -> String?)? = null,
        onEvent: (LotterynetRealtimeEvent) -> Unit,
    ): SubscriptionHandle {
        if (!isConfigured() || topic.isBlank()) {
            return object : SubscriptionHandle {
                override fun close() = Unit
            }
        }

        if (isPrivate) {
            if (bearerTokenProvider?.invoke().isNullOrBlank()) {
                Log.w(TAG, "Skipping private realtime broadcast for $topic because no auth token is available")
                return object : SubscriptionHandle {
                    override fun close() = Unit
                }
            }
        }

        val subscriptionKey = buildBroadcastSubscriptionKey(topic = topic, event = event, isPrivate = isPrivate)
        val (sharedSubscription, isCreator) = synchronized(sharedBroadcastSubscriptionLock) {
            val existing = sharedBroadcastSubscriptions[subscriptionKey]
            if (existing != null) {
                existing.listeners.add(onEvent)
                existing to false
            } else {
                SharedBroadcastSubscription().also { created ->
                    created.listeners.add(onEvent)
                    sharedBroadcastSubscriptions[subscriptionKey] = created
                } to true
            }
        }

        if (!isCreator) {
            return object : SubscriptionHandle {
                override fun close() = releaseSharedBroadcastSubscription(subscriptionKey, onEvent)
            }
        }

        val channel = client.channel(topic) {
            this.isPrivate = isPrivate
            broadcast {
                acknowledgeBroadcasts = true
                receiveOwnBroadcasts = false
            }
        }
        val messages = channel.broadcastFlow<JsonObject>(event)
        sharedSubscription.closeChannel = {
            sharedBroadcastScope.launch {
                try {
                    client.realtime.removeChannel(channel)
                } catch (error: Throwable) {
                    Log.w(TAG, "Realtime broadcast channel close failed for $topic", error)
                }
            }
        }
        val job = sharedBroadcastScope.launch(start = CoroutineStart.LAZY) {
            try {
                if (isPrivate && bearerTokenProvider != null) {
                    applyRealtimeAuth(bearerTokenProvider)
                    ensurePrivateRealtimeAuthRefreshLoop(bearerTokenProvider)
                }
                sharedBroadcastScope.launch {
                    messages.collect { payload ->
                        val listeners = synchronized(sharedBroadcastSubscriptionLock) {
                            sharedBroadcastSubscriptions[subscriptionKey]?.listeners?.toList().orEmpty()
                        }
                        val realtimeEvent = LotterynetRealtimeEvent(
                            type = LotterynetRealtimeEventType.UPDATE,
                            table = "broadcast",
                            filterValue = topic.substringAfterLast(':').takeIf { it.isNotBlank() },
                            payloadJson = payload.toString(),
                            topic = topic,
                        )
                        listeners.forEach { listener ->
                            runCatching { listener(realtimeEvent) }
                        }
                    }
                }
                channel.subscribe(blockUntilSubscribed = true)
                realtimeChannelHealthy = true
            } catch (error: Throwable) {
                realtimeChannelHealthy = false
                Log.w(TAG, "Realtime broadcast subscription failed for $topic", error)
            } finally {
                clearSharedBroadcastSubscription(subscriptionKey)
            }
        }
        val shouldStart = synchronized(sharedBroadcastSubscriptionLock) {
            if (sharedBroadcastSubscriptions[subscriptionKey] !== sharedSubscription) {
                sharedSubscription.job = job
                false
            } else {
                sharedSubscription.job = job
                true
            }
        }
        if (!shouldStart) {
            job.cancel()
            sharedSubscription.closeChannel?.invoke()
            return object : SubscriptionHandle {
                override fun close() = Unit
            }
        }
        val stillActive = synchronized(sharedBroadcastSubscriptionLock) {
            sharedBroadcastSubscriptions[subscriptionKey] === sharedSubscription && sharedSubscription.listeners.isNotEmpty()
        }
        if (!stillActive) {
            job.cancel()
            sharedSubscription.closeChannel?.invoke()
            return object : SubscriptionHandle {
                override fun close() = Unit
            }
        }
        job.start()

        return object : SubscriptionHandle {
            override fun close() = releaseSharedBroadcastSubscription(subscriptionKey, onEvent)
        }
    }

    private suspend fun applyRealtimeAuth(bearerTokenProvider: (() -> String?)?) {
        val token = bearerTokenProvider?.invoke()?.takeIf { it.isNotBlank() } ?: return
        val shouldApply = synchronized(realtimeAuthLock) { token != lastAppliedRealtimeAuthToken }
        if (!shouldApply) return
        client.realtime.setAuth(token)
        synchronized(realtimeAuthLock) {
            lastAppliedRealtimeAuthToken = token
        }
    }

    private fun ensurePrivateRealtimeAuthRefreshLoop(bearerTokenProvider: (() -> String?)?) {
        if (bearerTokenProvider == null) return
        synchronized(privateRealtimeAuthRefreshLock) {
            privateRealtimeAuthProvider = bearerTokenProvider
            if (privateRealtimeAuthRefreshJob != null) return
            privateRealtimeAuthRefreshJob = clientScope.launch {
                try {
                    while (isActive) {
                        applyRealtimeAuth(privateRealtimeAuthProvider)
                        delay(PRIVATE_REALTIME_AUTH_REFRESH_INTERVAL_MS)
                    }
                } finally {
                    synchronized(privateRealtimeAuthRefreshLock) {
                        privateRealtimeAuthRefreshJob = null
                    }
                }
            }
        }
    }

    fun subscribeTicketOwnerSignals(
        ownerKey: String,
        bearerTokenProvider: (() -> String?)? = null,
        onEvent: (LotterynetRealtimeEvent) -> Unit,
    ): List<SubscriptionHandle> {
        if (ownerKey.isBlank()) return emptyList()
        return listOf(
            subscribeBroadcast(
                topic = LotterynetRealtimeSubscription.ticketOwnerBroadcastTopic(ownerKey),
                event = "UPDATE",
                isPrivate = true,
                bearerTokenProvider = bearerTokenProvider,
                onEvent = onEvent,
            ),
        )
    }

    fun subscribeResultsSignals(
        dateKey: String,
        bearerTokenProvider: (() -> String?)? = null,
        onEvent: (LotterynetRealtimeEvent) -> Unit,
    ): List<SubscriptionHandle> {
        if (dateKey.isBlank()) return emptyList()
        return listOf(
            subscribeBroadcast(
                topic = LotterynetRealtimeSubscription.resultsBroadcastTopic(dateKey),
                event = "UPDATE",
                isPrivate = true,
                bearerTokenProvider = bearerTokenProvider,
                onEvent = onEvent,
            ),
        )
    }

    fun subscribeUsersStateSignals(
        bearerTokenProvider: (() -> String?)? = null,
        onEvent: (LotterynetRealtimeEvent) -> Unit,
    ): List<SubscriptionHandle> {
        if (!isConfigured()) {
            return emptyList()
        }
        // The users payload remains behind the authenticated Edge Function.
        // Realtime only delivers a private invalidation signal.
        return listOf(
            subscribeBroadcast(
                topic = "ln:users:global",
                event = "UPDATE",
                isPrivate = true,
                bearerTokenProvider = bearerTokenProvider,
                onEvent = onEvent,
            ),
            subscribeBroadcast(
                topic = "ln:users:global",
                event = "INSERT",
                isPrivate = true,
                bearerTokenProvider = bearerTokenProvider,
                onEvent = onEvent,
            ),
        )
    }

    fun shutdown() {
        realtimeChannelHealthy = false
        synchronized(privateRealtimeAuthRefreshLock) {
            privateRealtimeAuthRefreshJob?.cancel()
            privateRealtimeAuthRefreshJob = null
        }
        clientScope.cancel()
    }

    private fun releaseSharedBroadcastSubscription(
        subscriptionKey: String,
        listener: (LotterynetRealtimeEvent) -> Unit,
    ) {
        val closeChannel = synchronized(sharedBroadcastSubscriptionLock) {
            val shared = sharedBroadcastSubscriptions[subscriptionKey] ?: return
            shared.listeners.remove(listener)
            if (shared.listeners.isNotEmpty()) return
            sharedBroadcastSubscriptions.remove(subscriptionKey)
            shared.job?.cancel()
            shared.closeChannel
        }
        closeChannel?.invoke()
    }

    private fun clearSharedBroadcastSubscription(subscriptionKey: String) {
        val closeChannel = synchronized(sharedBroadcastSubscriptionLock) {
            val shared = sharedBroadcastSubscriptions.remove(subscriptionKey) ?: return
            shared.listeners.clear()
            shared.job?.cancel()
            shared.closeChannel
        }
        closeChannel?.invoke()
    }

    private fun releaseSharedUsersStateSubscription(
        subscriptionKey: String,
        listener: (LotterynetRealtimeEvent) -> Unit,
    ) {
        val closeChannel = synchronized(sharedUsersStateSubscriptionLock) {
            val shared = sharedUsersStateSubscription ?: return
            shared.listeners.remove(listener)
            if (shared.listeners.isNotEmpty()) return
            sharedUsersStateSubscription = null
            shared.job?.cancel()
            shared.closeChannel
        }
        closeChannel?.invoke()
    }

    private fun clearSharedUsersStateSubscription() {
        val closeChannel = synchronized(sharedUsersStateSubscriptionLock) {
            val shared = sharedUsersStateSubscription ?: return
            shared.listeners.clear()
            shared.job?.cancel()
            sharedUsersStateSubscription = null
            shared.closeChannel
        }
        closeChannel?.invoke()
    }

    private fun PostgresAction.toLotterynetType(): LotterynetRealtimeEventType = when (this) {
        is PostgresAction.Delete -> LotterynetRealtimeEventType.DELETE
        is PostgresAction.Insert -> LotterynetRealtimeEventType.INSERT
        is PostgresAction.Update -> LotterynetRealtimeEventType.UPDATE
        is PostgresAction.Select -> LotterynetRealtimeEventType.UPDATE
    }

}

private class SharedBroadcastSubscription {
    val listeners: MutableSet<(LotterynetRealtimeEvent) -> Unit> = linkedSetOf()
    var job: Job? = null
    var closeChannel: (() -> Unit)? = null
}

private class SharedUsersStateSubscription {
    val listeners: MutableSet<(LotterynetRealtimeEvent) -> Unit> = linkedSetOf()
    var job: Job? = null
    var closeChannel: (() -> Unit)? = null
}

private fun buildBroadcastSubscriptionKey(topic: String, event: String, isPrivate: Boolean): String {
    return listOf(topic.trim().lowercase(), event.trim().uppercase(), isPrivate.toString()).joinToString("|")
}

private fun buildUsersStateSubscriptionKey(): String {
    return listOf(
        "users-state",
        "public",
        "lotterynet_users_state",
        "scope=eq.global",
    ).joinToString("|")
}

private fun io.github.jan.supabase.realtime.PostgresChangeFilter.applyRealtimeFilter(raw: String) {
    val match = Regex("""^([^=]+)=eq\.(.+)$""").matchEntire(raw.trim()) ?: return
    filter(match.groupValues[1], FilterOperator.EQ, match.groupValues[2])
}
