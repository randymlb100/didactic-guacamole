import asyncio
import json
import unittest
from unittest.mock import patch, AsyncMock

import httpx
import scrape_and_save as scraper


class KvBatchTest(unittest.IsolatedAsyncioTestCase):
    async def test_cron_three_day_run_uses_one_kv_request(self):
        calls = []
        def respond(request):
            calls.append(request)
            return httpx.Response(200, json=[])
        async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
            with patch("sys.argv", ["scrape_and_save.py"]), \
                    patch.object(scraper, "SUPABASE_KEY", "test"), \
                    patch.object(scraper, "get_http_client", return_value=client), \
                    patch.object(scraper, "close_http_client", new_callable=AsyncMock), \
                    patch.object(scraper, "default_scrape_dates", return_value=["13-10-2026", "12-10-2026", "11-10-2026"]), \
                    patch.object(scraper, "get_dr_date_str", return_value="13-10-2026"), \
                    patch.object(scraper, "should_run_full_us_pick_sweep", return_value=False), \
                    patch.object(scraper, "recently_closed_us_pick_catalog_rows", return_value=[]), \
                    patch.object(scraper, "_async_scrape", new_callable=AsyncMock, return_value=[]), \
                    patch.object(scraper, "non_current_backfill_should_run", return_value=False), \
                    patch.object(scraper, "async_recover_pending_reconcile_scopes", new_callable=AsyncMock) as recover:
                await scraper._async_main()
                recover.assert_awaited_once()
        self.assertEqual(1, sum(r.url.path.endswith("lotterynet_kv") for r in calls))
        self.assertEqual(2, sum(r.url.path.endswith("result_draws") for r in calls))

    async def test_three_days_share_one_request_including_missing_keys(self):
        calls = []
        keys = [f"{kind}_results_cache_by_day:{day}-10-2026"
                for day in (11, 12, 13) for kind in ("lot", "pick")]
        def respond(request):
            calls.append(request)
            self.assertEqual("key,value", request.url.params["select"])
            self.assertEqual("LotteryNet-Render/1.0", request.headers["user-agent"])
            return httpx.Response(200, json=[
                {"key": keys[0], "value": [{"id": "1"}]},
                {"key": keys[1], "value": json.dumps({"results": [{"id": "pick"}]})},
            ])
        async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
            with patch.object(scraper, "SUPABASE_KEY", "test"):
                snapshot = await scraper._async_fetch_kv_snapshot(keys, client=client)
                values = await asyncio.gather(*[
                    scraper._async_fetch_kv_list(key, client=client, snapshot=snapshot)
                    for key in keys * 2
                ])
        self.assertEqual(1, len(calls))
        self.assertEqual([{"id": "1"}], values[0])
        self.assertEqual([{"id": "pick"}], values[1])
        self.assertEqual([], values[2])
        self.assertEqual(values[:6], values[6:])

    async def test_failed_batch_does_not_hide_per_key_result(self):
        calls = []
        def respond(request):
            calls.append(request)
            return (httpx.Response(503) if len(calls) == 1 else
                    httpx.Response(200, json=[{"value": [{"id": "available"}]}]))
        async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
            with patch.object(scraper, "SUPABASE_KEY", "test"):
                snapshot = await scraper._async_fetch_kv_snapshot(["key"], client=client)
                result = await scraper._async_fetch_kv_list("key", client=client, snapshot=snapshot)
        self.assertEqual([{"id": "available"}], result)
        self.assertEqual(2, len(calls))

    async def test_missing_kv_still_reads_authoritative_results(self):
        calls = []
        def respond(request):
            calls.append(request)
            if request.url.path.endswith("lotterynet_kv"):
                return httpx.Response(200, json=[])
            return httpx.Response(200, json=[{
                "lottery_legacy_id": "1", "lottery_name": "Test",
                "number_raw": "01-02-03", "status": "published",
            }])
        key = "lot_results_cache_by_day:13-10-2026"
        async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
            with patch.object(scraper, "SUPABASE_KEY", "test"):
                snapshot = await scraper._async_fetch_kv_snapshot([key], client=client)
                rows = await scraper._async_fetch_existing_from_supabase(
                    "13-10-2026", client=client, snapshot=snapshot)
        self.assertEqual("published", rows[0]["status"])
        self.assertEqual(["/rest/v1/lotterynet_kv", "/rest/v1/result_draws"],
                         [r.url.path for r in calls])

    async def test_next_run_fetches_fresh_values(self):
        calls = []
        def respond(request):
            calls.append(request)
            return httpx.Response(200, json=[{"key": "key", "value": [{"version": len(calls)}]}])
        async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
            with patch.object(scraper, "SUPABASE_KEY", "test"):
                first = await scraper._async_fetch_kv_snapshot(["key"], client=client)
                second = await scraper._async_fetch_kv_snapshot(["key"], client=client)
        self.assertEqual(1, first["key"][0]["version"])
        self.assertEqual(2, second["key"][0]["version"])

    async def test_published_row_is_not_written_or_reread(self):
        rows = [{"id": "1", "name": "Test", "number": "01-02-03", "status": "published"}]
        with patch.object(scraper, "_async_fetch_existing_from_supabase", new_callable=AsyncMock) as read, \
                patch.object(scraper, "_async_save_native_results_table", new_callable=AsyncMock) as write, \
                patch.object(scraper, "filter_invalid_dominican_results", return_value=rows):
            await scraper._async_save_to_supabase("13-10-2026", rows, existing_rows=rows)
        read.assert_not_awaited()
        write.assert_not_awaited()


if __name__ == "__main__":
    unittest.main()
