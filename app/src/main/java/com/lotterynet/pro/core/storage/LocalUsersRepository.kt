package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.repository.UsersRepository
import org.json.JSONArray
import org.json.JSONObject

class LocalUsersRepository(
    context: Context,
) : UsersRepository {
    private val prefs = context.getSharedPreferences(UsersStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)
    private val usersPayloadCache = UsersPayloadCache<UsersPayload>()

    fun cacheRawPayload(payloadJson: String) {
        prefs.edit {
            putString(UsersStorageKeys.USERS_KEY, payloadJson)
        }
        usersPayloadCache.invalidate()
    }

    fun exportPayloadJson(): String {
        return prefs.getString(UsersStorageKeys.USERS_KEY, null)
            ?: JSONObject().apply {
                put("admins", JSONArray())
                put("cajeros", JSONArray())
            }.toString()
    }

    fun hasCachedUsers(): Boolean {
        val raw = prefs.getString(UsersStorageKeys.USERS_KEY, null)
        return !raw.isNullOrBlank() && (raw.contains("\"admins\"") || raw.contains("\"cajeros\""))
    }

    override fun getAdmins(): List<UserAccount> = getUsersPayload().admins

    override fun getSupervisors(): List<UserAccount> = getUsersPayload().supervisors

    override fun getCashiers(): List<UserAccount> = getUsersPayload().cashiers

    override fun findByIdOrUser(idOrUser: String): UserAccount? {
        val needle = idOrUser.trim()
        if (needle.isBlank()) return null
        return (getAdmins() + getSupervisors() + getCashiers()).firstOrNull { account ->
            account.id.equals(needle, ignoreCase = true) ||
                account.user.equals(needle, ignoreCase = true) ||
                account.displayName.equals(needle, ignoreCase = true)
        }
    }

    override fun saveUsers(admins: List<UserAccount>, cashiers: List<UserAccount>) {
        saveUsers(admins, getSupervisors(), cashiers)
    }

    override fun saveUsers(admins: List<UserAccount>, supervisors: List<UserAccount>, cashiers: List<UserAccount>) {
        val payload = buildUsersPayload(admins, supervisors, cashiers)
        prefs.edit {
            putString(UsersStorageKeys.USERS_KEY, payload)
        }
        usersPayloadCache.invalidate()
    }

    fun buildPayloadWithAccount(updated: UserAccount): String {
        val payload = getUsersPayload()
        val admins = payload.admins.replaceOrAppend(updated, UserRole.ADMIN)
        val supervisors = payload.supervisors.replaceOrAppend(updated, UserRole.SUPERVISOR)
        val cashiers = payload.cashiers.replaceOrAppend(updated, UserRole.CASHIER)
        return buildUsersPayload(admins, supervisors, cashiers)
    }

    fun updateAccount(updated: UserAccount) {
        cacheRawPayload(buildPayloadWithAccount(updated))
    }

    fun touchLastSeen(idOrUser: String, epochMs: Long = System.currentTimeMillis()) {
        val needle = idOrUser.trim()
        if (needle.isBlank()) return
        findByIdOrUser(needle)?.let { account ->
            updateAccount(account.copy(lastSeenAtEpochMs = epochMs))
        }
    }

    fun touchSession(session: ActiveSession?, epochMs: Long = System.currentTimeMillis()) {
        if (session == null) return
        touchLastSeen(session.userId, epochMs)
        touchLastSeen(session.username, epochMs)
    }

    private fun getUsersPayload(): UsersPayload {
        val raw = prefs.getString(UsersStorageKeys.USERS_KEY, null)
        return usersPayloadCache.getOrParse(raw, UsersPayload(emptyList(), emptyList(), emptyList()), ::parseUsersPayload)
    }

    private fun parseUsersPayload(raw: String): UsersPayload {
        return runCatching {
            val root = JSONObject(raw)
            val admins = root.optJSONArray("admins").toAccounts(UserRole.ADMIN)
            val supervisors = root.optJSONArray("supervisores").toAccounts(UserRole.SUPERVISOR) +
                root.optJSONArray("supervisors").toAccounts(UserRole.SUPERVISOR)
            val topLevelCashiers = root.optJSONArray("cajeros").toAccounts(UserRole.CASHIER)
            val nestedCashiers = buildList {
                root.optJSONArray("admins")?.let { adminsArray ->
                    for (index in 0 until adminsArray.length()) {
                        val adminJson = adminsArray.optJSONObject(index) ?: continue
                        val adminId = adminJson.optString("id").takeIf { it.isNotBlank() }
                        val adminUser = adminJson.optString("user").takeIf { it.isNotBlank() }
                        val banca = adminJson.optString("banca").takeIf { it.isNotBlank() }
                        adminJson.optJSONArray("cajeros")?.let { cashiersArray ->
                            for (cashierIndex in 0 until cashiersArray.length()) {
                                val cashierJson = cashiersArray.optJSONObject(cashierIndex) ?: continue
                                add(
                                    cashierJson.toAccount(
                                        defaultRole = UserRole.CASHIER,
                                        fallbackAdminId = adminId,
                                        fallbackAdminUser = adminUser,
                                        fallbackBanca = banca,
                                    )
                                )
                            }
                        }
                    }
                }
            }
            val cashiers = (topLevelCashiers + nestedCashiers).distinctBy { "${it.user.lowercase()}|${it.id.lowercase()}" }
            UsersPayload(
                admins = admins,
                supervisors = supervisors.distinctBy { "${it.user.lowercase()}|${it.id.lowercase()}" },
                cashiers = cashiers,
            )
        }.getOrElse {
            UsersPayload(emptyList(), emptyList(), emptyList())
        }
    }

    private fun JSONArray?.toAccounts(defaultRole: UserRole): List<UserAccount> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val json = optJSONObject(index) ?: continue
                add(json.toAccount(defaultRole))
            }
        }
    }

    private fun JSONObject.toAccount(
        defaultRole: UserRole,
        fallbackAdminId: String? = null,
        fallbackAdminUser: String? = null,
        fallbackBanca: String? = null,
    ): UserAccount {
        return UserAccount(
            id = optString("id"),
            user = optString("user"),
            role = UserRole.fromRaw(optString("role")).takeIf { it != UserRole.UNKNOWN } ?: defaultRole,
            displayName = optString("nombre").takeIf { it.isNotBlank() }
                ?: optString("name").takeIf { it.isNotBlank() },
            ownerName = optString("own").takeIf { it.isNotBlank() }
                ?: optString("ownerName").takeIf { it.isNotBlank() },
            address = optString("addr").takeIf { it.isNotBlank() }
                ?: optString("address").takeIf { it.isNotBlank() },
            active = optBoolean("activo", true),
            adminId = optString("adminId").takeIf { it.isNotBlank() } ?: fallbackAdminId,
            adminUser = optString("adminUser").takeIf { it.isNotBlank() } ?: fallbackAdminUser,
            banca = optString("banca").takeIf { it.isNotBlank() } ?: fallbackBanca,
            cashierPrefix = optString("cajPrefix").takeIf { it.isNotBlank() },
            createdLabel = optString("creado").takeIf { it.isNotBlank() }
                ?: optString("createdLabel").takeIf { it.isNotBlank() },
            territory = optString("territory").takeIf { it.isNotBlank() }
                ?: optString("territorio").takeIf { it.isNotBlank() },
            phone = optString("tel").takeIf { it.isNotBlank() }
                ?: optString("phone").takeIf { it.isNotBlank() },
            balance = optDouble("balance", 0.0),
            rechargesEnabled = optBoolean("recargasEnabled", false),
            rechargesAssignedBalance = optDouble(
                "recargasAssignedBalance",
                optDouble("recargasAssigned", optDouble("recargasBalance", 0.0)),
            ),
            rechargesBalance = optDouble("recargasBalance", 0.0),
            recargasRapidasUsername = optString("recargasRapidasUsername").takeIf { it.isNotBlank() },
            recargasRapidasPassword = optString("recargasRapidasPassword").takeIf { it.isNotBlank() },
            commissionRate = commissionRateFromJson(this),
            recargaTxLimit = rechargeTxLimitFromJson(this),
            supervisorIds = optStringList("supervisorIds") + optString("supervisorId").takeIf { it.isNotBlank() }.orEmptyList(),
            supervisorUsers = optStringList("supervisorUsers") + optString("supervisorUser").takeIf { it.isNotBlank() }.orEmptyList(),
            lastSeenAtEpochMs = optLong("lastSeenAt").takeIf { it > 0L },
            passwordSalt = optString("passwordSalt").takeIf { it.isNotBlank() },
            passwordHash = optString("passwordHash").takeIf { it.isNotBlank() },
            passwordVersion = optString("passwordVersion").takeIf { it.isNotBlank() },
            authUserId = optString("authUserId").takeIf { it.isNotBlank() }
                ?: optString("auth_user_id").takeIf { it.isNotBlank() },
            credChangedAtEpochMs = optLong("credChangedAt").takeIf { it > 0L },
            updatedAtEpochMs = optLong("updatedAt").takeIf { it > 0L },
            systemModeOverride = optString("systemModeOverride").takeIf { it.isNotBlank() }
                ?: optString("cashierSystemMode").takeIf { it.isNotBlank() }
                ?: optString("modoSistema").takeIf { it.isNotBlank() },
        )
    }

    private fun UserAccount.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("user", user)
            put("role", role.name.lowercase())
            put("nombre", displayName)
            put("own", ownerName)
            put("addr", address)
            put("activo", active)
            put("adminId", adminId)
            put("adminUser", adminUser)
            put("banca", banca)
            put("cajPrefix", cashierPrefix)
            put("creado", createdLabel)
            put("territory", territory)
            put("tel", phone)
            put("balance", balance)
            put("recargasEnabled", rechargesEnabled)
            put("recargasAssignedBalance", rechargesAssignedBalance)
            put("recargasBalance", rechargesBalance)
            put("commissionRate", commissionRate)
            put("recargaTx", recargaTxLimit)
            put("supervisorIds", JSONArray().apply { supervisorIds.forEach { put(it) } })
            put("supervisorUsers", JSONArray().apply { supervisorUsers.forEach { put(it) } })
            put("lastSeenAt", lastSeenAtEpochMs)
            put("passwordSalt", passwordSalt)
            put("passwordHash", passwordHash)
            put("passwordVersion", passwordVersion)
            put("authUserId", authUserId)
            put("credChangedAt", credChangedAtEpochMs)
            put("updatedAt", updatedAtEpochMs)
            put("systemModeOverride", systemModeOverride)
        }
    }

    private fun buildUsersPayload(admins: List<UserAccount>, cashiers: List<UserAccount>): String {
        return buildUsersPayload(admins, getSupervisors(), cashiers)
    }

    private fun buildUsersPayload(
        admins: List<UserAccount>,
        supervisors: List<UserAccount>,
        cashiers: List<UserAccount>,
    ): String {
        return JSONObject().apply {
            put("admins", JSONArray().apply { admins.forEach { put(it.toJson()) } })
            put("supervisores", JSONArray().apply { supervisors.forEach { put(it.toJson()) } })
            put("cajeros", JSONArray().apply { cashiers.forEach { put(it.toJson()) } })
        }.toString()
    }

    private fun List<UserAccount>.replaceOrAppend(
        updated: UserAccount,
        targetRole: UserRole,
    ): List<UserAccount> {
        val normalized = updated.copy(role = targetRole)
        var replaced = false
        val mapped = map { account ->
            if (account.id.equals(updated.id, ignoreCase = true) || account.user.equals(updated.user, ignoreCase = true)) {
                replaced = true
                normalized
            } else {
                account
            }
        }
        return if (!replaced && updated.role == targetRole) mapped + normalized else mapped
    }

    private fun JSONObject.optStringList(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun String?.orEmptyList(): List<String> = if (isNullOrBlank()) emptyList() else listOf(this)

    private fun commissionRateFromJson(json: JSONObject): Double? {
        val raw = when {
            json.has("commissionRate") -> json.optDouble("commissionRate")
            json.has("comision") -> json.optDouble("comision")
            else -> Double.NaN
        }
        if (!raw.isFinite()) return null
        var normalized = raw
        if (normalized > 1.0 && normalized <= 100.0) normalized /= 100.0
        if (normalized < 0.0) normalized = 0.0
        if (normalized > 1.0) normalized = 1.0
        return normalized
    }

    private fun rechargeTxLimitFromJson(json: JSONObject): Double? {
        val raw = when {
            json.has("recargaTx") -> json.optDouble("recargaTx")
            json.has("limiteRecargaTx") -> json.optDouble("limiteRecargaTx")
            else -> Double.NaN
        }
        if (!raw.isFinite()) return null
        return raw.coerceAtLeast(0.0)
    }
}

private data class UsersPayload(
    val admins: List<UserAccount>,
    val supervisors: List<UserAccount>,
    val cashiers: List<UserAccount>,
)

internal fun buildUsersPayloadForTest(
    admins: List<UserAccount>,
    cashiers: List<UserAccount>,
): String {
    fun UserAccount.toSanitizedJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("user", user)
            put("role", role.name.lowercase())
            put("recargasEnabled", rechargesEnabled)
            put("recargasAssignedBalance", rechargesAssignedBalance)
            put("recargasBalance", rechargesBalance)
            put("recargaTx", recargaTxLimit)
            put("systemModeOverride", systemModeOverride)
        }
    }
    return JSONObject().apply {
        put("admins", JSONArray().apply { admins.forEach { put(it.toSanitizedJson()) } })
        put("supervisores", JSONArray())
        put("cajeros", JSONArray().apply { cashiers.forEach { put(it.toSanitizedJson()) } })
    }.toString()
}
