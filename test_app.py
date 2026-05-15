import json
import time
import unittest
from unittest.mock import patch

import app


def fake_results():
    base = [
        {"id": str(index), "name": f"Loteria {index}", "date": "02-05-2026", "number": "01-02-03"}
        for index in range(1, 19)
    ] + [
        {"id": "19", "name": "NJ Pick 3 Día", "date": "02-05-2026", "number": "3-7-1"},
        {"id": "20", "name": "NJ Pick 3 Noche", "date": "02-05-2026", "number": "5-3-4"},
        {"id": "21", "name": "NJ Pick 4 Día", "date": "02-05-2026", "number": "0-8-4-3"},
        {"id": "22", "name": "NJ Pick 4 Noche", "date": "02-05-2026", "number": "1-2-2-4"},
        {"id": "23", "name": "King Lottery Día", "date": "02-05-2026", "number": "90-76-95"},
        {"id": "24", "name": "King Lottery Noche", "date": "02-05-2026", "number": "85-31-78"},
        {"id": "25", "name": "New Jersey AM", "date": "02-05-2026", "number": "71-08-43"},
        {"id": "26", "name": "New Jersey PM", "date": "02-05-2026", "number": "34-12-24"},
        {"id": "27", "name": "Haiti Bolet 11:30 AM", "date": "02-05-2026", "number": "03-21-01"},
        {"id": "28", "name": "Haiti Bolet 6:30 PM", "date": "02-05-2026", "number": "52-35-42"},
    ]
    return base


