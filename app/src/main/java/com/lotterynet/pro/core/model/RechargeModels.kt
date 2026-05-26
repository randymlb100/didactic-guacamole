package com.lotterynet.pro.core.model

data class RechargeRecord(
    val id: String,
    val providerId: String? = null,
    val providerName: String? = null,
    val phoneNumber: String? = null,
    val amount: Double = 0.0,
    val productType: String = "recarga",
    val status: String = "pending",
    val providerReference: String? = null,
    val userId: String? = null,
    val userName: String? = null,
    val adminId: String? = null,
    val adminUser: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)
