import unittest
from unittest.mock import AsyncMock, patch
import scrape_and_save as s


class PickDateRegressionTest(unittest.TestCase):
    def test_explicit_month_formats(self):
        for text in ('12 Oct 26', '12 October 26', '12 October 2026'):
            self.assertEqual('12-10-2026', s.parse_us_pick_date_only(text))
            self.assertEqual(('12-10-2026', 'Evening Draw'),
                             s.parse_us_pick_draw_date(text + ' Evening Draw'))
        self.assertEqual('', s.parse_us_pick_date_only('31 February 26'))
        self.assertEqual('', s.parse_us_pick_date_only('Evening Draw'))

    def test_observed_overview_layout(self):
        html = '''<div class="drawContainer"><img alt="Arizona Pick 3 Latest Draws!"/>
          <span>12 October 26</span><span>Evening Draw</span>
          <ul><li>6</li><li>8</li><li>6</li></ul><a>Check Numbers</a></div>'''
        rows = s.parse_us_pick_overview(html, 'pick3')
        self.assertEqual('12-10-2026', rows[0]['date'])
        self.assertEqual('6-8-6', rows[0]['number'])

    def test_nj_routes_are_game_specific(self):
        self.assertEqual('https://nj.pick-3.com/results', s.us_pick_history_url_candidates('pick3', 'NJ')[0])
        self.assertEqual('https://nj.pick-4.com/pick-4-results', s.us_pick_history_url_candidates('pick4', 'NJ')[0])


class PickOverviewFallbackTest(unittest.IsolatedAsyncioTestCase):
    async def test_only_requested_date_can_be_recovered_from_overview(self):
        row = {'id': 'US-P3-AZ-PICK-3-EVENING', 'date': '12-10-2026',
               'stateCode': 'AZ', 'state': 'Arizona', 'game': 'pick3',
               'gameName': 'Pick 3', 'draw': 'Evening Draw', 'number': '6-8-6'}
        with patch.object(s, '_async_fetch_us_pick_overview', new_callable=AsyncMock, return_value=[row]), \
                patch.object(s, '_async_fetch_us_pick_state_history', new_callable=AsyncMock, return_value=[]), \
                patch.object(s, '_async_fetch_new_jersey_pick_home', new_callable=AsyncMock, return_value=[]):
            rows = await s._async_fetch_working_us_pick_history('12-10-2026', 'pick3', [row])
            self.assertEqual('6-8-6', rows[0]['number'])
            self.assertEqual([], await s._async_fetch_working_us_pick_history('13-10-2026', 'pick3', [row]))
            row['date'] = ''
            self.assertEqual([], await s._async_fetch_working_us_pick_history('12-10-2026', 'pick3', [row]))
