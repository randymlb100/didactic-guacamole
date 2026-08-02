package com.lotterynet.pro.core.servicesgames

import com.lotterynet.pro.core.model.UserRole
import java.util.Locale

/** New business modules. IPTV is intentionally not represented here. */
enum class ServicesGamesModule(val wireValue: String, val label: String) {
    SERVICES("services", "Servicios"),
    VIDEO_GAMES("video_games", "Videojuegos");

    companion object {
        fun fromWire(value: String?): ServicesGamesModule? {
            return entries.firstOrNull { it.wireValue == value?.trim()?.lowercase(Locale.US) }
        }
    }
}

enum class ServicesGamesTargetType(val wireValue: String) {
    ADMIN("admin"),
    CASHIER("cashier");
}

data class ServicesGamesFeatureConfig(
    val module: ServicesGamesModule,
    val enabled: Boolean = false,
    val allowedAdminKeys: Set<String> = emptySet(),
    val allowedCashierKeys: Set<String> = emptySet(),
    val cashierAdminKeys: Set<String> = emptySet(),
    val updatedAtEpochMs: Long = 0L,
    val updatedBy: String = "",
) {
    fun canOpen(role: UserRole, actorKey: String?, adminKey: String? = null): Boolean {
        if (!enabled) return false
        val actor = normalizeKey(actorKey)
        val admin = normalizeKey(adminKey)
        val admins = allowedAdminKeys.map(::normalizeKey).filter(String::isNotBlank).toSet()
        val cashiers = allowedCashierKeys.map(::normalizeKey).filter(String::isNotBlank).toSet()
        val cashierAdmins = cashierAdminKeys.map(::normalizeKey).filter(String::isNotBlank).toSet()

        return when (role) {
            UserRole.MASTER -> true
            UserRole.ADMIN -> actor.isNotBlank() && actor in admins
            UserRole.CASHIER -> actor.isNotBlank() && (actor in cashiers || admin in cashierAdmins)
            else -> false
        }
    }
}

data class ServicesGamesCatalogItem(
    val module: ServicesGamesModule,
    val providerId: String,
    val productId: String,
    val name: String,
    val logoAssetKey: String,
    val clientPrice: Double? = null,
    val providerCost: Double? = null,
    val commission: Double? = null,
    val currency: String = "DOP",
    val updatedAtEpochMs: Long = 0L,
)

private fun normalizeKey(value: String?): String = value.orEmpty().trim().lowercase(Locale.US)
