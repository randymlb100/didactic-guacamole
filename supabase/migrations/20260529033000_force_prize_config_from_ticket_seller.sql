begin;

do $$
declare
  v_def text;
  v_next text;
begin
  select pg_get_functiondef('public.lotterynet_resolve_ticket_prize_against_payload(jsonb,jsonb)'::regprocedure)
  into v_def;

  if v_def is null then
    raise exception 'lotterynet_resolve_ticket_prize_against_payload(jsonb,jsonb) not found';
  end if;

  v_next := replace(
    v_def,
    'config := coalesce(nullif(ticket->>''payoutConfigSnapshot'', '''')::jsonb, public.lotterynet_ticket_payout_config(ticket));',
    'config := public.lotterynet_ticket_payout_config(ticket);'
  );

  if v_next = v_def then
    raise exception 'Expected stale payoutConfigSnapshot branch not found in lotterynet_resolve_ticket_prize_against_payload';
  end if;

  execute v_next;
end
$$;

comment on function public.lotterynet_resolve_ticket_prize_against_payload(jsonb,jsonb)
is 'LotteryNet prize resolver: payout table is resolved from the ticket seller/cashier at reconcile time, not from stale ticket snapshots.';

commit;
