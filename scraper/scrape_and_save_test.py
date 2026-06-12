import os
import json
import unittest
import datetime
from unittest.mock import AsyncMock, patch

import scrape_and_save as scraper


class ScraperContractsTest(unittest.TestCase):
    def test_supabase_secret_key_accepts_service_role_env_alias(self):
        env = {"SUPABASE_SERVICE_ROLE_KEY": "service-role-key"}

        self.assertEqual("service-role-key", scraper.get_supabase_secret_key_from_env(env))

    def test_supabase_key_prefers_publishable_for_results_flow(self):
        env = {
            "SUPABASE_KEY": "sb_publishable_public",
            "SUPABASE_SERVICE_ROLE_KEY": "service-role-key",
            "SUPABASE_ANON_KEY": "anon-key",
        }

        self.assertEqual("sb_publishable_public", scraper.get_supabase_key_from_env(env))

    def test_supabase_key_falls_back_to_explicit_key(self):
        env = {"SUPABASE_KEY": "explicit-key"}

        self.assertEqual("explicit-key", scraper.get_supabase_key_from_env(env))

    def test_supabase_key_prefers_working_publishable_over_secret_alias(self):
        env = {
            "SUPABASE_KEY": "sb_publishable_public",
            "SUPABASE_SECRET_KEY": "sb_secret_stale",
        }

        self.assertEqual("sb_publishable_public", scraper.get_supabase_key_from_env(env))

    def test_supabase_secret_key_stays_separate_from_publishable_key(self):
        env = {
            "SUPABASE_PUBLISHABLE_KEY": "sb_publishable_public",
            "SUPABASE_SECRET_KEY": "sb_secret_server",
        }

        self.assertEqual("sb_publishable_public", scraper.get_supabase_key_from_env(env))
        self.assertEqual("sb_secret_server", scraper.get_supabase_secret_key_from_env(env))

    def test_default_scrape_dates_include_recent_backfill_days(self):
        with patch.object(scraper, "get_dr_date_str_for_offset", side_effect=["17-05-2026", "16-05-2026", "15-05-2026"]):
            dates = scraper.default_scrape_dates()

        self.assertEqual(["17-05-2026", "16-05-2026", "15-05-2026"], dates)

    def test_non_current_backfill_runs_when_pick_catalog_row_is_absent(self):
        existing_picks = [
            {"id": "US-P3-FL-PICK-3-MIDDAY", "number": "1-2-3", "status": "published"},
        ]
        catalog = [
            {"id": "US-P3-FL-PICK-3-MIDDAY", "game": "pick3"},
            {"id": "US-P3-LA-PICK-3-DAY", "game": "pick3"},
        ]

        with patch.object(scraper, "static_us_pick_catalog_rows", return_value=catalog):
            should_run = scraper.non_current_backfill_should_run([], existing_picks)

        self.assertTrue(should_run)

    def test_missing_us_pick_catalog_ids_detects_louisiana_gap(self):
        existing_picks = [
            {"id": "US-P3-FL-PICK-3-MIDDAY", "number": "1-2-3"},
            {"id": "US-P4-FL-PICK-4-MIDDAY", "number": "1-2-3-4"},
        ]
        catalog = [
            {"id": "US-P3-FL-PICK-3-MIDDAY", "game": "pick3"},
            {"id": "US-P4-FL-PICK-4-MIDDAY", "game": "pick4"},
            {"id": "US-P3-LA-PICK-3-DAY", "game": "pick3"},
            {"id": "US-P4-LA-PICK-4-DAY", "game": "pick4"},
        ]

        self.assertEqual(
            ["US-P3-LA-PICK-3-DAY", "US-P4-LA-PICK-4-DAY"],
            scraper.missing_us_pick_catalog_ids(existing_picks, catalog),
        )

    def test_supabase_rest_headers_send_sb_secret_as_bearer_key(self):
        headers = scraper.supabase_rest_headers("sb_secret_abc123")

        self.assertEqual("sb_secret_abc123", headers["apikey"])
        self.assertEqual("Bearer sb_secret_abc123", headers["Authorization"])

    def test_supabase_rest_headers_keep_legacy_jwt_authorization(self):
        legacy_key = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test"

        headers = scraper.supabase_rest_headers(legacy_key)

        self.assertEqual(legacy_key, headers["apikey"])
        self.assertEqual(f"Bearer {legacy_key}", headers["Authorization"])

    def test_prune_stale_us_pick_rows_when_catalog_is_complete(self):
        existing = [
            {"id": "US-P3-FL-PICK-3-MIDDAY", "number": "1-2-3"},
            {"id": "US-P3-OLD-STALE-ID", "number": "9-9-9"},
        ]
        incoming = [
            {"id": "US-P3-FL-PICK-3-MIDDAY", "number": "4-5-6"},
            {"id": "US-P3-FL-PICK-3-EVENING", "number": "7-8-9"},
        ]
        catalog = [
            {"id": "US-P3-FL-PICK-3-MIDDAY"},
            {"id": "US-P3-FL-PICK-3-EVENING"},
        ]

        pruned = scraper.prune_stale_us_pick_rows_when_catalog_is_complete(existing, incoming, catalog_rows=catalog)

        self.assertEqual(["US-P3-FL-PICK-3-MIDDAY"], [row["id"] for row in pruned])

    def test_prune_stale_us_pick_rows_keeps_existing_when_catalog_is_partial(self):
        existing = [
            {"id": "US-P3-FL-PICK-3-MIDDAY", "number": "1-2-3"},
            {"id": "US-P3-OLD-STALE-ID", "number": "9-9-9"},
        ]
        incoming = [{"id": "US-P3-FL-PICK-3-MIDDAY", "number": "4-5-6"}]
        catalog = [
            {"id": "US-P3-FL-PICK-3-MIDDAY"},
            {"id": "US-P3-FL-PICK-3-EVENING"},
        ]

        pruned = scraper.prune_stale_us_pick_rows_when_catalog_is_complete(existing, incoming, catalog_rows=catalog)

        self.assertEqual(existing, pruned)

    def test_prune_stale_us_pick_rows_removes_absent_pending_rows(self):
        existing = [
            {"id": "US-P3-FL-PICK-3-MIDDAY", "number": "1-2-3"},
            {"id": "US-P3-OLD-PENDING-ID", "number": "", "status": "pending"},
        ]
        incoming = [{"id": "US-P3-FL-PICK-3-MIDDAY", "number": "4-5-6"}]

        pruned = scraper.prune_stale_us_pick_rows_when_catalog_is_complete(existing, incoming, catalog_rows=[])

        self.assertEqual(["US-P3-FL-PICK-3-MIDDAY"], [row["id"] for row in pruned])

    def test_suppress_early_us_pick_result_before_official_draw_time(self):
        rows = [{
            "id": "US-P3-NJ-PICK-3-MIDDAY",
            "state": "New Jersey",
            "stateCode": "NJ",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Midday Draw",
            "date": "10-05-2026",
            "number": "8-1-4",
            "pick3": "8-1-4",
            "source": "lotteryusa.com",
        }]

        filtered = scraper.suppress_early_us_pick_results(
            "10-05-2026",
            rows,
            now_utc=datetime.datetime(2026, 5, 10, 16, 58, tzinfo=datetime.UTC),
        )

        self.assertEqual("", filtered[0]["number"])
        self.assertEqual("", filtered[0]["pick3"])
        self.assertEqual("pending", filtered[0]["status"])
        self.assertEqual("early-result-suppressed", filtered[0]["source"])

    def test_keep_us_pick_result_at_official_draw_time(self):
        rows = [{
            "id": "US-P3-NJ-PICK-3-MIDDAY",
            "state": "New Jersey",
            "stateCode": "NJ",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Midday Draw",
            "date": "10-05-2026",
            "number": "8-1-4",
            "pick3": "8-1-4",
            "source": "lotteryusa.com",
        }]

        filtered = scraper.suppress_early_us_pick_results(
            "10-05-2026",
            rows,
            now_utc=datetime.datetime(2026, 5, 10, 16, 59, tzinfo=datetime.UTC),
        )

        self.assertEqual("8-1-4", filtered[0]["number"])
        self.assertEqual("8-1-4", filtered[0]["pick3"])
        self.assertNotEqual("early-result-suppressed", filtered[0].get("source"))

    def test_suppress_early_us_pick_result_for_day_alias_using_midday_time(self):
        rows = [{
            "id": "US-P3-ME-PICK-3-DAY",
            "state": "Maine",
            "stateCode": "ME",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Day Draw",
            "date": "10-05-2026",
            "number": "1-2-3",
            "pick3": "1-2-3",
            "source": "lotteryusa.com",
        }]

        filtered = scraper.suppress_early_us_pick_results(
            "10-05-2026",
            rows,
            now_utc=datetime.datetime(2026, 5, 10, 17, 9, tzinfo=datetime.UTC),
        )

        self.assertEqual("", filtered[0]["number"])
        self.assertEqual("pending", filtered[0]["status"])

    def test_keep_us_pick_day_alias_result_at_official_midday_time(self):
        rows = [{
            "id": "US-P3-ME-PICK-3-DAY",
            "state": "Maine",
            "stateCode": "ME",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Day Draw",
            "date": "10-05-2026",
            "number": "1-2-3",
            "pick3": "1-2-3",
            "source": "lotteryusa.com",
        }]

        filtered = scraper.suppress_early_us_pick_results(
            "10-05-2026",
            rows,
            now_utc=datetime.datetime(2026, 5, 10, 17, 10, tzinfo=datetime.UTC),
        )

        self.assertEqual("1-2-3", filtered[0]["number"])
        self.assertNotEqual("early-result-suppressed", filtered[0].get("source"))

    def test_suppress_nj_evening_before_official_draw_time_and_publish_at_draw_time(self):
        rows = [{
            "id": "US-P3-NJ-PICK-3-EVENING",
            "state": "New Jersey",
            "stateCode": "NJ",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Evening Draw",
            "date": "10-05-2026",
            "number": "5-1-4",
            "pick3": "5-1-4",
            "source": "lotteryusa.com",
        }]

        before = scraper.suppress_early_us_pick_results(
            "10-05-2026",
            rows,
            now_utc=datetime.datetime(2026, 5, 11, 2, 56, tzinfo=datetime.UTC),
        )
        at_draw = scraper.suppress_early_us_pick_results(
            "10-05-2026",
            rows,
            now_utc=datetime.datetime(2026, 5, 11, 2, 57, tzinfo=datetime.UTC),
        )

        self.assertEqual("", before[0]["number"])
        self.assertEqual("pending", before[0]["status"])
        self.assertEqual("5-1-4", at_draw[0]["number"])

    def test_us_pick_draw_time_uses_dst_aware_state_timezone(self):
        row = {
            "id": "US-P3-NJ-PICK-3-MIDDAY",
            "stateCode": "NJ",
            "draw": "Midday Draw",
            "date": "10-05-2026",
            "number": "8-1-4",
        }
        winter_row = dict(row, date="10-01-2026")

        self.assertEqual(
            datetime.datetime(2026, 5, 10, 16, 59, tzinfo=datetime.UTC),
            scraper.us_pick_official_draw_utc(row, "10-05-2026"),
        )
        self.assertEqual(
            datetime.datetime(2026, 1, 10, 17, 59, tzinfo=datetime.UTC),
            scraper.us_pick_official_draw_utc(winter_row, "10-01-2026"),
        )

    def test_recently_closed_pick_rows_prioritize_due_draws(self):
        catalog = [
            {
                "id": "US-P3-TN-CASH-3-MORNING",
                "stateCode": "TN",
                "game": "pick3",
                "draw": "Morning Draw",
            },
            {
                "id": "US-P3-NJ-PICK-3-EVENING",
                "stateCode": "NJ",
                "game": "pick3",
                "draw": "Evening Draw",
            },
        ]

        rows = scraper.recently_closed_us_pick_catalog_rows(
            "29-05-2026",
            catalog_rows=catalog,
            now_utc=datetime.datetime(2026, 5, 29, 14, 35, tzinfo=datetime.UTC),
            lookback_minutes=20,
        )

        self.assertEqual(["US-P3-TN-CASH-3-MORNING"], [row["id"] for row in rows])

    def test_recently_closed_pick_rows_keep_pending_due_rows_after_short_window(self):
        catalog = [
            {
                "id": "US-P3-NJ-PICK-3-MIDDAY",
                "stateCode": "NJ",
                "game": "pick3",
                "draw": "Midday Draw",
            },
        ]
        existing = [
            {
                "id": "US-P3-NJ-PICK-3-MIDDAY",
                "stateCode": "NJ",
                "game": "pick3",
                "draw": "Midday Draw",
                "number": "",
                "status": "pending",
            }
        ]

        rows = scraper.recently_closed_us_pick_catalog_rows(
            "29-05-2026",
            catalog_rows=catalog,
            existing_rows=existing,
            now_utc=datetime.datetime(2026, 5, 29, 20, 0, tzinfo=datetime.UTC),
            lookback_minutes=20,
        )

        self.assertEqual(["US-P3-NJ-PICK-3-MIDDAY"], [row["id"] for row in rows])

    def test_backend_pick_schedule_stays_aligned_with_android_critical_rows(self):
        default_android_root = os.path.abspath(os.path.join(
            os.path.dirname(__file__),
            "..",
            "..",
            "lotterynet_android",
        ))
        android_root = os.environ.get("LOTTERYNET_ANDROID_ROOT", default_android_root)
        android_file = os.path.abspath(os.path.join(
            android_root,
            "app",
            "src",
            "main",
            "java",
            "com",
            "lotterynet",
            "pro",
            "core",
            "catalog",
            "UsPickScheduleResolver.kt",
        ))
        if not os.path.exists(android_file):
            self.skipTest(f"Android schedule file is not available in this checkout: {android_file}")
        with open(android_file, "r", encoding="utf-8") as schedule_file:
            android_text = schedule_file.read()

        expected_times = {
            '("NJ" to "MIDDAY") to "12:59 PM"',
            '("NJ" to "EVENING") to "10:57 PM"',
            '("ME" to "MIDDAY") to "1:10 PM"',
            '("NY" to "MIDDAY") to "2:30 PM"',
            '("FL" to "EVENING") to "9:45 PM"',
            '("TX" to "NIGHT") to "10:12 PM"',
            '("CA" to "EVENING") to "6:30 PM"',
        }
        expected_zones = {
            '"NJ" to "America/New_York"',
            '"ME" to "America/New_York"',
            '"NY" to "America/New_York"',
            '"FL" to "America/New_York"',
            '"TX" to "America/Chicago"',
            '"CA" to "America/Los_Angeles"',
        }

        for snippet in expected_times | expected_zones:
            self.assertIn(snippet, android_text)

    def test_merge_us_pick_results_marks_numbered_pending_rows_published(self):
        existing = [{
            "id": "US-P4-AR-CASH-4-DAY",
            "date": "14-05-2026",
            "game": "pick4",
            "number": "1-4-3-2",
            "status": "pending",
        }]

        merged = scraper.merge_us_pick_results_by_id(existing, [], observed_at="2026-05-14T02:00:00Z")

        self.assertEqual("published", merged[0]["status"])

    def test_prune_stale_us_pick_rows_removes_known_west_virginia_aliases(self):
        existing = [
            {"id": "US-P3-WV-DAILY-3-09-00-PM", "number": "6-3-3"},
            {"id": "US-P3-WV-DAILY-3-01-30-PM", "number": "6-3-3"},
            {"id": "US-P4-WV-DAILY-4-DAY", "number": "0-7-9-2"},
        ]
        incoming = [
            {"id": "US-P3-WV-DAILY-3-DAY", "number": "6-3-3"},
        ]

        pruned = scraper.prune_stale_us_pick_rows_when_catalog_is_complete(existing, incoming)

        self.assertEqual(["US-P4-WV-DAILY-4-DAY"], [row["id"] for row in pruned])

    def test_authoritative_nj_ids_cover_pick_and_new_jersey(self):
        self.assertEqual({"19", "20", "21", "22", "25", "26"}, scraper.AUTHORITATIVE_NJ_IDS)

    def test_tracked_remote_ids_include_core_rd_king_and_haiti_bolet(self):
        self.assertEqual(
            {
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15", "16", "17",
                "18", "23", "24", "25", "26", "27", "28", "29", "30",
                "31", "32", "33", "34", "35", "36", "37", "38", "39",
                "40", "41", "42", "43", "44", "45", "46",
            },
            scraper.TRACKED_REMOTE_RESULT_IDS,
        )

    def test_parse_loterias_dominicanas_api_site_reads_core_midday_results(self):
        payload = {
            "siteCompanies": [
                {
                    "title": "La Primera",
                    "siteGames": [
                        {
                            "title": "La Primera Día",
                            "game": {
                                "sessions": [
                                    {
                                        "date": "2026-06-12T04:00:00.000Z",
                                        "score": [["64", "00", "68"]],
                                    }
                                ]
                            },
                        }
                    ],
                },
                {
                    "title": "La Suerte",
                    "siteGames": [
                        {
                            "title": "La Suerte 12:30",
                            "game": {
                                "sessions": [
                                    {
                                        "date": "2026-06-12T04:00:00.000Z",
                                        "score": [["29", "59", "49"]],
                                    }
                                ]
                            },
                        }
                    ],
                },
                {
                    "title": "LoteDom",
                    "siteGames": [
                        {
                            "title": "Quiniela LoteDom",
                            "game": {
                                "sessions": [
                                    {
                                        "date": "2026-06-12T04:00:00.000Z",
                                        "score": [["10", "99", "97"]],
                                    }
                                ]
                            },
                        }
                    ],
                },
            ],
        }

        rows = scraper.parse_loterias_dominicanas_api_site(payload, "12-06-2026")

        self.assertEqual(
            [
                {"id": "1", "name": "La Primera Día", "date": "12-06-2026", "number": "64-00-68"},
                {"id": "3", "name": "La Suerte 12:30", "date": "12-06-2026", "number": "29-59-49"},
                {"id": "7", "name": "Quiniela LoteDom", "date": "12-06-2026", "number": "10-99-97"},
            ],
            rows,
        )

    def test_miloteria_maps_new_jersey_pm(self):
        self.assertEqual("26", scraper.MILOTERIA_NJ_MAP["new jersey pm"]["id"])
        self.assertEqual("New Jersey PM", scraper.MILOTERIA_NJ_MAP["new jersey pm"]["name"])

    def test_us_pick_result_ids_include_draw_label(self):
        self.assertEqual(
            "US-P3-FL-PICK-3-MIDDAY",
            scraper.build_us_pick_result_id("pick3", "FL", "Pick 3", "Midday Draw"),
        )
        self.assertEqual(
            "US-P4-NY-WIN-4-EVENING",
            scraper.build_us_pick_result_id("pick4", "NY", "Win 4", "Evening Draw"),
        )

    def test_static_catalog_keeps_west_virginia_daily_3_on_app_day_id(self):
        rows = [
            row for row in scraper.static_us_pick_catalog_rows(games=["pick3"])
            if row.get("stateCode") == "WV" and row.get("gameName") == "Daily 3"
        ]

        self.assertEqual(["US-P3-WV-DAILY-3-DAY"], [row["id"] for row in rows])
        self.assertEqual(["Day Draw"], [row["draw"] for row in rows])

    def test_parse_pick_overview_keeps_midday_and_evening_as_separate_rows(self):
        html = """
        <section>
          <img alt="Florida Pick 3 Latest Draws!" />
          <p>08 May 26 Midday Draw</p>
          <ul><li>1</li><li>2</li><li>3</li></ul>
          <a href="https://fl.pick-3.com">Check Numbers</a>
        </section>
        <section>
          <img alt="Florida Pick 3 Latest Draws!" />
          <p>08 May 26 Evening Draw</p>
          <ul><li>9</li><li>2</li><li>0</li></ul>
          <a href="https://fl.pick-3.com">Check Numbers</a>
        </section>
        """

        rows = scraper.parse_us_pick_overview(html, game="pick3")

        self.assertEqual(
            ["US-P3-FL-PICK-3-EVENING", "US-P3-FL-PICK-3-MIDDAY"],
            sorted(row["id"] for row in rows),
        )
        self.assertEqual(["1-2-3", "9-2-0"], sorted(row["number"] for row in rows))

    def test_parse_pick_overview_keeps_new_jersey_pick_rows(self):
        html = """
        <section>
          <img alt="New Jersey Pick 3 Latest Draws!" />
          <p>08 May 26 Midday Draw</p>
          <ul><li>3</li><li>8</li><li>3</li></ul>
          <a href="https://nj.pick-3.com">Check Numbers</a>
        </section>
        """

        rows = scraper.parse_us_pick_overview(html, game="pick3")

        self.assertEqual(["US-P3-NJ-PICK-3-MIDDAY"], [row["id"] for row in rows])
        self.assertEqual(["3-8-3"], [row["number"] for row in rows])

    def test_parse_new_jersey_pick_home_keeps_midday_and_evening_without_fireball(self):
        html = """
        <div class="resultsHome">
          <div class="date">Saturday, May 9, 2026</div>
          <div class="result-box">
            <div class="box">
              <div class="text">Midday</div>
              <ul class="balls">
                <li class="resultBall ball number-part-01">4</li>
                <li class="resultBall ball number-part-02">7</li>
                <li class="resultBall ball number-part-03">6</li>
                <li class="resultBall ball number-part-04">1</li>
                <li class="resultBall ball fireball">6</li>
              </ul>
            </div>
            <div class="box">
              <div class="text">Evening</div>
              <ul class="balls">
                <li class="resultBall ball number-part-01">9</li>
                <li class="resultBall ball number-part-02">3</li>
                <li class="resultBall ball number-part-03">0</li>
                <li class="resultBall ball number-part-04">8</li>
                <li class="resultBall ball fireball">8</li>
              </ul>
            </div>
          </div>
        </div>
        """

        rows = scraper.parse_new_jersey_pick_home(html, game="pick4")

        self.assertEqual(
            ["US-P4-NJ-PICK-4-EVENING", "US-P4-NJ-PICK-4-MIDDAY"],
            sorted(row["id"] for row in rows),
        )
        self.assertEqual(["4-7-6-1", "9-3-0-8"], sorted(row["number"] for row in rows))
        self.assertEqual(["09-05-2026", "09-05-2026"], sorted(row["date"] for row in rows))

    def test_sanitize_unreleased_nj_evening_keeps_today_pending_before_draw(self):
        rows = [
            {
                "id": "US-P3-NJ-PICK-3-EVENING",
                "state": "New Jersey",
                "stateCode": "NJ",
                "game": "pick3",
                "gameName": "Pick 3",
                "draw": "Evening Draw",
                "date": "24-05-2026",
                "number": "1-3-2",
                "pick3": "1-3-2",
            },
            {
                "id": "US-P3-NJ-PICK-3-MIDDAY",
                "state": "New Jersey",
                "stateCode": "NJ",
                "game": "pick3",
                "gameName": "Pick 3",
                "draw": "Midday Draw",
                "date": "24-05-2026",
                "number": "8-1-4",
                "pick3": "8-1-4",
            },
        ]
        now_et = datetime.datetime(2026, 5, 24, 15, 45, tzinfo=scraper.ZoneInfo("America/New_York"))

        sanitized = scraper.sanitize_unreleased_nj_pick_rows(rows, "24-05-2026", now_et=now_et)
        by_id = {row["id"]: row for row in sanitized}

        self.assertEqual("", by_id["US-P3-NJ-PICK-3-EVENING"]["number"])
        self.assertEqual("", by_id["US-P3-NJ-PICK-3-EVENING"]["pick3"])
        self.assertEqual("pending", by_id["US-P3-NJ-PICK-3-EVENING"]["status"])
        self.assertEqual("8-1-4", by_id["US-P3-NJ-PICK-3-MIDDAY"]["number"])

    def test_sanitize_unreleased_nj_evening_allows_after_draw_time(self):
        rows = [{
            "id": "US-P4-NJ-PICK-4-EVENING",
            "state": "New Jersey",
            "stateCode": "NJ",
            "game": "pick4",
            "gameName": "Pick 4",
            "draw": "Evening Draw",
            "date": "24-05-2026",
            "number": "2-5-0-8",
            "pick4": "2-5-0-8",
        }]
        now_et = datetime.datetime(2026, 5, 24, 23, 5, tzinfo=scraper.ZoneInfo("America/New_York"))

        sanitized = scraper.sanitize_unreleased_nj_pick_rows(rows, "24-05-2026", now_et=now_et)

        self.assertEqual("2-5-0-8", sanitized[0]["number"])

    def test_sanitize_unreleased_us_pick_rows_keeps_maine_evening_pending_before_draw(self):
        rows = [
            {
                "id": "US-P3-ME-PICK-3-EVENING",
                "state": "Maine",
                "stateCode": "ME",
                "game": "pick3",
                "gameName": "Pick 3",
                "draw": "Evening Draw",
                "date": "24-05-2026",
                "number": "5-9-1",
                "pick3": "5-9-1",
            },
            {
                "id": "US-P3-ME-PICK-3-DAY",
                "state": "Maine",
                "stateCode": "ME",
                "game": "pick3",
                "gameName": "Pick 3",
                "draw": "Day Draw",
                "date": "24-05-2026",
                "number": "9-2-8",
                "pick3": "9-2-8",
            },
        ]
        now_et = datetime.datetime(2026, 5, 24, 14, 0, tzinfo=scraper.ZoneInfo("America/New_York"))

        sanitized = scraper.sanitize_unreleased_us_pick_rows(rows, "24-05-2026", now=now_et)
        by_id = {row["id"]: row for row in sanitized}

        self.assertEqual("", by_id["US-P3-ME-PICK-3-EVENING"]["number"])
        self.assertEqual("pending", by_id["US-P3-ME-PICK-3-EVENING"]["status"])
        self.assertEqual("9-2-8", by_id["US-P3-ME-PICK-3-DAY"]["number"])

    def test_sanitize_unreleased_us_pick_rows_uses_state_timezone(self):
        rows = [{
            "id": "US-P3-TX-PICK-3-NIGHT",
            "state": "Texas",
            "stateCode": "TX",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Night Draw",
            "date": "24-05-2026",
            "number": "1-2-3",
            "pick3": "1-2-3",
        }]
        before_texas_draw = datetime.datetime(2026, 5, 24, 22, 0, tzinfo=scraper.ZoneInfo("America/New_York"))
        after_texas_draw = datetime.datetime(2026, 5, 24, 23, 30, tzinfo=scraper.ZoneInfo("America/New_York"))

        pending = scraper.sanitize_unreleased_us_pick_rows(rows, "24-05-2026", now=before_texas_draw)
        published = scraper.sanitize_unreleased_us_pick_rows(rows, "24-05-2026", now=after_texas_draw)

        self.assertEqual("", pending[0]["number"])
        self.assertEqual("1-2-3", published[0]["number"])

    def test_parse_new_jersey_pick_home_reads_marker_layout_date(self):
        html = """
        <main>
          <h2>Latest NJ Pick 4 Results</h2>
          <p>Saturday, May 9, 2026</p>
          <span class="resultBall alt ball drawTime middayDraw"></span>
          <span class="resultBall">4</span><span class="resultBall">7</span>
          <span class="resultBall">6</span><span class="resultBall">1</span>
          <span class="resultBall">6</span>
          <span class="resultBall alt ball drawTime eveningDraw"></span>
          <span class="resultBall">9</span><span class="resultBall">3</span>
          <span class="resultBall">0</span><span class="resultBall">8</span>
          <span class="resultBall">8</span>
        </main>
        """

        rows = scraper.parse_new_jersey_pick_home(html, game="pick4")

        self.assertEqual(["09-05-2026", "09-05-2026"], sorted(row["date"] for row in rows))
        self.assertEqual(["4-7-6-1", "9-3-0-8"], sorted(row["number"] for row in rows))

    def test_parse_us_pick_history_page_reads_pick3_results_box_dates(self):
        html = """
        <div class="resultsBox">
          <div class="date">Friday, May 8, 2026</div>
          <div class="box"><div>Midday Draw</div>
            <span class="resultBall ball number-part-01">8719</span>
            <span class="resultBall ball number-part-02">719</span>
            <span class="resultBall ball number-part-03">19</span>
            <span class="resultBall ball fireball">9</span>
          </div>
          <div class="box"><div>Evening Draw</div>
            <span class="resultBall ball number-part-01">9203</span>
            <span class="resultBall ball number-part-02">203</span>
            <span class="resultBall ball number-part-03">03</span>
            <span class="resultBall ball fireball">3</span>
          </div>
        </div>
        """

        rows = scraper.parse_us_pick_history_page(
            html,
            game="pick3",
            state_code="FL",
            state_name="Florida",
            game_name="Pick 3",
            target_date="08-05-2026",
        )

        self.assertEqual(
            ["US-P3-FL-PICK-3-EVENING", "US-P3-FL-PICK-3-MIDDAY"],
            sorted(row["id"] for row in rows),
        )
        self.assertEqual(["8-7-1", "9-2-0"], sorted(row["number"] for row in rows))

    def test_parse_us_pick_history_page_reads_pick4_marker_dates(self):
        html = """
        <div class="drawContainer">
          <a class="date"><span>Thursday, May 7, 2026</span></a>
          <ul class="drawBalls">
            <li class="resultBall alt ball drawTime middayDraw"></li>
            <li class="resultBall alt ball number-part-01">2</li>
            <li class="resultBall alt ball number-part-02">0</li>
            <li class="resultBall alt ball number-part-03">3</li>
            <li class="resultBall alt ball number-part-04">4</li>
            <li class="resultBall alt ball fireball">7</li>
          </ul>
          <ul class="drawBalls">
            <li class="resultBall alt ball drawTime eveningDraw"></li>
            <li class="resultBall alt ball number-part-01">4</li>
            <li class="resultBall alt ball number-part-02">3</li>
            <li class="resultBall alt ball number-part-03">4</li>
            <li class="resultBall alt ball number-part-04">7</li>
            <li class="resultBall alt ball fireball">5</li>
          </ul>
        </div>
        """

        rows = scraper.parse_us_pick_history_page(
            html,
            game="pick4",
            state_code="FL",
            state_name="Florida",
            game_name="Pick 4",
            target_date="07-05-2026",
        )

        self.assertEqual(["2-0-3-4", "4-3-4-7"], sorted(row["number"] for row in rows))
        self.assertEqual(["07-05-2026", "07-05-2026"], sorted(row["date"] for row in rows))

    def test_parse_us_pick_history_page_reads_single_draw_day_states_without_label(self):
        html = """
        <div class="genBox mBottom resultsBox colHalf">
          <div class="row fx -cn -al">
            <div class="date">Friday, May 8, 2026</div>
          </div>
          <div class="box">
            <ul class="balls alt">
              <li class="resultBall ball number-part-01 medium">6</li>
              <li class="resultBall ball number-part-02 medium">9</li>
              <li class="resultBall ball number-part-03 medium">1</li>
            </ul>
          </div>
        </div>
        """

        rows = scraper.parse_us_pick_history_page(
            html,
            game="pick3",
            state_code="WA",
            state_name="Washington",
            game_name="Pick 3",
            target_date="08-05-2026",
        )

        self.assertEqual(1, len(rows))
        self.assertEqual("US-P3-WA-PICK-3-DAY", rows[0]["id"])
        self.assertEqual("6-9-1", rows[0]["number"])

    def test_parse_us_pick_history_page_reads_catalog_single_draw_states_without_label(self):
        html = """
        <div class="resultsBox">
          <div class="date">Friday, May 8, 2026</div>
          <div class="box">
            <span class="resultBall">5</span>
            <span class="resultBall">6</span>
            <span class="resultBall">1</span>
          </div>
        </div>
        """

        rows = scraper.parse_us_pick_history_page(
            html,
            game="pick3",
            state_code="MN",
            state_name="Minnesota",
            game_name="Pick 3",
            target_date="08-05-2026",
        )

        self.assertEqual(1, len(rows))
        self.assertEqual("US-P3-MN-PICK-3-DAY", rows[0]["id"])
        self.assertEqual("5-6-1", rows[0]["number"])

    def test_parse_us_pick_history_page_maps_dc_day_to_catalog_midday(self):
        html = """
        <div class="resultsBox">
          <div class="date">Friday, May 8, 2026</div>
          <div>Draw #28061</div>
          <div>Day</div><span>3</span><span>4</span><span>8</span><span>15</span>
          <div>Evening</div><span>4</span><span>6</span><span>2</span><span>12</span>
          <div>Night</div><span>0</span><span>1</span><span>7</span><span>8</span>
        </div>
        """

        rows = scraper.parse_us_pick_history_page(
            html,
            game="pick3",
            state_code="DC",
            state_name="Washington DC",
            game_name="3",
            target_date="08-05-2026",
        )

        self.assertIn("US-P3-DC-3-MIDDAY", [row["id"] for row in rows])
        self.assertIn("US-P3-DC-3-EVENING", [row["id"] for row in rows])

    def test_parse_us_pick_history_page_reads_tennessee_rows_with_no_year_date(self):
        html = """
        <div class="resultsBox">
          <div>Friday, May 8, 6:28pm</div>
          <div>Evening</div>
          <span class="resultBall">0</span><span class="resultBall">7</span>
          <span class="resultBall">8</span><span class="resultBall">7</span>
        </div>
        """

        rows = scraper.parse_us_pick_history_page(
            html,
            game="pick3",
            state_code="TN",
            state_name="Tennessee",
            game_name="Cash 3",
            target_date="08-05-2026",
        )

        self.assertEqual(1, len(rows))
        self.assertEqual("US-P3-TN-CASH-3-06-28-PM", rows[0]["id"])
        self.assertEqual("0-7-8", rows[0]["number"])

    def test_parse_us_pick_history_page_maps_wv_single_draw_to_9pm_catalog_id(self):
        html = """
        <div class="resultsBox">
          <div class="date">Friday, May 8, 2026</div>
          <span class="resultBall">7</span>
          <span class="resultBall">2</span>
          <span class="resultBall">2</span>
        </div>
        """

        rows = scraper.parse_us_pick_history_page(
            html,
            game="pick3",
            state_code="WV",
            state_name="West Virginia",
            game_name="Daily 3",
            target_date="08-05-2026",
        )

        self.assertEqual(1, len(rows))
        self.assertEqual("US-P3-WV-DAILY-3-09-00-PM", rows[0]["id"])
        self.assertEqual("7-2-2", rows[0]["number"])

    def test_sunday_pick_no_draw_rows_fill_known_closed_draws(self):
        rows = []

        scraper.append_us_pick_calendar_no_draw_rows(rows, "10-05-2026")

        ids = {row["id"] for row in rows}
        self.assertIn("US-P3-TX-PICK-3-MORNING", ids)
        self.assertIn("US-P4-TX-DAILY-4-NIGHT", ids)
        self.assertIn("US-P4-TN-CASH-4-DAY", ids)
        self.assertTrue(all(row["status"] == "no_draw" for row in rows))

    def test_pick_no_draw_calendar_does_not_add_rows_on_friday(self):
        rows = []

        scraper.append_us_pick_calendar_no_draw_rows(rows, "08-05-2026")

        self.assertEqual([], rows)

    def test_github_actions_requires_supabase_key(self):
        env = {"GITHUB_ACTIONS": "true"}

        self.assertTrue(scraper.should_fail_without_supabase_key("", env))
        self.assertFalse(scraper.should_fail_without_supabase_key("present", env))
        self.assertFalse(scraper.should_fail_without_supabase_key("", {}))

    def test_parse_miloteria_date_handles_api_formats(self):
        self.assertEqual("26-04-2026", scraper.parse_miloteria_date("Sunday, Apr 26, 2026"))
        self.assertEqual("26-04-2026", scraper.parse_miloteria_date("04/26/2026 11:00:00 PM"))

    def test_haiti_bolet_sources_are_mapped_to_catalog_ids(self):
        sources_by_id = {source["id"]: source["name"] for source in scraper.ENLOTERIA_HAITI_BOLET_SOURCES}
        self.assertEqual("Haiti Bolet 11:30 AM", sources_by_id["27"])
        self.assertEqual("Haiti Bolet 6:30 PM", sources_by_id["28"])

    def test_enloteria_sources_cover_new_catalog_ids(self):
        sources_by_id = {source["id"]: source for source in scraper.ENLOTERIA_RESULT_SOURCES}

        for lottery_id in [str(value) for value in range(29, 47)]:
            self.assertIn(lottery_id, sources_by_id)

        self.assertEqual("Georgia Día", sources_by_id["44"]["name"])
        self.assertEqual("Georgia Tarde", sources_by_id["45"]["name"])
        self.assertEqual("Georgia Noche", sources_by_id["46"]["name"])
        self.assertEqual("Florida Noche", sources_by_id["17"]["name"])
        self.assertIn("resultados-florida-noche", sources_by_id["17"]["url"])
        self.assertEqual("Anguilla 10AM", sources_by_id["2"]["source_name"])
        self.assertEqual("Anguilla 1PM", sources_by_id["4"]["source_name"])
        self.assertEqual("Anguilla 6PM", sources_by_id["11"]["source_name"])
        self.assertEqual("Anguilla 9PM", sources_by_id["14"]["source_name"])

    def test_parse_enloteria_haiti_bolet_jsonld_event(self):
        html = """
        <script type="application/ld+json">
        {
          "@context": "https://schema.org",
          "@graph": [
            {
              "@type": "Event",
              "name": "Haiti Bolet 11:30 AM",
              "startDate": "2026-04-28T11:30:00-04:00",
              "description": "Resultados de Haiti Bolet 11:30 AM del 28 de abril de 2026. Números ganadores: 00, 54, 25."
            }
          ]
        }
        </script>
        """

        row = scraper.parse_enloteria_haiti_bolet_jsonld(
            html,
            lottery_id="27",
            lottery_name="Haiti Bolet 11:30 AM",
            target_date="28-04-2026",
        )

        self.assertEqual(
            {"id": "27", "name": "Haiti Bolet 11:30 AM", "date": "28-04-2026", "number": "00-54-25"},
            row,
        )

    def test_parse_enloteria_result_jsonld_supports_source_name_alias(self):
        html = """
        <script type="application/ld+json">
        {
          "@context": "https://schema.org",
          "@graph": [
            {
              "@type": "Event",
              "name": "Anguilla 10AM",
              "startDate": "2026-04-28T10:00:00-04:00",
              "description": "Resultados de Anguilla 10AM. Números ganadores: 08, 14, 52."
            }
          ]
        }
        </script>
        """

        row = scraper.parse_enloteria_result_jsonld_for_dates(
            html,
            lottery_id="2",
            lottery_name="Anguila Mañana",
            target_dates=["28-04-2026"],
            source_name="Anguilla 10AM",
        )

        self.assertEqual(
            {"id": "2", "name": "Anguila Mañana", "date": "28-04-2026", "number": "08-14-52"},
            row,
        )

    def test_recent_dr_dates_include_yesterday_and_day_before(self):
        self.assertEqual(
            ["29-04-2026", "28-04-2026", "27-04-2026"],
            scraper.recent_dr_dates("29-04-2026", days_back=2),
        )

    def test_merge_results_by_id_preserves_existing_and_adds_late_haiti_bolet(self):
        existing = [
            {"id": "1", "name": "La Primera Día", "number": "01-02-03"},
            {"id": "26", "name": "New Jersey PM", "number": "11-22-33"},
        ]
        fresh = [
            {"id": "27", "name": "Haiti Bolet 11:30 AM", "number": "03-21-01"},
            {"id": "28", "name": "Haiti Bolet 6:30 PM", "number": "52-35-42"},
        ]

        merged = scraper.merge_results_by_id(existing, fresh, observed_at="2026-05-03T05:40:00Z")

        self.assertEqual(["1", "26", "27", "28"], [row["id"] for row in merged])
        self.assertEqual("2026-05-03T05:40:00Z", merged[-1]["firstSeenAt"])
        self.assertEqual("2026-05-03T05:40:00Z", merged[-1]["lastSeenAt"])
        self.assertEqual(
            [],
            scraper.missing_tracked_result_ids([
                {"id": result_id}
                for result_id in scraper.TRACKED_REMOTE_RESULT_IDS
            ]),
        )

    def test_merge_results_by_id_preserves_first_seen_for_same_result(self):
        existing = [
            {
                "id": "27",
                "name": "Haiti Bolet 11:30 AM",
                "number": "03-21-01",
                "firstSeenAt": "2026-05-03T01:00:00Z",
            },
        ]
        fresh = [
            {"id": "27", "name": "Haiti Bolet 11:30 AM", "number": "03-21-01"},
        ]

        merged = scraper.merge_results_by_id(existing, fresh, observed_at="2026-05-03T05:40:00Z")

        self.assertEqual("2026-05-03T01:00:00Z", merged[0]["firstSeenAt"])
        self.assertEqual("2026-05-03T05:40:00Z", merged[0]["lastSeenAt"])

    def test_missing_tracked_result_ids_detects_haiti_gap(self):
        complete_except_haiti = [
            {"id": result_id}
            for result_id in scraper.TRACKED_REMOTE_RESULT_IDS
            if result_id not in {"27", "28"}
        ]

        self.assertEqual(["27", "28"], scraper.missing_tracked_result_ids(complete_except_haiti))

    def test_merge_results_by_id_sorts_mixed_numeric_and_pick_ids(self):
        merged = scraper.merge_results_by_id(
            existing=[{"id": "US-P4-IL-PICK-4-MORNING", "number": "1-2-3-4"}],
            results=[{"id": "1", "number": "11-22-33"}],
            observed_at="2026-05-10T00:00:00Z",
        )

        self.assertEqual(["1", "US-P4-IL-PICK-4-MORNING"], [row["id"] for row in merged])

    def test_parse_enloteria_haiti_bolet_uses_recent_available_result(self):
        html = """
        <script type="application/ld+json">
        {
          "@context": "https://schema.org",
          "@graph": [
            {
              "@type": "Event",
              "name": "Haiti Bolet 6:30 PM",
              "startDate": "2026-04-28T18:30:00-04:00",
              "description": "Resultados de Haiti Bolet 6:30 PM. Números ganadores: 12, 08, 44."
            }
          ]
        }
        </script>
        """

        row = scraper.parse_enloteria_haiti_bolet_jsonld_for_dates(
            html,
            lottery_id="28",
            lottery_name="Haiti Bolet 6:30 PM",
            target_dates=["29-04-2026", "28-04-2026", "27-04-2026"],
        )

        self.assertEqual(
            {"id": "28", "name": "Haiti Bolet 6:30 PM", "date": "28-04-2026", "number": "12-08-44"},
            row,
        )

    def test_parse_enloteria_haiti_bolet_dom_history_for_yesterday_and_day_before(self):
        html = """
        <div class="text-center">
          <h5>Haiti Bolet 11:30 AM</h5>
          <span>Martes 28 de abril, 2026</span>
          <span>11:30AM</span>
          <span>00</span><span>54</span><span>25</span>
        </div>
        <div class="text-center">
          <h5>Haiti Bolet 11:30 AM</h5>
          <span>Lunes 27 de abril, 2026</span>
          <span>11:30AM</span>
          <span>81</span><span>74</span><span>77</span>
        </div>
        """

        row = scraper.parse_enloteria_haiti_bolet_jsonld_for_dates(
            html,
            lottery_id="27",
            lottery_name="Haiti Bolet 11:30 AM",
            target_dates=["27-04-2026"],
        )

        self.assertEqual(
            {"id": "27", "name": "Haiti Bolet 11:30 AM", "date": "27-04-2026", "number": "81-74-77"},
            row,
        )

    def test_parse_enloteria_haiti_bolet_does_not_use_yesterday_as_today(self):
        html = """
        <div class="text-center">
          <h5>Haiti Bolet 11:30 AM</h5>
          <span>Miércoles 29 de abril, 2026</span>
          <span>11:30AM</span>
          <span>Avísame cuando salga</span>
        </div>
        <div class="text-center">
          <h5>Haiti Bolet 11:30 AM</h5>
          <span>Martes 28 de abril, 2026</span>
          <span>11:30AM</span>
          <span>00</span><span>54</span><span>25</span>
        </div>
        """

        row = scraper.parse_enloteria_haiti_bolet_jsonld_for_dates(
            html,
            lottery_id="27",
            lottery_name="Haiti Bolet 11:30 AM",
            target_dates=["29-04-2026"],
        )

        self.assertIsNone(row)

    def test_king_past_date_without_source_rows_becomes_no_draw(self):
        rows = scraper.build_king_no_draw_rows(
            "01-05-2026",
            seen_ids=set(),
            now_dr=datetime.datetime(2026, 5, 2, 10, 0, 0),
        )

        self.assertEqual(["23", "24"], [row["id"] for row in rows])
        self.assertEqual(["no_draw", "no_draw"], [row["status"] for row in rows])
        self.assertEqual(["", ""], [row["number"] for row in rows])

    def test_king_no_draw_does_not_override_existing_published_result(self):
        rows = scraper.build_king_no_draw_rows(
            "30-04-2026",
            seen_ids={"23", "24"},
            now_dr=datetime.datetime(2026, 5, 2, 10, 0, 0),
        )

        self.assertEqual([], rows)

    def test_king_today_without_source_rows_stays_pending_not_no_draw(self):
        rows = scraper.build_king_no_draw_rows(
            "02-05-2026",
            seen_ids=set(),
            now_dr=datetime.datetime(2026, 5, 2, 10, 0, 0),
        )

        self.assertEqual([], rows)

    def test_lotteryusa_state_pick_sources_parse_draw_links(self):
        html = """
        <a href="/florida/midday-pick-3/">Pick 3 Midday</a>
        <a href="/florida/pick-3/">Pick 3 Evening</a>
        <a href="/florida/midday-pick-4/">Pick 4 Midday</a>
        <a href="/florida/pick-4/">Pick 4 Evening</a>
        """

        sources = scraper.parse_lotteryusa_state_pick_sources(html, "Florida", "FL")

        by_draw = {(source["gameName"], source["draw"]): source for source in sources}
        self.assertEqual("https://www.lotteryusa.com/florida/midday-pick-3/", by_draw[("Pick 3", "Midday Draw")]["url"])
        self.assertEqual("https://www.lotteryusa.com/florida/pick-4/", by_draw[("Pick 4", "Evening Draw")]["url"])

    def test_lotteryusa_state_pick_sources_parse_connecticut_play4_day(self):
        html = """
        <a href="/connecticut/play-3/">Play3 Day</a>
        <a href="/connecticut/midday-4/">Play4 Day</a>
        <a href="/connecticut/play-4/">Play4 Night</a>
        """

        sources = scraper.parse_lotteryusa_state_pick_sources(html, "Connecticut", "CT")

        by_draw = {(source["gameName"], source["draw"]): source for source in sources}
        self.assertEqual(
            "https://www.lotteryusa.com/connecticut/midday-4/",
            by_draw[("Play 4", "Day Draw")]["url"],
        )

    def test_lotteryusa_catalog_rows_keep_existing_app_ids(self):
        html_by_url = {
            "https://www.lotteryusa.com/florida/": """
            <a href="/florida/pick-4/">Pick 4 Evening</a>
            """,
            "https://www.lotteryusa.com/florida/pick-4/": """
            <table><tbody id="js-state-results-table">
              <tr class="c-draw-card">
                <td><span class="c-draw-card__draw-date-sub">May 14, 2026</span></td>
                <td><ul><li class="c-ball">2</li><li class="c-ball">8</li><li class="c-ball">4</li><li class="c-ball">4</li></ul></td>
              </tr>
            </tbody></table>
            """,
        }

        async def fake_get(url, client=None):
            class FakeResponse:
                text = html_by_url[url]
                content = text.encode("utf-8")
            return FakeResponse()

        catalog = [{
            "id": "US-P4-FL-PICK-4-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick4",
            "gameName": "Pick 4",
            "draw": "Evening Draw",
        }]

        with patch.object(scraper, "async_lotteryusa_http_get", side_effect=fake_get):
            rows = scraper.sync_run(scraper._async_fetch_lotteryusa_pick_catalog_rows("14-05-2026", catalog))

        self.assertEqual(1, len(rows))
        self.assertEqual("US-P4-FL-PICK-4-EVENING", rows[0]["id"])
        self.assertEqual("2-8-4-4", rows[0]["number"])
        self.assertEqual("lotteryusa.com", rows[0]["source"])

    def test_lotteryusa_catalog_rows_use_direct_url_without_state_catalog_request(self):
        html_by_url = {
            "https://www.lotteryusa.com/connecticut/midday-4/": """
            <table><tbody id="js-state-results-table">
              <tr class="c-draw-card">
                <td><span class="c-draw-card__draw-date-sub">May 14, 2026</span></td>
                <td><ul><li class="c-ball">8</li><li class="c-ball">3</li><li class="c-ball">5</li><li class="c-ball">7</li></ul></td>
              </tr>
            </tbody></table>
            """,
        }

        async def fake_get(url, client=None):
            if url == "https://www.lotteryusa.com/connecticut/":
                raise AssertionError("state catalog should not be fetched when a direct URL exists")

            class FakeResponse:
                text = html_by_url[url]
                content = text.encode("utf-8")
            return FakeResponse()

        catalog = [{
            "id": "US-P4-CT-PLAY-4-DAY",
            "state": "Connecticut",
            "stateCode": "CT",
            "game": "pick4",
            "gameName": "Play 4",
            "draw": "Day Draw",
            "lotteryUsaUrl": "https://www.lotteryusa.com/connecticut/midday-4/",
        }]

        with patch.object(scraper, "async_lotteryusa_http_get", side_effect=fake_get):
            rows = scraper.sync_run(scraper._async_fetch_lotteryusa_pick_catalog_rows("14-05-2026", catalog))

        self.assertEqual(1, len(rows))
        self.assertEqual("US-P4-CT-PLAY-4-DAY", rows[0]["id"])
        self.assertEqual("8-3-5-7", rows[0]["number"])

    def test_lotteryusa_catalog_rows_retry_transient_direct_url_error(self):
        html = """
        <table><tbody id="js-state-results-table">
          <tr class="c-draw-card">
            <td><span class="c-draw-card__draw-date-sub">May 14, 2026</span></td>
            <td><ul><li class="c-ball">2</li><li class="c-ball">1</li><li class="c-ball">0</li><li class="c-ball">4</li></ul></td>
          </tr>
        </tbody></table>
        """
        calls = {"count": 0}

        async def fake_get(url, client=None):
            calls["count"] += 1
            if calls["count"] == 1:
                raise TimeoutError("temporary timeout")

            class FakeResponse:
                text = html
                content = text.encode("utf-8")
            return FakeResponse()

        catalog = [{
            "id": "US-P4-DE-PLAY-4-EVENING",
            "state": "Delaware",
            "stateCode": "DE",
            "game": "pick4",
            "gameName": "Play 4",
            "draw": "Evening Draw",
            "lotteryUsaUrl": "https://www.lotteryusa.com/delaware/play-4/",
        }]

        with patch.object(scraper, "async_lotteryusa_http_get", side_effect=fake_get):
            rows = scraper.sync_run(scraper._async_fetch_lotteryusa_pick_catalog_rows("14-05-2026", catalog))

        self.assertEqual(2, calls["count"])
        self.assertEqual(1, len(rows))
        self.assertEqual("2-1-0-4", rows[0]["number"])

    def test_scrape_us_picks_with_catalog_skips_slow_pick_domains(self):
        catalog = [{
            "id": "US-P4-FL-PICK-4-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick4",
            "gameName": "Pick 4",
            "draw": "Evening Draw",
        }]
        lotteryusa_rows = [{
            "id": "US-P4-FL-PICK-4-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick4",
            "gameName": "Pick 4",
            "draw": "Evening Draw",
            "date": "14-05-2026",
            "number": "2-8-4-4",
            "source": "lotteryusa.com",
        }]

        async def fake_catalog(date_str, catalog_rows, client=None):
            return lotteryusa_rows

        with patch.object(scraper, "_async_fetch_lotteryusa_pick_catalog_rows", side_effect=fake_catalog), \
            patch.object(scraper, "_async_fetch_us_pick_overview") as overview:
            rows = scraper.scrape_us_picks("14-05-2026", games=("pick4",), existing_rows=catalog)

        self.assertEqual(1, len(rows))
        self.assertEqual("2-8-4-4", rows[0]["number"])
        overview.assert_not_called()

    def test_merge_us_pick_results_preserves_existing_published_over_new_pending(self):
        existing = [{
            "id": "US-P4-FL-PICK-4-EVENING",
            "date": "14-05-2026",
            "game": "pick4",
            "name": "Florida Pick 4 Evening",
            "number": "1-2-3-4",
            "status": "published",
            "firstSeenAt": "2026-05-14T01:00:00Z",
            "lastSeenAt": "2026-05-14T01:00:00Z",
        }]
        incoming = [{
            "id": "US-P4-FL-PICK-4-EVENING",
            "date": "14-05-2026",
            "game": "pick4",
            "name": "Florida Pick 4 Evening",
            "number": "",
            "status": "pending",
        }]

        merged = scraper.merge_us_pick_results_by_id(
            existing,
            incoming,
            observed_at="2026-05-14T02:00:00Z",
        )

        self.assertEqual(1, len(merged))
        self.assertEqual("1-2-3-4", merged[0]["number"])
        self.assertEqual("published", merged[0]["status"])
        self.assertEqual("2026-05-14T01:00:00Z", merged[0]["firstSeenAt"])
        self.assertEqual("2026-05-14T02:00:00Z", merged[0]["lastSeenAt"])

    def test_merge_us_pick_results_upgrades_pending_to_published(self):
        existing = [{
            "id": "US-P4-FL-PICK-4-EVENING",
            "date": "14-05-2026",
            "game": "pick4",
            "name": "Florida Pick 4 Evening",
            "number": "",
            "status": "pending",
            "firstSeenAt": "2026-05-14T01:00:00Z",
            "lastSeenAt": "2026-05-14T01:00:00Z",
        }]
        incoming = [{
            "id": "US-P4-FL-PICK-4-EVENING",
            "date": "14-05-2026",
            "game": "pick4",
            "name": "Florida Pick 4 Evening",
            "number": "1-2-3-4",
            "status": "published",
        }]

        merged = scraper.merge_us_pick_results_by_id(
            existing,
            incoming,
            observed_at="2026-05-14T02:00:00Z",
        )

        self.assertEqual(1, len(merged))
        self.assertEqual("1-2-3-4", merged[0]["number"])
        self.assertEqual("published", merged[0]["status"])
        self.assertEqual("2026-05-14T02:00:00Z", merged[0]["firstSeenAt"])
        self.assertEqual("2026-05-14T02:00:00Z", merged[0]["lastSeenAt"])

    def test_merge_us_pick_results_prunes_legacy_arizona_day_alias(self):
        existing = [{
            "id": "US-P3-AZ-PICK-3-DAY",
            "date": "15-05-2026",
            "game": "pick3",
            "name": "Arizona Pick 3 Day Draw",
            "number": "4-5-2",
            "status": "published",
        }]
        incoming = [{
            "id": "US-P3-AZ-PICK-3-DRAW",
            "date": "15-05-2026",
            "game": "pick3",
            "name": "Arizona Pick 3",
            "number": "3-7-3",
            "status": "published",
        }]

        merged = scraper.merge_us_pick_results_by_id(
            existing,
            incoming,
            observed_at="2026-05-16T03:00:00Z",
        )

        self.assertEqual(["US-P3-AZ-PICK-3-DRAW"], [row["id"] for row in merged])
        self.assertEqual("3-7-3", merged[0]["number"])

    def test_refresh_missing_us_pick_results_targets_only_pending_rows(self):
        existing = [
            {
                "id": "US-P3-LA-PICK-3-DAY",
                "date": "18-05-2026",
                "game": "pick3",
                "gameName": "Pick 3",
                "draw": "Day Draw",
                "number": "",
                "status": "pending",
            },
            {
                "id": "US-P3-FL-PICK-3-MIDDAY",
                "date": "18-05-2026",
                "game": "pick3",
                "number": "1-2-3",
                "status": "published",
            },
        ]
        refreshed = [{
            "id": "US-P3-LA-PICK-3-DAY",
            "date": "18-05-2026",
            "number": "2-6-0",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Day Draw",
        }]

        with patch.object(scraper, "_async_fetch_lotteryusa_pick_catalog_rows", AsyncMock(return_value=refreshed)), \
                patch.object(scraper, "_async_fetch_lotteryusa_pick_fallbacks", AsyncMock(return_value=[])):
            rows = scraper.sync_run(scraper._async_refresh_missing_us_pick_results("18-05-2026", existing))

        by_id = {row["id"]: row for row in rows}
        self.assertEqual("2-6-0", by_id["US-P3-LA-PICK-3-DAY"]["number"])
        self.assertEqual("1-2-3", by_id["US-P3-FL-PICK-3-MIDDAY"]["number"])

    def test_refresh_missing_us_pick_results_canonicalizes_legacy_arizona_alias(self):
        existing = [{
            "id": "US-P3-AZ-PICK-3-DAY",
            "date": "18-05-2026",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Day Draw",
            "number": "",
            "status": "pending",
        }]
        refreshed = [{
            "id": "US-P3-AZ-PICK-3-DRAW",
            "date": "18-05-2026",
            "number": "4-7-2",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Draw",
        }]

        with patch.object(scraper, "_async_fetch_lotteryusa_pick_catalog_rows", AsyncMock(return_value=refreshed)), \
                patch.object(scraper, "_async_fetch_lotteryusa_pick_fallbacks", AsyncMock(return_value=[])):
            rows = scraper.sync_run(scraper._async_refresh_missing_us_pick_results("18-05-2026", existing))

        self.assertEqual(["US-P3-AZ-PICK-3-DRAW"], [row["id"] for row in rows])
        self.assertEqual("4-7-2", rows[0]["number"])

    def test_async_scrape_us_picks_does_not_redate_stale_overview_number(self):
        overview_rows = [{
            "id": "US-P3-AZ-PICK-3-DRAW",
            "state": "Arizona",
            "stateCode": "AZ",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Day Draw",
            "date": "14-05-2026",
            "number": "4-5-2",
            "status": "published",
            "playTypes": ["straight", "box"],
            "source": "pick-3.com",
        }]
        fallback_rows = [{
            "id": "US-P3-AZ-PICK-3-DRAW",
            "name": "Arizona Pick 3",
            "date": "15-05-2026",
            "number": "3-7-3",
            "status": "published",
        }]

        with patch.object(scraper, "_async_fetch_us_pick_overview", AsyncMock(return_value=overview_rows)), \
                patch.object(scraper, "static_us_pick_catalog_rows", return_value=[]), \
                patch.object(scraper, "_async_fetch_us_pick_state_history", AsyncMock(return_value=[])), \
                patch.object(scraper, "_async_fetch_new_jersey_pick_home", AsyncMock(return_value=[])), \
                patch.object(scraper, "_async_fetch_nj_picks_lotteryusa", AsyncMock(return_value=[])), \
                patch.object(scraper, "_async_fetch_lotteryusa_pick_fallbacks", AsyncMock(return_value=fallback_rows)), \
                patch.object(scraper, "_async_fetch_wa_match4", AsyncMock(return_value=[])):
            rows = scraper.sync_run(scraper._async_scrape_us_picks("15-05-2026", games=("pick3",)))

        self.assertEqual(1, len(rows))
        self.assertEqual("US-P3-AZ-PICK-3-DRAW", rows[0]["id"])
        self.assertEqual("15-05-2026", rows[0]["date"])
        self.assertEqual("3-7-3", rows[0]["number"])

    def test_async_scrape_us_picks_uses_lotteryusa_fallback_for_missing_pick(self):
        overview_rows = [{
            "id": "US-P4-FL-PICK-4-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick4",
            "gameName": "Pick 4",
            "draw": "Evening Draw",
            "date": "",
            "number": "",
            "status": "pending",
            "playTypes": ["straight", "box"],
            "source": "pick-4.com",
        }]
        fallback_rows = [{
            "id": "US-P4-FL-PICK-4-EVENING",
            "name": "Florida Pick 4 Evening",
            "date": "14-05-2026",
            "number": "2-8-4-4",
        }]

        with patch.object(scraper, "_async_fetch_us_pick_overview", AsyncMock(return_value=overview_rows)), \
                patch.object(scraper, "static_us_pick_catalog_rows", return_value=[]), \
                patch.object(scraper, "_async_fetch_us_pick_state_history", AsyncMock(return_value=[])), \
                patch.object(scraper, "_async_fetch_new_jersey_pick_home", AsyncMock(return_value=[])), \
                patch.object(scraper, "_async_fetch_nj_picks_lotteryusa", AsyncMock(return_value=[])), \
                patch.object(scraper, "_async_fetch_lotteryusa_pick_fallbacks", AsyncMock(return_value=fallback_rows)), \
                patch.object(scraper, "_async_fetch_wa_match4", AsyncMock(return_value=[])):
            rows = scraper.sync_run(scraper._async_scrape_us_picks("14-05-2026", games=("pick4",)))

        self.assertEqual(1, len(rows))
        self.assertEqual("2-8-4-4", rows[0]["number"])
        self.assertEqual("14-05-2026", rows[0]["date"])

    def test_async_scrape_us_picks_uses_nj_lotteryusa_backup(self):
        overview_rows = [{
            "id": "20",
            "state": "New Jersey",
            "stateCode": "NJ",
            "game": "pick3",
            "gameName": "Pick 3",
            "draw": "Evening Draw",
            "date": "",
            "number": "",
            "status": "pending",
            "playTypes": ["straight", "box"],
            "source": "pick-3.com",
        }]
        nj_rows = [{
            "id": "20",
            "name": "NJ Pick 3 Noche",
            "date": "14-05-2026",
            "number": "7-6-4",
        }]

        with patch.object(scraper, "_async_fetch_us_pick_overview", AsyncMock(return_value=overview_rows)), \
                patch.object(scraper, "static_us_pick_catalog_rows", return_value=[]), \
                patch.object(scraper, "_async_fetch_us_pick_state_history", AsyncMock(return_value=[])), \
                patch.object(scraper, "_async_fetch_new_jersey_pick_home", AsyncMock(return_value=[])), \
                patch.object(scraper, "_async_fetch_nj_picks_lotteryusa", AsyncMock(return_value=nj_rows)), \
                patch.object(scraper, "_async_fetch_lotteryusa_pick_fallbacks", AsyncMock(return_value=[])), \
                patch.object(scraper, "_async_fetch_wa_match4", AsyncMock(return_value=[])):
            rows = scraper.sync_run(scraper._async_scrape_us_picks("14-05-2026", games=("pick3",)))

        self.assertEqual(1, len(rows))
        self.assertEqual("7-6-4", rows[0]["number"])
        self.assertEqual("14-05-2026", rows[0]["date"])

    def test_supabase_rest_post_retries_statement_timeout(self):
        class FakeClient:
            def __init__(self):
                self.calls = 0

            async def post(self, url, content=None, headers=None):
                self.calls += 1
                request = scraper.httpx.Request("POST", url)
                if self.calls == 1:
                    return scraper.httpx.Response(
                        500,
                        request=request,
                        json={"code": "57014", "message": "canceling statement due to statement timeout"},
                    )
                return scraper.httpx.Response(201, request=request, json={})

        client = FakeClient()
        with patch.object(scraper.asyncio, "sleep", AsyncMock()) as sleep:
            response = scraper.sync_run(scraper.async_supabase_rest_post(
                "https://example.supabase.co/rest/v1/lotterynet_kv",
                b"{}",
                {"Content-Type": "application/json"},
                client=client,
                label="test save",
            ))

        self.assertEqual(201, response.status_code)
        self.assertEqual(2, client.calls)
        sleep.assert_awaited_once()

    def test_result_draws_save_uses_service_key_rpc(self):
        class FakeClient:
            def __init__(self):
                self.post_calls = 0
                self.post_urls = []
                self.headers = []
                self.payloads = []

            async def post(self, url, content=None, headers=None):
                self.post_calls += 1
                self.post_urls.append(url)
                self.headers.append(headers or {})
                self.payloads.append(json.loads(content.decode("utf-8")))
                return scraper.httpx.Response(201, request=scraper.httpx.Request("POST", url), json={})

        client = FakeClient()
        with patch.object(scraper, "SUPABASE_KEY", "publishable-key"), \
                patch.object(scraper, "SUPABASE_SECRET_KEY", "service-role-key"):
            response = scraper.sync_run(scraper.async_save_result_draws_payload(
                "24-05-2026",
                [{"id": "1", "name": "La Primera Día", "number": "01-02-03"}],
                "lottery",
                client=client,
            ))

        self.assertEqual(201, response.status_code)
        self.assertEqual(1, client.post_calls)
        self.assertIn("/rest/v1/rpc/lotterynet_upsert_result_draws_from_payload", client.post_urls[0])
        self.assertEqual("publishable-key", client.headers[0]["apikey"])
        self.assertEqual("Bearer service-role-key", client.headers[0]["Authorization"])
        self.assertEqual("lottery", client.payloads[0]["p_source"])

    def test_result_draws_save_uses_modern_secret_key_as_apikey_only(self):
        class FakeClient:
            def __init__(self):
                self.headers = []

            async def post(self, url, content=None, headers=None):
                self.headers.append(headers or {})
                return scraper.httpx.Response(201, request=scraper.httpx.Request("POST", url), json={})

        client = FakeClient()
        with patch.object(scraper, "SUPABASE_KEY", "sb_publishable_123"), \
                patch.object(scraper, "SUPABASE_SECRET_KEY", "sb_secret_456"):
            response = scraper.sync_run(scraper.async_save_result_draws_payload(
                "24-05-2026",
                [{"id": "1", "name": "La Primera Día", "number": "01-02-03"}],
                "lottery",
                client=client,
            ))

        self.assertEqual(201, response.status_code)
        self.assertEqual("sb_secret_456", client.headers[0]["apikey"])
        self.assertEqual("LotteryNet-Render/1.0", client.headers[0]["User-Agent"])
        self.assertNotIn("Authorization", client.headers[0])

    def test_supabase_kv_save_falls_back_to_patch_when_rpc_unavailable(self):
        class FakeClient:
            def __init__(self):
                self.patch_calls = 0
                self.post_calls = 0
                self.patch_urls = []

            async def patch(self, url, content=None, headers=None):
                self.patch_calls += 1
                self.patch_urls.append(url)
                return scraper.httpx.Response(204, request=scraper.httpx.Request("PATCH", url))

            async def post(self, url, content=None, headers=None):
                self.post_calls += 1
                return scraper.httpx.Response(
                    404,
                    request=scraper.httpx.Request("POST", url),
                    json={"message": "function not found"},
                )

        client = FakeClient()
        response = scraper.sync_run(scraper.async_supabase_kv_save(
            "lot_results_cache_by_day:24-05-2026",
            "[]",
            client=client,
            label="test kv",
        ))

        self.assertEqual(204, response.status_code)
        self.assertEqual(1, client.patch_calls)
        self.assertEqual(1, client.post_calls)
        self.assertIn("key=eq.lot_results_cache_by_day%3A24-05-2026", client.patch_urls[0])

    def test_supabase_kv_save_falls_back_to_patch_when_rpc_statement_times_out(self):
        class FakeClient:
            def __init__(self):
                self.patch_calls = 0
                self.post_calls = 0

            async def patch(self, url, content=None, headers=None):
                self.patch_calls += 1
                return scraper.httpx.Response(204, request=scraper.httpx.Request("PATCH", url))

            async def post(self, url, content=None, headers=None):
                self.post_calls += 1
                return scraper.httpx.Response(
                    500,
                    request=scraper.httpx.Request("POST", url),
                    json={"code": "57014", "message": "canceling statement due to statement timeout"},
                )

        client = FakeClient()
        with patch.object(scraper.asyncio, "sleep", AsyncMock()):
            response = scraper.sync_run(scraper.async_supabase_kv_save(
                "lot_results_cache_by_day:26-05-2026",
                "[]",
                client=client,
                label="test kv",
            ))

        self.assertEqual(204, response.status_code)
        self.assertEqual(1, client.patch_calls)
        self.assertEqual(3, client.post_calls)

    def test_save_to_supabase_writes_only_result_draws(self):
        with patch.object(scraper, "_async_fetch_existing_from_supabase", AsyncMock(return_value=[])), \
                patch.object(scraper, "_async_save_native_results_table", AsyncMock()) as native_save, \
                patch.object(scraper, "async_supabase_kv_save", AsyncMock()) as legacy_kv_save:
            scraper.sync_run(scraper._async_save_to_supabase(
                "26-05-2026",
                [{"id": "1", "name": "La Primera Día", "date": "26-05-2026", "number": "01-02-03"}],
            ))

        native_save.assert_awaited_once()
        legacy_kv_save.assert_not_awaited()

    def test_default_backfill_only_requires_current_day_save(self):
        self.assertTrue(scraper.should_require_supabase_save(
            target_date="24-05-2026",
            current_date="24-05-2026",
            explicit_dates=False,
        ))
        self.assertFalse(scraper.should_require_supabase_save(
            target_date="23-05-2026",
            current_date="24-05-2026",
            explicit_dates=False,
        ))
        self.assertTrue(scraper.should_require_supabase_save(
            target_date="23-05-2026",
            current_date="24-05-2026",
            explicit_dates=True,
        ))

    def test_scheduled_current_save_timeout_continues_to_health_check(self):
        self.assertTrue(scraper.should_continue_after_supabase_save_error(
            save_required=True,
            explicit_dates=False,
        ))
        self.assertFalse(scraper.should_continue_after_supabase_save_error(
            save_required=True,
            explicit_dates=True,
        ))
        self.assertTrue(scraper.should_continue_after_supabase_save_error(
            save_required=False,
            explicit_dates=False,
        ))

    def test_non_current_backfill_skips_complete_cached_day(self):
        rd_rows = [
            {"id": result_id, "number": "01-02-03"}
            for result_id in scraper.TRACKED_REMOTE_RESULT_IDS
        ]
        pick_rows = [
            {"id": "US-P3-NJ-PICK-3-MIDDAY", "number": "1-2-3", "pick3": "1-2-3"},
            {"id": "US-P4-NJ-PICK-4-MIDDAY", "number": "1-2-3-4", "pick4": "1-2-3-4"},
        ]

        with patch.object(scraper, "static_us_pick_catalog_rows", return_value=[
            {"id": "US-P3-NJ-PICK-3-MIDDAY", "game": "pick3"},
            {"id": "US-P4-NJ-PICK-4-MIDDAY", "game": "pick4"},
        ]):
            self.assertFalse(scraper.non_current_backfill_should_run(rd_rows, pick_rows))

    def test_non_current_backfill_runs_for_missing_tracked_rd_id(self):
        rd_rows = [
            {"id": "23", "number": "01-02-03"},
            {"id": "27", "number": "07-08-09"},
            {"id": "28", "number": "10-11-12"},
        ]

        self.assertTrue(scraper.non_current_backfill_should_run(rd_rows, []))

    def test_non_current_backfill_runs_for_missing_late_normal_result(self):
        rd_rows = [
            {"id": "23", "number": "01-02-03"},
            {"id": "24", "number": "04-05-06"},
            {"id": "27", "number": "07-08-09"},
            {"id": "28", "number": "10-11-12"},
        ]

        self.assertTrue(scraper.non_current_backfill_should_run(rd_rows, []))

    def test_non_current_backfill_runs_for_pending_pick_row(self):
        rd_rows = [
            {"id": "23", "number": "01-02-03"},
            {"id": "24", "number": "04-05-06"},
            {"id": "27", "number": "07-08-09"},
            {"id": "28", "number": "10-11-12"},
        ]
        pick_rows = [
            {"id": "US-P3-NJ-PICK-3-MIDDAY", "number": "", "pick3": "", "status": "pending"},
        ]

        self.assertTrue(scraper.non_current_backfill_should_run(rd_rows, pick_rows))

    def test_us_pick_rows_changed_detects_quality_improvement(self):
        existing = [{"id": "US-P3-NJ-PICK-3-MIDDAY", "number": "", "pick3": "", "status": "pending"}]
        refreshed = [{"id": "US-P3-NJ-PICK-3-MIDDAY", "number": "1-2-3", "pick3": "1-2-3"}]

        self.assertTrue(scraper.us_pick_rows_changed(existing, refreshed))
        self.assertFalse(scraper.us_pick_rows_changed(refreshed, refreshed))


if __name__ == "__main__":
    unittest.main()
