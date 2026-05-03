import json
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

    def test_root_returns_28_unique_results(self):
        rows = fake_results() + [fake_results()[1]]
        with patch("app.scrape", return_value=rows):
            response = self.client.get("/?date=02-05-2026")

        self.assertEqual(200, response.status_code)
        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(28, len(payload))
        self.assertEqual("28", payload[-1]["id"])

    def test_results_endpoint_returns_metadata(self):
        with patch("app.scrape", return_value=fake_results()):
            response = self.client.get("/results?date=02-05-2026")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual("02-05-2026", payload["date"])
        self.assertEqual(28, payload["count"])
        self.assertEqual("27", payload["results"][-2]["id"])

    def test_haiti_route_filters_haiti_bolet(self):
        with patch("app.scrape", return_value=fake_results()):
            response = self.client.get("/loteria-haiti?date=02-05-2026")

        payload = json.loads(response.data.decode("utf-8"))
        self.assertEqual(["27", "28"], [row["id"] for row in payload])

    def test_pick_routes_include_pick_specific_fields(self):
        with patch("app.scrape", return_value=fake_results()):
            pick3 = self.client.get("/loteria-pick3?date=02-05-2026")
            pick4 = self.client.get("/loteria-pick4?date=02-05-2026")

        pick3_payload = json.loads(pick3.data.decode("utf-8"))
        pick4_payload = json.loads(pick4.data.decode("utf-8"))
        self.assertEqual(["19", "20"], [row["id"] for row in pick3_payload])
        self.assertEqual("3-7-1", pick3_payload[0]["pick3"])
        self.assertNotIn("pick4", pick3_payload[0])
        self.assertEqual(["21", "22"], [row["id"] for row in pick4_payload])
        self.assertEqual("0-8-4-3", pick4_payload[0]["pick4"])
        self.assertNotIn("pick3", pick4_payload[0])

    def test_reuses_scrape_cache_for_same_date(self):
        with patch("app.scrape", return_value=fake_results()) as scrape_mock:
            self.client.get("/loteria-pick3?date=02-05-2026")
            self.client.get("/loteria-pick4?date=02-05-2026")

        self.assertEqual(1, scrape_mock.call_count)


if __name__ == "__main__":
    unittest.main()
