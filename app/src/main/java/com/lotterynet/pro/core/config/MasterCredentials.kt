package com.lotterynet.pro.core.config

data class MasterCredentials(
    val masterUser: String,
    val masterSalt: String,
    val masterHash: String,
    val authHashVersion: String,
) {
    companion object {
        val DEFAULT = MasterCredentials(
            masterUser = "master",
            masterSalt = "lotterynet-master-v1",
            masterHash = "e3f47a15e241ff814b2c8aececb8c1d1e7c8c69a58daa2c58a7ad9d43339f78f",
            authHashVersion = "sha256-v1",
        )
    }
}
