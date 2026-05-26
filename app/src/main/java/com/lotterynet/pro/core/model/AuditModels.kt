package com.lotterynet.pro.core.model

data class AuditEntry(
    val timestampLabel: String,
    val user: String,
    val role: String,
    val action: String,
    val detail: String,
)
