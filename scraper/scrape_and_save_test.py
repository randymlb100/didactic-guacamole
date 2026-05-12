import os
import unittest
import datetime
from unittest.mock import Mock, patch

import scrape_and_save as scraper


class ScraperContractsTest(unittest.TestCase):
    def test_authoritative_nj_ids_cover_pick_and_new_jersey(self):
        self.assertEqual({"19", "20", "21", "22", "25", "26"}, scraper.AUTHORITATIVE_NJ_IDS)

    def test_tracked_remote_ids_include_king_and_haiti_bolet(self):
        self.assertEqual({"23", "24", "27", "28"}, scraper.TRACKED_REMOTE_RESULT_IDS)

    def test_miloteria_maps_new_jersey_pm(self):
        self.assertEqual("26", scraper.MILOTERIA_NJ_MAP["new jersey pm"]["id"])
        self.assertEqual("New Jersey PM", scraper.MILOTERIA_NJ_MAP["new jersey pm"]["name"])

    def test_github_actions_requires_supabase_key(self):
        env = {"GITHUB_ACTIONS": "true"}

        self.assertTrue(scraper.should_fail_without_supabase_key("", env))
        self.assertFalse(scraper.should_fail_without_supabase_key("present", env))
        self.assertFalse(scraper.should_fail_without_supabase_key("", {}))

    @patch.dict(os.environ, {}, clear=True)
    def test_configured_supabase_url_uses_default_when_env_missing(self):
        self.assertEqual(scraper.DEFAULT_SUPABASE_URL, scraper.configured_supabase_url())

    @patch.dict(os.environ, {"SUPABASE_URL": ""}, clear=True)
    def test_configured_supabase_url_ignores_blank_env(self):
        self.assertEqual(scraper.DEFAULT_SUPABASE_URL, scraper.configured_supabase_url())

    @patch.dict(os.environ, {"SUPABASE_URL": "https://example.supabase.co/"}, clear=True)
    def test_configured_supabase_url_trims_trailing_slash(self):
        self.assertEqual("https://example.supabase.co", scraper.configured_supabase_url())

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
        self.assertEqual([], scraper.missing_tracked_result_ids([
            {"id": "23"},
            {"id": "24"},
            {"id": "27"},
            {"id": "28"},
        ]))

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
        self.assertEqual(["27", "28"], scraper.missing_tracked_result_ids([
            {"id": "23"},
            {"id": "24"},
        ]))

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

    def test_us_pick_result_ids_include_game_state_and_draw_without_numeric_collision(self):
        self.assertEqual(
            "US-P3-FL-PICK-3-EVENING",
            scraper.build_us_pick_result_id("pick3", "FL", "Pick 3", "Evening Draw"),
        )
        self.assertEqual(
            "US-P4-GA-CASH-4-NIGHT",
            scraper.build_us_pick_result_id("pick4", "GA", "Cash 4", "Night Draw"),
        )

    def test_parse_pick3_overview_skips_new_jersey_because_it_is_normal_catalog(self):
        html = """
        <section>
          <img alt="Florida Pick 3 Latest Draws!" />
          <p>Latest Results</p>
          <h3>Florida Pick 3</h3>
          <p>08 May 26 Evening Draw</p>
          <ul><li>9</li><li>2</li><li>0</li></ul>
          <a href="https://fl.pick-3.com">Check Numbers</a>
        </section>
        <section>
          <img alt="New Jersey Pick 3 Latest Draws!" />
          <p>Latest Results</p>
          <h3>New Jersey Pick 3</h3>
          <p>08 May 26 Evening Draw</p>
          <ul><li>8</li><li>2</li><li>8</li></ul>
          <a href="https://nj.pick-3.com">Check Numbers</a>
        </section>
        """

        rows = scraper.parse_us_pick_overview(html, game="pick3")

        self.assertEqual(1, len(rows))
        self.assertEqual("US-P3-FL-PICK-3-EVENING", rows[0]["id"])
        self.assertEqual("FL", rows[0]["stateCode"])
        self.assertEqual("Florida", rows[0]["state"])
        self.assertEqual("Pick 3", rows[0]["gameName"])
        self.assertEqual("pick3", rows[0]["game"])
        self.assertEqual("9-2-0", rows[0]["number"])
        self.assertEqual(["straight", "box"], rows[0]["playTypes"])

    def test_parse_pick4_overview_reads_state_draw_time_and_source(self):
        html = """
        <article>
          <img alt="Georgia Cash 4 Latest Draws!" />
          <span>Latest Results</span>
          <strong>Georgia Cash 4</strong>
          <span>08 May 26 Night Draw</span>
          <ol><li>0</li><li>6</li><li>1</li><li>3</li></ol>
          <a href="https://ga.pick-4.com">Check Numbers</a>
        </article>
        """

        rows = scraper.parse_us_pick_overview(html, game="pick4")

        self.assertEqual(1, len(rows))
        self.assertEqual("US-P4-GA-CASH-4-NIGHT", rows[0]["id"])
        self.assertEqual("08-05-2026", rows[0]["date"])
        self.assertEqual("Night Draw", rows[0]["draw"])
        self.assertEqual("pick-4.com", rows[0]["source"])
        self.assertEqual("0-6-1-3", rows[0]["number"])

    def test_parse_pick_history_reads_single_daily_draw_without_label(self):
        html = """
        <div class="resultsBox">
          <div class="date">Monday, May 11, 2026</div>
          <span>5</span><span>9</span><span>9</span>
          <a>Check My Numbers</a>
        </div>
        """

        rows = scraper.parse_us_pick_history_page(
            html,
            game="pick3",
            state_code="LA",
            state_name="Louisiana",
            game_name="Pick 3",
            target_date="11-05-2026",
        )

        self.assertEqual(1, len(rows))
        self.assertEqual("US-P3-LA-PICK-3-DAY", rows[0]["id"])
        self.assertEqual("Day Draw", rows[0]["draw"])
        self.assertEqual("5-9-9", rows[0]["number"])

    def test_parse_pick_history_keeps_midday_evening_rows_with_superball(self):
        html = """
        <div class="resultsBox">
          <div class="date">Sunday, May 10, 2026</div>
          <span>Midday</span><span>8</span><span>1</span><span>3</span><span>6</span>
          <span>Evening</span><span>4</span><span>0</span><span>1</span><span>1</span>
        </div>
        """

        rows = scraper.parse_us_pick_history_page(
            html,
            game="pick3",
            state_code="IN",
            state_name="Indiana",
            game_name="Daily 3",
            target_date="10-05-2026",
        )

        self.assertEqual(
            [("US-P3-IN-DAILY-3-EVENING", "4-0-1"), ("US-P3-IN-DAILY-3-MIDDAY", "8-1-3")],
            [(row["id"], row["number"]) for row in rows],
        )

    def test_parse_boliteros_feed_reads_pick_rows_and_skips_new_jersey(self):
        html = """
        <section>
          <div>
            <h3>Florida</h3><span>9:48 PM</span>
            <span>May 8, 2026</span>
            <span>Pick 3</span><span>9</span><span>2</span><span>0</span><span>3</span><span>FB</span>
            <span>Pick 4</span><span>1</span><span>9</span><span>8</span><span>6</span><span>3</span><span>FB</span>
          </div>
          <div>
            <h3>New Jersey</h3><span>11:09 PM</span>
            <span>May 8, 2026</span>
            <span>Pick-3</span><span>8</span><span>2</span><span>8</span>
          </div>
        </section>
        """

        rows = scraper.parse_boliteros_pick_feed(html)

        self.assertEqual(["US-P3-FL-PICK-3-9-48-PM", "US-P4-FL-PICK-4-9-48-PM"], [row["id"] for row in rows])
        self.assertEqual(["pick3", "pick4"], [row["game"] for row in rows])
        self.assertEqual(["9-2-0", "1-9-8-6"], [row["number"] for row in rows])
        self.assertEqual(["08-05-2026", "08-05-2026"], [row["date"] for row in rows])
        self.assertEqual(["boliteros.com", "boliteros.com"], [row["source"] for row in rows])

    def test_merge_pick_sources_uses_backup_date_when_primary_date_is_missing(self):
        primary = [{
            "id": "US-P4-FL-PICK-4-EVENING",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick4",
            "gameName": "Pick 4",
            "draw": "Evening Draw",
            "date": "",
            "number": "1-9-8-6",
            "playTypes": ["straight", "box"],
            "source": "pick-4.com",
        }]
        backup = [{
            "id": "US-P4-FL-PICK-4-9-48-PM",
            "state": "Florida",
            "stateCode": "FL",
            "game": "pick4",
            "gameName": "Pick 4",
            "draw": "9:48 PM",
            "date": "08-05-2026",
            "number": "1-9-8-6",
            "playTypes": ["straight", "box"],
            "source": "boliteros.com",
        }]

        rows = scraper.merge_us_pick_sources(primary, backup)

        self.assertEqual(1, len(rows))
        self.assertEqual("08-05-2026", rows[0]["date"])
        self.assertEqual("pick-4.com,boliteros.com", rows[0]["source"])

    def test_pick_supabase_key_is_separate_from_normal_lottery_results(self):
        self.assertEqual(
            "pick_results_cache_by_day:08-05-2026",
            scraper.pick_results_cache_key("08-05-2026"),
        )
        self.assertNotEqual(
            "lot_results_cache_by_day:08-05-2026",
            scraper.pick_results_cache_key("08-05-2026"),
        )

    def test_valid_pick_result_requires_number_and_exact_date(self):
        self.assertTrue(scraper.has_valid_pick_result({
            "id": "US-P3-FL-PICK-3-EVENING",
            "date": "08-05-2026",
            "number": "9-2-0",
        }, "08-05-2026"))
        self.assertFalse(scraper.has_valid_pick_result({
            "id": "US-P3-FL-PICK-3-EVENING",
            "date": "07-05-2026",
            "number": "9-2-0",
        }, "08-05-2026"))
        self.assertFalse(scraper.has_valid_pick_result({
            "id": "US-P3-FL-PICK-3-EVENING",
            "date": "08-05-2026",
            "number": "",
        }, "08-05-2026"))

    def test_pick_fallback_plan_skips_cached_and_limits_calls(self):
        expected = [
            scraper.ExpectedPickDraw(
                id=f"US-P3-TX-PICK-3-{index}",
                state="Texas",
                state_code="TX",
                game="pick3",
                game_name="Pick 3",
                draw=f"{index}:00 PM Draw",
            )
            for index in range(0, 30)
        ]
        existing = [{
            "id": "US-P3-TX-PICK-3-0",
            "date": "08-05-2026",
            "number": "1-2-3",
        }]

        missing = scraper.pick_draws_needing_fallback(
            expected,
            existing,
            "08-05-2026",
            max_calls=25,
            now_dr=datetime.datetime(2026, 5, 9, 9, 0),
        )

        self.assertEqual(25, len(missing))
        self.assertNotIn("US-P3-TX-PICK-3-0", [draw.id for draw in missing])

    def test_pick_fallback_plan_does_not_query_future_dates(self):
        expected = [scraper.ExpectedPickDraw(
            id="US-P3-TX-PICK-3-EVENING",
            state="Texas",
            state_code="TX",
            game="pick3",
            game_name="Pick 3",
            draw="Evening Draw",
        )]

        missing = scraper.pick_draws_needing_fallback(
            expected,
            [],
            "10-05-2026",
            max_calls=25,
            now_dr=datetime.datetime(2026, 5, 9, 9, 0),
        )

        self.assertEqual([], missing)

    def test_smart_fallback_only_fetches_missing_draws(self):
        expected = [
            scraper.ExpectedPickDraw(
                id="US-P3-IN-DAILY-3-MIDDAY",
                state="Indiana",
                state_code="IN",
                game="pick3",
                game_name="Daily 3",
                draw="Midday Draw",
            ),
            scraper.ExpectedPickDraw(
                id="US-P3-IN-DAILY-3-EVENING",
                state="Indiana",
                state_code="IN",
                game="pick3",
                game_name="Daily 3",
                draw="Evening Draw",
            ),
        ]
        primary = [{
            "id": "US-P3-IN-DAILY-3-EVENING",
            "date": "11-05-2026",
            "number": "3-1-7",
        }]
        fetcher = Mock(return_value={
            "id": "US-P3-IN-DAILY-3-MIDDAY",
            "date": "11-05-2026",
            "number": "5-8-6",
            "source": "lotteryusa.com",
        })

        rows = scraper.fetch_pick_fallback_rows(
            expected,
            primary,
            "11-05-2026",
            max_calls=25,
            now_dr=datetime.datetime(2026, 5, 12, 9, 0),
            fetcher=fetcher,
        )

        self.assertEqual(["US-P3-IN-DAILY-3-MIDDAY"], [row["id"] for row in rows])
        fetcher.assert_called_once()

    def test_lotteryusa_pick_url_candidates_prefer_explicit_urls(self):
        draw = scraper.ExpectedPickDraw(
            id="US-P3-DC-3-MIDDAY",
            state="District of Columbia",
            state_code="DC",
            game="pick3",
            game_name="DC Lucky",
            draw="Midday Draw",
            preferred_urls=("https://www.lotteryusa.com/district-of-columbia/dc-lucky-midday/",),
        )

        candidates = scraper.lotteryusa_pick_url_candidates(draw)

        self.assertEqual("https://www.lotteryusa.com/district-of-columbia/dc-lucky-midday/", candidates[0])
        self.assertIn("https://www.lotteryusa.com/district-of-columbia/pick-3/", candidates)

    def test_configured_fallback_draws_cover_known_sunday_pick_gaps(self):
        self.assertEqual(
            [
                "US-P3-IN-DAILY-3-MIDDAY",
                "US-P3-IN-DAILY-3-EVENING",
                "US-P4-IN-DAILY-4-MIDDAY",
                "US-P4-IN-DAILY-4-EVENING",
                "US-P3-AR-CASH-3-EVENING",
                "US-P3-DC-3-MIDDAY",
                "US-P3-TN-CASH-3-06-28-PM",
                "19",
                "20",
                "21",
                "22",
                "US-P4-SC-PICK-4-EVENING",
            ],
            [draw.id for draw in scraper.configured_pick_fallback_draws(["pick3", "pick4"])],
        )
        self.assertEqual(
            [
                "US-P3-IN-DAILY-3-MIDDAY",
                "US-P3-IN-DAILY-3-EVENING",
                "US-P3-AR-CASH-3-EVENING",
                "US-P3-DC-3-MIDDAY",
                "US-P3-TN-CASH-3-06-28-PM",
                "19",
                "20",
            ],
            [draw.id for draw in scraper.configured_pick_fallback_draws(["pick3"])],
        )

    def test_scrape_us_picks_does_not_overwrite_existing_valid_with_empty_fallback(self):
        existing = [{
            "id": "US-P3-IN-DAILY-3-MIDDAY",
            "date": "11-05-2026",
            "number": "5-8-6",
            "source": "supabase-cache",
        }]
        with patch.object(scraper, "fetch_us_pick_overview", return_value=[]), \
            patch.object(scraper, "fetch_us_pick_history_batch", return_value={}), \
            patch.object(scraper, "fetch_new_jersey_pick_home", return_value=[]), \
            patch.object(scraper, "fetch_pick_fallback_rows", return_value=[]):
            rows = scraper.scrape_us_picks("11-05-2026", games=["pick3"], existing_rows=existing)

        self.assertEqual(existing, rows)

    def test_scrape_us_picks_skips_fallback_for_today_when_primary_sources_are_empty(self):
        with patch.object(scraper, "get_dr_date_str", return_value="12-05-2026"), \
            patch.object(scraper, "fetch_us_pick_overview", return_value=[]), \
            patch.object(scraper, "fetch_us_pick_history_batch", return_value={}), \
            patch.object(scraper, "fetch_new_jersey_pick_home", return_value=[]), \
            patch.object(scraper, "fetch_pick_fallback_rows") as fallback:
            rows = scraper.scrape_us_picks(
                "12-05-2026",
                games=["pick3"],
                existing_rows=[],
                now_dr=datetime.datetime(2026, 5, 12, 9, 0),
            )

        self.assertEqual([], rows)
        fallback.assert_not_called()


if __name__ == "__main__":
    unittest.main()
