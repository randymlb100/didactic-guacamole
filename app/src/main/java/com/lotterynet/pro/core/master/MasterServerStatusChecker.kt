package com.lotterynet.pro.core.master

import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MasterServerProbeResult(
    val ok: Boolean,
    val message: String,
    val detail: String,
)

class MasterServerStatusChecker {
    private val backendStore: MasterConfigRemoteStore = SupabaseMasterConfigRemoteStore()

    fun probe(): MasterServerProbeResult {
        return runCatching { runProbe() }.getOrElse { error ->
            when (error) {
                is SocketTimeoutException -> MasterServerProbeResult(
                    ok = false,
                    message = "El servidor tardó demasiado en responder.",
                    detail = "Timeout verificando Supabase.",
                )

                else -> MasterServerProbeResult(
                    ok = false,
                    message = error.message ?: "No se pudo verificar el servidor.",
                    detail = "Fallo verificando el backend remoto.",
                )
            }
        }
    }

    private fun runProbe(): MasterServerProbeResult {
        val startAt = System.currentTimeMillis()
        backendStore.probeAccess()
        val latencyMs = System.currentTimeMillis() - startAt

        val remoteStamp = backendStore.fetchUpdatedAt("sys_users_v4").orEmpty()
        val remoteLabel = remoteStamp.toHumanServerTime()

        val detail = buildString {
            append("Supabase accesible")
            append(" · Backend de configuración OK")
            append(" · ")
            append("Latencia ${latencyMs}ms")
            if (remoteLabel != null) {
                append(" · Última carga de usuarios $remoteLabel")
            }
        }
        return MasterServerProbeResult(
            ok = true,
            message = "Servidor disponible.",
            detail = detail,
        )
    }

    private fun String.toHumanServerTime(): String? {
        if (isBlank()) return null
        val parsed = runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US).parse(this)
                ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US).parse(this)
        }.getOrNull() ?: return take(19).replace('T', ' ')
        return SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).format(Date(parsed.time))
    }
}
