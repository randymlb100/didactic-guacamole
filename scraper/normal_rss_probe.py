"""Read-only live-source verification. Never imports credentials or writes results."""
import asyncio
from collections import Counter
import datetime as dt
import json
import sys
import httpx
from normal_rss import CATALOG, HybridNormalRun, parse_rss
import scrape_and_save as scraper


async def main():
    calls = Counter()
    async with httpx.AsyncClient(timeout=30, follow_redirects=True) as client:
        async def fetch(url):
            calls[url] += 1
            response = await client.get(url)
            response.raise_for_status()
            return response.text
        async def general(date):
            iso = dt.datetime.strptime(date, '%d-%m-%Y').strftime('%Y-%m-%d')
            html = await fetch(f'https://enloteria.com/resultados-loterias-{iso}')
            return scraper.parse_enloteria_general_jsonld_for_dates(html, [date])
        def detail(html, row, date):
            return scraper.parse_enloteria_result_jsonld_for_dates(html, row['id'], row['name'], [date], source_name=row['source_name'])
        dates = sys.argv[1:] or scraper.default_scrape_dates()[:2]
        run = HybridNormalRun(fetch, general, detail)
        rows = await asyncio.gather(*(run.collect(date) for date in dates))
        feed = parse_rss(await run._fetch('https://enloteria.com/rss'))
        summary = {
            'catalog': len(CATALOG), 'rss_known': len(feed),
            'rss_published': sum(bool(r['number']) for r in feed.values()),
            'http_requests': sum(calls.values()), 'unique_urls': len(calls),
            'duplicate_urls': {url: count for url, count in calls.items() if count > 1},
            'dates': {date: {'count': len(day), 'new_ids': [r['id'] for r in day if int(r['id']) >= 49],
                            'georgia_night': [r for r in day if r['id'] == '46']}
                      for date, day in zip(dates, rows)},
        }
        assert summary['rss_known'] == 60, summary
        assert not summary['duplicate_urls'], summary
        for date, day in zip(dates, rows):
            assert all(r['date'] == date and r['id'] in CATALOG for r in day)
        print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    asyncio.run(main())
