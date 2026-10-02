import unittest
from unittest.mock import AsyncMock, patch
import scrape_and_save as scraper


class PickReadReuseTest(unittest.IsolatedAsyncioTestCase):
    async def check_save(self, snapshot, supplied):
        with patch.object(scraper, '_async_fetch_existing_pick_results_from_supabase', new_callable=AsyncMock, return_value=[]) as read, patch.object(scraper, 'async_save_result_draws_payload', new_callable=AsyncMock) as write, patch.object(scraper, 'async_dispatch_result_reconcile_scopes', new_callable=AsyncMock):
            kwargs = {'existing_rows': snapshot} if supplied else {}
            await scraper._async_save_us_picks_to_supabase('01-10-2026', [], client=object(), **kwargs)
            self.assertEqual(0 if supplied else 1, read.await_count)
            write.assert_awaited_once()

    async def test_empty_snapshot_is_reused_not_refetched(self):
        await self.check_save([], True)

    async def test_standalone_save_still_reads_current_data(self):
        await self.check_save(None, False)

    async def test_snapshot_keeps_published_rows_and_does_not_dispatch_them_again(self):
        snapshot = [{'id': 'US-P3-NY-MIDDAY', 'game': 'pick3', 'number': '123', 'status': 'published'}]
        with patch.object(scraper, '_async_fetch_existing_pick_results_from_supabase', new_callable=AsyncMock) as read, patch.object(scraper, 'async_save_result_draws_payload', new_callable=AsyncMock) as write, patch.object(scraper, 'async_dispatch_result_reconcile_scopes', new_callable=AsyncMock) as dispatch:
            await scraper._async_save_us_picks_to_supabase('01-10-2026', [], client=object(), existing_rows=snapshot)
            read.assert_not_awaited()
            self.assertEqual('123', write.await_args.args[1][0]['number'])
            self.assertEqual([], dispatch.await_args.args[1])
            self.assertEqual('123', snapshot[0]['number'])
