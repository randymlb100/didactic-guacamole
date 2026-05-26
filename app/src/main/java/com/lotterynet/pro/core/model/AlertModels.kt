package com.lotterynet.pro.core.model

data class SystemAlert(
    val id: String,
    val timestampLabel: String,
    val type: String,
    val message: String,
    val level: String = "info",
    val read: Boolean = false,
)
