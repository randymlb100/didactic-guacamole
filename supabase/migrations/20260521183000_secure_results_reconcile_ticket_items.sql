-- Result refreshes are allowed through a narrow public policy, but the
-- after-insert/update trigger must reconcile tickets in private tables.
-- Keep ticket_items closed to clients and let only the server-owned functions
-- perform the reconciliation work.

alter function public.lotterynet_ticket_json_from_tables(uuid)
  security definer
  set search_path = public;

alter function public.lotterynet_calculate_ticket_prize(uuid)
  security definer
  set search_path = public;

alter function public.lotterynet_sync_ticket_owner_payload(uuid)
  security definer
  set search_path = public;

alter function public.lotterynet_reconcile_ticket_prize(uuid)
  security definer
  set search_path = public;

alter function public.lotterynet_reconcile_tickets_for_results_day()
  security definer
  set search_path = public;

revoke execute on function public.lotterynet_ticket_json_from_tables(uuid)
  from public, anon, authenticated;
revoke execute on function public.lotterynet_calculate_ticket_prize(uuid)
  from public, anon, authenticated;
revoke execute on function public.lotterynet_sync_ticket_owner_payload(uuid)
  from public, anon, authenticated;
revoke execute on function public.lotterynet_reconcile_ticket_prize(uuid)
  from public, anon, authenticated;
revoke execute on function public.lotterynet_reconcile_tickets_for_results_day()
  from public, anon, authenticated;

grant execute on function public.lotterynet_ticket_json_from_tables(uuid)
  to service_role;
grant execute on function public.lotterynet_calculate_ticket_prize(uuid)
  to service_role;
grant execute on function public.lotterynet_sync_ticket_owner_payload(uuid)
  to service_role;
grant execute on function public.lotterynet_reconcile_ticket_prize(uuid)
  to service_role;
grant execute on function public.lotterynet_reconcile_tickets_for_results_day()
  to service_role;
