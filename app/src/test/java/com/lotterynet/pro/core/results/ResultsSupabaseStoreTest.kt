package com.lotterynet.pro.core.results

import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.IllegalStateException
import java.net.SocketException
import java.net.UnknownHostException
import org.json.JSONArray
import org.json.JSONObject

class ResultsSupabaseStoreTest {

    private val store = ResultsSupabaseStore()

    @Test
    fun `parse compact number maps classic lotteries into first second third`() {
        val parsed = store.parseCompactNumber(
            rawNumber = "13-97-64",
            lotteryName = "La Primera Día",
        )

        assertEquals("13", parsed.first)
        assertEquals("97", parsed.second)
        assertEquals("64", parsed.third)
        assertNull(parsed.pick3)
        assertNull(parsed.pick4)
    }

    @Test
    fun `parse compact number keeps pick 3 payload for pick lotteries`() {
        val parsed = store.parseCompactNumber(
            rawNumber = "8-7-3",
            lotteryName = "NJ Pick 3 Día",
        )

        assertNull(parsed.first)
        assertNull(parsed.second)
        assertNull(parsed.third)
        assertEquals("8-7-3", parsed.pick3)
        assertNull(parsed.pick4)
    }

    @Test
    fun `parse compact number keeps play 3 payload as pick result`() {
        val parsed = store.parseCompactNumber(
            rawNumber = "5-8-8",
            lotteryName = "Connecticut Play3 Day Draw",
            lotteryId = "US-P3-CT-PLAY3-DAY",
        )

        assertNull(parsed.first)
        assertNull(parsed.second)
        assertNull(parsed.third)
        assertEquals("5-8-8", parsed.pick3)
        assertNull(parsed.pick4)
    }

    @Test
    fun `parse compact number keeps pick 4 payload when four digits are provided`() {
        val parsed = store.parseCompactNumber(
            rawNumber = "4-7-7-9",
            lotteryName = "NJ Pick 4 Día",
        )

        assertNull(parsed.first)
        assertNull(parsed.second)
        assertNull(parsed.third)
        assertNull(parsed.pick3)
        assertEquals("4-7-7-9", parsed.pick4)
    }

    @Test
    fun `pick results with numbers array do not duplicate primary result balls`() {
        val rows = store.parseResultsValue(
            JSONObject(
                """
                {
                  "results": [
                    {
                      "id": "19",
                      "name": "NJ Pick 3 Dia",
                      "date": "06-05-2026",
                      "number": "1-6-5",
                      "numbers": ["1", "6", "5"]
                    },
                    {
                      "id": "21",
                      "name": "NJ Pick 4 Dia",
                      "date": "06-05-2026",
                      "number": "7-9-6-8",
                      "numbers": ["7", "9", "6", "8"]
                    }
                  ]
                }
                """.trimIndent(),
            ),
            "2026-05-06",
        )

        assertNull(rows[0].first)
        assertNull(rows[0].second)
        assertNull(rows[0].third)
        assertEquals("1-6-5", rows[0].pick3)
        assertNull(rows[1].first)
        assertNull(rows[1].second)
        assertNull(rows[1].third)
        assertEquals("7-9-6-8", rows[1].pick4)
    }

    @Test
    fun `pick result rows with classic fields do not duplicate primary result balls`() {
        val rows = store.parseResultsValue(
            JSONArray(
                """
                [
                  {
                    "id": "US-P3-CT-PLAY3-DAY",
                    "name": "Connecticut Play3 Day Draw",
                    "date": "2026-05-09",
                    "number": "5-8-8",
                    "first": "5",
                    "second": "8",
                    "third": "8",
                    "pick3": "5-8-8"
                  }
                ]
                """.trimIndent(),
            ),
            "09-05-2026",
        )

        assertEquals(1, rows.size)
        assertNull(rows.single().first)
        assertNull(rows.single().second)
        assertNull(rows.single().third)
        assertEquals("5-8-8", rows.single().pick3)
    }

    @Test
    fun `transient network failures are downgraded from hard errors`() {
        assertTrue(store.isTransientNetworkFailure(UnknownHostException("dns")))
        assertTrue(store.isTransientNetworkFailure(SocketException("abort")))
    }

    @Test
    fun `non network failures still surface as real errors`() {
        assertFalse(store.isTransientNetworkFailure(IllegalStateException("bad json")))
    }

