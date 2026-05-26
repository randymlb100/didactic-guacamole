package com.lotterynet.pro.core.users

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import org.json.JSONObject

class UserPasswordBackendClient(
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(SupabaseConfig.URL, SupabaseConfig.KEY),
) {
    fun changePassword(
        session: ActiveSession,
        target: UserAccount,
        newPassword: String,
    ): UserPasswordChangeResult {
        val response = edgeClient.invoke(
            "change-user-password",
            buildChangeUserPasswordPayload(session, target, newPassword),
        )
        return UserPasswordChangeResult(
            targetUser = response.optString("targetUser").ifBlank { target.user },
            targetId = response.optString("targetId").ifBlank { target.id },
            authUpdated = response.optBoolean("authUpdated", false),
        )
    }
}

data class UserPasswordChangeResult(
    val targetUser: String,
    val targetId: String,
    val authUpdated: Boolean,
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
