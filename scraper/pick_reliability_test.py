import copy
import datetime
import unittest
from unittest.mock import AsyncMock, patch
import httpx

import scrape_and_save as s


def row(id="US-P3-FL-PICK-3-MIDDAY", number="", status="pending"):
    return {"id": id, "state": "Florida", "stateCode": "FL", "game": "pick3",
            "gameName": "Pick 3", "draw": "Midday Draw", "date": "03-11-2026",
            "number": number, "status": status}


class PickReliabilityTest(unittest.IsolatedAsyncioTestCase):
    async def test_connection_outage_does_not_probe_other_paths_on_same_host(self):
        for error_type in (httpx.ConnectTimeout, httpx.ConnectError):
            with self.subTest(error=error_type.__name__):
                async def unavailable(url, client=None):
                    raise error_type("connection unavailable")
                with patch.object(s, "async_pick_http_get", side_effect=unavailable) as get:
                    found = await s._async_fetch_us_pick_state_history(
                        "pick3", "FL", "Florida", "Pick 3", "03-11-2026")
                self.assertEqual([], found)
                self.assertEqual(1, get.await_count)

    async def test_http_route_failure_still_tries_another_history_path(self):
        request = httpx.Request("GET", "https://fl.pick-3.com/numbers")
        response = httpx.Response(404, request=request)
        failure = httpx.HTTPStatusError("route missing", request=request, response=response)
        with patch.object(s, "async_pick_http_get", AsyncMock(side_effect=[failure, httpx.Response(200, text="history")])) as get, \
             patch.object(s, "parse_us_pick_history_page", return_value=[row(number="1-2-3")]):
            found = await s._async_fetch_us_pick_state_history(
                "pick3", "FL", "Florida", "Pick 3", "03-11-2026")
        self.assertEqual(2, get.await_count)
        self.assertEqual("1-2-3", found[0]["number"])

    async def test_compact_native_snapshot_is_batched_and_reused(self):
        calls = []
        def respond(request):
            calls.append(request)
            self.assertEqual("eq.pick", request.url.params["source"])
            self.assertNotIn("source_payload", request.url.params["select"])
            return httpx.Response(200, json=[{"result_day_key": "03-11-2026", "lottery_legacy_id": "US-P3-FL-PICK-3-MIDDAY",
                                             "game": "pick3", "number_raw": "1-2-3", "status": "published"}])
        async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
            snapshot = await s._async_fetch_pick_snapshot(["03-11-2026", "02-11-2026", "01-11-2026"], client)
            for day in ["03-11-2026", "02-11-2026", "01-11-2026"] * 2:
                await s._async_fetch_existing_pick_results_from_supabase(day, client, snapshot)
        self.assertEqual(1, len(calls))

    async def test_native_snapshot_failure_is_not_replaced_by_stale_kv(self):
        async with httpx.AsyncClient(transport=httpx.MockTransport(lambda r: httpx.Response(503))) as client:
            snapshot = await s._async_fetch_pick_snapshot(["03-11-2026"], client)
            self.assertEqual({}, snapshot)
            with self.assertRaises(RuntimeError):
                await s._async_fetch_existing_pick_results_from_supabase("03-11-2026", client, snapshot)

    async def test_capped_native_snapshot_is_not_treated_as_complete(self):
        record = {"result_day_key": "03-11-2026", "lottery_legacy_id": "US-P3-FL-PICK-3-MIDDAY"}
        async with httpx.AsyncClient(transport=httpx.MockTransport(lambda r: httpx.Response(200, json=[record] * 1000))) as client:
            self.assertEqual({}, await s._async_fetch_pick_snapshot(["03-11-2026"], client))

    async def test_pick_read_failure_does_not_block_normal_lottery_save(self):
        def respond(request):
            return httpx.Response(503) if request.url.params.get("source") == "eq.pick" else httpx.Response(200, json=[])
        async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
            with patch("sys.argv", ["scrape_and_save.py"]), \
                 patch.object(s, "SUPABASE_KEY", "test"), \
                 patch.object(s, "get_http_client", return_value=client), \
                 patch.object(s, "default_scrape_dates", return_value=["03-11-2026"]), \
                 patch.object(s, "get_dr_date_str", return_value="03-11-2026"), \
                 patch.object(s, "_async_scrape", AsyncMock(return_value=[{"id": "1", "name": "QA normal", "number": "01-02-03"}])), \
                 patch.object(s, "_async_scrape_us_picks", AsyncMock()) as pick, \
                 patch.object(s, "_async_save_to_supabase", AsyncMock()) as normal, \
                 patch.object(s, "_async_save_us_picks_to_supabase", AsyncMock()) as write_pick, \
                 patch.object(s, "async_recover_pending_reconcile_scopes", AsyncMock()):
                await s._async_main()
        normal.assert_awaited_once()
        pick.assert_not_awaited()
        write_pick.assert_not_awaited()

    async def test_same_public_page_is_requested_once_per_cycle(self):
        calls = []
        async def get(url, client=None):
            calls.append(url)
            return object()
        async with httpx.AsyncClient() as client:
            client._lotterynet_pick_requests = {}
            with patch.object(s, "async_http_get", side_effect=get):
                import asyncio
                values = await asyncio.gather(*[s.async_pick_http_get("https://fl.pick-3.com/numbers", client) for _ in range(4)])
        self.assertEqual(1, len(calls))
        self.assertTrue(all(value is values[0] for value in values))

    async def test_overview_outage_does_not_hide_independent_state_host(self):
        template = row()
        async def overview(game, client=None, source_health=None):
            source_health["overview_ok"] = False
            return []
        with patch.object(s, "_async_fetch_us_pick_overview", side_effect=overview), \
             patch.object(s, "_async_fetch_us_pick_state_history", AsyncMock(return_value=[row(number="1-2-3")])) as history, \
             patch.object(s, "_async_fetch_new_jersey_pick_home", AsyncMock(return_value=[])):
            found = await s._async_fetch_working_us_pick_history("03-11-2026", "pick3", [template], source_health={})
        self.assertEqual(1, len(found))
        history.assert_awaited_once()

    async def test_history_recovers_absent_catalog_row_without_rechecking_final(self):
        final = row(number="1-2-3", status="published")
        missing = row(id="US-P3-FL-PICK-3-EVENING")
        missing["draw"] = "Evening Draw"
        recovered = dict(missing, number="4-5-6", status="published")
        with patch.object(s, "static_us_pick_catalog_rows", return_value=[final, missing]), \
             patch.object(s, "_async_fetch_working_us_pick_history", AsyncMock(return_value=[recovered])) as history:
            found = await s._async_refresh_missing_us_pick_results("03-11-2026", [final])
        self.assertEqual({final["id"], missing["id"]}, {r["id"] for r in found})
        self.assertEqual([missing["id"]], [r["id"] for r in history.await_args.args[2]])
        self.assertEqual(final, next(r for r in found if r["id"] == final["id"]))

    async def test_save_never_overwrites_published_result_or_dispatches_it(self):
        final = row(number="1-2-3", status="published")
        original = copy.deepcopy(final)
        with patch.object(s, "async_save_result_draws_payload", AsyncMock()) as write, \
             patch.object(s, "async_dispatch_result_reconcile_scopes", AsyncMock()) as dispatch:
            await s._async_save_us_picks_to_supabase("03-11-2026", [row(number="9-9-9", status="published")],
                                                   existing_rows=[final], client=object())
        write.assert_not_awaited()
        dispatch.assert_not_awaited()
        self.assertEqual(original, final)

    async def test_save_writes_only_new_result_not_whole_day(self):
        final = row(number="1-2-3", status="published")
        new = dict(row(id="US-P3-FL-PICK-3-EVENING", number="4-5-6", status="published"), draw="Evening Draw")
        with patch.object(s, "async_save_result_draws_payload", AsyncMock()) as write, \
             patch.object(s, "async_dispatch_result_reconcile_scopes", AsyncMock()):
            await s._async_save_us_picks_to_supabase("03-11-2026", [new], existing_rows=[final], client=object())
        self.assertEqual([new["id"]], [r["id"] for r in write.await_args.args[1]])

    async def test_new_primary_result_without_status_is_published_before_dispatch(self):
        new = row(number="1-2-3")
        new.pop("status")
        with patch.object(s, "async_save_result_draws_payload", AsyncMock()) as write, \
             patch.object(s, "async_dispatch_result_reconcile_scopes", AsyncMock()) as dispatch:
            await s._async_save_us_picks_to_supabase("03-11-2026", [new], existing_rows=[], client=object())
        self.assertEqual("published", write.await_args.args[1][0]["status"])
        self.assertEqual((new["id"], "pick3"), s.result_reconcile_scope(dispatch.await_args.args[1][0], "pick"))

    def test_day_fallback_urls_do_not_alias_morning(self):
        catalog = {r["id"]: r for r in s.static_us_pick_catalog_rows()}
        for day, morning in [("US-P3-TX-PICK-3-DAY", "US-P3-TX-PICK-3-MORNING"),
                             ("US-P4-TX-DAILY-4-DAY", "US-P4-TX-DAILY-4-MORNING"),
                             ("US-P4-TN-CASH-4-DAY", "US-P4-TN-CASH-4-MORNING")]:
            self.assertNotEqual(s.lotteryusa_catalog_row_url(catalog[day]),
                                s.lotteryusa_catalog_row_url(catalog[morning]))

    def test_verified_multi_draw_periods_have_distinct_schedules(self):
        for code, periods in [("OR", ["MORNING", "DAY", "EVENING", "NIGHT"]), ("ID", ["MIDDAY", "EVENING"])]:
            times = [s.us_pick_official_draw_utc({"id": f"US-P4-{code}-PICK-4-{period}"}, "03-11-2026")
                     for period in periods]
            self.assertNotIn(None, times)
            self.assertEqual(len(times), len(set(times)))

    def test_period_aliases_never_join_distinct_draws(self):
        self.assertFalse(s.lotteryusa_draw_keys_compatible("Morning Draw", "Day Draw"))
        self.assertFalse(s.lotteryusa_draw_keys_compatible("Evening Draw", "Night Draw"))
        self.assertTrue(s.lotteryusa_draw_keys_compatible("Midday Draw", "Day Draw"))

    def test_tennessee_archive_reads_each_turn_and_excludes_wild_ball(self):
        html = '<table>' + ''.join(
            f'<tr><td>Tuesday, November 3, 2026 - {clock}</td><td>{label}</td><td>'
            + ''.join(f'<div class="number-part-0{i+1}">{n}</div>' for i, n in enumerate(number))
            + '<div class="wild-ball">9</div></td></tr>'
            for clock, label, number in [("9:28am", "Morning", "608"), ("12:28pm", "Midday", "706"), ("6:28pm", "Evening", "280")]
        ) + '</table>'
        rows = s.parse_us_pick_history_page(html, "pick3", "TN", "Tennessee", "Cash 3", "03-11-2026")
        self.assertEqual({"US-P3-TN-CASH-3-MORNING": "6-0-8", "US-P3-TN-CASH-3-MIDDAY": "7-0-6",
                          "US-P3-TN-CASH-3-06-28-PM": "2-8-0"}, {r["id"]: r["number"] for r in rows})
        self.assertEqual([], s.parse_us_pick_history_page(html, "pick3", "TN", "Tennessee", "Cash 3", "03-11-2025"))

    def test_recent_draw_selection_does_not_recheck_published(self):
        final = row(number="1-2-3", status="published")
        now = datetime.datetime(2026, 11, 3, 18, 30, tzinfo=datetime.UTC)
        self.assertEqual([], s.recently_closed_us_pick_catalog_rows("03-11-2026", [final], [final], now))

    def test_legacy_id_aliases_are_resolved_without_new_ticket_identity(self):
        catalog = s.static_us_pick_catalog_rows()
        cases = [("NH", "US-P3-NH-PICK-3-DAY", "Day Draw", "US-P3-NH--DAY"),
                 ("AZ", "US-P3-AZ-PICK-3-EVENING", "Evening Draw", "US-P3-AZ-PICK-3-DRAW"),
                 ("WV", "US-P3-WV-DAILY-3-09-00-PM", "09:00 PM Draw", "US-P3-WV-DAILY-3-DAY")]
        for code, id, draw, expected in cases:
            source = dict(row(id=id, number="1-2-3"), stateCode=code, gameName="Daily 3" if code == "WV" else "Pick 3", draw=draw)
            matched = s.matched_us_pick_catalog_rows(source, catalog)
            self.assertEqual([expected], [r["id"] for r in matched])

    def test_calendar_and_overnight_keep_draw_date(self):
        sunday = "01-11-2026"
        self.assertFalse(s.us_pick_has_draw_on_date({"id": "US-P4-TX-DAILY-4-NIGHT"}, sunday))
        self.assertFalse(s.us_pick_has_draw_on_date({"id": "US-P3-TN-CASH-3-MORNING"}, sunday))
        self.assertTrue(s.us_pick_has_draw_on_date({"id": "US-P4-OR-PICK-4-NIGHT"}, sunday))
        night = s.us_pick_official_draw_utc({"id": "US-P4-OR-PICK-4-NIGHT"}, "03-11-2026")
        self.assertEqual("2026-11-04T06:00:00+00:00", night.isoformat())
        summer = s.us_pick_official_draw_utc({"id": "US-P4-OR-PICK-4-NIGHT"}, "03-07-2026")
        self.assertEqual("2026-07-04T05:00:00+00:00", summer.isoformat())

    def test_washington_match4_cannot_enter_decimal_catalog(self):
        self.assertFalse(any(r["id"] == "US-P4-WA-MATCH-4-EVENING" for r in s.static_us_pick_catalog_rows()))
        self.assertEqual([], s.matched_us_pick_catalog_rows(dict(row(), game="pick4", number="05-07-08-20"), []))


if __name__ == "__main__":
    unittest.main()
