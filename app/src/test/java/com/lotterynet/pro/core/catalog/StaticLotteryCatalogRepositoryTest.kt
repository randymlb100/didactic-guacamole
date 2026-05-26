package com.lotterynet.pro.core.catalog

import com.lotterynet.pro.ui.results.noDrawLotteryIdsForResultDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StaticLotteryCatalogRepositoryTest {
    private val catalog = StaticLotteryCatalogRepository()

    @Test
    fun `catalog keeps EnLoteria aligned evening draw times`() {
        val primeraNoche = catalog.getLotteryById("16") ?: error("Missing Primera Noche")
        val loteka = catalog.getLotteryById("12") ?: error("Missing Loteka")
        val leidsa = catalog.getLotteryById("15") ?: error("Missing Leidsa")
        val ganaMas = catalog.getLotteryById("9") ?: error("Missing Gana Más")

        assertEquals("7:00 PM", primeraNoche.baseDrawTime)
        assertEquals("19:00", primeraNoche.baseCloseTime)
        assertEquals("7:55 PM", loteka.baseDrawTime)
        assertEquals("19:55", loteka.baseCloseTime)
        assertEquals("3:50 PM", leidsa.sundayOverride?.drawTime)
        assertEquals("15:50", leidsa.sundayOverride?.closeTime)
        assertEquals("2:40 PM", ganaMas.baseDrawTime)
        assertEquals("14:35", ganaMas.baseCloseTime)
        assertEquals(null, ganaMas.sundayOverride)
    }

    @Test
    fun `catalog includes EnLoteria extra three number lotteries for sale and results`() {
        val expected = listOf(
            "Anguilla 8AM",
            "Anguilla 9AM",
            "Anguilla 11AM",
            "Anguilla 12PM",
            "Anguilla 2PM",
            "Anguilla 3PM",
            "Anguilla 4PM",
            "Anguilla 5PM",
            "Anguilla 7PM",
            "Anguilla 8PM",
            "Anguilla 10PM",
            "Haiti Bolet 9:30 AM",
            "Haiti Bolet 10:30 AM",
            "Haiti Bolet 5:30 PM",
            "Haiti Bolet 7:30 PM",
            "Georgia Día",
            "Georgia Tarde",
            "Georgia Noche",
        )

        expected.forEach { name ->
            val lottery = catalog.getLotteryByName(name)
            assertNotNull("Missing $name", lottery)
            assertNotNull("Missing logo for $name", lottery?.logoAssetPath)
        }
    }

    @Test
    fun `catalog resolves remote result aliases to sale lottery rows`() {
        val expected = mapOf(
            "Anguilla 10AM" to "2",
            "Anguila Manana" to "2",
            "Anguila Mediodia" to "4",
            "Anguilla 1PM" to "4",
            "Anguila Tarde" to "11",
            "Anguilla 6PM" to "11",
            "Anguila Noche" to "14",
            "Anguilla 9PM" to "14",
            "King Lottery 12:30" to "23",
            "King Lottery Midday" to "23",
            "King Lottery 7:30" to "24",
            "King Lottery Evening" to "24",
            "Haiti Bolet 6:30 PM" to "28",
            "Georgia Day" to "44",
            "Georgia Evening" to "45",
            "Georgia Night" to "46",
        )

        expected.forEach { (name, id) ->
            assertEquals(name, id, catalog.getLotteryByName(name)?.id)
        }
    }

    @Test
    fun `catalog exposes bundled us pick states for sale selector`() {
        val picks = catalog.getAllLotteries().filter { it.id.startsWith("US-P") }
        val salePickLotteries = catalog.getAllLotteries().filter { it.playCapabilities.supportsStraight && it.playCapabilities.supportsBox }
        val floridaPick3Midday = catalog.getLotteryById("US-P3-FL-PICK-3-MIDDAY")
        val floridaPick3Evening = catalog.getLotteryById("US-P3-FL-PICK-3-EVENING")
        val georgiaPick4Midday = catalog.getLotteryById("US-P4-GA-CASH-4-MIDDAY")
        val indianaPick4Midday = catalog.getLotteryById("US-P4-IN-DAILY-4-MIDDAY")
        val indianaPick4Evening = catalog.getLotteryById("US-P4-IN-DAILY-4-EVENING")
        val texasPick4Day = catalog.getLotteryById("US-P4-TX-DAILY-4-DAY")
        val texasPick4Night = catalog.getLotteryById("US-P4-TX-DAILY-4-NIGHT")
        val newJerseyPick3Day = catalog.getLotteryById("19")
        val newJerseyPick3Night = catalog.getLotteryById("20")
        val newJerseyPick4Day = catalog.getLotteryById("21")
        val newJerseyPick4Night = catalog.getLotteryById("22")

        val californiaDaily4Day = catalog.getLotteryById("US-P4-CA-DAILY-4-DAY")
        val louisianaPick4Day = catalog.getLotteryById("US-P4-LA-PICK-4-DAY")
        val nebraskaPick4Day = catalog.getLotteryById("US-P4-NE-PICK-4-DAY")
        val westVirginiaDaily3Day = catalog.getLotteryById("US-P3-WV-DAILY-3-DAY")
        val westVirginiaDaily4Day = catalog.getLotteryById("US-P4-WV-DAILY-4-DAY")

        assertEquals("Expected all Render Pick variants plus legacy NJ day/night rows", 120, salePickLotteries.size)
        assertTrue("Expected draw-level Pick catalog, not one generic row per state", picks.size >= 115)
        assertTrue(
            "Every remote US Pick must carry a verified source timezone",
            picks.all { it.timeZoneId != null },
        )
        assertEquals("Florida Pick 3 Midday", floridaPick3Midday?.name)
        assertEquals("1:30 PM", floridaPick3Midday?.baseDrawTime)
        assertEquals("1:25 PM", floridaPick3Midday?.baseCloseTime)
        assertEquals("Florida Pick 3 Evening", floridaPick3Evening?.name)
        assertEquals("9:45 PM", floridaPick3Evening?.baseDrawTime)
        assertEquals("lot-logos/us-pick/pick3/fl.svg", floridaPick3Midday?.logoAssetPath)
        assertEquals("Georgia Cash 4 Midday", georgiaPick4Midday?.name)
        assertEquals("lot-logos/us-pick/pick4/ga.svg", georgiaPick4Midday?.logoAssetPath)
        assertEquals("1:20 PM", indianaPick4Midday?.baseDrawTime)
        assertEquals("11:00 PM", indianaPick4Evening?.baseDrawTime)
        assertEquals("12:27 PM", texasPick4Day?.baseDrawTime)
        assertEquals("10:12 PM", texasPick4Night?.baseDrawTime)
        assertEquals("America/Chicago", texasPick4Night?.timeZoneId)
        assertEquals("California Daily 4 Day", californiaDaily4Day?.name)
        assertEquals("6:30 PM", californiaDaily4Day?.baseDrawTime)
        assertEquals("6:25 PM", californiaDaily4Day?.baseCloseTime)
        assertEquals("America/Los_Angeles", californiaDaily4Day?.timeZoneId)
        assertEquals("Louisiana Pick 4 Day", louisianaPick4Day?.name)
        assertEquals("Nebraska Pick 4 Day", nebraskaPick4Day?.name)
        assertEquals("West Virginia Daily 3 Day", westVirginiaDaily3Day?.name)
        assertEquals("West Virginia Daily 4 Day", westVirginiaDaily4Day?.name)
        assertEquals("NJ Pick 3 Dia", newJerseyPick3Day?.name)
        assertEquals("NJ Pick 3 Noche", newJerseyPick3Night?.name)
        assertEquals("NJ Pick 4 Dia", newJerseyPick4Day?.name)
        assertEquals("NJ Pick 4 Noche", newJerseyPick4Night?.name)
        assertEquals(null, catalog.getLotteryById("US-P3-FL"))
        assertEquals(null, catalog.getLotteryById("US-P4-GA"))
    }

    @Test
    fun `us pick schedule resolver tolerates malformed remote result names`() {
        val noisyName = "Remote Pick ${"x".repeat(16_000)} \u0000 \uD83D\uDE80"

        assertEquals(null, UsPickScheduleResolver.resolve("US-P3-ZZ-REMOTE", noisyName))
        assertEquals("1:30 PM", UsPickScheduleResolver.resolve("US-P3-FL-PICK-3-MIDDAY", noisyName)?.drawTime)
    }

    @Test
    fun `us pick schedule resolver lets id period win over noisy remote name`() {
        val schedule = UsPickScheduleResolver.resolve(
            id = "US-P3-FL-PICK-3-MIDDAY",
            name = "Florida Pick 3 Morning Remote Result",
        )

        assertEquals("1:30 PM", schedule?.drawTime)
        assertEquals("America/New_York", schedule?.timeZoneId)
    }

    @Test
    fun `us pick schedule resolver keeps critical backend parity rows`() {
        val expected = mapOf(
            "US-P3-NJ-PICK-3-MIDDAY" to ("12:59 PM" to "America/New_York"),
            "US-P3-NJ-PICK-3-EVENING" to ("10:57 PM" to "America/New_York"),
            "US-P3-ME-PICK-3-DAY" to ("1:10 PM" to "America/New_York"),
            "US-P3-NY-PICK-3-DAY" to ("2:30 PM" to "America/New_York"),
            "US-P3-FL-PICK-3-EVENING" to ("9:45 PM" to "America/New_York"),
            "US-P4-TX-DAILY-4-NIGHT" to ("10:12 PM" to "America/Chicago"),
            "US-P4-CA-DAILY-4-EVENING" to ("6:30 PM" to "America/Los_Angeles"),
        )

        expected.forEach { (id, schedulePair) ->
            val schedule = UsPickScheduleResolver.resolve(id, id)
            assertEquals("draw time for $id", schedulePair.first, schedule?.drawTime)
            assertEquals("timezone for $id", schedulePair.second, schedule?.timeZoneId)
        }
    }

    @Test
    fun `sunday calendar disables known no draw pick states`() {
        val disabled = noDrawLotteryIdsForResultDate(catalog.getCalendarRule(), "10-05-2026")

        assertTrue(disabled.contains("US-P3-AR-CASH-3-MIDDAY"))
        assertTrue(disabled.contains("US-P4-AR-CASH-4-MIDDAY"))
        assertTrue(disabled.contains("US-P3-SC-PICK-3-MIDDAY"))
        assertTrue(disabled.contains("US-P4-SC-PICK-4-MIDDAY"))
        assertTrue(disabled.contains("US-P4-TN-CASH-4-MORNING"))
        assertTrue(disabled.contains("US-P4-TN-CASH-4-DAY"))
        assertTrue(disabled.contains("US-P3-TX-PICK-3-MORNING"))
        assertTrue(disabled.contains("US-P4-TX-DAILY-4-NIGHT"))
        assertTrue(disabled.contains("US-P3-WV-DAILY-3-DAY"))
        assertTrue(disabled.contains("US-P4-WV-DAILY-4-DAY"))
        assertTrue("NJ domingo sí debe seguir activo", !disabled.contains("US-P3-NJ-PICK-3-MIDDAY"))
        assertTrue("NJ noche domingo sí debe seguir activo", !disabled.contains("US-P4-NJ-PICK-4-EVENING"))
        assertTrue("Arkansas evening domingo sí debe seguir activo", !disabled.contains("US-P3-AR-CASH-3-EVENING"))
        assertTrue("South Carolina evening domingo sí debe seguir activo", !disabled.contains("US-P4-SC-PICK-4-EVENING"))
        assertTrue("Tennessee evening domingo sí debe seguir activo", !disabled.contains("US-P3-TN-CASH-3-06-28-PM"))
    }

    @Test
    fun `backend scraper sunday no draw rows stay aligned with android calendar`() {
        val scraperFile = listOf(
            File("scraper/scrape_and_save.py"),
            File("../scraper/scrape_and_save.py"),
        ).firstOrNull { it.exists() }
        assertNotNull("Missing scraper/scrape_and_save.py contract file", scraperFile)

        val scraperText = scraperFile!!.readText()
        val sundayBlock = Regex(
            "US_PICK_SUNDAY_NO_DRAW_ROWS\\s*=\\s*\\[(.*?)\\]",
            setOf(RegexOption.DOT_MATCHES_ALL),
        ).find(scraperText)?.groupValues?.get(1).orEmpty()
        val backendSundayClosedIds = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(sundayBlock)
            .map { it.groupValues[1] }
            .toSet()

        assertTrue("Backend Sunday no-draw list must not be empty", backendSundayClosedIds.isNotEmpty())

        val catalogPickIds = catalog.getAllLotteries()
            .filter { it.id.startsWith("US-P3-") || it.id.startsWith("US-P4-") || it.id in setOf("19", "20", "21", "22") }
            .map { it.id }
            .toSet()
        assertTrue(
            "Backend Sunday no-draw ids must exist in Android Pick catalog: ${backendSundayClosedIds - catalogPickIds}",
            catalogPickIds.containsAll(backendSundayClosedIds),
        )

        val androidSundayClosedIds = noDrawLotteryIdsForResultDate(catalog.getCalendarRule(), "10-05-2026")
            .filter { it.startsWith("US-P3-") || it.startsWith("US-P4-") }
            .toSet()
        assertEquals(
            "Backend scraper Sunday no-draw rows must match Android Pick calendar closures",
            androidSundayClosedIds,
            backendSundayClosedIds,
        )
    }
}
