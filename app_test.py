import unittest
from unittest.mock import patch

import app as server_app


class LiveResultsSourcePolicyTest(unittest.TestCase):
    def test_live_lottery_snapshot_is_served_without_scheduling_another_scrape(self):
        date_key = server_app.get_dr_date_str()
        published_rows = [{"id": "1", "name": "Loteria de prueba", "date": date_key, "number": "123"}]

        with server_app.app.test_request_context("/system-results?live=1"):
            with patch.object(server_app, "fetch_existing_from_supabase", return_value=published_rows), \
                    patch.object(server_app, "scrape_cached") as scrape:
                rows = server_app.lottery_rows_for_request_date(date_key)

        self.assertEqual([row["id"] for row in rows], ["1"])
        scrape.assert_not_called()

    def test_live_lottery_scrapes_only_when_no_published_snapshot_exists(self):
        date_key = server_app.get_dr_date_str()
        scraped_rows = [{"id": "1", "name": "Loteria de prueba", "date": date_key, "number": "123"}]

        with server_app.app.test_request_context("/system-results?live=1"):
            with patch.object(server_app, "fetch_existing_from_supabase", return_value=[]), \
                    patch.object(server_app, "scrape_cached", return_value=scraped_rows) as scrape:
                rows = server_app.lottery_rows_for_request_date(date_key)

        self.assertEqual([row["id"] for row in rows], ["1"])
        scrape.assert_called_once_with(date_key)


if __name__ == "__main__":
    unittest.main()
