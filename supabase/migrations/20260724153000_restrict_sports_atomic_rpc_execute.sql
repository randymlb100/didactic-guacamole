-- Restrict direct Data API/RPC access to the atomic sports helpers.
-- Edge Functions call these helpers with service_role after validating the user.
-- Auth, pg_net, Realtime and the function bodies are intentionally unchanged.

begin;

revoke all on function public.create_sports_ticket_atomic(jsonb, jsonb) from public, anon, authenticated;
revoke all on function public.create_sports_ticket_atomic(jsonb, jsonb, numeric) from public, anon, authenticated;
revoke all on function public.pay_sports_ticket_atomic(uuid, text, numeric, text) from public, anon, authenticated;
revoke all on function public.settle_sports_ticket_atomic(uuid, text, text, text) from public, anon, authenticated;

grant execute on function public.create_sports_ticket_atomic(jsonb, jsonb) to service_role;
grant execute on function public.create_sports_ticket_atomic(jsonb, jsonb, numeric) to service_role;
grant execute on function public.pay_sports_ticket_atomic(uuid, text, numeric, text) to service_role;
grant execute on function public.settle_sports_ticket_atomic(uuid, text, text, text) to service_role;

commit;