    @Test
    fun `remote payload merge keeps native rows and adds legacy haiti bolet rows`() {
        val native = JSONArray(
            """
            [
              {"id":"1","name":"La Primera Día","date":"29-04-2026","number":"13-97-64"}
            ]
            """.trimIndent(),
        )
        val legacy = JSONArray(
            """
            [
              {"id":"27","name":"Haiti Bolet 11:30 AM","date":"29-04-2026","number":"42-45-90"},
              {"id":"28","name":"Haiti Bolet 6:30 PM","date":"29-04-2026","number":"92-59-38"}
            ]
            """.trimIndent(),
        )

        val merged = SupabaseResultsRemoteStore.mergeResultPayloads(native, legacy)
            ?: error("Expected merged result payload")

        assertEquals(3, merged.length())
        assertEquals("1", merged.getJSONObject(0).getString("id"))
        assertEquals("27", merged.getJSONObject(1).getString("id"))
        assertEquals("28", merged.getJSONObject(2).getString("id"))
    }

    @Test
    fun `remote payload merge lets render primary replace stale supabase row`() {
        val supabase = JSONArray(
            """
            [
              {"id":"23","name":"King Lottery Día","date":"02-05-2026","number":"10-20-30","source":"supabase"}
            ]
            """.trimIndent(),
        )
        val render = JSONArray(
            """
            [
              {"id":"23","name":"King Lottery Día","date":"02-05-2026","number":"86-11-90","source":"render"}
            ]
            """.trimIndent(),
        )

        val merged = SupabaseResultsRemoteStore.mergeResultPayloads(supabase, render)
            ?: error("Expected merged result payload")

        assertEquals(1, merged.length())
        assertEquals("86-11-90", merged.getJSONObject(0).getString("number"))
    }

    @Test
    fun `render date key converts app date to scraper date`() {
        val client = RenderResultsRemoteClient()

        assertEquals("03-05-2026", client.toRenderDateKey("2026-05-03"))
        assertEquals("03-05-2026", client.toRenderDateKey("03-05-2026"))
    }

    @Test
    fun `force live render client stays on one snapshot request to avoid duplicate traffic`() {
        val seenQueries = mutableListOf<Map<String, String>>()
        val client = RenderResultsRemoteClient(
            requestFetcher = { _, query, _, _ ->
                seenQueries += query.toMap()
                JSONObject(
                    """
                    {"date":"12-05-2026","lotteries":{"results":[{"id":"30","name":"Anguilla 9AM","date":"12-05-2026","number":"03-65-42"}]},"picks":{"results":[{"id":"US-P3-FL-PICK-3-EVENING","name":"Florida Pick 3 Evening Draw","date":"12-05-2026","number":"9-2-0","pick3":"9-2-0"}]}}
                    """.trimIndent(),
                )
            },
        )

        val rawPayload = client.fetchResultsPayload("2026-05-12", forceLive = true) as JSONObject
        val payload = SupabaseResultsRemoteStore.extractResultPayload(rawPayload) as JSONArray

        assertEquals(2, payload.length())
        assertEquals("30", payload.getJSONObject(0).getString("id"))
        assertEquals("US-P3-FL-PICK-3-EVENING", payload.getJSONObject(1).getString("id"))
        assertEquals(1, seenQueries.size)
        assertEquals("both", seenQueries.single()["mode"])
        assertEquals("1", seenQueries.single()["live"])
    }

    @Test
    fun `force live render client does not fallback to scraper endpoint when system snapshot is empty`() {
        var requestCount = 0
        val client = RenderResultsRemoteClient(
            requestFetcher = { path, _, _, _ ->
                requestCount += 1
                when (path) {
                    "/system-results" -> JSONObject("""{"date":"12-05-2026","lotteries":{"results":[]},"picks":{"results":[]}}""")
                    else -> JSONObject(
                        """
                        {"date":"12-05-2026","results":[{"id":"40","name":"Haiti Bolet 9:30 AM","date":"12-05-2026","number":"09-40-49"}]}
                        """.trimIndent(),
                    )
                }
            },
        )

        val rawPayload = client.fetchResultsPayload("2026-05-12", forceLive = true) as JSONObject

        assertNull(SupabaseResultsRemoteStore.extractResultPayload(rawPayload))
        assertEquals(1, requestCount)
    }

    @Test
    fun `force live render client uses standard snapshot timeout`() {
        var usedTimeout = 0
        val client = RenderResultsRemoteClient(
            requestFetcher = { _, query, _, readTimeout ->
                if (query.toMap()["mode"] == "both") {
                    usedTimeout = readTimeout
                }
                JSONObject("""{"date":"12-05-2026","lotteries":{"results":[]},"picks":{"results":[]}}""")
            },
        )

        client.fetchResultsPayload("2026-05-12", forceLive = true)

        assertEquals(8000, usedTimeout)
    }

