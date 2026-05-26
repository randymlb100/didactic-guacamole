package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.model.RechargeRecord
import com.lotterynet.pro.core.storage.LocalRechargeRepository

data class NativeRechargeCloudSyncResult(
    val ok: Boolean,
    val message: String,
    val pushedCount: Int = 0,
    val pulledCount: Int = 0,
)

class NativeRechargeCloudSyncCoordinator(
    private val rechargeRepository: LocalRechargeRepository,
    private val remoteStore: NativeRechargeRemoteStore = NativeRechargeRemoteStore(),
) {
    fun refreshOwnerFromRealtime(ownerKey: String): NativeRechargeCloudSyncResult {
        return hydrateOwner(ownerKey)
    }

    fun saveAndFlush(record: RechargeRecord, ownerKey: String): NativeRechargeCloudSyncResult {
        rechargeRepository.saveRecharge(record)
        return flushOwner(ownerKey)
    }

    fun hydrateOwner(ownerKey: String): NativeRechargeCloudSyncResult {
        return runCatching {
            val normalizedOwner = ownerKey.trim()
            if (normalizedOwner.isBlank()) {
                return NativeRechargeCloudSyncResult(false, "No hay banca/admin para sincronizar recargas.")
            }
            val remoteRecharges = remoteStore.fetchRecharges(normalizedOwner)
            if (remoteRecharges.isNotEmpty()) {
                rechargeRepository.replaceScopedImportedRecharges(normalizedOwner, remoteRecharges)
            }
            val flush = flushOwner(normalizedOwner)
            NativeRechargeCloudSyncResult(
                ok = flush.ok,
                message = if (flush.ok) "Recargas sincronizadas con servidor." else flush.message,
                pushedCount = flush.pushedCount,
                pulledCount = remoteRecharges.size,
            )
        }.getOrElse { error ->
            NativeRechargeCloudSyncResult(false, error.message ?: "No se pudo cargar recargas del servidor.")
        }
    }

    fun flushOwner(ownerKey: String): NativeRechargeCloudSyncResult {
        return runCatching {
            val normalizedOwner = ownerKey.trim()
            if (normalizedOwner.isBlank()) {
                return NativeRechargeCloudSyncResult(false, "No hay banca/admin para subir recargas.")
            }
            val localRecharges = rechargeRepository.getRechargesForOwner(normalizedOwner)
            val remoteRecharges = remoteStore.fetchRecharges(normalizedOwner)
            val merged = mergeRechargesPreferImported(
                existing = remoteRecharges,
                imported = localRecharges,
            )
            if (merged.isNotEmpty()) {
                rechargeRepository.replaceScopedImportedRecharges(normalizedOwner, merged)
                remoteStore.upsertRecharges(normalizedOwner, merged)
            }
            NativeRechargeCloudSyncResult(
                ok = true,
                message = "Recargas subidas y conciliadas con servidor.",
                pushedCount = localRecharges.size,
                pulledCount = remoteRecharges.size,
            )
        }.getOrElse { error ->
            NativeRechargeCloudSyncResult(false, error.message ?: "No se pudo sincronizar recargas.")
        }
    }
}
