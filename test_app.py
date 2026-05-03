import json
import unittest
from unittest.mock import patch

import app


def fake_results():
    return [
        {"id": str(index), "name": f"Loteria {index}", "date": "02-05-2026", "number": "01-02-03"}
        for index in range(1, 27)
    ] + [
        {"id": "27", "name": "Haiti Bolet 11:30 AM", "date": "02-05-2026", "number": "03-21-01"},
        {"id": "28", "name": "Haiti Bolet 6:30 PM", "date": "02-05-2026", "number": "52-35-42"},
    ]


class RenderApiContractsTest(unittest.TestCase):
    def setUp(self):
        self.client = app.app.test_client()

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


if __name__ == "__main__":
    unittest.main()