    @Test
    fun `haiti bolet remote payload normalizes legacy id and primera segunda tercera fields`() {
        val rows = store.parseResultsValue(
            JSONObject(
                """
                {
                  "results": [
                    {
                      "id": "haiti_bolet_1130",
                      "name": "Haiti Bolet 11:30 AM",
                      "date": "29-04-2026",
                      "primera": "42",
                      "segunda": "45",
                      "tercera": "90"
                    },
                    {
                      "lotteryId": "haiti_bolet_630",
                      "lotteryName": "Haiti Bolet 6:30 PM",
                      "date": "29-04-2026",
                      "1ra": "92",
                      "2da": "59",
                      "3ra": "38"
                    }
                  ]
                }
                """.trimIndent(),
            ),
            "2026-04-29",
        )

        assertEquals("27", rows[0].lotteryId)
        assertEquals("42", rows[0].first)
        assertEquals("45", rows[0].second)
        assertEquals("90", rows[0].third)
        assertEquals("28", rows[1].lotteryId)
        assertEquals("92", rows[1].first)
        assertEquals("59", rows[1].second)
        assertEquals("38", rows[1].third)
        assertEquals("2026-04-29", rows[1].date)
    }

    @Test
    fun `enloteria extra lottery names normalize to catalog ids`() {
        assertEquals("29", store.normalizeRemoteLotteryId("", "Anguilla 8AM"))
        assertEquals("40", store.normalizeRemoteLotteryId("", "Haiti Bolet 9:30 AM"))
        assertEquals("42", store.normalizeRemoteLotteryId("", "Haiti Bolet 5:30 PM"))
        assertEquals("44", store.normalizeRemoteLotteryId("", "Georgia Día"))
        assertEquals("46", store.normalizeRemoteLotteryId("", "Georgia Noche"))
    }

    @Test
    fun `primera morning remote names normalize to primera dia id`() {
        assertEquals("1", store.normalizeRemoteLotteryId("", "La Primera Mañana"))
        assertEquals("1", store.normalizeRemoteLotteryId("", "La Primera Día"))
        assertEquals("16", store.normalizeRemoteLotteryId("", "Primera Noche"))
    }

    @Test
    fun `new jersey pick result names normalize to day and night pick ids`() {
        assertEquals("19", store.normalizeRemoteLotteryId("", "NJ Pick 3 Dia"))
        assertEquals("20", store.normalizeRemoteLotteryId("", "New Jersey Pick 3 Noche"))
        assertEquals("21", store.normalizeRemoteLotteryId("", "NJ Pick 4 Midday"))
        assertEquals("22", store.normalizeRemoteLotteryId("", "New Jersey Pick 4 Evening"))
    }

    @Test
    fun `remote pick draw names normalize through catalog aliases`() {
        assertEquals("US-P3-FL-PICK-3-MIDDAY", store.normalizeRemoteLotteryId("", "Florida Pick 3 Midday Draw"))
        assertEquals("US-P4-NY-WIN-4-MIDDAY", store.normalizeRemoteLotteryId("", "New York Win 4 Midday Draw"))
    }

    @Test
    fun `king lottery remote payload normalizes name only rows`() {
        val rows = store.parseResultsValue(
            JSONObject(
                """
                {
                  "results": [
                    {
                      "name": "King Lottery Dia",
                      "date": "29-04-2026",
                      "number": "12-34-56"
                    },
                    {
                      "name": "King Lottery Noche",
                      "date": "29-04-2026",
                      "number": "78-90-11"
                    }
                  ]
                }
                """.trimIndent(),
            ),
            "29-04-2026",
        )

        assertEquals("23", rows[0].lotteryId)
        assertEquals("12", rows[0].first)
        assertEquals("34", rows[0].second)
        assertEquals("56", rows[0].third)
        assertEquals("24", rows[1].lotteryId)
        assertEquals("78", rows[1].first)
        assertEquals("90", rows[1].second)
        assertEquals("11", rows[1].third)
    }

