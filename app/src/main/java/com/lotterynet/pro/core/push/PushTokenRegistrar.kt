package com.lotterynet.pro.core.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import com.lotterynet.pro.core.storage.LocalSessionRepository
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import kotlin.concurrent.thread

class PushTokenRegistrar(
    context: Context,
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(),
) {
    private val appContext = context.applicationContext

    fun registerCurrentToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            val token = task.result?.takeIf { it.isNotBlank() } ?: return@addOnCompleteListener
            thread(name = "lotterynet-current-push-token-register") {
                register(token)
            }
        }
    }

    fun register(token: String) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) return
        val sessionRepository = LocalSessionRepository(appContext)
        val session = sessionRepository.getActiveSession() ?: return
        val bearerToken = SupabaseSessionTokenProvider(sessionRepository).freshAccessToken() ?: return
        edgeClient.invokeAuthenticated(
            "register-push-token",
            buildPayload(session, cleanToken),
            bearerToken,
        )
    }

    private fun buildPayload(session: ActiveSession, token: String): JSONObject {
        val ownerKey = resolveOwnerKey(session)
        return JSONObject().apply {
            put("token", token)
            put("platform", "android")
            put("ownerKeyHash", sha256(ownerKey))
            put("actorRole", session.role.name.lowercase(Locale.US))
        }
    }
}

internal fun resolveOwnerKey(session: ActiveSession): String {
    return session.adminId?.takeIf { it.isNotBlank() }
        ?: session.adminUser?.takeIf { it.isNotBlank() }
        ?: session.userId
}

internal fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { "%02x".format(it) }
}
