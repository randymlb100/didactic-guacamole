package com.lotterynet.pro.core.model

data class DeletedUserRef(
    val id: String? = null,
    val user: String? = null,
    val adminId: String? = null,
    val adminUser: String? = null,
    val banca: String? = null,
    val deletedAtEpochMs: Long = 0L,
)

data class DeletedUsersState(
    val admins: List<DeletedUserRef> = emptyList(),
    val cashiers: List<DeletedUserRef> = emptyList(),
)