    @Test
    fun `king no draw status is parsed without fake winning numbers`() {
        val rows = store.parseResultsValue(
            JSONObject(
                """
                {
                  "results": [
                    {
                      "id": "23",
                      "name": "King Lottery Día",
                      "date": "01-05-2026",
                      "status": "no_draw",
                      "source": "no_draw"
                    }
                  ]
                }
                """.trimIndent(),
            ),
            "01-05-2026",
        )

        assertEquals("23", rows.single().lotteryId)
        assertEquals(RESULT_STATUS_NO_DRAW, rows.single().status)
        assertEquals("no_draw", rows.single().source)
        assertNull(rows.single().first)
        assertNull(rows.single().second)
        assertNull(rows.single().third)
    }

    @Test
    fun `manual override metadata is preserved when parsing results`() {
        val rows = store.parseResultsValue(
            JSONObject(
                """
                {
                  "results": [
                    {
                      "id": "19",
                      "name": "NJ Pick 3 Día",
                      "date": "01-05-2026",
                      "number": "3-7-1",
                      "game": "pick3",
                      "source": "manual-override",
                      "isManualOverride": true,
                      "manualEditedBy": "ramonc03",
                      "manualEditedAt": "2026-05-01T12:00:00Z"
                    }
                  ]
                }
                """.trimIndent(),
            ),
            "01-05-2026",
        )

        val row = rows.single()
        assertEquals("19", row.lotteryId)
        assertEquals("3-7-1", row.pick3)
        assertTrue(row.isManualOverride)
        assertEquals("ramonc03", row.manualEditedBy)
        assertEquals("2026-05-01T12:00:00Z", row.manualEditedAt)
    }

    @Test
    fun `haiti bolet string payload with results array is parsed`() {
        val rows = store.parseResultsValue(
            """
            {
              "results": [
                {
                  "id": "haiti_bolet_1130",
                  "name": "Haiti Bolet 11:30 AM",
                  "date": "29-04-2026",
                  "number": "42-45-90"
                }
              ]
            }
            """.trimIndent(),
            "29-04-2026",
        )

        assertEquals(1, rows.size)
        assertEquals("27", rows[0].lotteryId)
        assertEquals("42", rows[0].first)
        assertEquals("45", rows[0].second)
        assertEquals("90", rows[0].third)
    }

    @Test
    fun `large render payload parses normal and pick results without dropping rows`() {
        val results = JSONArray()
        repeat(42) { index ->
            results.put(
                JSONObject()
                    .put("id", (index + 1).toString())
                    .put("name", "Loteria ${index + 1}")
                    .put("date", "09-05-2026")
                    .put("number", "01-02-03"),
            )
        }
        repeat(63) { index ->
            results.put(
                JSONObject()
                    .put("id", "US-P3-FL-PICK-3-${index + 1}")
                    .put("name", "Florida Pick 3 ${index + 1}")
                    .put("date", "09-05-2026")
                    .put("game", "pick3")
                    .put("number", "1-2-3"),
            )
        }
        repeat(46) { index ->
            results.put(
                JSONObject()
                    .put("id", "US-P4-FL-PICK-4-${index + 1}")
                    .put("name", "Florida Pick 4 ${index + 1}")
                    .put("date", "09-05-2026")
                    .put("game", "pick4")
                    .put("number", "1-2-3-4"),
            )
        }

        val rows = store.parseResultsValue(JSONObject().put("results", results), "2026-05-09")

        assertEquals(151, rows.size)
        assertEquals(42, rows.count { it.first != null && it.second != null && it.third != null })
        assertEquals(63, rows.count { it.pick3 != null })
        assertEquals(46, rows.count { it.pick4 != null })
        assertTrue(rows.filter { it.pick3 != null || it.pick4 != null }.all { it.first == null && it.second == null && it.third == null })
    }

    @Test
    fun `system results payload parses lotteries and picks together`() {
        val payload = JSONObject(
            """
            {
              "date": "09-05-2026",
              "mode": "both",
              "lotteries": {
                "results": [
                  {"id":"1","name":"La Primera Día","date":"09-05-2026","number":"54-39-37"}
                ]
              },
              "picks": {
                "results": [
                  {"id":"US-P3-NJ-PICK-3-MIDDAY","name":"New Jersey Pick 3 Midday Draw","date":"09-05-2026","game":"pick3","number":"8-3-7"},
                  {"id":"US-P3-NJ-PICK-3-EVENING","name":"New Jersey Pick 3 Evening Draw","date":"09-05-2026","game":"pick3","number":"7-4-6"},
                  {"id":"US-P4-NJ-PICK-4-MIDDAY","name":"New Jersey Pick 4 Midday Draw","date":"09-05-2026","game":"pick4","number":"1-2-3-4"},
                  {"id":"US-P4-NJ-PICK-4-EVENING","name":"New Jersey Pick 4 Evening Draw","date":"09-05-2026","game":"pick4","number":"5-6-7-8"}
                ]
              }
            }
            """.trimIndent(),
        )

        val rows = store.parseResultsValue(payload, "09-05-2026")

        assertEquals(5, rows.size)
        assertEquals(setOf("1", "19", "20", "21", "22"), rows.map { it.lotteryId }.toSet())
        assertEquals("8-3-7", rows.first { it.lotteryId == "19" }.pick3)
        assertEquals("7-4-6", rows.first { it.lotteryId == "20" }.pick3)
        assertEquals("1-2-3-4", rows.first { it.lotteryId == "21" }.pick4)
        assertEquals("5-6-7-8", rows.first { it.lotteryId == "22" }.pick4)
    }

