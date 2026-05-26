package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit

data class BancaBranding(
    val logoUri: String = "",
)

class LocalBrandingRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBranding(): BancaBranding {
        return BancaBranding(
            logoUri = prefs.getString(KEY_LOGO_URI, "").orEmpty(),
        )
    }

    fun saveLogoUri(uri: String): BancaBranding {
        prefs.edit { putString(KEY_LOGO_URI, uri.trim()) }
        return getBranding()
    }

    fun clearLogo(): BancaBranding {
        prefs.edit { remove(KEY_LOGO_URI) }
        return getBranding()
    }

    companion object {
        private const val PREFS_NAME = "lotterynet_branding_v1"
        private const val KEY_LOGO_URI = "banca_logo_uri"
    }
}
