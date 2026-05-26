package com.lotterynet.pro.core.results

import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.repository.ResultsRepository
import com.lotterynet.pro.core.sync.SyncFreshnessKey
import com.lotterynet.pro.core.sync.SyncFreshnessRecord
import com.lotterynet.pro.core.sync.SyncFreshnessRepository
import com.lotterynet.pro.core.sync.SyncFreshnessState
import com.lotterynet.pro.core.sync.SyncFreshnessType
import com.lotterynet.pro.core.sync.SyncGovernor
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultsScraperOrchestratorTest {

    @Test
    fun `refresh checks remote when cached classic result is missing third prize`() {
        val repository = FakeResultsRepository(
            initial = listOf(
                LotteryResult(
                    lotteryId = "13",
                    lotteryName = "Lotería Nacional",
                    date = "26-04-2026",
                    first = "22",
                    second = "54",
                    third = null,
                    source = "local",
                    fetchedAtEpochMs = 2000L,
                ),
            ),
        )
        val remote = FakeRemoteStore(
            results = listOf(
                LotteryResult(
                    lotteryId = "13",
                    lotteryName = "Lotería Nacional",
                    date = "26-04-2026",
                    first = "22",
                    second = "54",
                    third = "29",
                    source = "supabase",
                    fetchedAtEpochMs = 1000L,
                ),
            ),
        )

        val result = ResultsScraperOrchestrator(remote, repository).refreshDate(
            date = "26-04-2026",
            forceRemote = false,
        )

        assertEquals(1, remote.fetchCount)
        assertEquals("29", result.results.single().third)
        assertEquals("29", repository.saved.single().third)
    }

    @Test
    fun `refresh uses local when cached result is complete`() {
        val repository = FakeResultsRepository(
            initial = listOf(
                LotteryResult(
                    lotteryId = "13",
                    lotteryName = "Lotería Nacional",
                    date = "26-04-2026",
                    first = "22",
                    second = "54",
                    third = "29",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
                LotteryResult(
                    lotteryId = "23",
                    lotteryName = "King Lottery Día",
                    date = "26-04-2026",
                    status = RESULT_STATUS_NO_DRAW,
                    source = RESULT_STATUS_NO_DRAW,
                    fetchedAtEpochMs = 1_000L,
                ),
                LotteryResult(
                    lotteryId = "24",
                    lotteryName = "King Lottery Noche",
                    date = "26-04-2026",
                    status = RESULT_STATUS_NO_DRAW,
                    source = RESULT_STATUS_NO_DRAW,
                    fetchedAtEpochMs = 1_000L,
                ),
                LotteryResult(
                    lotteryId = "27",
                    lotteryName = "Haiti Bolet 11:30 AM",
                    date = "26-04-2026",
                    first = "18",
                    second = "24",
                    third = "67",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
                LotteryResult(
                    lotteryId = "28",
                    lotteryName = "Haiti Bolet 6:30 PM",
                    date = "26-04-2026",
                    first = "67",
                    second = "76",
                    third = "87",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
            ),
        )
        val remote = FakeRemoteStore(
            results = listOf(
                LotteryResult(
                    lotteryId = "13",
                    lotteryName = "Lotería Nacional",
                    date = "26-04-2026",
                    first = "23",
                    second = "55",
                    third = "30",
                    source = "supabase",
                    fetchedAtEpochMs = 70_000L,
                ),
            ),
        )

        val result = ResultsScraperOrchestrator(
            remoteStore = remote,
            localResultsRepository = repository,
            nowEpochMs = { 70_000L },
        ).refreshDate(date = "26-04-2026", forceRemote = false)

        assertEquals(0, remote.fetchCount)
        assertEquals("22", result.results.first { it.lotteryId == "13" }.first)
        assertEquals("local", result.source)
    }

    @Test
    fun `complete classic cache refreshes after freshness window`() {
        val repository = FakeResultsRepository(
            initial = completeTrackedResults(date = "26-04-2026"),
        )
        val remote = FakeRemoteStore(
            results = listOf(
                LotteryResult(
                    lotteryId = "13",
                    lotteryName = "Lotería Nacional",
                    date = "26-04-2026",
                    first = "99",
                    second = "88",
                    third = "77",
                    source = "supabase",
                    fetchedAtEpochMs = 2_000_000L,
                ),
            ),
        )
        val freshnessKey = SyncFreshnessKey(
            type = SyncFreshnessType.RESULTS,
            ownerKey = "admin-1",
            banca = "Banca",
            dateKey = "26-04-2026",
        )
        val freshness = FakeFreshnessRepository(
            record = SyncFreshnessRecord(
                state = SyncFreshnessState.SERVER_UPDATED,
                updatedAtEpochMs = 1_000L,
            ),
        )

        val result = ResultsScraperOrchestrator(
            remoteStore = remote,
            localResultsRepository = repository,
            freshnessRepository = freshness,
            freshnessKeyFactory = { freshnessKey },
            nowEpochMs = { 2_000_000L },
        ).refreshDate(date = "26-04-2026", forceRemote = false)

        assertEquals(1, remote.fetchCount)
        assertEquals("99", result.results.first { it.lotteryId == "13" }.first)
        assertEquals("local+supabase", result.source)
    }

    @Test
    fun `fresh complete classic cache is reused briefly`() {
        val repository = FakeResultsRepository(
            initial = completeTrackedResults(date = "26-04-2026"),
        )
        val remote = FakeRemoteStore(
            results = listOf(
                LotteryResult(
                    lotteryId = "13",
                    lotteryName = "Lotería Nacional",
                    date = "26-04-2026",
                    first = "99",
                    second = "88",
                    third = "77",
                    source = "supabase",
                    fetchedAtEpochMs = 12_000L,
                ),
            ),
        )
        val freshnessKey = SyncFreshnessKey(
            type = SyncFreshnessType.RESULTS,
            ownerKey = "admin-1",
            banca = "Banca",
            dateKey = "26-04-2026",
        )
        val freshness = FakeFreshnessRepository(
            record = SyncFreshnessRecord(
                state = SyncFreshnessState.SERVER_UPDATED,
                updatedAtEpochMs = 10_000L,
            ),
        )

        val result = ResultsScraperOrchestrator(
            remoteStore = remote,
            localResultsRepository = repository,
            freshnessRepository = freshness,
            freshnessKeyFactory = { freshnessKey },
            nowEpochMs = { 12_000L },
        ).refreshDate(date = "26-04-2026", forceRemote = false)

        assertEquals(0, remote.fetchCount)
        assertEquals("22", result.results.first { it.lotteryId == "13" }.first)
        assertEquals("local", result.source)
    }

    @Test
    fun `fresh classic cache missing expected id checks server instead of reusing partial local`() {
        val repository = FakeResultsRepository(
            initial = listOf(
                LotteryResult(
                    lotteryId = "13",
                    lotteryName = "Lotería Nacional",
                    date = "26-04-2026",
                    first = "22",
                    second = "54",
                    third = "29",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
                LotteryResult(
                    lotteryId = "23",
                    lotteryName = "King Lottery Día",
                    date = "26-04-2026",
                    status = RESULT_STATUS_NO_DRAW,
                    source = RESULT_STATUS_NO_DRAW,
                    fetchedAtEpochMs = 1_000L,
                ),
                LotteryResult(
                    lotteryId = "24",
                    lotteryName = "King Lottery Noche",
                    date = "26-04-2026",
                    status = RESULT_STATUS_NO_DRAW,
                    source = RESULT_STATUS_NO_DRAW,
                    fetchedAtEpochMs = 1_000L,
                ),
                LotteryResult(
                    lotteryId = "27",
                    lotteryName = "Haiti Bolet 11:30 AM",
                    date = "26-04-2026",
                    first = "18",
                    second = "24",
                    third = "67",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
                LotteryResult(
                    lotteryId = "28",
                    lotteryName = "Haiti Bolet 6:30 PM",
                    date = "26-04-2026",
                    first = "67",
                    second = "76",
                    third = "87",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
            ),
        )
        val remote = FakeRemoteStore(
            results = listOf(
                LotteryResult(
                    lotteryId = "17",
                    lotteryName = "Florida Noche",
                    date = "26-04-2026",
                    first = "10",
                    second = "20",
                    third = "30",
                    source = "supabase",
                    fetchedAtEpochMs = 12_000L,
                ),
            ),
        )
        val freshnessKey = SyncFreshnessKey(
            type = SyncFreshnessType.RESULTS,
            ownerKey = "admin-1",
            banca = "Banca",
            dateKey = "26-04-2026",
        )
        val freshness = FakeFreshnessRepository(
            record = SyncFreshnessRecord(
                state = SyncFreshnessState.SERVER_UPDATED,
                updatedAtEpochMs = 10_000L,
            ),
        )

        val result = ResultsScraperOrchestrator(
            remoteStore = remote,
            localResultsRepository = repository,
            freshnessRepository = freshness,
            freshnessKeyFactory = { freshnessKey },
            expectedResultIdsProvider = { setOf("13", "17", "23", "24", "27", "28") },
            nowEpochMs = { 12_000L },
        ).refreshDate(date = "26-04-2026", forceRemote = false)

        assertEquals(1, remote.fetchCount)
        assertEquals(setOf("13", "17", "23", "24", "27", "28"), result.results.map { it.lotteryId }.toSet())
        assertEquals("local+supabase", result.source)
    }

    @Test
    fun `complete classic cache without server marker refreshes and preserves rows`() {
        val repository = FakeResultsRepository(
            initial = completeTrackedResults(date = "26-04-2026"),
        )
        val remote = FakeRemoteStore(
            results = listOf(
                LotteryResult(
                    lotteryId = "17",
                    lotteryName = "Florida Noche",
                    date = "26-04-2026",
                    first = "10",
                    second = "20",
                    third = "30",
                    source = "supabase",
                    fetchedAtEpochMs = 2_000L,
                ),
            ),
        )
        val freshnessKey = SyncFreshnessKey(
            type = SyncFreshnessType.RESULTS,
            ownerKey = "admin-1",
            banca = "Banca",
            dateKey = "26-04-2026",
        )

        val result = ResultsScraperOrchestrator(
            remoteStore = remote,
            localResultsRepository = repository,
            freshnessRepository = FakeFreshnessRepository(record = null),
            freshnessKeyFactory = { freshnessKey },
            nowEpochMs = { 20_000L },
        ).refreshDate(date = "26-04-2026", forceRemote = false)

        assertEquals(1, remote.fetchCount)
        assertEquals(
            completeTrackedResults("26-04-2026").map { it.lotteryId }.toSet() + "17",
            result.results.map { it.lotteryId }.toSet(),
        )
    }

    @Test
    fun `manual refresh for past date checks remote without live scrape`() {
        val repository = FakeResultsRepository(
            initial = listOf(
                LotteryResult(
                    lotteryId = "13",
                    lotteryName = "Lotería Nacional",
                    date = "26-04-2026",
                    first = "22",
                    second = "54",
                    third = "29",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
            ),
        )
        val remote = FakeRemoteStore(
            results = listOf(
                LotteryResult(
                    lotteryId = "13",
                    lotteryName = "Lotería Nacional",
                    date = "26-04-2026",
                    first = "23",
                    second = "55",
                    third = "30",
                    source = "supabase",
                    fetchedAtEpochMs = 70_000L,
                ),
            ),
        )

        val result = ResultsScraperOrchestrator(
            remoteStore = remote,
            localResultsRepository = repository,
            todayDateProvider = { "12-05-2026" },
        ).refreshDate(date = "26-04-2026", forceRemote = true, allowLive = false)

        assertEquals(1, remote.fetchCount)
        assertEquals(listOf(false), remote.forceLiveRequests)
        assertEquals("23", result.results.single().first)
        assertEquals("local+supabase", result.source)
    }

    @Test
    fun `missing expected pick coverage asks render for live refresh`() {
        val repository = FakeResultsRepository(
            initial = listOf(
                LotteryResult(
                    lotteryId = "US-P3-IN-DAILY-3-EVENING",
                    lotteryName = "Indiana Daily 3 Evening Draw",
                    date = "09-05-2026",
                    pick3 = "0-3-3",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
            ),
        )
        val remote = FakeRemoteStore(
            results = listOf(
                LotteryResult(
                    lotteryId = "US-P3-IN-DAILY-3-MIDDAY",
                    lotteryName = "Indiana Daily 3 Midday Draw",
                    date = "09-05-2026",
                    pick3 = "6-6-9",
                    source = "render-live",
                    fetchedAtEpochMs = 2_000L,
                ),
            ),
        )

        ResultsScraperOrchestrator(
            remote,
            repository,
            expectedResultIdsProvider = {
                setOf("US-P3-IN-DAILY-3-EVENING", "US-P3-IN-DAILY-3-MIDDAY")
            },
            todayDateProvider = { "09-05-2026" },
        ).refreshDate(date = "09-05-2026", forceRemote = false, allowLive = true)

        assertEquals(listOf(true), remote.forceLiveRequests)
    }

    @Test
    fun `missing expected pick coverage uses cached remote path for past date`() {
        val repository = FakeResultsRepository(
            initial = listOf(
                LotteryResult(
                    lotteryId = "US-P3-IN-DAILY-3-EVENING",
                    lotteryName = "Indiana Daily 3 Evening Draw",
                    date = "09-05-2026",
                    pick3 = "0-3-3",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
            ),
        )
        val remote = FakeRemoteStore(
            results = listOf(
                LotteryResult(
                    lotteryId = "US-P3-IN-DAILY-3-MIDDAY",
                    lotteryName = "Indiana Daily 3 Midday Draw",
                    date = "09-05-2026",
                    pick3 = "6-6-9",
                    source = "supabase",
                    fetchedAtEpochMs = 2_000L,
                ),
            ),
        )

        ResultsScraperOrchestrator(
            remote,
            repository,
            expectedResultIdsProvider = {
                setOf("US-P3-IN-DAILY-3-EVENING", "US-P3-IN-DAILY-3-MIDDAY")
            },
            todayDateProvider = { "12-05-2026" },
        ).refreshDate(date = "09-05-2026", forceRemote = false, allowLive = true)

        assertEquals(listOf(false), remote.forceLiveRequests)
    }

    @Test
    fun `no draw status is treated as a complete remote result`() {
        val repository = FakeResultsRepository(initial = emptyList())
        val remote = FakeRemoteStore(
            results = listOf(
                LotteryResult(
                    lotteryId = "23",
                    lotteryName = "King Lottery Día",
                    date = "01-05-2026",
                    status = RESULT_STATUS_NO_DRAW,
                    source = RESULT_STATUS_NO_DRAW,
                    fetchedAtEpochMs = 1_000L,
                ),
            ),
        )

        val result = ResultsScraperOrchestrator(remote, repository).refreshDate(
            date = "01-05-2026",
            forceRemote = false,
        )

        assertEquals(1, remote.fetchCount)
        assertEquals(RESULT_STATUS_NO_DRAW, result.results.single().status)
        assertEquals(RESULT_STATUS_NO_DRAW, repository.saved.single().status)
    }

    @Test
    fun `today no draw local marker is refreshed so late published result can replace it`() {
        val date = "17-05-2026"
        val repository = FakeResultsRepository(
            initial = listOf(
                LotteryResult(
                    lotteryId = "US-P4-CA-DAILY-4-DAY",
                    lotteryName = "California Daily 4 Day Draw",
                    date = date,
                    status = RESULT_STATUS_NO_DRAW,
                    source = RESULT_STATUS_NO_DRAW,
                    fetchedAtEpochMs = 1_000L,
                ),
            ),
        )
        val remote = FakeRemoteStore(
            results = listOf(
                LotteryResult(
                    lotteryId = "US-P4-CA-DAILY-4-DAY",
                    lotteryName = "California Daily 4 Day Draw",
                    date = date,
                    pick4 = "1-2-3-4",
                    source = "render-live",
                    fetchedAtEpochMs = 2_000L,
                ),
            ),
        )

        val result = ResultsScraperOrchestrator(
            remoteStore = remote,
            localResultsRepository = repository,
            expectedResultIdsProvider = { setOf("US-P4-CA-DAILY-4-DAY") },
            todayDateProvider = { date },
        ).refreshDate(date = date, forceRemote = false, allowLive = true)

        assertEquals(1, remote.fetchCount)
        assertEquals(listOf(true), remote.forceLiveRequests)
        assertEquals("1-2-3-4", result.results.single().pick4)
        assertEquals("1-2-3-4", repository.saved.single().pick4)
    }

    @Test
    fun `refresh checks remote when local cache is missing haiti bolet tracked ids`() {
        val repository = FakeResultsRepository(
            initial = listOf(
                LotteryResult(
                    lotteryId = "1",
                    lotteryName = "La Primera Día",
                    date = "30-04-2026",
                    first = "01",
                    second = "02",
                    third = "03",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
            ),
        )
        val remote = FakeRemoteStore(
            results = listOf(
                LotteryResult(
                    lotteryId = "27",
                    lotteryName = "Haiti Bolet 11:30 AM",
                    date = "30-04-2026",
                    first = "18",
                    second = "24",
                    third = "67",
                    source = "supabase",
                    fetchedAtEpochMs = 2_000L,
                ),
                LotteryResult(
                    lotteryId = "28",
                    lotteryName = "Haiti Bolet 6:30 PM",
                    date = "30-04-2026",
                    first = "67",
                    second = "76",
                    third = "87",
                    source = "supabase",
                    fetchedAtEpochMs = 2_000L,
                ),
            ),
        )

        val result = ResultsScraperOrchestrator(remote, repository).refreshDate(
            date = "30-04-2026",
            forceRemote = false,
        )

        assertEquals(1, remote.fetchCount)
        assertEquals(setOf("1", "27", "28"), result.results.map { it.lotteryId }.toSet())
        assertEquals(setOf("1", "27", "28"), repository.saved.map { it.lotteryId }.toSet())
    }

    @Test
    fun `pick cache without fresh server marker refreshes and preserves existing rows`() {
        val repository = FakeResultsRepository(
            initial = listOf(
                LotteryResult(
                    lotteryId = "US-P3-FL-PICK-3-MIDDAY",
                    lotteryName = "Florida Pick 3 Midday Draw",
                    date = "09-05-2026",
                    pick3 = "7-1-5",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
            ),
        )
        val remote = FakeRemoteStore(
            results = listOf(
                LotteryResult(
                    lotteryId = "US-P3-FL-PICK-3-EVENING",
                    lotteryName = "Florida Pick 3 Evening Draw",
                    date = "09-05-2026",
                    pick3 = "0-8-9",
                    source = "supabase",
                    fetchedAtEpochMs = 2_000L,
                ),
            ),
        )
        val freshnessKey = SyncFreshnessKey(SyncFreshnessType.RESULTS, "admin-1", "Banca", "09-05-2026")

        val result = ResultsScraperOrchestrator(
            remoteStore = remote,
            localResultsRepository = repository,
            freshnessRepository = FakeFreshnessRepository(record = null),
            freshnessKeyFactory = { freshnessKey },
            nowEpochMs = { 20_000L },
        ).refreshDate(date = "09-05-2026", forceRemote = false)

        assertEquals(1, remote.fetchCount)
        assertEquals(
            setOf("US-P3-FL-PICK-3-MIDDAY", "US-P3-FL-PICK-3-EVENING"),
            result.results.map { it.lotteryId }.toSet(),
        )
        assertEquals(result.results.map { it.lotteryId }.toSet(), repository.saved.map { it.lotteryId }.toSet())
    }

    @Test
    fun `fresh pick cache is reused briefly to avoid asking every app open`() {
        val repository = FakeResultsRepository(
            initial = listOf(
                LotteryResult(
                    lotteryId = "US-P3-FL-PICK-3-EVENING",
                    lotteryName = "Florida Pick 3 Evening Draw",
                    date = "09-05-2026",
                    pick3 = "0-8-9",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
            ),
        )
        val remote = FakeRemoteStore(results = emptyList())
        val freshnessKey = SyncFreshnessKey(SyncFreshnessType.RESULTS, "admin-1", "Banca", "09-05-2026")
        val freshness = FakeFreshnessRepository(
            record = SyncFreshnessRecord(
                state = SyncFreshnessState.SERVER_UPDATED,
                updatedAtEpochMs = 10_000L,
            ),
        )

        val result = ResultsScraperOrchestrator(
            remoteStore = remote,
            localResultsRepository = repository,
            freshnessRepository = freshness,
            freshnessKeyFactory = { freshnessKey },
            nowEpochMs = { 12_000L },
        ).refreshDate(date = "09-05-2026", forceRemote = false)

        assertEquals(0, remote.fetchCount)
        assertEquals("local", result.source)
        assertEquals("US-P3-FL-PICK-3-EVENING", result.results.single().lotteryId)
    }

    @Test
    fun `stale pick cache refreshes after freshness window`() {
        val repository = FakeResultsRepository(
            initial = listOf(
                LotteryResult(
                    lotteryId = "US-P3-FL-PICK-3-MIDDAY",
                    lotteryName = "Florida Pick 3 Midday Draw",
                    date = "09-05-2026",
                    pick3 = "7-1-5",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
            ),
        )
        val remote = FakeRemoteStore(
            results = listOf(
                LotteryResult(
                    lotteryId = "US-P3-FL-PICK-3-EVENING",
                    lotteryName = "Florida Pick 3 Evening Draw",
                    date = "09-05-2026",
                    pick3 = "0-8-9",
                    source = "supabase",
                    fetchedAtEpochMs = 2_000L,
                ),
            ),
        )
        val freshnessKey = SyncFreshnessKey(SyncFreshnessType.RESULTS, "admin-1", "Banca", "09-05-2026")
        val freshness = FakeFreshnessRepository(
            record = SyncFreshnessRecord(
                state = SyncFreshnessState.SERVER_UPDATED,
                updatedAtEpochMs = 10_000L,
            ),
        )

        val result = ResultsScraperOrchestrator(
            remoteStore = remote,
            localResultsRepository = repository,
            freshnessRepository = freshness,
            freshnessKeyFactory = { freshnessKey },
            nowEpochMs = { 10_000L + ResultsScraperOrchestrator.RESULTS_TODAY_REFRESH_WINDOW_MS + 1 },
        ).refreshDate(date = "09-05-2026", forceRemote = false)

        assertEquals(1, remote.fetchCount)
        assertEquals(2, result.results.size)
    }

    @Test
    fun `complete expected pick cache is reused without calling server again`() {
        val expectedIds = setOf(
            "US-P3-NY-NUMBERS-MIDDAY",
            "US-P3-NY-NUMBERS-EVENING",
        )
        val repository = FakeResultsRepository(
            initial = listOf(
                LotteryResult(
                    lotteryId = "US-P3-NY-NUMBERS-MIDDAY",
                    lotteryName = "New York Numbers Midday Draw",
                    date = "09-05-2026",
                    pick3 = "7-3-2",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
                LotteryResult(
                    lotteryId = "US-P3-NY-NUMBERS-EVENING",
                    lotteryName = "New York Numbers Evening Draw",
                    date = "09-05-2026",
                    pick3 = "7-1-7",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
            ),
        )
        val remote = FakeRemoteStore(results = emptyList())
        val freshnessKey = SyncFreshnessKey(SyncFreshnessType.RESULTS, "admin-1", "Banca", "09-05-2026")
        val freshness = FakeFreshnessRepository(
            record = SyncFreshnessRecord(
                state = SyncFreshnessState.SERVER_UPDATED,
                updatedAtEpochMs = 10_000L,
            ),
        )

        val result = ResultsScraperOrchestrator(
            remoteStore = remote,
            localResultsRepository = repository,
            freshnessRepository = freshness,
            freshnessKeyFactory = { freshnessKey },
            expectedResultIdsProvider = { expectedIds },
            nowEpochMs = { 10_000L + ResultsScraperOrchestrator.RESULTS_TODAY_REFRESH_WINDOW_MS + 1 },
        ).refreshDate(date = "09-05-2026", forceRemote = false)

        assertEquals(0, remote.fetchCount)
        assertEquals("local", result.source)
        assertEquals(expectedIds, result.results.map { it.lotteryId }.toSet())
    }

    @Test
    fun `complete expected pick cache is reused when local row has equivalent timed id`() {
        val repository = FakeResultsRepository(
            initial = listOf(
                LotteryResult(
                    lotteryId = "US-P4-FL-PICK-4-9-45-PM",
                    lotteryName = "Florida Pick 4",
                    date = "09-05-2026",
                    pick4 = "7-0-8-2",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
            ),
        )
        val remote = FakeRemoteStore(results = emptyList())

        val result = ResultsScraperOrchestrator(
            remoteStore = remote,
            localResultsRepository = repository,
            expectedResultIdsProvider = { setOf("P4|FL|21:45") },
        ).refreshDate(date = "09-05-2026", forceRemote = false)

        assertEquals(0, remote.fetchCount)
        assertEquals("local", result.source)
        assertEquals(listOf("US-P4-FL-PICK-4-9-45-PM"), result.results.map { it.lotteryId })
    }

    @Test
    fun `merge replaces incomplete timed pick with complete catalog equivalent row`() {
        val repository = FakeResultsRepository(
            initial = listOf(
                LotteryResult(
                    lotteryId = "US-P4-FL-PICK-4-9-45-PM",
                    lotteryName = "Florida Pick 4",
                    date = "09-05-2026",
                    source = "local",
                    fetchedAtEpochMs = 1_000L,
                ),
            ),
        )
        val remote = FakeRemoteStore(
            results = listOf(
                LotteryResult(
                    lotteryId = "US-P4-FL-PICK-4-EVENING",
                    lotteryName = "Florida Pick 4 Evening Draw",
                    date = "09-05-2026",
                    pick4 = "7-0-8-2",
                    source = "supabase",
                    fetchedAtEpochMs = 2_000L,
                ),
            ),
        )

        val result = ResultsScraperOrchestrator(
            remoteStore = remote,
            localResultsRepository = repository,
        ).refreshDate(date = "09-05-2026", forceRemote = true)

        assertEquals(listOf("US-P4-FL-PICK-4-EVENING"), result.results.map { it.lotteryId })
        assertEquals("7-0-8-2", result.results.single().pick4)
        assertEquals(listOf("US-P4-FL-PICK-4-EVENING"), repository.saved.map { it.lotteryId })
    }

    @Test
    fun `manual forced results refresh bypasses recent governor window`() {
        var nowMs = 1_000L
        val repository = FakeResultsRepository(initial = emptyList())
        val remote = FakeRemoteStore(
            results = listOf(
                LotteryResult(
                    lotteryId = "13",
                    lotteryName = "Lotería Nacional",
                    date = "09-05-2026",
                    first = "22",
                    second = "54",
                    third = "29",
                    source = "supabase",
                    fetchedAtEpochMs = 1_000L,
                ),
            ),
        )
        val governor = SyncGovernor(nowEpochMs = { nowMs })
        val orchestrator = ResultsScraperOrchestrator(
            remoteStore = remote,
            localResultsRepository = repository,
            syncGovernor = governor,
        )

        val first = orchestrator.refreshDateFromRealtime("09-05-2026")
        nowMs = 5_000L
        val second = orchestrator.refreshDate("09-05-2026", forceRemote = true)

        assertEquals("supabase", first.source)
        assertEquals("local+supabase", second.source)
        assertEquals(2, remote.fetchCount)
    }

    private class FakeResultsRepository(
        initial: List<LotteryResult>,
    ) : ResultsRepository {
        private var current = initial
        var saved: List<LotteryResult> = emptyList()
            private set

        override fun getResultsForDate(date: String): List<LotteryResult> = current

        override fun saveResultsForDate(date: String, results: List<LotteryResult>) {
            saved = results
            current = results
        }

        override fun clearResultsForDate(date: String) {
            current = emptyList()
        }
    }

    private class FakeRemoteStore(
        private val results: List<LotteryResult>,
    ) : ResultsRemoteStore {
        var fetchCount = 0
            private set
        val forceLiveRequests = mutableListOf<Boolean>()

        override fun fetchResultsForDate(date: String, forceLive: Boolean): List<LotteryResult> {
            fetchCount += 1
            forceLiveRequests += forceLive
            return results
        }
    }

    private class FakeFreshnessRepository(
        private val record: SyncFreshnessRecord?,
    ) : SyncFreshnessRepository {
        override fun getRecord(key: SyncFreshnessKey): SyncFreshnessRecord? = record
        override fun mark(key: SyncFreshnessKey, state: SyncFreshnessState, nowEpochMs: Long) = Unit
    }

    private fun completeTrackedResults(date: String): List<LotteryResult> {
        return listOf(
            LotteryResult("13", "Lotería Nacional", date, "22", "54", "29", source = "local", fetchedAtEpochMs = 1_000L),
            LotteryResult("23", "King Lottery Día", date, status = RESULT_STATUS_NO_DRAW, source = RESULT_STATUS_NO_DRAW, fetchedAtEpochMs = 1_000L),
            LotteryResult("24", "King Lottery Noche", date, status = RESULT_STATUS_NO_DRAW, source = RESULT_STATUS_NO_DRAW, fetchedAtEpochMs = 1_000L),
            LotteryResult("27", "Haiti Bolet 11:30 AM", date, "18", "24", "67", source = "local", fetchedAtEpochMs = 1_000L),
            LotteryResult("28", "Haiti Bolet 6:30 PM", date, "67", "76", "87", source = "local", fetchedAtEpochMs = 1_000L),
        )
    }
}
