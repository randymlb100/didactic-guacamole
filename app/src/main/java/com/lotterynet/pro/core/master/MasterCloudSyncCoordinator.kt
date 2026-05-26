package com.lotterynet.pro.core.master

import com.lotterynet.pro.core.storage.LocalAlertsRepository
import com.lotterynet.pro.core.storage.LocalAuditRepository
import com.lotterynet.pro.core.storage.LocalMasterConfigRepository
import com.lotterynet.pro.core.storage.LocalPresenceRepository
import com.lotterynet.pro.core.storage.LocalRechargeLimitRepository
import com.lotterynet.pro.core.storage.LocalCashierSalesLimitRepository
import com.lotterynet.pro.core.storage.LocalUsersDeletedRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.storage.UsersStorageKeys
import com.lotterynet.pro.core.users.SupabaseUsersRemoteStore
import com.lotterynet.pro.core.users.UsersRemoteStore
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MasterCloudSyncResult(
    val ok: Boolean,
    val message: String,
    val detail: String,
    val adminCount: Int = 0,
    val cashierCount: Int = 0,
)

class MasterCloudSyncCoordinator(
    private val usersRepository: LocalUsersRepository,
    private val deletedRepository: LocalUsersDeletedRepository,
    private val auditRepository: LocalAuditRepository,
    private val alertsRepository: LocalAlertsRepository,
    private val presenceRepository: LocalPresenceRepository,
    private val masterConfigRepository: LocalMasterConfigRepository,
    private val rechargeLimitRepository: LocalRechargeLimitRepository,
    private val cashierSalesLimitRepository: LocalCashierSalesLimitRepository? = null,
    private val backendStore: MasterConfigRemoteStore = SupabaseMasterConfigRemoteStore(),
    private val usersRemoteStore: UsersRemoteStore = SupabaseUsersRemoteStore(),
) {
    fun sync(): MasterCloudSyncResult {
        return runCatching { doSync() }.getOrElse { error ->
            MasterCloudSyncResult(
                ok = false,
                message = error.message ?: "No se pudo sincronizar master con la nube.",
                detail = "Falló el merge remoto de bancas, borrados o settings globales.",
                adminCount = usersRepository.getAdmins().size,
                cashierCount = usersRepository.getCashiers().size,
            )
        }
    }

    fun refreshRemoteSnapshot(): MasterCloudSyncResult {
        return runCatching { doRefreshRemoteSnapshot() }.getOrElse { error ->
            MasterCloudSyncResult(
                ok = false,
                message = error.message ?: "No se pudo cargar el snapshot remoto de master.",
                detail = "Falló la carga nativa de usuarios, borrados o caches auxiliares desde Supabase.",
                adminCount = usersRepository.getAdmins().size,
                cashierCount = usersRepository.getCashiers().size,
            )
        }
    }

    private fun doSync(): MasterCloudSyncResult {
        val usersPayload = usersRepository.exportPayloadJson()
        val deletedLocal = deletedRepository.getState()
        val usersFiltered = deletedRepository.applyDeletedUsers(usersPayload, deletedLocal)
        val usersRoot = JSONObject(usersFiltered)
        val admins = usersRoot.optJSONArray("admins")?.length() ?: 0
        val cashiers = usersRoot.optJSONArray("cajeros")?.length() ?: 0

        backendStore.upsertJsonValue(UsersStorageKeys.USERS_DELETED_KEY, deletedRepository.exportStateJson())
        backendStore.upsertJsonValue("sys_users_v4", usersFiltered)
        backendStore.upsertJsonValue("sys_audit_v4", auditRepository.exportPayload())
        backendStore.upsertJsonValue("sys_alerts_v4", alertsRepository.exportPayload())
        backendStore.upsertJsonValue("sys_presence_v1", presenceRepository.exportPayload())
        backendStore.upsertJsonValue(
            LocalMasterConfigRepository.LEGACY_RLDLY_CLIENT_ID,
            JSONObject.quote(masterConfigRepository.getReloadlySettings().clientId),
        )
        backendStore.upsertJsonValue(
            LocalMasterConfigRepository.LEGACY_RLDLY_CLIENT_SECRET,
            JSONObject.quote(masterConfigRepository.getReloadlySettings().clientSecret),
        )
        backendStore.upsertJsonValue(
            LocalMasterConfigRepository.LEGACY_RLDLY_ENABLED,
            if (masterConfigRepository.getReloadlySettings().enabled) "true" else "false",
        )
        backendStore.upsertJsonValue("sys_master_limits_v1", rechargeLimitRepository.exportLegacyPayload())
        usersRoot.optJSONArray("admins")?.let { adminsArray ->
            for (index in 0 until adminsArray.length()) {
                val adminId = adminsArray.optJSONObject(index)?.optString("id").orEmpty()
                if (adminId.isNotBlank()) {
                    cashierSalesLimitRepository?.let { limits ->
                        backendStore.upsertJsonValue("cashier_limits:$adminId", limits.exportPayload(adminId))
                    }
                }
            }
        }

        val mergedDeleted = deletedRepository.merge(
            local = deletedLocal,
            remote = fetchDeletedState(),
        )
        deletedRepository.saveState(mergedDeleted)

        val remoteUsersJson = usersRemoteStore.fetchUsersPayload()
            ?: backendStore.fetchValue("sys_users_v4")?.toRawJsonString()
        val mergedUsers = deletedRepository.applyDeletedUsers(
            deletedRepository.mergeUsersPayloadPreferNewest(
                localPayloadJson = usersFiltered,
                remotePayloadJson = remoteUsersJson ?: usersFiltered,
            ),
            mergedDeleted,
        )
        usersRepository.cacheRawPayload(mergedUsers)
        usersRemoteStore.upsertUsersPayload(mergedUsers)
        backendStore.fetchValue(LocalMasterConfigRepository.LEGACY_RLDLY_CLIENT_ID)?.let {
            masterConfigRepository.cacheLegacyValue(LocalMasterConfigRepository.LEGACY_RLDLY_CLIENT_ID, wrapLegacyPayload(it))
        }
        backendStore.fetchValue(LocalMasterConfigRepository.LEGACY_RLDLY_CLIENT_SECRET)?.let {
            masterConfigRepository.cacheLegacyValue(LocalMasterConfigRepository.LEGACY_RLDLY_CLIENT_SECRET, wrapLegacyPayload(it))
        }
        backendStore.fetchValue(LocalMasterConfigRepository.LEGACY_RLDLY_ENABLED)?.let {
            masterConfigRepository.cacheLegacyValue(LocalMasterConfigRepository.LEGACY_RLDLY_ENABLED, wrapLegacyPayload(it))
        }
        backendStore.fetchValue("sys_master_limits_v1")?.let {
            rechargeLimitRepository.cacheLegacyPayload(it)
        }
        usersRepository.getAdmins().forEach { admin ->
            backendStore.fetchValue("cashier_limits:${admin.id}")?.let { payload ->
                cashierSalesLimitRepository?.cachePayload(admin.id, payload.toRawJsonString())
            }
        }
        backendStore.fetchValue("sys_audit_v4")?.let {
            auditRepository.cachePayload(wrapLegacyPayload(it).unwrapQuotedJsonArray())
        }
        backendStore.fetchValue("sys_alerts_v4")?.let {
            alertsRepository.cachePayload(wrapLegacyPayload(it).unwrapQuotedJsonArray())
        }
        backendStore.fetchValue("sys_presence_v1")?.let {
            presenceRepository.cachePayload(wrapLegacyPayload(it).unwrapQuotedJsonObject())
        }

        val syncedAt = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).format(Date())
        return MasterCloudSyncResult(
            ok = true,
            message = "Sincronización master completada.",
            detail = "Subidos y conciliados $admins admin(s), $cashiers cajero(s), borrados heredados, auditoría, alertas, presencia y settings globales. Último corte $syncedAt.",
            adminCount = usersRepository.getAdmins().size,
            cashierCount = usersRepository.getCashiers().size,
        )
    }

    private fun doRefreshRemoteSnapshot(): MasterCloudSyncResult {
        val mergedDeleted = deletedRepository.merge(
            local = deletedRepository.getState(),
            remote = fetchDeletedState(),
        )
        deletedRepository.saveState(mergedDeleted)

        val remoteUsersJson = usersRemoteStore.fetchUsersPayload()
            ?: backendStore.fetchValue("sys_users_v4")?.toRawJsonString()
        if (!remoteUsersJson.isNullOrBlank()) {
            val localUsers = usersRepository.exportPayloadJson()
            val mergedUsers = deletedRepository.applyDeletedUsers(
                deletedRepository.mergeUsersPayloadPreferNewest(localUsers, remoteUsersJson),
                mergedDeleted,
            )
            usersRepository.cacheRawPayload(mergedUsers)
        }
        backendStore.fetchValue("sys_audit_v4")?.let {
            auditRepository.cachePayload(wrapLegacyPayload(it).unwrapQuotedJsonArray())
        }
        backendStore.fetchValue("sys_alerts_v4")?.let {
            alertsRepository.cachePayload(wrapLegacyPayload(it).unwrapQuotedJsonArray())
        }
        backendStore.fetchValue("sys_presence_v1")?.let {
            presenceRepository.cachePayload(wrapLegacyPayload(it).unwrapQuotedJsonObject())
        }
        backendStore.fetchValue(LocalMasterConfigRepository.LEGACY_RLDLY_CLIENT_ID)?.let {
            masterConfigRepository.cacheLegacyValue(LocalMasterConfigRepository.LEGACY_RLDLY_CLIENT_ID, wrapLegacyPayload(it))
        }
        backendStore.fetchValue(LocalMasterConfigRepository.LEGACY_RLDLY_CLIENT_SECRET)?.let {
            masterConfigRepository.cacheLegacyValue(LocalMasterConfigRepository.LEGACY_RLDLY_CLIENT_SECRET, wrapLegacyPayload(it))
        }
        backendStore.fetchValue(LocalMasterConfigRepository.LEGACY_RLDLY_ENABLED)?.let {
            masterConfigRepository.cacheLegacyValue(LocalMasterConfigRepository.LEGACY_RLDLY_ENABLED, wrapLegacyPayload(it))
        }
        backendStore.fetchValue("sys_master_limits_v1")?.let {
            rechargeLimitRepository.cacheLegacyPayload(it)
        }
        usersRepository.getAdmins().forEach { admin ->
            backendStore.fetchValue("cashier_limits:${admin.id}")?.let { payload ->
                cashierSalesLimitRepository?.cachePayload(admin.id, payload.toRawJsonString())
            }
        }
        val syncedAt = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).format(Date())
        return MasterCloudSyncResult(
            ok = true,
            message = "Snapshot remoto cargado.",
            detail = "Master hidrató usuarios, borrados y caches auxiliares desde Supabase. Último corte $syncedAt.",
            adminCount = usersRepository.getAdmins().size,
            cashierCount = usersRepository.getCashiers().size,
        )
    }

    private fun fetchDeletedState() =
        backendStore.fetchValue(UsersStorageKeys.USERS_DELETED_KEY)?.let { value ->
            deletedRepository.parseRawPayload(value.toRawJsonString())
        } ?: deletedRepository.getState()

    private fun Any.toRawJsonString(): String {
        return when (this) {
            is JSONObject -> toString()
            is String -> this
            else -> toString()
        }
    }

    private fun wrapLegacyPayload(value: Any): String {
        return when (value) {
            is JSONObject -> value.toString()
            is JSONArray -> value.toString()
            is String -> JSONObject.quote(value)
            is Boolean -> if (value) "true" else "false"
            is Number -> value.toString()
            else -> JSONObject.quote(value.toString())
        }
    }

    private fun String.unwrapQuotedJsonArray(): String {
        return runCatching {
            if (startsWith("\"")) JSONObject("{\"value\":$this}").optString("value") else this
        }.getOrDefault(this)
    }

    private fun String.unwrapQuotedJsonObject(): String {
        return runCatching {
            if (startsWith("\"")) JSONObject("{\"value\":$this}").optString("value") else this
        }.getOrDefault(this)
    }
}