    @Test
    fun `pick cache ignores previous draw rows when loading today`() {
        val payload = JSONObject(
            """
            {
              "results": [
                {"id":"US-P3-NJ-PICK-3-EVENING","name":"New Jersey Pick 3 Evening Draw","date":"09-05-2026","game":"pick3","number":"7-4-6"},
                {"id":"US-P3-NJ-PICK-3-MIDDAY","name":"New Jersey Pick 3 Midday Draw","date":"10-05-2026","game":"pick3","number":"8-3-7"}
              ]
            }
            """.trimIndent(),
        )

        val rows = store.parseResultsValue(payload, "10-05-2026")

        assertEquals(listOf("19"), rows.map { it.lotteryId })
    }

    @Test
    fun `cache payload reports whether pick rows are present`() {
        val classicOnly = JSONArray(
            """
            [
              {"id":"1","name":"La Primera Dia","date":"08-05-2026","number":"38-73-26"}
            ]
            """.trimIndent(),
        )
        val withPick = JSONArray(
            """
            [
              {"id":"1","name":"La Primera Dia","date":"09-05-2026","number":"54-39-37"},
              {"id":"US-P3-CT-PLAY3-DAY","name":"Connecticut Play3 Day Draw","date":"09-05-2026","number":"5-8-8","pick3":"5-8-8"}
            ]
            """.trimIndent(),
        )

        assertFalse(SupabaseResultsRemoteStore.payloadHasPickRows(classicOnly))
        assertTrue(SupabaseResultsRemoteStore.payloadHasPickRows(withPick))
    }

    @Test
    fun `cache payload requires full us pick coverage before it can stop remote fallback`() {
        val pickLotteries = StaticLotteryCatalogRepository().getAllLotteries()
            .filter { lottery -> lottery.id.startsWith("US-P3-") || lottery.id.startsWith("US-P4-") || lottery.id in setOf("19", "20", "21", "22") }
        val partial = JSONArray().apply {
            pickLotteries.dropLast(1).forEach { lottery ->
                put(
                    JSONObject()
                        .put("id", lottery.id)
                        .put("name", lottery.name)
                        .put("date", "09-05-2026")
                        .put(if (lottery.type == "Pick4") "pick4" else "pick3", if (lottery.type == "Pick4") "1-2-3-4" else "1-2-3"),
                )
            }
            repeat(20) { index ->
                put(
                    JSONObject()
                        .put("id", "US-P3-DC-3-EXTRA-$index")
                        .put("name", "Washington DC 3 Extra $index Draw")
                        .put("date", "09-05-2026")
                        .put("pick3", "1-2-3"),
                )
            }
        }
        val complete = JSONArray().apply {
            pickLotteries.forEach { lottery ->
                put(
                    JSONObject()
                        .put("id", lottery.id)
                        .put("name", lottery.name)
                        .put("date", "09-05-2026")
                        .put(if (lottery.type == "Pick4") "pick4" else "pick3", if (lottery.type == "Pick4") "1-2-3-4" else "1-2-3"),
                )
            }
        }

        assertTrue(SupabaseResultsRemoteStore.payloadUsPickRowCount(partial) >= 119)
        assertFalse(SupabaseResultsRemoteStore.payloadHasUsPickCoverage(partial))
        assertTrue(SupabaseResultsRemoteStore.payloadHasUsPickCoverage(complete))
    }

