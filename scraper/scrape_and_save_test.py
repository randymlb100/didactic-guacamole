import os
import unittest
import datetime

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

    def test_parse_miloteria_date_handles_api_formats(self):
        self.assertEqual("26-04-2026", scraper.parse_miloteria_date("Sunday, Apr 26, 2026"))
        self.assertEqual("26-04-2026", scraper.parse_miloteria_date("04/26/2026 11:00:00 PM"))

    def test_haiti_bolet_sources_are_mapped_to_catalog_ids(self):
        self.assertEqual("27", scraper.ENLOTERIA_HAITI_BOLET_SOURCES[0]["id"])
        self.assertEqual("Haiti Bolet 11:30 AM", scraper.ENLOTERIA_HAITI_BOLET_SOURCES[0]["name"])
        self.assertEqual("28", scraper.ENLOTERIA_HAITI_BOLET_SOURCES[1]["id"])
        self.assertEqual("Haiti Bolet 6:30 PM", scraper.ENLOTERIA_HAITI_BOLET_SOURCES[1]["name"])

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


if __name__ == "__main__":
    unittest.main()
