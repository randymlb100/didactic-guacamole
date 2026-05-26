package com.lotterynet.pro.ui.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class NativeOnlySourceContractsTest {
    @Test
    fun `production sources do not ship webview or legacy web assets`() {
        val projectRoot = File(System.getProperty("user.dir") ?: error("System property 'user.dir' is not set"))
        val appRoot = listOf(
            File(projectRoot, "app/src/main"),
            File(projectRoot, "src/main"),
        ).firstOrNull { it.isDirectory } ?: error("Cannot find app/src/main from ${projectRoot.absolutePath}")
        val productionFiles = appRoot.walkTopDown()
            .filter { it.isFile }
            .filterNot { it.invariantSeparatorsPath.contains("/assets/lot-logos/") }
            .filterNot { it.invariantSeparatorsPath.contains("/assets/pos-sfx/") }
            .toList()

        val forbidden = listOf(
            "WebView",
            "file:///android_asset/index.html",
            "allow_legacy_webview",
        )
        val offenders = productionFiles.flatMap { file ->
            val text = runCatching { file.readText() }.getOrDefault("")
            forbidden.filter { token -> text.contains(token, ignoreCase = true) }
                .map { token -> "${file.relativeTo(projectRoot).invariantSeparatorsPath}:$token" }
        }

        assertFalse(offenders.joinToString("\n"), offenders.isNotEmpty())
        assertFalse(File(appRoot, "assets/index.html").exists())
        assertFalse(File(appRoot, "assets/supabase-js-v2.min.js").exists())
        assertFalse(File(appRoot, "assets/qrcode.min.js").exists())
    }
}
