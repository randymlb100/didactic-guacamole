package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.model.ThermalPrinterPrefs
import com.lotterynet.pro.core.repository.ThermalPrinterRepository
import org.json.JSONObject

class LocalThermalPrinterRepository(
    context: Context,
) : ThermalPrinterRepository {
    private val prefs = context.getSharedPreferences(ThermalPrinterStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    override fun getPrefs(): ThermalPrinterPrefs {
        val raw = prefs.getString(ThermalPrinterStorageKeys.THERMAL_PREFS_KEY, null) ?: return ThermalPrinterPrefs()
        return ThermalPrinterPrefsCodec.decode(raw)
    }

    override fun savePrefs(prefs: ThermalPrinterPrefs) {
        this.prefs.edit {
            putString(
                ThermalPrinterStorageKeys.THERMAL_PREFS_KEY,
                ThermalPrinterPrefsCodec.encode(prefs),
            )
        }
    }

    override fun applyClassicPreset(): ThermalPrinterPrefs {
        val classic = ThermalPrinterPrefs(
            selectedPrinterAddress = getPrefs().selectedPrinterAddress,
            paperWidth = "58",
            fontFamily = "consolas",
            typeLabelMode = "single",
            widthMode = "standard",
            customChars = "32",
            density = "tight",
            separator = "short",
            headerScale = "compact",
            serialScale = "normal",
            itemScale = "compact",
            lotteryScale = "normal",
            playTypeScale = "compact",
            playNumberScale = "normal",
            amountScale = "normal",
            securityScale = "normal",
            totalScale = "normal",
            previewZoom = "100",
            showOriginal = true,
            showAddress = false,
            showPhone = true,
            showDateTime = true,
            showDrawTime = true,
            showSecurity = true,
            showFooter = false,
        )
        savePrefs(classic)
        return classic
    }
}

internal object ThermalPrinterPrefsCodec {
    fun decode(raw: String): ThermalPrinterPrefs {
        return runCatching {
            val json = JSONObject(raw)
            val legacyItemScale = oneOf(json.optString("itemScale", "compact"), thermalScales, "compact")
            ThermalPrinterPrefs(
                selectedPrinterAddress = json.optString("selectedPrinterAddress", ""),
                paperWidth = normalizePaperWidth(json.optString("paperWidth", "58")),
                fontFamily = normalizeFont(json.optString("fontFamily", "consolas")),
                typeLabelMode = oneOf(json.optString("typeLabelMode", "single"), setOf("single", "double", "full"), "single"),
                widthMode = oneOf(json.optString("widthMode", "standard"), setOf("narrow", "standard", "wide", "custom"), "standard"),
                customChars = normalizeChars(json.optString("customChars", "32")),
                density = oneOf(json.optString("density", "tight"), setOf("tight", "balanced", "airy"), "tight"),
                separator = oneOf(json.optString("separator", "short"), setOf("short", "full", "minimal"), "short"),
                headerScale = oneOf(json.optString("headerScale", "compact"), thermalScales, "compact"),
                serialScale = oneOf(json.optString("serialScale", "normal"), thermalScales, "normal"),
                itemScale = legacyItemScale,
                lotteryScale = oneOf(json.optString("lotteryScale", "normal"), thermalScales, "normal"),
                playTypeScale = oneOf(json.optString("playTypeScale", legacyItemScale), thermalScales, legacyItemScale),
                playNumberScale = oneOf(json.optString("playNumberScale", "normal"), thermalScales, "normal"),
                amountScale = oneOf(json.optString("amountScale", "normal"), thermalScales, "normal"),
                securityScale = oneOf(json.optString("securityScale", "normal"), thermalScales, "normal"),
                totalScale = oneOf(json.optString("totalScale", "normal"), thermalScales, "normal"),
                previewZoom = oneOf(json.optString("previewZoom", "100"), setOf("90", "100", "115", "130"), "100"),
                showOriginal = json.optBoolean("showOriginal", true),
                showAddress = json.optBoolean("showAddress", false),
                showPhone = json.optBoolean("showPhone", true),
                showDateTime = json.optBoolean("showDateTime", true),
                showDrawTime = json.optBoolean("showDrawTime", true),
                showSecurity = json.optBoolean("showSecurity", true),
                showFooter = json.optBoolean("showFooter", false),
            )
        }.getOrDefault(ThermalPrinterPrefs())
    }

    fun encode(value: ThermalPrinterPrefs): String {
        val safe = sanitize(value)
        return JSONObject().apply {
            put("selectedPrinterAddress", safe.selectedPrinterAddress)
            put("paperWidth", safe.paperWidth)
            put("fontFamily", safe.fontFamily)
            put("typeLabelMode", safe.typeLabelMode)
            put("widthMode", safe.widthMode)
            put("customChars", safe.customChars)
            put("density", safe.density)
            put("separator", safe.separator)
            put("headerScale", safe.headerScale)
            put("serialScale", safe.serialScale)
            put("itemScale", safe.itemScale)
            put("lotteryScale", safe.lotteryScale)
            put("playTypeScale", safe.playTypeScale)
            put("playNumberScale", safe.playNumberScale)
            put("amountScale", safe.amountScale)
            put("securityScale", safe.securityScale)
            put("totalScale", safe.totalScale)
            put("previewZoom", safe.previewZoom)
            put("showOriginal", safe.showOriginal)
            put("showAddress", safe.showAddress)
            put("showPhone", safe.showPhone)
            put("showDateTime", safe.showDateTime)
            put("showDrawTime", safe.showDrawTime)
            put("showSecurity", safe.showSecurity)
            put("showFooter", safe.showFooter)
        }.toString()
    }

    private val thermalScales = setOf("compact", "normal", "large")

    private fun sanitize(value: ThermalPrinterPrefs): ThermalPrinterPrefs {
        return value.copy(
            selectedPrinterAddress = value.selectedPrinterAddress.trim(),
            paperWidth = normalizePaperWidth(value.paperWidth),
            fontFamily = normalizeFont(value.fontFamily),
            typeLabelMode = oneOf(value.typeLabelMode, setOf("single", "double", "full"), "single"),
            widthMode = oneOf(value.widthMode, setOf("narrow", "standard", "wide", "custom"), "standard"),
            customChars = normalizeChars(value.customChars),
            density = oneOf(value.density, setOf("tight", "balanced", "airy"), "tight"),
            separator = oneOf(value.separator, setOf("short", "full", "minimal"), "short"),
            headerScale = oneOf(value.headerScale, thermalScales, "compact"),
            serialScale = oneOf(value.serialScale, thermalScales, "normal"),
            itemScale = oneOf(value.itemScale, thermalScales, "compact"),
            lotteryScale = oneOf(value.lotteryScale, thermalScales, "normal"),
            playTypeScale = oneOf(value.playTypeScale, thermalScales, "compact"),
            playNumberScale = oneOf(value.playNumberScale, thermalScales, "normal"),
            amountScale = oneOf(value.amountScale, thermalScales, "normal"),
            securityScale = oneOf(value.securityScale, thermalScales, "normal"),
            totalScale = oneOf(value.totalScale, thermalScales, "normal"),
            previewZoom = oneOf(value.previewZoom, setOf("90", "100", "115", "130"), "100"),
        )
    }

    private fun normalizePaperWidth(raw: String): String = if (raw == "80") "80" else "58"

    private fun normalizeFont(raw: String): String =
        oneOf(raw, setOf("consolas", "jetbrains", "courier"), "consolas")

    private fun normalizeChars(raw: String): String {
        val value = raw.toIntOrNull() ?: 32
        return value.coerceIn(24, 60).toString()
    }

    private fun oneOf(raw: String, allowed: Set<String>, fallback: String): String {
        return raw.takeIf { it in allowed } ?: fallback
    }
}
