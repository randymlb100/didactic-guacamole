package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.users.clearUsersPayloadMemoryCache
import com.lotterynet.pro.core.users.SupabaseUsersRemoteStore
import com.lotterynet.pro.core.users.UsersRemoteStore

class NativeUsersBootstrapper(
    private val usersRepository: LocalUsersRepository,
    private val usersRemoteStore: UsersRemoteStore = SupabaseUsersRemoteStore(),
) {
    fun bootstrap(forceRemoteRefresh: Boolean = false): BootstrapResult {
        val localAdmins = usersRepository.getAdmins()
        val localCashiers = usersRepository.getCashiers()
        if (!shouldFetchRemoteUsers(
                hasLocalUsers = localAdmins.isNotEmpty() || localCashiers.isNotEmpty(),
                forceRemoteRefresh = forceRemoteRefresh,
            )
        ) {
            return BootstrapResult(
                ok = true,
                source = "local",
                adminCount = localAdmins.size,
                cashierCount = localCashiers.size,
            )
        }

        if (forceRemoteRefresh) {
            clearUsersPayloadMemoryCache()
        }
        return runCatching {
            val payload = fetchRemoteUsersPayload() ?: return BootstrapResult(
                ok = false,
                source = "remote",
                message = "Supabase no devolvio usuarios."
            )
            usersRepository.cacheRawPayload(payload)
            val admins = usersRepository.getAdmins()
            val cashiers = usersRepository.getCashiers()
            BootstrapResult(
                ok = admins.isNotEmpty() || cashiers.isNotEmpty(),
                source = "remote",
                adminCount = admins.size,
                cashierCount = cashiers.size,
                message = if (admins.isNotEmpty() || cashiers.isNotEmpty()) null else "El cache remoto llego vacio.",
            )
        }.getOrElse { error ->
            BootstrapResult(
                ok = false,
                source = "remote",
                message = error.message ?: "No se pudo sincronizar usuarios.",
            )
        }
    }

    private fun fetchRemoteUsersPayload(): String? {
        return usersRemoteStore.fetchUsersPayload()
    }
}

internal fun shouldFetchRemoteUsers(
    hasLocalUsers: Boolean,
    forceRemoteRefresh: Boolean,
): Boolean = forceRemoteRefresh || !hasLocalUsers

data class BootstrapResult(
    val ok: Boolean,
    val source: String,
    val adminCount: Int = 0,
    val cashierCount: Int = 0,
    val message: String? = null,
)
