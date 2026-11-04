import asyncio
import datetime as dt
import unittest
from normal_rss import HybridNormalRun, CATALOG, parse_rss, draw_is_due

def feed(title='Georgia Noche hoy: 07-08-09', link='georgia-noche'):
    return f'<rss><channel><item><title>{title}</title><link>https://enloteria.com/resultados-{link}-hoy</link><pubDate>Sun, 01 Nov 2026 23:59:58 -0400</pubDate></item></channel></rss>'

class RssContracts(unittest.IsolatedAsyncioTestCase):
    def test_catalog_has_all_sixty_normal_ids_and_no_pick(self):
        self.assertEqual(set(map(str, range(1,65)))-{'19','20','21','22'}, set(CATALOG))
        self.assertEqual(60, len({r['slug'] for r in CATALOG.values()}))

    def test_rss_preserves_zeroes_and_does_not_trust_pubdate(self):
        self.assertEqual('07-08-09', parse_rss(feed())['46']['number'])
        self.assertNotIn('date', parse_rss(feed())['46'])

    def test_pending_and_unknown_and_unsafe_links_never_become_results(self):
        self.assertIsNone(parse_rss(feed('Georgia Noche hoy: pendiente'))['46']['number'])
        self.assertEqual({},parse_rss(feed(link='unknown')))
        self.assertEqual({},parse_rss(feed().replace('https://enloteria.com/','https://evil.example/')))
        self.assertRaises(ValueError,parse_rss,'<!DOCTYPE rss><rss/>')

    def test_georgia_due_uses_source_day_across_midnight(self):
        before=dt.datetime.fromisoformat('2026-11-03T04:10:00+00:00')
        after=dt.datetime.fromisoformat('2026-11-03T04:50:00+00:00')
        self.assertFalse(draw_is_due(CATALOG['46'],'02-11-2026',before))
        self.assertTrue(draw_is_due(CATALOG['46'],'02-11-2026',after))
        self.assertFalse(draw_is_due(CATALOG['46'],'03-11-2026',after))
        self.assertFalse(draw_is_due(CATALOG['61'],'08-11-2026',after.replace(day=9)))

    async def test_shared_requests_dates_and_html_failover(self):
        calls=[]
        async def fetch(url):
            calls.append(url)
            return feed() if url.endswith('/rss') else 'html'
        async def general(date): return []
        def parse(html,row,date): return dict(row, date=date, number='07-08-09', source='https://enloteria.com/rss')
        run=HybridNormalRun(fetch,general,parse,now=lambda:dt.datetime.fromisoformat('2026-11-03T04:50:00+00:00'))
        a,b=await asyncio.gather(run.collect('02-11-2026',wanted_ids={'46'}),run.collect('03-11-2026',wanted_ids={'46'}))
        self.assertEqual('02-11-2026',a[0]['date'])
        self.assertEqual([],b)
        self.assertEqual(1,calls.count('https://enloteria.com/rss'))
        self.assertEqual(len(calls),len(set(calls)))

    async def test_existing_unchanged_skips_all_http(self):
        async def fetch(url): raise AssertionError(url)
        async def general(date): raise AssertionError(date)
        run=HybridNormalRun(fetch,general,lambda *args:None)
        self.assertEqual([],await run.collect('02-11-2026',existing_rows=[{'id':'46','status':'published','number':'07-08-09'}],wanted_ids={'46'}))

    async def test_pending_number_is_not_mistaken_for_a_published_result(self):
        calls=[]
        async def fetch(url): calls.append(url);return feed()
        async def general(date): return [{'id':'46','date':date,'number':'07-08-09'}]
        run=HybridNormalRun(fetch,general,lambda *args:None,now=lambda:dt.datetime.fromisoformat('2026-11-03T04:50:00+00:00'))
        rows=await run.collect('02-11-2026',existing_rows=[{'id':'46','status':'pending','number':'07-08-09'}],wanted_ids={'46'})
        self.assertEqual(1,len(rows))
        self.assertEqual(1,len(calls))

    async def test_concurrent_collectors_share_general_and_detail_requests(self):
        calls=[];generals=[]
        async def fetch(url):
            calls.append(url);await asyncio.sleep(0)
            return feed() if url.endswith('/rss') else 'html'
        async def general(date): generals.append(date);await asyncio.sleep(0);return []
        def parse(html,row,date): return dict(row,date=date,number='07-08-09')
        run=HybridNormalRun(fetch,general,parse,now=lambda:dt.datetime.fromisoformat('2026-11-03T04:50:00+00:00'))
        rows=await asyncio.gather(*(run.collect('02-11-2026',wanted_ids={'46'}) for _ in range(4)))
        self.assertTrue(all(len(day)==1 for day in rows))
        self.assertEqual(2,len(calls))
        self.assertEqual(['02-11-2026'],generals)

    async def test_rss_error_uses_html_and_wrong_date_is_rejected(self):
        async def fetch(url):
            if url.endswith('/rss'): raise ValueError('offline')
            return 'html'
        async def general(date): return []
        def parse(html,row,date): return {'id':'46','date':'01-11-2026','number':'07-08-09'}
        run=HybridNormalRun(fetch,general,parse,now=lambda:dt.datetime.fromisoformat('2026-11-03T04:50:00+00:00'))
        self.assertEqual([],await run.collect('02-11-2026',wanted_ids={'46'}))

    async def test_html_survives_rss_failure_and_general_avoids_detail(self):
        calls=[]
        async def fetch(url): calls.append(url);raise ValueError('RSS offline')
        async def general(date): return [{'id':'46','date':date,'number':'07-08-09'}]
        run=HybridNormalRun(fetch,general,lambda *args:None,now=lambda:dt.datetime.fromisoformat('2026-11-03T04:50:00+00:00'))
        self.assertEqual('07-08-09',(await run.collect('02-11-2026',wanted_ids={'46'}))[0]['number'])
        self.assertEqual(['https://enloteria.com/rss'],calls)

    async def test_rss_alone_cannot_publish_an_undated_result(self):
        async def fetch(url):
            if url.endswith('/rss'): return feed()
            raise ValueError('HTML offline')
        async def general(date): raise ValueError('HTML offline')
        run=HybridNormalRun(fetch,general,lambda *args:None,now=lambda:dt.datetime.fromisoformat('2026-11-03T04:50:00+00:00'))
        self.assertEqual([],await run.collect('02-11-2026',wanted_ids={'46'}))

    def test_georgia_dst_summer_winter_and_sunday_overrides(self):
        self.assertTrue(draw_is_due(CATALOG['46'],'01-07-2026',dt.datetime.fromisoformat('2026-07-02T03:35:00+00:00')))
        self.assertFalse(draw_is_due(CATALOG['46'],'02-07-2026',dt.datetime.fromisoformat('2026-07-02T03:35:00+00:00')))
        sunday=dt.datetime.fromisoformat('2026-11-08T22:01:00+00:00')
        self.assertTrue(draw_is_due(CATALOG['13'],'08-11-2026',sunday))
        self.assertTrue(draw_is_due(CATALOG['15'],'08-11-2026',sunday))
        self.assertFalse(draw_is_due(CATALOG['57'],'08-11-2026',sunday))

    async def test_all_sixty_identities_use_real_shared_jsonld_parser(self):
        import json
        import scrape_and_save as scraper
        calls=[]
        async def fetch(url):
            calls.append(url)
            if url.endswith('/rss'): return '<rss><channel/></rss>'
            row=next(r for r in CATALOG.values() if url.endswith(r['slug']+'-hoy'))
            return '<script type="application/ld+json">'+json.dumps({'@type':'Event','name':row['source_name'],
                'startDate':'2026-11-02T23:59:00-05:00','description':'Los números ganadores son: 07, 08, 09.'})+'</script>'
        async def general(date): return []
        def parse(html,row,date):
            return scraper.parse_enloteria_result_jsonld_for_dates(html,row['id'],row['name'],[date],source_name=row['source_name'])
        run=HybridNormalRun(fetch,general,parse,now=lambda:dt.datetime.fromisoformat('2026-11-04T12:00:00+00:00'))
        rows=await run.collect('02-11-2026')
        self.assertEqual(set(CATALOG),{r['id'] for r in rows})
        self.assertEqual(61,len(calls))
        self.assertEqual(len(calls),len(set(calls)))

    async def test_before_first_draw_does_not_fetch_rss_or_html(self):
        async def unexpected(*args): raise AssertionError('No normal draw due')
        run=HybridNormalRun(unexpected,unexpected,lambda *args:None,now=lambda:dt.datetime.fromisoformat('2026-11-02T10:00:00+00:00'))
        self.assertEqual([],await run.collect('02-11-2026'))

    async def test_cron_and_manual_normal_entrypoints_share_the_same_run(self):
        import scrape_and_save as scraper
        from unittest.mock import AsyncMock, patch
        calls=[]
        async def fetch(url): calls.append(url);return feed() if url.endswith('/rss') else 'html'
        async def general(date): return []
        def parse(html,row,date): return dict(row,date=date,number='07-08-09')
        run=HybridNormalRun(fetch,general,parse,now=lambda:dt.datetime.fromisoformat('2026-11-03T04:50:00+00:00'))
        complete=[dict(row,date='02-11-2026',number='07-08-09',status='published') for key,row in CATALOG.items() if key!='46']
        with patch.object(scraper,'SCRAPE_PICK_RESULTS',False), patch.object(scraper,'build_king_no_draw_rows',return_value=[]):
            first=await scraper._async_scrape('02-11-2026',hybrid=run,existing_rows=complete)
            second=await scraper._async_scrape_missing_rd_results('02-11-2026',{'46'},hybrid=run)
        self.assertEqual(['46'],[r['id'] for r in first])
        self.assertEqual(first,second)
        self.assertEqual(2,len(calls))
        with patch.object(scraper,'_async_save_native_results_table',new_callable=AsyncMock) as write, \
                patch.object(scraper,'async_dispatch_reconcile_rows',new_callable=AsyncMock,create=True):
            await scraper._async_save_to_supabase('02-11-2026',first,existing_rows=complete+first)
            # Same numbers already accepted: no result rewrite or repeated ticket work.
            write.assert_not_awaited()

    async def test_cron_delta_never_prunes_unchanged_normal_new_jersey(self):
        import scrape_and_save as scraper
        from unittest.mock import AsyncMock, patch
        from contextlib import ExitStack
        date='02-11-2026'
        existing=[{'id':'25','name':'New Jersey Tarde','date':date,'number':'07-08-09','status':'published'}]
        new=[{'id':'50','name':'Pennsylvania Día','date':date,'number':'04-05-06'}]
        with ExitStack() as stack:
            for name,value in [('get_http_client',type('Client',(),{})()),('default_scrape_dates',[date]),
                               ('get_dr_date_str',date),('should_run_full_us_pick_sweep',False),
                               ('recently_closed_us_pick_catalog_rows',[])]:
                stack.enter_context(patch.object(scraper,name,return_value=value))
            stack.enter_context(patch.object(scraper,'SUPABASE_KEY','test'))
            stack.enter_context(patch('sys.argv',['scrape_and_save.py']))
            for name,value in [('_async_fetch_kv_snapshot',{f'lot_results_cache_by_day:{date}':existing,scraper.pick_results_cache_key(date):[]}),
                               ('_async_fetch_pick_snapshot',{scraper.pick_results_cache_key(date):[]}),
                               ('_async_prefetch_authoritative_results',None),('_async_fetch_existing_pick_results_from_supabase',[]),
                               ('_async_scrape',new),('async_recover_pending_reconcile_scopes',None),('close_http_client',None)]:
                stack.enter_context(patch.object(scraper,name,new_callable=AsyncMock,return_value=value))
            save=stack.enter_context(patch.object(scraper,'_async_save_to_supabase',new_callable=AsyncMock))
            await scraper._async_main()
        self.assertEqual({'19','20','21','22'},save.call_args.kwargs['prune_missing_ids'])
        self.assertEqual(existing,save.call_args.kwargs['existing_rows'])
        merged=scraper.merge_results_by_id(existing,new,save.call_args.kwargs['prune_missing_ids'])
        self.assertEqual({'25','50'},{r['id'] for r in merged})

    async def test_conflict_is_quarantined_and_general_match_avoids_detail(self):
        calls=[]
        async def fetch(url): calls.append(url);return feed()
        async def general(date): return [{'id':'46','date':date,'number':'01-02-03'}]
        run=HybridNormalRun(fetch,general,lambda *args:None,now=lambda:dt.datetime.fromisoformat('2026-11-03T04:50:00+00:00'))
        self.assertEqual([],await run.collect('02-11-2026',wanted_ids={'46'}))
        self.assertEqual(['https://enloteria.com/rss'],calls)

if __name__=='__main__': unittest.main()
