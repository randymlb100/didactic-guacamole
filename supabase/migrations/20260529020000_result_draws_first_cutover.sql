begin;

do $$
begin
  alter publication supabase_realtime add table public.result_draws;
exception
  when duplicate_object then null;
end
$$;

create or replace function public.lotterynet_touch_results_signal(
  p_result_day_key text,
  p_source text,
  p_changed_count int
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  -- Result realtime now comes from public.result_draws directly.
  return;
end;
$$;

create or replace function public.lotterynet_resolve_ticket_prize_v2(ticket jsonb)
returns jsonb
language plpgsql
stable
set search_path = public
as $$
declare
  raw_date text;
  normalized_payload jsonb;
begin
  raw_date := coalesce(nullif(ticket->>'drawDateKey',''), nullif(ticket->>'drawDate',''), nullif(ticket->>'dayKey',''), nullif(ticket->>'date',''));
  normalized_payload := public.lotterynet_result_draws_payload(raw_date);
  return public.lotterynet_resolve_ticket_prize_against_payload(ticket, normalized_payload)
    || jsonb_build_object('source', 'result_draws');
end;
$$;

revoke all on function public.lotterynet_touch_results_signal(text, text, int) from public, anon, authenticated;

commit;
