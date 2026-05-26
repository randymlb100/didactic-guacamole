package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.model.PrizeTableConfig
import com.lotterynet.pro.core.model.normalizedPrizeTableConfig
import org.json.JSONObject

class LocalPrizeConfigRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PrizeConfigStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun getConfig(): PrizeTableConfig {
        val raw = prefs.getString(PrizeConfigStorageKeys.PRIZE_CONFIG_KEY, null) ?: return PrizeTableConfig()
        return runCatching {
            val json = JSONObject(raw)
            sanitize(
                PrizeTableConfig(
                    q1 = json.optInt("q1", 60),
                    q2 = json.optInt("q2", 12),
                    q3 = json.optInt("q3", 4),
                    pale = json.optInt("p", PrizeTableConfig().pale),
                    pale12 = json.optInt("pale12", json.optInt("p12", json.optInt("p", PrizeTableConfig().pale))),
                    pale13 = json.optInt("pale13", json.optInt("p13", json.optInt("p", PrizeTableConfig().pale))),
                    pale23 = json.optInt("pale23", json.optInt("p23", json.optInt("p", PrizeTableConfig().pale))),
                    tripleta = json.optInt("t", 20000),
                    tripleta3 = json.optInt("tripleta3", json.optInt("t3", json.optInt("t", 20000))),
                    tripleta2 = json.optInt("tripleta2", json.optInt("t2", 1000)),
                    superPale = json.optInt("sp", 3000),
                    pick3Straight = json.optInt("p3", 500),
                    pick3Box3 = json.optInt("p3box3", 160),
                    pick3Box6 = json.optInt("p3box6", 80),
                    pick4Straight = json.optInt("p4", 5000),
                    pick4Box4 = json.optInt("p4box4", 1200),
                    pick4Box6 = json.optInt("p4box6", 800),
                    pick4Box12 = json.optInt("p4box12", 400),
                    pick4Box24 = json.optInt("p4box24", 200),
                    pick3BackPair = json.optInt("p3b", 50),
                    pick4BackPair = json.optInt("p4b", 50),
                ),
            )
        }.getOrDefault(PrizeTableConfig())
    }

    fun saveConfig(config: PrizeTableConfig) {
        val safe = sanitize(config)
        prefs.edit {
            putString(
                PrizeConfigStorageKeys.PRIZE_CONFIG_KEY,
                JSONObject().apply {
                    put("q1", safe.q1)
                    put("q2", safe.q2)
                    put("q3", safe.q3)
                    put("p", safe.pale)
                    put("pale12", safe.pale12)
                    put("pale13", safe.pale13)
                    put("pale23", safe.pale23)
                    put("t", safe.tripleta)
                    put("tripleta3", safe.tripleta3)
                    put("tripleta2", safe.tripleta2)
                    put("sp", safe.superPale)
                    put("p3", safe.pick3Straight)
                    put("p3box3", safe.pick3Box3)
                    put("p3box6", safe.pick3Box6)
                    put("p4", safe.pick4Straight)
                    put("p4box4", safe.pick4Box4)
                    put("p4box6", safe.pick4Box6)
                    put("p4box12", safe.pick4Box12)
                    put("p4box24", safe.pick4Box24)
                    put("p3b", safe.pick3BackPair)
                    put("p4b", safe.pick4BackPair)
                }.toString(),
            )
        }
    }

    fun applyDefaultConfig(): PrizeTableConfig {
        val defaults = PrizeTableConfig()
        saveConfig(defaults)
        return defaults
    }

    private fun sanitize(value: PrizeTableConfig): PrizeTableConfig {
        val normalized = value.copy(
            q1 = value.q1.positiveOr(60),
            q2 = value.q2.positiveOr(12),
            q3 = value.q3.positiveOr(4),
            pale = value.pale.positiveOr(PrizeTableConfig().pale),
            pale12 = value.pale12.positiveOr(value.pale.positiveOr(PrizeTableConfig().pale)),
            pale13 = value.pale13.positiveOr(value.pale.positiveOr(PrizeTableConfig().pale)),
            pale23 = value.pale23.positiveOr(value.pale.positiveOr(PrizeTableConfig().pale)),
            tripleta = value.tripleta.positiveOr(20000),
            tripleta3 = value.tripleta3.positiveOr(value.tripleta.positiveOr(20000)),
            tripleta2 = value.tripleta2.positiveOr(1000),
            superPale = value.superPale.positiveOr(3000),
            pick3Straight = value.pick3Straight.positiveOr(500),
            pick3Box3 = value.pick3Box3.positiveOr(160),
            pick3Box6 = value.pick3Box6.positiveOr(80),
            pick4Straight = value.pick4Straight.positiveOr(5000),
            pick4Box4 = value.pick4Box4.positiveOr(1200),
            pick4Box6 = value.pick4Box6.positiveOr(800),
            pick4Box12 = value.pick4Box12.positiveOr(400),
            pick4Box24 = value.pick4Box24.positiveOr(200),
            pick3BackPair = value.pick3BackPair.positiveOr(50),
            pick4BackPair = value.pick4BackPair.positiveOr(50),
        )
        return normalized.normalizedPrizeTableConfig().copy(
            q1 = if (normalized.q1 == 80) 60 else normalized.q1,
            tripleta = if (normalized.tripleta == 5000) 20000 else normalized.tripleta,
            superPale = if (normalized.superPale == 8000) 3000 else normalized.superPale,
        )
    }

    private fun Int.positiveOr(fallback: Int): Int = takeIf { it > 0 } ?: fallback
}

private object PrizeConfigStorageKeys {
    const val PREFS_NAME = "lotterynet_prize_config"
    const val PRIZE_CONFIG_KEY = "admin_prize_config"
}