class RenderApiContractsTest(unittest.TestCase):
    def setUp(self):
        self.client = app.app.test_client()
        app._scrape_cache.clear()
        app._pick_scrape_cache.clear()
        app._lottery_refresh_inflight.clear()
        app._pick_refresh_inflight.clear()
        app._pick_refresh_last_started.clear()
        app._pick_refresh_last_completed.clear()
        app._pick_refresh_last_error.clear()
        app._live_system_results_cache.clear()
        app._manual_override_cache.clear()

    def save_override_cache(self, date_key, rows):
        normalized = [app.normalize_manual_override_row(row, date_key) for row in rows]
        app.set_cached_manual_overrides(date_key, normalized)

    def test_root_returns_28_unique_results(self):
        rows = fake_results() + [fake_results()[1]]
        with patch("app.fetch_existing_from_supabase", return_value=rows), \
            patch("app.fetch_pick_rows_from_supabase", return_value=[]), \
            patch("app.scrape") as scrape_mock:
            response = self.client.get("/?date=02-05-2026")

        scrape_mock.assert_not_called()
        self.assertEqual(200, response.status_code)
        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(28, len(payload))
        self.assertEqual("28", payload[-1]["id"])

    def test_results_endpoint_returns_metadata(self):
        with patch("app.fetch_existing_from_supabase", return_value=fake_results()), \
            patch("app.fetch_pick_rows_from_supabase", return_value=[]), \
            patch("app.scrape") as scrape_mock:
            response = self.client.get("/results?date=02-05-2026")

        scrape_mock.assert_not_called()
        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual("02-05-2026", payload["date"])
        self.assertEqual(28, payload["count"])
        self.assertEqual("supabase-cache", payload["source"])
        self.assertEqual("27", payload["results"][-2]["id"])

    def test_haiti_route_filters_haiti_bolet(self):
        with patch("app.fetch_existing_from_supabase", return_value=fake_results()), patch("app.scrape") as scrape_mock:
            response = self.client.get("/loteria-haiti?date=02-05-2026")

        scrape_mock.assert_not_called()
        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(["27", "28"], [row["id"] for row in payload])

    def test_pick_routes_include_pick_specific_fields(self):
        with patch("app.fetch_existing_from_supabase", return_value=fake_results()), \
            patch("app.fetch_pick_rows_from_supabase", return_value=[]), \
            patch("app.scrape") as scrape_mock:
            pick3 = self.client.get("/loteria-pick3?date=02-05-2026")
            pick4 = self.client.get("/loteria-pick4?date=02-05-2026")

        scrape_mock.assert_not_called()
        pick3_payload = json.loads(pick3.data.decode("utf-8"))
        pick4_payload = json.loads(pick4.data.decode("utf-8"))
        self.assertEqual(["19", "20"], [row["id"] for row in pick3_payload])
        self.assertEqual("3-7-1", pick3_payload[0]["pick3"])
        self.assertNotIn("pick4", pick3_payload[0])
        self.assertEqual(["21", "22"], [row["id"] for row in pick4_payload])
        self.assertEqual("0-8-4-3", pick4_payload[0]["pick4"])
        self.assertNotIn("pick3", pick4_payload[0])

    def test_public_routes_do_not_scrape_for_same_date(self):
        with patch("app.fetch_existing_from_supabase", return_value=fake_results()), \
            patch("app.fetch_pick_rows_from_supabase", return_value=[]), \
            patch("app.scrape") as scrape_mock:
            self.client.get("/loteria-pick3?date=02-05-2026")
            self.client.get("/loteria-pick4?date=02-05-2026")

        scrape_mock.assert_not_called()

    def test_results_endpoint_includes_normal_and_new_pick_results(self):
        pick_rows = [
            {
                "id": "US-P3-FL-PICK-3-EVENING",
                "state": "Florida",
                "stateCode": "FL",
                "game": "pick3",
                "gameName": "Pick 3",
                "draw": "Evening Draw",
                "date": "02-05-2026",
                "number": "9-2-0",
                "playTypes": ["straight", "box"],
                "source": "pick-3.com",
            },
        ]
        with patch("app.fetch_existing_from_supabase", return_value=fake_results()), \
            patch("app.fetch_pick_rows_from_supabase", return_value=pick_rows), \
            patch("app.scrape") as scrape_mock:
            normal = self.client.get("/results?date=02-05-2026")
            picks = self.client.get("/pick-results?date=02-05-2026")

        scrape_mock.assert_not_called()
        normal_payload = json.loads(normal.data.decode("utf-8"))
        pick_payload = json.loads(picks.data.decode("utf-8"))
        self.assertEqual(25, normal_payload["count"])
        self.assertEqual("US-P3-FL-PICK-3-EVENING", normal_payload["results"][-1]["id"])
        self.assertEqual("Florida Pick 3 Evening Draw", normal_payload["results"][-1]["name"])
        self.assertEqual("9-2-0", normal_payload["results"][-1]["pick3"])
        self.assertEqual(1, pick_payload["count"])
        self.assertEqual("picks", pick_payload["section"])
        self.assertEqual("US-P3-FL-PICK-3-EVENING", pick_payload["results"][0]["id"])

    def test_combined_results_endpoint_honors_mode_without_changing_default(self):
        pick_rows = [
            {
                "id": "US-P4-GA-CASH-4-NIGHT",
                "state": "Georgia",
                "stateCode": "GA",
                "game": "pick4",
                "gameName": "Cash 4",
                "draw": "Night Draw",
                "date": "02-05-2026",
                "number": "0-6-1-3",
                "playTypes": ["straight", "box"],
                "source": "pick-4.com",
            },
        ]
        with patch("app.fetch_existing_from_supabase", return_value=fake_results()), \
            patch("app.fetch_pick_rows_from_supabase", return_value=pick_rows), \
            patch("app.scrape") as scrape_mock:
            default_response = self.client.get("/system-results?date=02-05-2026")
            both_response = self.client.get("/system-results?date=02-05-2026&mode=both")

        scrape_mock.assert_not_called()
        default_payload = json.loads(default_response.data.decode("utf-8"))
        both_payload = json.loads(both_response.data.decode("utf-8"))
        self.assertEqual("lottery", default_payload["mode"])
        self.assertEqual(24, default_payload["lotteries"]["count"])
        self.assertNotIn("picks", default_payload)
        self.assertEqual("both", both_payload["mode"])
        self.assertEqual(24, both_payload["lotteries"]["count"])
        self.assertEqual(1, both_payload["picks"]["count"])

    def test_root_without_date_is_lightweight_health_check(self):
        with patch("app.scrape") as scrape_mock, patch("app.fetch_existing_from_supabase") as cache_mock:
            response = self.client.get("/")

        scrape_mock.assert_not_called()
        cache_mock.assert_not_called()
        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertTrue(payload["ok"])

    def test_system_results_does_not_scrape_when_cache_missing(self):
        with patch("app.fetch_existing_from_supabase", return_value=[]), \
            patch("app.get_dr_date_str", return_value="03-05-2026"), \
            patch("app.fetch_pick_rows_from_supabase", return_value=[]), \
            patch("app.scrape") as scrape_mock:
            response = self.client.get("/system-results?date=02-05-2026&mode=both")

        scrape_mock.assert_not_called()
        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual("cache-miss", payload["source"])
        self.assertEqual(0, payload["lotteries"]["count"])
        self.assertEqual(0, payload["picks"]["count"])

    def test_system_results_today_pick_cache_miss_schedules_background_refresh(self):
        with patch("app.fetch_existing_from_supabase", return_value=[]), \
            patch("app.fetch_pick_rows_from_supabase", return_value=[]), \
            patch("app.get_dr_date_str", return_value="02-05-2026"), \
            patch("app.schedule_background_pick_refresh", return_value=True) as background_refresh, \
            patch("app.scrape_us_picks") as pick_scrape:
            response = self.client.get("/system-results?date=02-05-2026&mode=pick")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertEqual(0, payload["picks"]["count"])
        background_refresh.assert_called_once_with("02-05-2026")
        pick_scrape.assert_not_called()

    def test_system_results_historical_pick_uses_fresh_memory_cache(self):
        cached_rows = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Evening Draw",
            "date": "02-05-2026",
            "number": "9-2-0",
        }]
        app.set_pick_scrape_cache("02-05-2026", cached_rows)
        with patch("app.fetch_existing_from_supabase", return_value=[]), \
            patch("app.fetch_pick_rows_from_supabase", return_value=[]), \
            patch("app.get_dr_date_str", return_value="03-05-2026"), \
            patch("app.schedule_background_pick_refresh") as background_refresh, \
            patch("app.scrape_us_picks") as pick_scrape:
            response = self.client.get("/system-results?date=02-05-2026&mode=pick")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertEqual(1, payload["picks"]["count"])
        self.assertEqual("9-2-0", payload["picks"]["results"][0]["number"])
        background_refresh.assert_not_called()
        pick_scrape.assert_not_called()

    def test_run_system_scraper_can_save_pick_only_without_touching_lottery(self):
        pick_rows = [
            {
                "id": "US-P3-FL-PICK-3-EVENING",
                "state": "Florida",
                "stateCode": "FL",
                "game": "pick3",
                "gameName": "Pick 3",
                "draw": "Evening Draw",
                "date": "02-05-2026",
                "number": "9-2-0",
                "playTypes": ["straight", "box"],
                "source": "pick-3.com",
            },
        ]
        with patch("app.scrape") as lottery_scrape, \
            patch("app.scrape_us_picks", return_value=pick_rows), \
            patch("app.save_to_supabase") as save_lottery, \
            patch("app.save_us_picks_to_supabase") as save_picks, \
            patch.dict("os.environ", {"SUPABASE_KEY": "test-key"}):
            response = self.client.post("/run-system-scraper?date=02-05-2026&mode=pick")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertEqual("pick", payload["mode"])
        self.assertEqual(1, payload["picks"]["count"])
        lottery_scrape.assert_not_called()
        save_lottery.assert_not_called()
        save_picks.assert_called_once()

    def test_run_system_scraper_pick_mode_passes_existing_pick_cache(self):
        existing_pick_rows = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "date": "02-05-2026",
            "number": "9-2-0",
        }]
        refreshed_pick_rows = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "date": "02-05-2026",
            "number": "9-2-0",
        }]
        with patch("app.scrape") as lottery_scrape, \
            patch("app.fetch_pick_rows_from_supabase", return_value=existing_pick_rows), \
            patch("app.scrape_us_picks", return_value=refreshed_pick_rows) as pick_scrape, \
            patch("app.save_to_supabase") as save_lottery, \
            patch("app.save_us_picks_to_supabase") as save_picks, \
            patch.dict("os.environ", {"SUPABASE_KEY": "test-key"}):
            response = self.client.post("/run-system-scraper?date=02-05-2026&mode=pick")

        self.assertEqual(200, response.status_code)
        pick_scrape.assert_called_once_with("02-05-2026", existing_rows=existing_pick_rows)
        lottery_scrape.assert_not_called()
        save_lottery.assert_not_called()
        save_picks.assert_called_once()

    def test_run_pick_scraper_defaults_to_background_refresh(self):
        cached_rows = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Evening Draw",
            "date": "02-05-2026",
            "number": "9-2-0",
        }]
        with patch("app.schedule_background_pick_refresh", return_value=True) as background_refresh, \
            patch("app.fetch_pick_rows_from_supabase", return_value=cached_rows), \
            patch("app.scrape_us_picks") as pick_scrape, \
            patch("app.save_us_picks_to_supabase") as save_picks:
            response = self.client.get("/run-pick-scraper?date=02-05-2026")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(202, response.status_code)
        self.assertTrue(payload["scheduled"])
        self.assertTrue(payload["refreshing"])
        self.assertEqual(1, payload["count"])
        background_refresh.assert_called_once_with("02-05-2026", force=True)
        pick_scrape.assert_not_called()
        save_picks.assert_not_called()

    def test_run_pick_scraper_sync_mode_still_scrapes_and_saves(self):
        existing_pick_rows = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "date": "02-05-2026",
            "number": "9-2-0",
        }]
        refreshed_pick_rows = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Evening Draw",
            "date": "02-05-2026",
            "number": "9-2-0",
        }]
        with patch("app.fetch_pick_rows_from_supabase", return_value=existing_pick_rows), \
            patch("app.scrape_us_picks", return_value=refreshed_pick_rows) as pick_scrape, \
            patch("app.save_us_picks_to_supabase") as save_picks:
            response = self.client.get("/run-pick-scraper?date=02-05-2026&sync=1")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertTrue(payload["saved"])
        self.assertEqual(1, payload["count"])
        pick_scrape.assert_called_once_with("02-05-2026", existing_rows=existing_pick_rows)
        save_picks.assert_called_once()

    def test_run_pick_scraper_sync_uses_recent_catalog_when_current_cache_empty(self):
        recent_pick_rows = [{
            "id": "US-P4-FL-PICK-4-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick4",
            "gameName": "Pick 4",
            "draw": "Evening Draw",
            "date": "01-05-2026",
            "number": "1-2-3-4",
        }]
        refreshed_pick_rows = [{
            "id": "US-P4-FL-PICK-4-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick4",
            "gameName": "Pick 4",
            "draw": "Evening Draw",
            "date": "02-05-2026",
            "number": "2-8-4-4",
        }]
        with patch("app.fetch_pick_rows_from_supabase", side_effect=[[], recent_pick_rows]), \
            patch("app.scrape_us_picks", return_value=refreshed_pick_rows) as pick_scrape, \
            patch("app.save_us_picks_to_supabase"):
            response = self.client.get("/run-pick-scraper?date=02-05-2026&sync=1")

        self.assertEqual(200, response.status_code)
        pick_scrape.assert_called_once_with("02-05-2026", existing_rows=recent_pick_rows)

    def test_system_results_live_pick_refresh_saves_updated_cache(self):
        existing_pick_rows = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "date": "02-05-2026",
            "number": "9-2-0",
        }]
        refreshed_pick_rows = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "date": "02-05-2026",
            "number": "9-2-0",
        }]
        with patch("app.fetch_pick_rows_from_supabase", return_value=existing_pick_rows), \
            patch("app.scrape_us_picks", return_value=refreshed_pick_rows) as pick_scrape, \
            patch("app.save_us_picks_to_supabase") as save_picks:
            response = self.client.get("/system-results?date=02-05-2026&mode=pick&live=1")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertEqual("live-scraper", payload["source"])
        self.assertEqual(1, payload["picks"]["count"])
        pick_scrape.assert_called_once_with("02-05-2026", existing_rows=existing_pick_rows)
        save_picks.assert_called_once()
        self.assertEqual("02-05-2026", save_picks.call_args.args[0])
        self.assertEqual("US-P3-FL-PICK-3-EVENING", save_picks.call_args.args[1][0]["id"])
        self.assertEqual("9-2-0", save_picks.call_args.args[1][0]["number"])

    def test_today_live_pick_uses_cached_snapshot_and_triggers_background_refresh(self):
        existing_pick_rows = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Evening Draw",
            "date": "02-05-2026",
            "number": "9-2-0",
            "pick3": "9-2-0",
        }]
        with patch("app.fetch_pick_rows_from_supabase", return_value=existing_pick_rows), \
            patch("app.get_dr_date_str", return_value="02-05-2026"), \
            patch("app.schedule_background_pick_refresh", return_value=True) as background_refresh, \
            patch("app.scrape_us_picks") as pick_scrape, \
            patch("app.save_us_picks_to_supabase") as save_picks:
            response = self.client.get("/system-results?date=02-05-2026&mode=pick&live=1")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertEqual("live-scraper", payload["source"])
        self.assertEqual(1, payload["picks"]["count"])
        background_refresh.assert_called_once_with("02-05-2026")
        pick_scrape.assert_not_called()
        save_picks.assert_not_called()

    def test_today_live_pick_without_cached_snapshot_scrapes_inline(self):
        refreshed_pick_rows = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Evening Draw",
            "date": "02-05-2026",
            "number": "9-2-0",
            "pick3": "9-2-0",
        }]
        with patch("app.fetch_pick_rows_from_supabase", return_value=[]), \
            patch("app.get_dr_date_str", return_value="02-05-2026"), \
            patch("app.schedule_background_pick_refresh", return_value=False) as background_refresh, \
            patch("app.scrape_us_picks", return_value=refreshed_pick_rows) as pick_scrape, \
            patch("app.save_us_picks_to_supabase") as save_picks:
            response = self.client.get("/system-results?date=02-05-2026&mode=pick&live=1")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertEqual(1, payload["picks"]["count"])
        background_refresh.assert_not_called()
        pick_scrape.assert_called_once_with("02-05-2026", existing_rows=[])
        save_picks.assert_called_once()

    def test_today_live_pick_reuses_fresh_memory_snapshot_without_supabase_lookup(self):
        cached_pick_rows = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Evening Draw",
            "date": "02-05-2026",
            "number": "9-2-0",
            "pick3": "9-2-0",
        }]
        app.set_pick_scrape_cache("02-05-2026", cached_pick_rows)
        with patch("app.get_dr_date_str", return_value="02-05-2026"), \
            patch("app.fetch_pick_rows_from_supabase") as fetch_picks, \
            patch("app.schedule_background_pick_refresh") as background_refresh, \
            patch("app.scrape_us_picks") as pick_scrape:
            response = self.client.get("/system-results?date=02-05-2026&mode=pick&live=1")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertEqual(1, payload["picks"]["count"])
        fetch_picks.assert_not_called()
        background_refresh.assert_not_called()
        pick_scrape.assert_not_called()

    def test_today_live_pick_reuses_recent_live_response_cache(self):
        pick_rows = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Evening Draw",
            "date": "02-05-2026",
            "number": "9-2-0",
            "pick3": "9-2-0",
        }]
        with patch("app.get_dr_date_str", return_value="02-05-2026"), \
            patch("app.pick_rows_for_request_date", return_value=pick_rows) as pick_fetch:
            first = self.client.get("/system-results?date=02-05-2026&mode=pick&live=1")
            second = self.client.get("/system-results?date=02-05-2026&mode=pick&live=1")

        first_payload = json.loads(first.data.decode("utf-8"))
        second_payload = json.loads(second.data.decode("utf-8"))
        self.assertEqual(200, first.status_code)
        self.assertEqual(200, second.status_code)
        self.assertEqual(1, first_payload["picks"]["count"])
        self.assertEqual(1, second_payload["picks"]["count"])
        pick_fetch.assert_called_once_with("02-05-2026")

    def test_schedule_background_lottery_refresh_starts_thread(self):
        with patch("app.threading.Thread") as thread_ctor:
            started = app.schedule_background_lottery_refresh("02-05-2026")

        self.assertTrue(started)
        thread_ctor.assert_called_once()
        thread_ctor.return_value.start.assert_called_once()

    def test_schedule_background_pick_refresh_rate_limits_same_date(self):
        with patch("app.threading.Thread") as thread_ctor, \
            patch("app.time.time", side_effect=[1000.0, 1010.0]):
            first = app.schedule_background_pick_refresh("02-05-2026")
            app._pick_refresh_inflight.clear()
            second = app.schedule_background_pick_refresh("02-05-2026")

        self.assertTrue(first)
        self.assertFalse(second)
        thread_ctor.assert_called_once()

    def test_today_live_both_reuses_fresh_section_caches(self):
        lottery_payload = {
            "date": "02-05-2026",
            "mode": "lottery",
            "source": "live-scraper",
            "generatedAt": "2026-05-02T12:00:00Z",
            "lotteries": {
                "section": "lotteries",
                "count": 1,
                "results": [{"id": "1", "name": "La Primera Día", "date": "02-05-2026", "number": "01-02-03"}],
            },
        }
        pick_payload = {
            "date": "02-05-2026",
            "mode": "pick",
            "source": "live-scraper",
            "generatedAt": "2026-05-02T12:00:01Z",
            "picks": {
                "section": "picks",
                "count": 1,
                "results": [{"id": "US-P3-FL-PICK-3-EVENING", "name": "Florida Pick 3 Evening Draw", "date": "02-05-2026", "number": "9-2-0"}],
            },
        }
        app.set_live_system_results_cache("02-05-2026", "lottery", lottery_payload)
        app.set_live_system_results_cache("02-05-2026", "pick", pick_payload)
        with patch("app.get_dr_date_str", return_value="02-05-2026"), \
            patch("app.live_results_sections_for_date") as live_fetch:
            response = self.client.get("/system-results?date=02-05-2026&mode=both&live=1")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertEqual(1, payload["lotteries"]["count"])
        self.assertEqual(1, payload["picks"]["count"])
        live_fetch.assert_not_called()

    def test_admin_can_create_manual_override_and_results_show_it(self):
        with patch("app.fetch_manual_overrides_from_supabase", return_value=[]), \
            patch("app.save_manual_overrides_to_supabase", side_effect=self.save_override_cache) as save_overrides:
            save_response = self.client.post(
                "/admin/results/manual-override",
                json={
                    "role": "admin",
                    "editedBy": "ramonc03",
                    "date": "02-05-2026",
                    "resultId": "19",
                    "name": "NJ Pick 3 Día",
                    "number": "3-7-1",
                    "game": "pick3",
                },
            )
        with patch("app.fetch_existing_from_supabase", return_value=[]), \
            patch("app.fetch_pick_rows_from_supabase", return_value=[]):
            results_response = self.client.get("/system-results?date=02-05-2026&mode=pick")

        save_payload = json.loads(save_response.data.decode("utf-8"))
        results_payload = json.loads(results_response.data.decode("utf-8"))
        self.assertEqual(200, save_response.status_code)
        self.assertTrue(save_payload["saved"])
        self.assertEqual(1, results_payload["picks"]["count"])
        self.assertEqual("19", results_payload["picks"]["results"][0]["id"])
        self.assertEqual("3-7-1", results_payload["picks"]["results"][0]["pick3"])
        self.assertTrue(results_payload["picks"]["results"][0]["isManualOverride"])
        save_overrides.assert_called()

    def test_manual_override_rejected_for_non_admin(self):
        response = self.client.post(
            "/admin/results/manual-override",
            json={
                "role": "cashier",
                "editedBy": "ramonc03",
                "date": "02-05-2026",
                "resultId": "19",
                "name": "NJ Pick 3 Día",
                "number": "3-7-1",
                "game": "pick3",
            },
        )

        self.assertEqual(403, response.status_code)

    def test_admin_can_delete_manual_override(self):
        with patch("app.fetch_existing_from_supabase", return_value=[]), \
            patch("app.fetch_pick_rows_from_supabase", return_value=[]), \
            patch("app.fetch_manual_overrides_from_supabase", return_value=[]), \
            patch("app.save_manual_overrides_to_supabase", side_effect=self.save_override_cache):
            self.client.post(
                "/admin/results/manual-override",
                json={
                    "role": "admin",
                    "editedBy": "ramonc03",
                    "date": "02-05-2026",
                    "resultId": "19",
                    "name": "NJ Pick 3 Día",
                    "number": "3-7-1",
                    "game": "pick3",
                },
            )

        with patch("app.fetch_existing_from_supabase", return_value=[]), \
            patch("app.fetch_pick_rows_from_supabase", return_value=[]), \
            patch("app.save_manual_overrides_to_supabase", side_effect=self.save_override_cache):
            delete_response = self.client.delete(
                "/admin/results/manual-override",
                json={
                    "role": "admin",
                    "editedBy": "ramonc03",
                    "date": "02-05-2026",
                    "resultId": "19",
                },
            )
            results_response = self.client.get("/system-results?date=02-05-2026&mode=pick")

        self.assertEqual(200, delete_response.status_code)
        results_payload = json.loads(results_response.data.decode("utf-8"))
        self.assertEqual(0, results_payload["picks"]["count"])

    def test_real_result_replaces_manual_override_when_different(self):
        with patch("app.fetch_manual_overrides_from_supabase", return_value=[]), \
            patch("app.save_manual_overrides_to_supabase", side_effect=self.save_override_cache):
            self.client.post(
                "/admin/results/manual-override",
                json={
                    "role": "admin",
                    "editedBy": "ramonc03",
                    "date": "02-05-2026",
                    "resultId": "19",
                    "name": "NJ Pick 3 Día",
                    "number": "3-7-1",
                    "game": "pick3",
                },
            )
        real_pick_rows = [{
            "id": "19",
            "name": "NJ Pick 3 Día",
            "date": "02-05-2026",
            "number": "5-8-9",
            "pick3": "5-8-9",
            "game": "pick3",
            "source": "official",
        }]
        with patch("app.fetch_pick_rows_from_supabase", return_value=real_pick_rows), \
            patch("app.save_manual_overrides_to_supabase") as save_overrides:
            response = self.client.get("/system-results?date=02-05-2026&mode=pick")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual("5-8-9", payload["picks"]["results"][0]["pick3"])
        self.assertFalse(payload["picks"]["results"][0].get("isManualOverride", False))
        save_overrides.assert_called()

    def test_today_live_pick_sets_served_from_supabase_snapshot(self):
        existing_pick_rows = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Evening Draw",
            "date": "02-05-2026",
            "number": "9-2-0",
            "pick3": "9-2-0",
        }]
        with patch("app.fetch_pick_rows_from_supabase", return_value=existing_pick_rows), \
            patch("app.get_dr_date_str", return_value="02-05-2026"), \
            patch("app.schedule_background_pick_refresh", return_value=True):
            response = self.client.get("/system-results?date=02-05-2026&mode=pick&live=1")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual("supabase-snapshot", payload["servedFrom"])

    def test_today_live_pick_sets_served_from_inline_scrape(self):
        refreshed_pick_rows = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Evening Draw",
            "date": "02-05-2026",
            "number": "9-2-0",
            "pick3": "9-2-0",
        }]
        with patch("app.fetch_pick_rows_from_supabase", return_value=[]), \
            patch("app.get_dr_date_str", return_value="02-05-2026"), \
            patch("app.schedule_background_pick_refresh", return_value=False), \
            patch("app.scrape_us_picks", return_value=refreshed_pick_rows), \
            patch("app.save_us_picks_to_supabase"):
            response = self.client.get("/system-results?date=02-05-2026&mode=pick&live=1")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual("inline-scrape", payload["servedFrom"])

    def test_today_live_pick_sets_served_from_response_cache(self):
        pick_rows = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Evening Draw",
            "date": "02-05-2026",
            "number": "9-2-0",
            "pick3": "9-2-0",
        }]
        with patch("app.get_dr_date_str", return_value="02-05-2026"), \
            patch("app.pick_rows_for_request_date", return_value=pick_rows):
            self.client.get("/system-results?date=02-05-2026&mode=pick&live=1")
            response = self.client.get("/system-results?date=02-05-2026&mode=pick&live=1")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual("response-cache", payload["servedFrom"])

    def test_today_live_system_results_logs_served_path_and_duration(self):
        pick_rows = [{
            "id": "US-P3-FL-PICK-3-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Evening Draw",
            "date": "02-05-2026",
            "number": "9-2-0",
            "pick3": "9-2-0",
        }]
        with patch("app.get_dr_date_str", return_value="02-05-2026"), \
            patch("app.pick_rows_for_request_date", return_value=pick_rows), \
            patch("builtins.print") as print_mock:
            self.client.get("/system-results?date=02-05-2026&mode=pick&live=1")

        printed = " ".join(str(arg) for arg in print_mock.call_args.args)
        self.assertIn("Results live request", printed)
        self.assertIn("mode=pick", printed)
        self.assertIn("servedFrom=", printed)
        self.assertIn("durationMs=", printed)

    def test_pick_results_expose_no_draw_and_backfill_metadata(self):
        pick_rows = [
            {
                "id": "US-P3-AR-CASH-3-MIDDAY",
                "state": "Arkansas",
                "stateCode": "AR",
                "game": "pick3",
                "gameName": "Cash 3",
                "draw": "Midday Draw",
                "date": "10-05-2026",
                "number": "",
                "status": "no_draw",
                "source": "no_draw",
                "noDrawReason": "calendar_rule",
                "backfilled": False,
            },
            {
                "id": "US-P3-DC-3-MIDDAY",
                "state": "District of Columbia",
                "stateCode": "DC",
                "game": "pick3",
                "gameName": "DC Lucky",
                "draw": "Midday Draw",
                "date": "10-05-2026",
                "number": "8-7-3",
                "status": "published",
                "source": "lotteryusa.com",
                "backfilled": True,
            },
        ]
        with patch("app.fetch_pick_rows_from_supabase", return_value=pick_rows):
            response = self.client.get("/pick-results?date=10-05-2026")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertEqual("no_draw", payload["results"][0]["status"])
        self.assertEqual("calendar_rule", payload["results"][0]["noDrawReason"])
        self.assertFalse(payload["results"][0]["isBackfilled"])
        self.assertTrue(payload["results"][1]["isBackfilled"])

    def test_system_results_live_both_runs_lottery_and_pick_in_parallel(self):
        def slow_lottery(_date_key):
            time.sleep(0.25)
            return [{"id": "1", "name": "La Primera Día", "date": "02-05-2026", "number": "01-02-03"}]

        def slow_pick(_date_key, _game_filter=""):
            time.sleep(0.25)
            return [{"id": "US-P3-FL-PICK-3-EVENING", "name": "Florida Pick 3 Evening Draw", "date": "02-05-2026", "number": "9-2-0", "pick3": "9-2-0"}]

        with patch("app.lottery_rows_for_request_date", side_effect=slow_lottery), \
            patch("app.pick_rows_for_request_date", side_effect=slow_pick):
            started = time.perf_counter()
            response = self.client.get("/system-results?date=02-05-2026&mode=both&live=1")
            elapsed = time.perf_counter() - started

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertEqual(1, payload["lotteries"]["count"])
        self.assertEqual(1, payload["picks"]["count"])
        self.assertLess(elapsed, 0.45, f"expected parallel live fetch, got {elapsed:.3f}s")

    def test_today_live_lottery_uses_cached_snapshot_and_triggers_background_refresh(self):
        with patch("app.fetch_existing_from_supabase", return_value=fake_results()), \
            patch("app.get_dr_date_str", return_value="02-05-2026"), \
            patch("app.schedule_background_lottery_refresh", return_value=True) as background_refresh, \
            patch("app.scrape") as scrape_mock:
            response = self.client.get("/system-results?date=02-05-2026&mode=lottery&live=1")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertEqual("live-scraper", payload["source"])
        self.assertEqual(24, payload["lotteries"]["count"])
        background_refresh.assert_called_once_with("02-05-2026")
        scrape_mock.assert_not_called()

    def test_today_live_lottery_without_cached_snapshot_scrapes_inline(self):
        live_rows = [{"id": "1", "name": "La Primera Día", "date": "02-05-2026", "number": "01-02-03"}]
        with patch("app.fetch_existing_from_supabase", return_value=[]), \
            patch("app.get_dr_date_str", return_value="02-05-2026"), \
            patch("app.schedule_background_lottery_refresh", return_value=False) as background_refresh, \
            patch("app.scrape", return_value=live_rows) as scrape_mock:
            response = self.client.get("/system-results?date=02-05-2026&mode=lottery&live=1")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertEqual(1, payload["lotteries"]["count"])
        background_refresh.assert_not_called()
        scrape_mock.assert_called_once_with("02-05-2026")

    def test_run_scraper_uses_configured_fallback_key_when_render_env_is_missing(self):
        lottery_rows = [
            {"id": "1", "name": "La Primera Día", "date": "02-05-2026", "number": "01-02-03"},
        ]
        pick_rows = [
            {
                "id": "US-P3-FL-PICK-3-EVENING",
                "state": "Florida",
                "stateCode": "FL",
                "game": "pick3",
                "gameName": "Pick 3",
                "draw": "Evening Draw",
                "date": "02-05-2026",
                "number": "9-2-0",
            },
        ]
        with patch("app.scrape", return_value=lottery_rows), \
            patch("app.scrape_us_picks", return_value=pick_rows), \
            patch("app.save_to_supabase") as save_lottery, \
            patch("app.save_us_picks_to_supabase") as save_picks, \
            patch.dict("os.environ", {}, clear=True):
            response = self.client.post("/run-scraper?date=02-05-2026")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertTrue(payload["saved"])
        save_lottery.assert_called_once()
        save_picks.assert_called_once()

    def test_users_state_reads_payload_from_supabase_kv(self):
        payload = {"admins": [{"id": "a1"}], "cajeros": [{"id": "c1"}], "supervisores": []}
        with patch("app.fetch_users_state_from_supabase", return_value=payload):
            response = self.client.get("/users-state")

        body = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertTrue(body["ok"])
        self.assertEqual(1, body["adminCount"])
        self.assertEqual(1, body["cashierCount"])
        self.assertEqual(payload, body["payload"])

    def test_users_state_update_persists_payload_to_supabase_kv(self):
        payload = {"admins": [], "cajeros": [], "supervisores": []}
        with patch("app.save_users_state_to_supabase") as save_mock:
            response = self.client.post("/users-state", json={"payload": payload})

        body = json.loads(response.data.decode("utf-8"))
        self.assertEqual(200, response.status_code)
        self.assertTrue(body["ok"])
        save_mock.assert_called_once_with(payload)

    def test_fetch_users_state_targets_legacy_users_table(self):
        payload = [{"payload": {"admins": [], "cajeros": [], "supervisores": []}}]

        class FakeResponse:
            def read(self):
                return json.dumps(payload).encode("utf-8")

        def fake_urlopen(request, timeout=0):
            self.assertIn("/rest/v1/lotterynet_users_state?", request.full_url)
            self.assertIn("scope=eq.global", request.full_url)
            self.assertIn("select=payload", request.full_url)
            self.assertEqual(8, timeout)
            return FakeResponse()

        with patch("app.urllib.request.urlopen", side_effect=fake_urlopen):
            value = app.fetch_users_state_from_supabase()

        self.assertEqual({"admins": [], "cajeros": [], "supervisores": []}, value)

    def test_save_users_state_targets_legacy_users_table(self):
        captured = {}

        def fake_urlopen(request, timeout=0):
            captured["url"] = request.full_url
            captured["method"] = request.get_method()
            captured["body"] = request.data.decode("utf-8")
            captured["timeout"] = timeout
            class FakeResponse:
                def read(self):
                    return b""
            return FakeResponse()

        with patch("app.urllib.request.urlopen", side_effect=fake_urlopen):
            app.save_users_state_to_supabase({"admins": [], "cajeros": [], "supervisores": []})

        body = json.loads(captured["body"])
        self.assertEqual("/rest/v1/lotterynet_users_state?on_conflict=scope", captured["url"].replace(app.SUPABASE_URL, ""))
        self.assertEqual("POST", captured["method"])
        self.assertEqual("global", body["scope"])
        self.assertIn("payload", body)
        self.assertEqual(15, captured["timeout"])


if __name__ == "__main__":
    unittest.main()
