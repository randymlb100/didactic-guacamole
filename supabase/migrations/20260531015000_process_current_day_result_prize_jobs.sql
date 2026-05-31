begin;

create or replace function public.lotterynet_process_result_reconcile_jobs_for_day(
  p_result_day_key text,
  p_job_limit int default 12,
  p_ticket_limit int default 500
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  job_row record;
  ticket_row record;
  processed_jobs int := 0;
  processed_tickets int := 0;
  job_tickets int;
begin
  for job_row in
    select *
    from public.result_reconcile_jobs
    where status = 'pending'
      and result_day_key = any(public.lotterynet_ticket_date_aliases(p_result_day_key))
    order by created_at desc
    limit greatest(coalesce(p_job_limit, 12), 1)
    for update skip locked
  loop
    begin
      job_tickets := 0;

      update public.result_reconcile_jobs
      set status = 'running',
          locked_at = now(),
          attempts = attempts + 1,
          last_error = null
      where id = job_row.id;

      for ticket_row in
        select distinct t.id
        from public.tickets t
        join public.ticket_items ti on ti.ticket_id = t.id
        where coalesce(t.draw_date_real::text, t.legacy_day_key, t.draw_date) = any(public.lotterynet_ticket_date_aliases(job_row.result_day_key))
          and (job_row.lottery_legacy_id is null or ti.lottery_legacy_id = job_row.lottery_legacy_id or ti.secondary_lottery_legacy_id = job_row.lottery_legacy_id)
          and t.deleted_at is null
          and t.voided_at is null
          and t.invalidated_at is null
          and lower(coalesce(t.status, t.estado, '')) not in ('pagado','paid','cobrado','premio_pagado')
        order by t.id
        limit greatest(coalesce(p_ticket_limit, 500), 1)
      loop
        perform public.lotterynet_reconcile_ticket_prize_v2(ticket_row.id);
        processed_tickets := processed_tickets + 1;
        job_tickets := job_tickets + 1;
      end loop;

      update public.result_reconcile_jobs
      set status = 'completed',
          completed_at = now(),
          locked_at = null,
          last_error = case
            when job_tickets >= greatest(coalesce(p_ticket_limit, 500), 1)
              then 'Completed after ticket limit; run again if more tickets are expected for this draw.'
            else null
          end
      where id = job_row.id;

      processed_jobs := processed_jobs + 1;
    exception when others then
      update public.result_reconcile_jobs
      set status = 'failed',
          locked_at = null,
          last_error = sqlerrm
      where id = job_row.id;
    end;
  end loop;

  return jsonb_build_object(
    'ok', true,
    'dayKey', p_result_day_key,
    'processedJobs', processed_jobs,
    'processedTickets', processed_tickets
  );
end;
$$;

revoke all on function public.lotterynet_process_result_reconcile_jobs_for_day(text, int, int) from public, anon, authenticated;
grant execute on function public.lotterynet_process_result_reconcile_jobs_for_day(text, int, int) to service_role;

commit;
