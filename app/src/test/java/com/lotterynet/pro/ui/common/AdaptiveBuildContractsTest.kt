package com.lotterynet.pro.ui.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdaptiveBuildContractsTest {

    @Test
    fun `android build stays ready for target sdk 36 adaptive layouts`() {
        val appBuild = File("build.gradle.kts").readText()

        assertTrue(appBuild.contains("compileSdk = 36"))
        assertTrue(appBuild.contains("targetSdk = 36"))
        assertTrue(appBuild.contains("androidx.compose.material3:material3-window-size-class"))
        assertTrue(appBuild.contains("androidx.compose.material3.adaptive:adaptive:"))
        assertTrue(appBuild.contains("androidx.compose.material3.adaptive:adaptive-layout:"))
        assertTrue(appBuild.contains("androidx.compose.material3.adaptive:adaptive-navigation:"))
    }
}
