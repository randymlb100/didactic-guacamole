package com.lotterynet.pro.core.users

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import org.json.JSONObject

class UserPasswordBackendClient(
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(SupabaseConfig.URL, SupabaseConfig.KEY),
    private val bearerTokenProvider: () -> String? = { null },
) {
    fun changePassword(
        session: ActiveSession,
        target: UserAccount,
        newPassword: String,
    ): UserPasswordChangeResult {
        val response = edgeClient.invokeAuthenticated(
            "change-user-password",
            buildChangeUserPasswordPayload(session, target, newPassword),
            bearerTokenProvider(),
        )
        return UserPasswordChangeResult(
            targetUser = response.optString("targetUser").ifBlank { target.user },
            targetId = response.optString("targetId").ifBlank { target.id },
            authUpdated = response.optBoolean("authUpdated", false),
        )
    }

    fun changeCashierGroupPassword(
        session: ActiveSession,
        admin: UserAccount,
        newPassword: String,
    ): UserPasswordGroupChangeResult {
        val response = edgeClient.invokeAuthenticated(
            "change-user-password",
            JSONObject().apply {
                put("action", "change-cashier-group-password")
                put("adminId", admin.id)
                put("newPassword", newPassword)
            },
            bearerTokenProvider(),
        )
        return UserPasswordGroupChangeResult(
            updatedCount = response.optInt("updatedCount", 0),
            authUpdatedCount = response.optInt("authUpdatedCount", 0),
            payloadConfirmed = response.optBoolean("payloadConfirmed", false),
        )
    }
}

data class UserPasswordChangeResult(
    val targetUser: String,
    val targetId: String,
    val authUpdated: Boolean,
)

data class UserPasswordGroupChangeResult(
    val updatedCount: Int,
    val authUpdatedCount: Int,
    val payloadConfirmed: Boolean,
)

internal fun buildChangeUserPasswordPayload(
    session: ActiveSession,
    target: UserAccount,
    newPassword: String,
): JSONObject {
    return JSONObject().apply {
        put("actorId", session.userId)
        put("actorUser", session.username)
        put("actorRole", session.role.name.lowercase())
        put("actorAdminId", session.adminId)
        put("actorAdminUser", session.adminUser)
        put("targetId", target.id)
        put("targetUser", target.user)
        put("targetRole", target.role.name.lowercase())
        put("newPassword", newPassword)
    }
}
