package com.lotterynet.pro.core.catalog

import com.lotterynet.pro.core.model.LotteryCalendarRule
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryPlayCapabilities
import com.lotterynet.pro.core.model.LotterySchedule
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.repository.LotteryCatalogRepository
import java.text.Normalizer
import java.util.Locale

class StaticLotteryCatalogRepository : LotteryCatalogRepository {
    override fun getAllLotteries(): List<LotteryCatalogItem> = lotteries

    override fun getLotteryById(id: String): LotteryCatalogItem? {
        return lotteries.firstOrNull { it.id == id }
    }

    override fun getLotteryByName(name: String): LotteryCatalogItem? {
        val target = normalize(name)
        val simplifiedTarget = simplifyCatalogLookup(target)
        val aliasId = resolveCatalogAliasId(target)
        if (aliasId != null) return getLotteryById(aliasId)
        return lotteries.firstOrNull { normalize(it.name) == target }
            ?: lotteries.firstOrNull { simplifyCatalogLookup(normalize(it.name)) == simplifiedTarget }
    }

    override fun getCalendarRule(): LotteryCalendarRule = calendarRule

    companion object {
        internal val logosById: Map<String, String> = mapOf(
            "1" to "lot-logos/1.png",
            "2" to "lot-logos/2.png",
            "3" to "lot-logos/3.png",
            "4" to "lot-logos/4.png",
            "5" to "lot-logos/5.png",
            "6" to "lot-logos/6.png",
            "7" to "lot-logos/7.png",
            "8" to "lot-logos/8.png",
            "9" to "lot-logos/9.png",
            "10" to "lot-logos/10.png",
            "11" to "lot-logos/11.png",
            "12" to "lot-logos/12.png",
            "13" to "lot-logos/13.png",
            "14" to "lot-logos/14.png",
            "15" to "lot-logos/15.png",
            "16" to "lot-logos/16.png",
            "17" to "lot-logos/17.png",
            "18" to "lot-logos/18.png",
            "19" to "lot-logos/19.svg",
            "20" to "lot-logos/20.svg",
            "21" to "lot-logos/21.svg",
            "22" to "lot-logos/22.svg",
            "23" to "lot-logos/23.png",
            "24" to "lot-logos/24.png",
            "25" to "lot-logos/25.png",
            "26" to "lot-logos/26.png",
            "27" to "lot-logos/haiti_bolet.svg",
            "28" to "lot-logos/haiti_bolet.svg",
            "29" to "lot-logos/2.png",
            "30" to "lot-logos/2.png",
            "31" to "lot-logos/2.png",
            "32" to "lot-logos/2.png",
            "33" to "lot-logos/2.png",
            "34" to "lot-logos/2.png",
            "35" to "lot-logos/2.png",
            "36" to "lot-logos/2.png",
            "37" to "lot-logos/2.png",
            "38" to "lot-logos/2.png",
            "39" to "lot-logos/2.png",
            "40" to "lot-logos/haiti_bolet.svg",
            "41" to "lot-logos/haiti_bolet.svg",
            "42" to "lot-logos/haiti_bolet.svg",
            "43" to "lot-logos/haiti_bolet.svg",
            "44" to "lot-logos/georgia.svg",
            "45" to "lot-logos/georgia.svg",
            "46" to "lot-logos/georgia.svg",
        )

        private val pickCapabilities = LotteryPlayCapabilities(
            supportsStraight = true,
            supportsBox = true,
        )

        private val classicCapabilities = LotteryPlayCapabilities(
            supportsQuiniela = true,
            supportsPale = true,
            supportsTripleta = true,
            supportsSuperPale = true,
        )

        private val dynamicUsPickLotteries = buildDynamicUsPickLotteries()
        private val sundayClosedUsPickIds = setOf(
            "US-P3-AR-CASH-3-MIDDAY",
            "US-P3-SC-PICK-3-MIDDAY",
            "US-P3-TX-PICK-3-DAY",
            "US-P3-TX-PICK-3-EVENING",
            "US-P3-TX-PICK-3-MORNING",
            "US-P3-TX-PICK-3-NIGHT",
            "US-P3-WV-DAILY-3-DAY",
            "US-P4-AR-CASH-4-MIDDAY",
            "US-P4-SC-PICK-4-MIDDAY",
            "US-P4-TN-CASH-4-DAY",
            "US-P4-TN-CASH-4-MORNING",
            "US-P4-TX-DAILY-4-DAY",
            "US-P4-TX-DAILY-4-EVENING",
            "US-P4-TX-DAILY-4-MORNING",
            "US-P4-TX-DAILY-4-NIGHT",
            "US-P4-WV-DAILY-4-DAY",
        )
        private val americanLotteryIds = setOf("6", "8", "17", "18", "19", "20", "21", "22", "25", "26", "44", "45", "46") +
            dynamicUsPickLotteries.map { it.id }
        private val dominicanLotteryIds = setOf(
            "1", "2", "3", "4", "5", "7", "9", "10", "11", "12", "13", "14", "15", "16",
            "27", "28", "29", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39",
            "40", "41", "42", "43",
        )

        private val lotteries: List<LotteryCatalogItem> = listOf(
            lot("1", "La Primera Día", "Primera", "12:00 PM", "11:55", "#3b82f6"),
            lot("2", "Anguila 10AM", "Anguila", "10:00 AM", "09:55", "#06b6d4"),
            lot("3", "La Suerte 12:30", "Suerte", "12:30 PM", "12:25", "#8b5cf6"),
            lot("4", "Anguila Mediodía", "Anguila", "1:00 PM", "12:55", "#0891b2"),
            lot("5", "Quiniela Real", "Real", "12:55 PM", "12:50", "#10b981"),
            lot("6", "Florida Día", "Florida", "1:30 PM", "13:25", "#f59e0b", territory = LotteryTerritory.USA),
            lot("7", "Quiniela LoteDom", "LoteDom", "12:00 PM", "11:55", "#f97316"),
            lot("8", "New York Tarde", "NY", "2:30 PM", "14:25", "#1e40af", territory = LotteryTerritory.USA),
            lot(
                "9",
                "Gana Más",
                "Nacional",
                "2:40 PM",
                "14:35",
                "#ef4444",
                usesExplicitCloseTime = true,
            ),
            lot("10", "La Suerte Tarde", "Suerte", "6:00 PM", "17:55", "#7c3aed"),
            lot("11", "Anguila 6PM", "Anguila", "6:00 PM", "17:55", "#0284c7"),
            lot("12", "Loteka", "Loteka", "7:55 PM", "19:55", "#ec4899", usesExplicitCloseTime = true),
            lot("13", "Lotería Nacional", "Nacional", "9:00 PM", "20:55", "#dc2626", sundayOverride = LotterySchedule("6:00 PM", "17:55")),
            lot("14", "Anguila 9PM", "Anguila", "9:00 PM", "20:55", "#0369a1"),
            lot("15", "Leidsa", "Leidsa", "8:55 PM", "20:50", "#b91c1c", sundayOverride = LotterySchedule("3:50 PM", "15:50")),
            lot("16", "Primera Noche", "Primera", "7:00 PM", "19:00", "#1d4ed8"),
            lot("17", "Florida Noche", "Florida", "9:45 PM", "21:40", "#d97706", territory = LotteryTerritory.USA),
            lot("18", "New York Noche", "NY", "10:30 PM", "22:25", "#1e3a8a", territory = LotteryTerritory.USA),
            lot("19", "NJ Pick 3 Dia", "Pick3", "12:59 PM", "12:50 PM", "#0ea5e9", territory = LotteryTerritory.USA, playCapabilities = pickCapabilities, standardTimeOverride = LotterySchedule("1:59 PM", "1:50 PM")),
            lot("20", "NJ Pick 3 Noche", "Pick3", "10:57 PM", "10:50 PM", "#0284c7", territory = LotteryTerritory.USA, playCapabilities = pickCapabilities, standardTimeOverride = LotterySchedule("11:57 PM", "11:50 PM")),
            lot("21", "NJ Pick 4 Dia", "Pick4", "12:59 PM", "12:50 PM", "#16a34a", territory = LotteryTerritory.USA, playCapabilities = pickCapabilities, standardTimeOverride = LotterySchedule("1:59 PM", "1:50 PM")),
            lot("22", "NJ Pick 4 Noche", "Pick4", "10:57 PM", "10:50 PM", "#15803d", territory = LotteryTerritory.USA, playCapabilities = pickCapabilities, standardTimeOverride = LotterySchedule("11:57 PM", "11:50 PM")),
            lot("23", "King Lottery Día", "King", "12:30 PM", "12:25", "#7e22ce"),
            lot("24", "King Lottery Noche", "King", "7:30 PM", "19:25", "#6b21a8"),
            lot("25", "New Jersey Tarde", "NJ", "12:59 PM", "12:59 PM", "#0f766e", territory = LotteryTerritory.USA, standardTimeOverride = LotterySchedule("1:59 PM", "1:59 PM")),
            lot("26", "New Jersey Noche", "NJ", "10:57 PM", "10:57 PM", "#115e59", territory = LotteryTerritory.USA, standardTimeOverride = LotterySchedule("11:57 PM", "11:57 PM")),
            lot("27", "Haiti Bolet 11:30 AM", "Haiti", "11:30 AM", "11:25", "#2563eb"),
            lot("28", "Haiti Bolet 6:30 PM", "Haiti", "6:30 PM", "18:25", "#1d4ed8"),
            lot("29", "Anguilla 8AM", "Anguila", "8:00 AM", "08:00", "#06b6d4"),
            lot("30", "Anguilla 9AM", "Anguila", "9:00 AM", "09:00", "#06b6d4"),
            lot("31", "Anguilla 11AM", "Anguila", "11:00 AM", "11:00", "#06b6d4"),
            lot("32", "Anguilla 12PM", "Anguila", "12:00 PM", "12:00", "#06b6d4"),
            lot("33", "Anguilla 2PM", "Anguila", "2:00 PM", "14:00", "#0284c7"),
            lot("34", "Anguilla 3PM", "Anguila", "3:00 PM", "15:00", "#0284c7"),
            lot("35", "Anguilla 4PM", "Anguila", "4:00 PM", "16:00", "#0284c7"),
            lot("36", "Anguilla 5PM", "Anguila", "5:00 PM", "17:00", "#0284c7"),
            lot("37", "Anguilla 7PM", "Anguila", "7:00 PM", "19:00", "#0369a1"),
            lot("38", "Anguilla 8PM", "Anguila", "8:00 PM", "20:00", "#0369a1"),
            lot("39", "Anguilla 10PM", "Anguila", "10:00 PM", "22:00", "#0369a1"),
            lot("40", "Haiti Bolet 9:30 AM", "Haiti", "9:30 AM", "09:30", "#2563eb"),
            lot("41", "Haiti Bolet 10:30 AM", "Haiti", "10:30 AM", "10:30", "#2563eb"),
            lot("42", "Haiti Bolet 5:30 PM", "Haiti", "5:30 PM", "17:30", "#1d4ed8"),
            lot("43", "Haiti Bolet 7:30 PM", "Haiti", "7:30 PM", "19:30", "#1d4ed8"),
            lot("44", "Georgia Día", "Georgia", "12:29 PM", "12:29 PM", "#dc2626", territory = LotteryTerritory.USA),
            lot("45", "Georgia Tarde", "Georgia", "6:59 PM", "6:59 PM", "#b91c1c", territory = LotteryTerritory.USA),
            lot("46", "Georgia Noche", "Georgia", "11:34 PM", "11:34 PM", "#7f1d1d", territory = LotteryTerritory.USA),
        ) + dynamicUsPickLotteries

        private val calendarRule = LotteryCalendarRule(
            noDrawDatesByLottery = mapOf(
                "2026-04-02" to setOf("9", "13"),
            ),
            noDrawAllDates = emptySet(),
            holidayDisabledDates = emptyMap(),
            holidayAllDisabledDates = emptySet(),
            dayDisabledByWeekday = mapOf(
                0 to sundayClosedUsPickIds,
            ),
            americanLotteryIds = americanLotteryIds,
            dominicanLotteryIds = dominicanLotteryIds,
        )

        private fun lot(
            id: String,
            name: String,
            type: String,
            drawTime: String,
            closeTime: String,
            colorHex: String,
            territory: LotteryTerritory = LotteryTerritory.RD,
            playCapabilities: LotteryPlayCapabilities = if (type == "Pick3" || type == "Pick4") pickCapabilities else classicCapabilities,
            sundayOverride: LotterySchedule? = null,
            standardTimeOverride: LotterySchedule? = null,
            usesExplicitCloseTime: Boolean = false,
        ): LotteryCatalogItem {
            return LotteryCatalogItem(
                id = id,
                name = name,
                type = type,
                baseDrawTime = drawTime,
                baseCloseTime = closeTime,
                colorHex = colorHex,
                logoAssetPath = logosById[id],
                territory = territory,
                playCapabilities = playCapabilities,
                sundayOverride = sundayOverride,
                standardTimeOverride = standardTimeOverride,
                usesExplicitCloseTime = usesExplicitCloseTime,
            )
        }

        private data class UsPickDrawSpec(
            val id: String,
            val name: String,
            val type: String,
        )

        private fun usPickDrawSpecs(): List<UsPickDrawSpec> = listOf(
            UsPickDrawSpec("US-P3-AR-CASH-3-EVENING", "Arkansas Cash 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-AR-CASH-3-MIDDAY", "Arkansas Cash 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-AZ-PICK-3-DRAW", "Arizona Pick 3 Draw", "Pick3"),
            UsPickDrawSpec("US-P3-CA-PICK-3-EVENING", "California Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-CA-PICK-3-MIDDAY", "California Pick 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-CO-PICK-3-EVENING", "Colorado Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-CO-PICK-3-MIDDAY", "Colorado Pick 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-CT-PLAY3-DAY", "Connecticut Play3 Day Draw", "Pick3"),
            UsPickDrawSpec("US-P3-CT-PLAY3-NIGHT", "Connecticut Play3 Night Draw", "Pick3"),
            UsPickDrawSpec("US-P3-DC-3-EVENING", "Washington DC 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-DC-3-MIDDAY", "Washington DC 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-DE-PLAY-3-DAY", "Delaware Play 3 Day Draw", "Pick3"),
            UsPickDrawSpec("US-P3-DE-PLAY-3-NIGHT", "Delaware Play 3 Night Draw", "Pick3"),
            UsPickDrawSpec("US-P3-FL-PICK-3-EVENING", "Florida Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-FL-PICK-3-MIDDAY", "Florida Pick 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-GA-PICK-3-EVENING", "Georgia Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-GA-PICK-3-MIDDAY", "Georgia Pick 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-IA-PICK-3-EVENING", "Iowa Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-IA-PICK-3-MIDDAY", "Iowa Pick 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-ID-PICK-3-DAY", "Idaho Pick 3 Day Draw", "Pick3"),
            UsPickDrawSpec("US-P3-ID-PICK-3-NIGHT", "Idaho Pick 3 Night Draw", "Pick3"),
            UsPickDrawSpec("US-P3-IL-PICK-3-EVENING", "Illinois Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-IL-PICK-3-MIDDAY", "Illinois Pick 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-IN-DAILY-3-EVENING", "Indiana Daily 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-IN-DAILY-3-MIDDAY", "Indiana Daily 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-KS-PICK-3-EVENING", "Kansas Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-KS-PICK-3-MIDDAY", "Kansas Pick 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-KY-PICK-3-EVENING", "Kentucky Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-KY-PICK-3-MIDDAY", "Kentucky Pick 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-LA-PICK-3-DAY", "Louisiana Pick 3 Day Draw", "Pick3"),
            UsPickDrawSpec("US-P3-MD-PICK-3-EVENING", "Maryland Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-MD-PICK-3-MIDDAY", "Maryland Pick 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-ME-PICK-3-DAY", "Maine Pick 3 Day Draw", "Pick3"),
            UsPickDrawSpec("US-P3-ME-PICK-3-EVENING", "Maine Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-MI-DAILY-3-EVENING", "Michigan Daily 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-MI-DAILY-3-MIDDAY", "Michigan Daily 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-MN-PICK-3-DAY", "Minnesota Pick 3 Day Draw", "Pick3"),
            UsPickDrawSpec("US-P3-MO-PICK-3-EVENING", "Missouri Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-MO-PICK-3-MIDDAY", "Missouri Pick 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-MS-CASH-3-EVENING", "Mississippi Cash 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-MS-CASH-3-MIDDAY", "Mississippi Cash 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-NC-PICK-3-EVENING", "North Carolina Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-NE-PICK-3-DAY", "Nebraska Pick 3 Day Draw", "Pick3"),
            UsPickDrawSpec("US-P3-NM-PICK-3-PLUS-EVENING", "New Mexico Pick 3 Plus Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-NY-NUMBERS-EVENING", "New York Numbers Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-NY-NUMBERS-MIDDAY", "New York Numbers Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-NY-PICK-3-EVENING", "New York Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-NY-PICK-3-MIDDAY", "New York Pick 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-OH-PICK-3-EVENING", "Ohio Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-OH-PICK-3-MIDDAY", "Ohio Pick 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-OK-PICK-3-DAY", "Oklahoma Pick 3 Day Draw", "Pick3"),
            UsPickDrawSpec("US-P3-PA-PICK-3-EVENING", "Pennsylvania Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-SC-PICK-3-EVENING", "South Carolina Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-SC-PICK-3-MIDDAY", "South Carolina Pick 3 Midday Draw", "Pick3"),
            UsPickDrawSpec("US-P3-TN-CASH-3-06-28-PM", "Tennessee Cash 3 06:28 PM Draw", "Pick3"),
            UsPickDrawSpec("US-P3-TX-PICK-3-DAY", "Texas Pick 3 Day Draw", "Pick3"),
            UsPickDrawSpec("US-P3-TX-PICK-3-EVENING", "Texas Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-TX-PICK-3-MORNING", "Texas Pick 3 Morning Draw", "Pick3"),
            UsPickDrawSpec("US-P3-TX-PICK-3-NIGHT", "Texas Pick 3 Night Draw", "Pick3"),
            UsPickDrawSpec("US-P3-VA-PICK-3-DAY", "Virginia Pick 3 Day Draw", "Pick3"),
            UsPickDrawSpec("US-P3-VA-PICK-3-NIGHT", "Virginia Pick 3 Night Draw", "Pick3"),
            UsPickDrawSpec("US-P3-VT-PICK-3-EVENING", "Vermont Pick 3 Evening Draw", "Pick3"),
            UsPickDrawSpec("US-P3-WA-PICK-3-DAY", "Washington Pick 3 Day Draw", "Pick3"),
            UsPickDrawSpec("US-P3-WI-PICK-3-1-30PM", "Wisconsin Pick 3 1:30PM Draw", "Pick3"),
            UsPickDrawSpec("US-P3-WI-PICK-3-9-00PM", "Wisconsin Pick 3 9:00PM Draw", "Pick3"),
            UsPickDrawSpec("US-P3-WV-DAILY-3-DAY", "West Virginia Daily 3 Day Draw", "Pick3"),
            UsPickDrawSpec("US-P4-CA-DAILY-4-DAY", "California Daily 4 Day Draw", "Pick4"),
            UsPickDrawSpec("US-P4-AR-CASH-4-MIDDAY", "Arkansas Cash 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-CT-PLAY-4-DAY", "Connecticut Play 4 Day Draw", "Pick4"),
            UsPickDrawSpec("US-P4-CT-PLAY-4-EVENING", "Connecticut Play 4 Evening Draw", "Pick4"),
            UsPickDrawSpec("US-P4-DC-MATCH-4-MIDDAY", "Washington DC Match 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-DE-PLAY-4-MIDDAY", "Delaware Play 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-FL-PICK-4-EVENING", "Florida Pick 4 Evening Draw", "Pick4"),
            UsPickDrawSpec("US-P4-FL-PICK-4-MIDDAY", "Florida Pick 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-GA-CASH-4-MIDDAY", "Georgia Cash 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-IA-PICK-4-MIDDAY", "Iowa Pick 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-ID-PICK-4-MIDDAY", "Idaho Pick 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-IL-PICK-4-EVENING", "Illinois Pick 4 Evening Draw", "Pick4"),
            UsPickDrawSpec("US-P4-IL-PICK-4-MORNING", "Illinois Pick 4 Morning Draw", "Pick4"),
            UsPickDrawSpec("US-P4-IN-DAILY-4-EVENING", "Indiana Daily 4 Evening Draw", "Pick4"),
            UsPickDrawSpec("US-P4-IN-DAILY-4-MIDDAY", "Indiana Daily 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-KY-PICK-4-MIDDAY", "Kentucky Pick 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-LA-PICK-4-DAY", "Louisiana Pick 4 Day Draw", "Pick4"),
            UsPickDrawSpec("US-P4-MA-PICK-4-MIDDAY", "Massachusetts Pick 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-MD-PICK-4-MIDDAY", "Maryland Pick 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-ME-PICK-4-MIDDAY", "Maine Pick 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-MI-DAILY-4-MIDDAY", "Michigan Daily 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-MO-PICK-4-DAY", "Missouri Pick 4 Day Draw", "Pick4"),
            UsPickDrawSpec("US-P4-MO-PICK-4-EVENING", "Missouri Pick 4 Evening Draw", "Pick4"),
            UsPickDrawSpec("US-P4-MS-CASH-4-EVENING", "Mississippi Cash 4 Evening Draw", "Pick4"),
            UsPickDrawSpec("US-P4-MS-CASH-4-MIDDAY", "Mississippi Cash 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-NC-PICK-4-EVENING", "North Carolina Pick 4 Evening Draw", "Pick4"),
            UsPickDrawSpec("US-P4-NC-PICK-4-MIDDAY", "North Carolina Pick 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-NE-PICK-4-DAY", "Nebraska Pick 4 Day Draw", "Pick4"),
            UsPickDrawSpec("US-P4-NH-PICK-4-MIDDAY", "New Hampshire Pick 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-NM-PICK-4-PLUS-MIDDAY", "New Mexico Pick 4 Plus Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-NY-WIN-4-MIDDAY", "New York Win 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-OH-PICK-4-MIDDAY", "Ohio Pick 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-OR-PICK-4-EVENING", "Oregon Pick 4 Evening Draw", "Pick4"),
            UsPickDrawSpec("US-P4-PA-PICK-4-DAY", "Pennsylvania Pick 4 Day Draw", "Pick4"),
            UsPickDrawSpec("US-P4-PA-PICK-4-EVENING", "Pennsylvania Pick 4 Evening Draw", "Pick4"),
            UsPickDrawSpec("US-P4-RI-PICK-4-MIDDAY", "Rhode Island Pick 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-SC-PICK-4-EVENING", "South Carolina Pick 4 Evening Draw", "Pick4"),
            UsPickDrawSpec("US-P4-SC-PICK-4-MIDDAY", "South Carolina Pick 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-TN-CASH-4-DAY", "Tennessee Cash 4 Day Draw", "Pick4"),
            UsPickDrawSpec("US-P4-TN-CASH-4-EVENING", "Tennessee Cash 4 Evening Draw", "Pick4"),
            UsPickDrawSpec("US-P4-TN-CASH-4-MORNING", "Tennessee Cash 4 Morning Draw", "Pick4"),
            UsPickDrawSpec("US-P4-TX-DAILY-4-DAY", "Texas Daily 4 Day Draw", "Pick4"),
            UsPickDrawSpec("US-P4-TX-DAILY-4-EVENING", "Texas Daily 4 Evening Draw", "Pick4"),
            UsPickDrawSpec("US-P4-TX-DAILY-4-MORNING", "Texas Daily 4 Morning Draw", "Pick4"),
            UsPickDrawSpec("US-P4-TX-DAILY-4-NIGHT", "Texas Daily 4 Night Draw", "Pick4"),
            UsPickDrawSpec("US-P4-VA-PICK-4-EVENING", "Virginia Pick 4 Evening Draw", "Pick4"),
            UsPickDrawSpec("US-P4-VA-PICK-4-MIDDAY", "Virginia Pick 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-VT-PICK-4-MIDDAY", "Vermont Pick 4 Midday Draw", "Pick4"),
            UsPickDrawSpec("US-P4-WV-DAILY-4-DAY", "West Virginia Daily 4 Day Draw", "Pick4"),
            UsPickDrawSpec("US-P4-WI-PICK-4-MIDDAY", "Wisconsin Pick 4 Midday Draw", "Pick4"),
        )

        private fun buildDynamicUsPickLotteries(): List<LotteryCatalogItem> {
            return usPickDrawSpecs().map { spec ->
            val stateCode = spec.id.uppercase(Locale.US).split("-").getOrNull(2)
                ?.lowercase(Locale.US)
                .orEmpty()
                val schedule = UsPickScheduleResolver.resolve(spec.id, spec.name)
                val drawTime = schedule?.drawTime ?: inferDynamicPickDrawTime(spec.id, spec.name)
                val logoFolder = if (spec.type == "Pick4") "pick4" else "pick3"
                LotteryCatalogItem(
                    id = spec.id,
                    name = spec.name.removeSuffix(" Draw"),
                    type = spec.type,
                    baseDrawTime = drawTime,
                    baseCloseTime = closeTimeBeforeDraw(drawTime),
                    colorHex = if (spec.type == "Pick4") "#16a34a" else "#0ea5e9",
                    logoAssetPath = "lot-logos/us-pick/$logoFolder/$stateCode.svg",
                    territory = LotteryTerritory.USA,
                    timeZoneId = schedule?.timeZoneId,
                    playCapabilities = pickCapabilities,
                )
            }.sortedWith(compareBy<LotteryCatalogItem>(
                { parseCatalogClockMinutes(it.baseDrawTime) },
                { it.name.lowercase(Locale.US) },
                { it.id },
            ))
        }

        private fun inferDynamicPickDrawTime(id: String, name: String): String {
            val text = "$id $name".uppercase(Locale.US)
            Regex("""(\d{1,2})-(\d{2})-?(AM|PM)""").find(text)?.let { match ->
                return "${match.groupValues[1].toInt()}:${match.groupValues[2]} ${match.groupValues[3]}"
            }
            Regex("""(\d{1,2}):(\d{2})\s*(AM|PM)""").find(text)?.let { match ->
                return "${match.groupValues[1].toInt()}:${match.groupValues[2]} ${match.groupValues[3]}"
            }
            return when {
                "MORNING" in text -> "10:00 AM"
                "MIDDAY" in text || hasPickDrawToken(text, "DIA") -> "1:00 PM"
                "EVENING" in text || "TARDE" in text -> "7:00 PM"
                "NIGHT" in text || "NOCHE" in text -> "11:00 PM"
                hasPickDrawToken(text, "DAY") -> "1:00 PM"
                else -> "11:00 PM"
            }
        }

        private fun hasPickDrawToken(text: String, token: String): Boolean {
            return Regex("""(^|[^A-Z0-9])${Regex.escape(token)}([^A-Z0-9]|$)""").containsMatchIn(text)
        }

        private fun closeTimeBeforeDraw(drawTime: String): String {
            val minutes = parseCatalogClockMinutes(drawTime)
            if (minutes == Int.MAX_VALUE) return drawTime
            val adjusted = (minutes - 5).floorMod(24 * 60)
            val hour24 = adjusted / 60
            val minute = adjusted % 60
            val suffix = if (hour24 >= 12) "PM" else "AM"
            val hour12 = when (val normalized = hour24 % 12) {
                0 -> 12
                else -> normalized
            }
            return "%d:%02d %s".format(Locale.US, hour12, minute, suffix)
        }

        private fun parseCatalogClockMinutes(raw: String): Int {
            val match = Regex("""(\d{1,2}):(\d{2})(?:\s*(AM|PM))?""")
                .find(raw.trim().uppercase(Locale.US))
                ?: return Int.MAX_VALUE
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val suffix = match.groupValues.getOrNull(3).orEmpty()
            if (suffix == "AM" && hour == 12) hour = 0
            if (suffix == "PM" && hour < 12) hour += 12
            return hour * 60 + minute
        }

        private fun Int.floorMod(divisor: Int): Int {
            val result = this % divisor
            return if (result < 0) result + divisor else result
        }

        private fun normalize(value: String): String {
            val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            return normalized.lowercase(Locale.ROOT).trim()
        }

        private fun resolveCatalogAliasId(normalized: String): String? {
            if ("primera" in normalized) {
                return when {
                    "noche" in normalized || "night" in normalized || "7:00" in normalized || "700" in normalized -> "16"
                    "manana" in normalized || "mañana" in normalized || "dia" in normalized ||
                        "day" in normalized || "12:00" in normalized || "1200" in normalized -> "1"
                    else -> null
                }
            }
            if ("king" in normalized && "lottery" in normalized) {
                return when {
                    "12:30" in normalized || "1230" in normalized || "12 30" in normalized ||
                        "dia" in normalized || "day" in normalized || "midday" in normalized -> "23"
                    "7:30" in normalized || "730" in normalized || "7 30" in normalized ||
                        "noche" in normalized || "night" in normalized || "evening" in normalized -> "24"
                    else -> null
                }
            }
            if ("haiti" in normalized && "bolet" in normalized) {
                return when {
                    "9:30" in normalized || "930" in normalized || "9 30" in normalized -> "40"
                    "10:30" in normalized || "1030" in normalized || "10 30" in normalized -> "41"
                    "11:30" in normalized || "1130" in normalized || "11 30" in normalized -> "27"
                    "5:30" in normalized || "530" in normalized || "5 30" in normalized -> "42"
                    "6:30" in normalized || "630" in normalized || "6 30" in normalized -> "28"
                    "7:30" in normalized || "730" in normalized || "7 30" in normalized -> "43"
                    else -> null
                }
            }
            if ("anguilla" in normalized || "anguila" in normalized) {
                return when {
                    "8am" in normalized || "8 am" in normalized || "8:00" in normalized || "800" in normalized -> "29"
                    "9am" in normalized || "9 am" in normalized || "9:00" in normalized || "900" in normalized -> "30"
                    "10am" in normalized || "10 am" in normalized || "10:00" in normalized || "1000" in normalized ||
                        "manana" in normalized -> "2"
                    "11am" in normalized || "11 am" in normalized || "11:00" in normalized || "1100" in normalized -> "31"
                    "12pm" in normalized || "12 pm" in normalized || "12:00" in normalized || "1200" in normalized -> "32"
                    "1pm" in normalized || "1 pm" in normalized || "1:00" in normalized || "100" in normalized ||
                        "medio dia" in normalized || "mediodia" in normalized -> "4"
                    "2pm" in normalized || "2 pm" in normalized || "2:00" in normalized || "200" in normalized -> "33"
                    "3pm" in normalized || "3 pm" in normalized || "3:00" in normalized || "300" in normalized -> "34"
                    "4pm" in normalized || "4 pm" in normalized || "4:00" in normalized || "400" in normalized -> "35"
                    "5pm" in normalized || "5 pm" in normalized || "5:00" in normalized || "500" in normalized -> "36"
                    "6pm" in normalized || "6 pm" in normalized || "6:00" in normalized || "600" in normalized ||
                        "tarde" in normalized -> "11"
                    "7pm" in normalized || "7 pm" in normalized || "7:00" in normalized || "700" in normalized -> "37"
                    "8pm" in normalized || "8 pm" in normalized || "8:00" in normalized || "800" in normalized -> "38"
                    "9pm" in normalized || "9 pm" in normalized || "9:00" in normalized || "900" in normalized ||
                        "noche" in normalized -> "14"
                    "10pm" in normalized || "10 pm" in normalized || "10:00" in normalized || "1000" in normalized -> "39"
                    else -> null
                }
            }
            if ("georgia" in normalized) {
                return when {
                    "dia" in normalized || "day" in normalized -> "44"
                    "tarde" in normalized || "evening" in normalized -> "45"
                    "noche" in normalized || "night" in normalized -> "46"
                    else -> null
                }
            }
            return null
        }

        private fun simplifyCatalogLookup(normalized: String): String {
            return normalized
                .replace(Regex("""\bdraw\b"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }
    }
}
