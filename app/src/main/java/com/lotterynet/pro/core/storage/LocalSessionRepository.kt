package com.lotterynet.pro.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.SavedLogin
import com.lotterynet.pro.core.model.SessionSnapshot
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.repository.SessionRepository
import org.json.JSONObject

class LocalSessionRepository(
    context: Context,
) : SessionRepository {
    private val prefs: SharedPreferences = run {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            SessionStorageKeys.PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun getSavedLogin(): SavedLogin? {
        val raw = prefs.getString(SessionStorageKeys.LOGIN_STORE_KEY, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            SavedLogin(
                username = json.optString("user"),
                password = json.optString("password"),
                remember = json.optBoolean("remember", true),
            )
        }.getOrNull()
    }

    override fun saveSavedLogin(savedLogin: SavedLogin?) {
        prefs.edit {
            if (savedLogin == null) {
                remove(SessionStorageKeys.LOGIN_STORE_KEY)
            } else {
                putString(
                    SessionStorageKeys.LOGIN_STORE_KEY,
                    JSONObject().apply {
                        put("user", savedLogin.username)
                        put("password", savedLogin.password)
                        put("remember", savedLogin.remember)
                    }.toString()
                )
            }
        }
    }

    override fun getActiveSession(): ActiveSession? {
        val raw = prefs.getString(SessionStorageKeys.ACTIVE_SESSION_KEY, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            ActiveSession(
                role = UserRole.fromRaw(json.optString("role")),
                userId = json.optString("id"),
                username = json.optString("user"),
                adminId = json.optString("adminId").takeIf { it.isNotBlank() },
                adminUser = json.optString("adminUser").takeIf { it.isNotBlank() },
                banca = json.optString("banca").takeIf { it.isNotBlank() },
                territory = json.optString("territory").takeIf { it.isNotBlank() },
                authUserId = json.optString("authUserId").takeIf { it.isNotBlank() },
                authAccessToken = json.optString("authAccessToken").takeIf { it.isNotBlank() },
                authRefreshToken = json.optString("authRefreshToken").takeIf { it.isNotBlank() },
                authExpiresAtEpochSeconds = json.takeIf { it.has("authExpiresAt") }?.optLong("authExpiresAt")?.takeIf { it > 0L },
                startedAtEpochMs = json.optLong("startedAt", System.currentTimeMillis()),
            )
        }.getOrNull()
    }

    override fun saveActiveSession(activeSession: ActiveSession?) {
        prefs.edit {
            if (activeSession == null) {
                remove(SessionStorageKeys.ACTIVE_SESSION_KEY)
            } else {
                putString(
                    SessionStorageKeys.ACTIVE_SESSION_KEY,
                    JSONObject().apply {
                        put("role", activeSession.role.name.lowercase())
                        put("id", activeSession.userId)
                        put("user", activeSession.username)
                        put("adminId", activeSession.adminId)
                        put("adminUser", activeSession.adminUser)
                        put("banca", activeSession.banca)
                        put("territory", activeSession.territory)
                        put("authUserId", activeSession.authUserId)
                        put("authAccessToken", activeSession.authAccessToken)
                        put("authRefreshToken", activeSession.authRefreshToken)
                        put("authExpiresAt", activeSession.authExpiresAtEpochSeconds)
                        put("startedAt", activeSession.startedAtEpochMs)
                    }.toString()
                )
            }
        }
    }

    override fun getSessionSnapshot(): SessionSnapshot? {
        val raw = prefs.getString(SessionStorageKeys.SESSION_STATE_KEY, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            SessionSnapshot(
                activeSession = getActiveSession(),
                currentScreen = json.optString("screen").takeIf { it.isNotBlank() },
                turnoStartEpochMs = json.takeIf { it.has("turnoStartAt") }?.optLong("turnoStartAt"),
                lastSyncEpochMs = json.takeIf { it.has("lastSyncAt") }?.optLong("lastSyncAt"),
                isOnline = json.optBoolean("online", true),
            )
        }.getOrNull()
    }

    override fun saveSessionSnapshot(snapshot: SessionSnapshot?) {
        prefs.edit {
            if (snapshot == null) {
                remove(SessionStorageKeys.SESSION_STATE_KEY)
            } else {
                putString(
                    SessionStorageKeys.SESSION_STATE_KEY,
                    JSONObject().apply {
                        put("screen", snapshot.currentScreen)
                        put("turnoStartAt", snapshot.turnoStartEpochMs)
                        put("lastSyncAt", snapshot.lastSyncEpochMs)
                        put("online", snapshot.isOnline)
                    }.toString()
                )
                snapshot.turnoStartEpochMs?.let { putLong(SessionStorageKeys.TURNO_START_KEY, it) }
            }
        }
    }

    override fun clearSession() {
        prefs.edit {
            remove(SessionStorageKeys.LOGIN_STORE_KEY)
            remove(SessionStorageKeys.ACTIVE_SESSION_KEY)
            remove(SessionStorageKeys.SESSION_STATE_KEY)
            remove(SessionStorageKeys.TURNO_START_KEY)
        }
    }
}
