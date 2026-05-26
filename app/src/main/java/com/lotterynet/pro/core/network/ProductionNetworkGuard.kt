package com.lotterynet.pro.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object ProductionNetworkGuard {
    const val NO_INTERNET_ACTION_MESSAGE = "Sin internet. No se puede ejecutar esta opción."

    fun canRunCriticalOperation(hasValidatedInternet: Boolean): Boolean {
        return hasValidatedInternet
    }

    fun hasValidatedInternet(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
