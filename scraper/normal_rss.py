"""Normal-lottery RSS/HTML collection; no writes and no Pick dependencies."""
import asyncio
import datetime as dt
import json
import logging
from pathlib import Path
import re
from zoneinfo import ZoneInfo
from xml.etree import ElementTree

CATALOG = {row['id']: row for row in json.loads(
    Path(__file__).with_name('normal_lottery_catalog.json').read_text(encoding='utf-8'))}
RSS_URL = 'https://enloteria.com/rss'
logger = logging.getLogger(__name__)


def detail_url(row):
    return f"https://enloteria.com/resultados-{row['slug']}-hoy"


def valid_number(value):
    return bool(re.fullmatch(r'\d{2}-\d{2}-\d{2}', str(value or ''))) and value != '00-00-00'


def parse_rss(text):
    if len(text.encode('utf-8')) > 2_000_000 or re.search(r'<!DOCTYPE|<!ENTITY', text, re.I):
        raise ValueError('Unsafe or oversized normal-lottery RSS')
    by_url = {detail_url(row): row for row in CATALOG.values()}
    results = {}
    for item in ElementTree.fromstring(text).findall('./channel/item')[:200]:
        row = by_url.get((item.findtext('link') or '').strip())
        if row is None:
            continue
        # pubDate sorts feed entries; it is NOT the draw date. GUIDs repeat daily.
        match = re.search(r':\s*(\d{2})\s*[-–]\s*(\d{2})\s*[-–]\s*(\d{2})\s*$', item.findtext('title') or '')
        number = '-'.join(match.groups()) if match else None
        if number is not None and not valid_number(number):
            number = None
        entry = dict(row, number=number)
        if row['id'] in results and results[row['id']]['number'] != number:
            raise ValueError(f"Conflicting RSS entries for {row['id']}")
        results[row['id']] = entry
    return results


def draw_is_due(row, date, now):
    day = dt.datetime.strptime(date, '%d-%m-%Y').date()
    if row['id'] in {'61','62','63','64'} and day.weekday() == 6:
        return False
    clock = row['draw_time']
    if row['id'] == '13' and day.weekday() == 6:
        clock = '18:00'
    if row['id'] == '15' and day.weekday() == 6:
        clock = '15:50'
    if row['id'] == '56' and day.weekday() >= 5:
        clock = '12:28'
    if row['id'] == '57' and day.weekday() == 6:
        clock = '20:10'
    time = dt.datetime.strptime(clock, '%I:%M %p').time() if 'M' in clock else dt.time.fromisoformat(clock)
    scheduled = dt.datetime.combine(day, time, ZoneInfo(row['zone']))
    return now >= scheduled


class HybridNormalRun:
    """Share in-flight requests only within one run; never retain stale daily data."""
    def __init__(self, fetch_text, general_fetch, parse_detail, now=None):
        self.fetch_text = fetch_text
        self.general_fetch = general_fetch
        self.parse_detail = parse_detail
        self.now = now or (lambda: dt.datetime.now(dt.timezone.utc))
        self.requests = {}
        self.general_requests = {}
        self.limit = asyncio.Semaphore(6)

    async def _fetch(self, url):
        async def bounded():
            async with self.limit:
                return await self.fetch_text(url)
        if url not in self.requests:
            self.requests[url] = asyncio.create_task(bounded())
        return await self.requests[url]

    async def collect(self, date, existing_rows=None, wanted_ids=None):
        complete = {str(r.get('id')) for r in existing_rows or []
                    if r.get('status') == 'no_draw' or
                    (r.get('status') in (None, '', 'published') and valid_number(r.get('number')))}
        wanted = ((set(CATALOG) if wanted_ids is None else set(map(str, wanted_ids))) & set(CATALOG)) - complete
        now = self.now()
        due = {key for key in wanted if draw_is_due(CATALOG[key], date, now)}
        if not due:
            logger.info('Normal hybrid %s: no missing draw due; skipping source HTTP', date)
            return []
        try:
            rss = parse_rss(await self._fetch(RSS_URL))
        except Exception as exc:
            logger.warning('Normal RSS unavailable; using dated HTML: %s', type(exc).__name__)
            rss = None
        if date not in self.general_requests:
            self.general_requests[date] = asyncio.create_task(self.general_fetch(date))
        try:
            general = await self.general_requests[date]
        except Exception as exc:
            logger.warning('Normal dated HTML unavailable: %s', type(exc).__name__)
            general = []
        accepted, conflicts = {}, set()

        def accept(row):
            if not row or str(row.get('id')) not in due or row.get('date') != date or not valid_number(row.get('number')):
                return
            key = str(row['id'])
            # Only compare with the feed's source-local day, never yesterday's numbers.
            feed_row = (rss or {}).get(key)
            source_today = now.astimezone(ZoneInfo(CATALOG[key]['zone'])).strftime('%d-%m-%Y')
            if date == source_today and feed_row and feed_row['number'] and feed_row['number'] != row['number']:
                conflicts.add(key)
                logger.warning('RSS/HTML conflict for normal lottery %s on %s; withholding result', key, date)
                return
            accepted[key] = dict(row, id=key, name=CATALOG[key]['name'], source='https://enloteria.com/rss+html')

        for row in general:
            accept(row)
        async def detail(key):
            row = CATALOG[key]
            try:
                html = await self._fetch(detail_url(row))
                accept(self.parse_detail(html, row, date))
            except Exception as exc:
                logger.warning('Normal detail unavailable for %s: %s', key, type(exc).__name__)
        await asyncio.gather(*(detail(key) for key in sorted(due - set(accepted) - conflicts, key=int)))
        logger.info('Normal hybrid %s: %d due, %d dated results, %d conflicts; %d shared source URLs',
                    date, len(due), len(accepted), len(conflicts), len(self.requests) + len(self.general_requests))
        return [accepted[key] for key in sorted(accepted, key=int)]
