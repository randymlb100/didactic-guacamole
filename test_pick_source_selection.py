import asyncio
import sys
import unittest
from unittest.mock import AsyncMock, patch

sys.path.insert(0, "scraper")
import scrape_and_save as scraper


class PickSourceSelectionTest(unittest.IsolatedAsyncioTestCase):
    async def test_catalog_scrape_uses_validated_pick_history_and_not_lotteryusa(self):
        catalog = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Evening Draw",
        }]
        overview = [{**catalog[0], "date": "", "number": ""}]
        history = [{**catalog[0], "date": "02-10-2026", "number": "6-5-3", "source": "pick-3.com"}]
        with patch.object(scraper, "_async_fetch_us_pick_overview", new=AsyncMock(return_value=overview)), \
                patch.object(scraper, "_async_fetch_us_pick_state_history", new=AsyncMock(return_value=history)), \
                patch.object(scraper, "_async_fetch_new_jersey_pick_home", new=AsyncMock(return_value=[])), \
                patch.object(scraper, "_async_fetch_lotteryusa_pick_catalog_rows", new=AsyncMock(side_effect=AssertionError("LotteryUSA must not be called"))), \
                patch.object(scraper, "_async_fetch_lotteryusa_pick_fallbacks", new=AsyncMock(side_effect=AssertionError("LotteryUSA fallback must not be called"))):
            rows = await scraper._async_scrape_us_picks(
                "02-10-2026", games=("pick3",), existing_rows=catalog,
            )
        self.assertEqual(["US-P3-FL-PICK-3-EVENING"], [row["id"] for row in rows])
        self.assertEqual("6-5-3", rows[0]["number"])

    async def test_catalog_scrape_does_not_emit_unmapped_source_ids(self):
        catalog = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Evening Draw",
        }]
        overview = [{**catalog[0], "date": "", "number": ""}]
        history = [{
            "id": "UNMAPPED-PICK-RESULT", "state": "Florida", "stateCode": "FL",
            "game": "pick3", "gameName": "Pick 3", "draw": "Evening Draw",
            "date": "02-10-2026", "number": "6-5-3", "source": "pick-3.com",
        }]
        with patch.object(scraper, "_async_fetch_us_pick_overview", new=AsyncMock(return_value=overview)), \
                patch.object(scraper, "_async_fetch_us_pick_state_history", new=AsyncMock(return_value=history)), \
                patch.object(scraper, "_async_fetch_new_jersey_pick_home", new=AsyncMock(return_value=[])):
            rows = await scraper._async_scrape_us_picks(
                "02-10-2026", games=("pick3",), existing_rows=catalog,
            )
        self.assertEqual([], rows)


if __name__ == "__main__":
    unittest.main()
