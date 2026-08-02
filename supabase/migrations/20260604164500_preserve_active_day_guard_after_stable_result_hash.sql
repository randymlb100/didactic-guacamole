begin;

do $$
declare
  v_sql text;
begin
  select pg_get_functiondef('public.lotterynet_upsert_result_draws_from_payload(text, jsonb, text)'::regprocedure)
  into v_sql;

  if position('stable hash upsert only enqueues active-day prize jobs' in v_sql) = 0 then
    v_sql := replace(
      v_sql,
      E'      perform public.lotterynet_enqueue_result_reconcile_job(p_result_day_key, row_value->>''id'', draw_game);',
      E'      -- stable hash upsert only enqueues active-day prize jobs; older rows require explicit backfill.\n      if coalesce(public.lotterynet_result_day_key_to_date(p_result_day_key), date ''1900-01-01'') >=\n          (now() at time zone ''America/Santo_Domingo'')::date - interval ''1 day'' then\n        perform public.lotterynet_enqueue_result_reconcile_job(p_result_day_key, row_value->>''id'', draw_game);\n      end if;'
    );
    execute v_sql;
  end if;

  update public.result_reconcile_jobs
  set status = 'deferred_backfill',
      completed_at = coalesce(completed_at, now()),
      locked_at = null,
      last_error = 'Deferred old stable-hash result backlog to protect active-day delivery.'
  where status in ('pending', 'failed')
    and coalesce(public.lotterynet_result_day_key_to_date(result_day_key), date '1900-01-01')
        < (now() at time zone 'America/Santo_Domingo')::date - interval '1 day';
end;
$$;

comment on function public.lotterynet_upsert_result_draws_from_payload(text, jsonb, text)
is 'Normalizes result payloads with stable hashes; automatic prize jobs are limited to active days, while older rows require explicit backfill.';

commit;