    @Test
    fun `cache payload requires expected classic ids before it can stop remote fallback`() {
        val cacheMissingGeorgiaNight = JSONArray(
            """
            [
              {"id":"44","name":"Georgia Día","date":"10-05-2026","number":"47-65-24"},
              {"id":"45","name":"Georgia Tarde","date":"10-05-2026","number":"30-65-25"},
              {"id":"US-P3-NY-NUMBERS-MIDDAY","name":"New York Numbers Midday Draw","date":"10-05-2026","pick3":"1-2-3"}
            ]
            """.trimIndent(),
        )
        val cacheWithGeorgiaNight = SupabaseResultsRemoteStore.mergeResultPayloads(
            cacheMissingGeorgiaNight,
            JSONArray().put(
                JSONObject()
                    .put("id", "46")
                    .put("name", "Georgia Noche")
                    .put("date", "10-05-2026")
                    .put("number", "98-80-61"),
            ),
        ) ?: error("Expected merged payload")

        assertFalse(
            SupabaseResultsRemoteStore.payloadHasExpectedCoverage(
                cacheMissingGeorgiaNight,
                setOf("44", "45", "46"),
            ),
        )
        assertTrue(
            SupabaseResultsRemoteStore.payloadHasExpectedCoverage(
                cacheWithGeorgiaNight,
                setOf("44", "45", "46"),
            ),
        )
        assertTrue(
            SupabaseResultsRemoteStore.payloadHasExpectedCoverage(
                cacheMissingGeorgiaNight,
                setOf("44", "45"),
            ),
        )
    }

    @Test
    fun `cache payload without expected ids can be used for partial today results`() {
        val todayPartial = JSONArray(
            """
            [
              {"id":"1","name":"La Primera Día","date":"11-05-2026","number":"19-93-58"},
              {"id":"44","name":"Georgia Día","date":"11-05-2026","number":"11-53-18"}
            ]
            """.trimIndent(),
        )

        assertTrue(SupabaseResultsRemoteStore.payloadHasExpectedCoverage(todayPartial, emptySet()))
    }

    @Test
    fun `force live payload skips slow edge function and goes direct to render`() {
        var edgeCalls = 0
        var renderCalls = 0
        val renderPayload = JSONArray().put(
            JSONObject()
                .put("id", "1")
                .put("name", "La Primera Día")
                .put("date", "11-05-2026")
                .put("number", "19-93-58"),
        )
        val store = SupabaseResultsRemoteStore(
            edgePayloadFetcher = {
                edgeCalls += 1
                JSONArray().put(JSONObject().put("id", "99"))
            },
            renderPayloadFetcher = { _, forceLive ->
                renderCalls += 1
                assertTrue(forceLive)
                renderPayload
            },
        )

        val payload = store.fetchResultsPayload(
            date = "2026-05-11",
            expectedResultIds = emptySet(),
            forceLive = true,
        ) as JSONArray

        assertEquals(0, edgeCalls)
        assertEquals(1, renderCalls)
        assertEquals("19-93-58", payload.getJSONObject(0).getString("number"))
    }

    @Test
    fun `remote payload reads supabase cache before render for realtime freshness`() {
        var cacheCalls = 0
        var renderCalls = 0
        var edgeCalls = 0
        val cachePayload = JSONArray().put(
            JSONObject()
                .put("id", "1")
                .put("name", "La Primera Día")
                .put("date", "11-05-2026")
                .put("number", "19-93-58"),
        )
        val store = SupabaseResultsRemoteStore(
            edgePayloadFetcher = {
                edgeCalls += 1
                cachePayload
            },
            renderPayloadFetcher = { _, forceLive ->
                renderCalls += 1
                assertFalse(forceLive)
                JSONArray().put(JSONObject().put("id", "99"))
            },
        )

        val payload = store.fetchResultsPayload(
            date = "2026-05-11",
            expectedResultIds = emptySet(),
            forceLive = false,
        ) as JSONArray

        assertEquals(0, renderCalls)
        assertEquals(0, cacheCalls)
        assertEquals(1, edgeCalls)
        assertEquals("19-93-58", payload.getJSONObject(0).getString("number"))
    }

    @Test
    fun `system results payload is considered usable fallback data`() {
        val payload = JSONObject()
            .put("lotteries", JSONObject().put("results", JSONArray().put(JSONObject().put("id", "1"))))
            .put("picks", JSONObject().put("results", JSONArray().put(JSONObject().put("id", "US-P3-NJ-PICK-3-MIDDAY"))))

        val extracted = SupabaseResultsRemoteStore.extractResultPayload(payload)

        assertTrue(SupabaseResultsRemoteStore.payloadHasRows(extracted))
        assertEquals(2, (extracted as JSONArray).length())
    }
}
