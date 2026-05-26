-- Production guard for ticket accounting RPCs. Edge Functions use
-- service_role internally after JWT validation; direct Data API/RPC callers
-- must not execute these accounting helpers.

revoke execute on function public.ln_create_ticket_legacy(jsonb)
  from public, anon, authenticated;

revoke execute on function public.ln_void_ticket_legacy(jsonb)
  from public, anon, authenticated;

revoke execute on function public.ln_legacy_report(jsonb)
  from public, anon, authenticated;

revoke execute on function public.ln_ticket_server_clock(uuid, date)
  from public, anon, authenticated;

revoke execute on function public.ln_reserve_number_limit(
  text,
  text,
  uuid,
  date,
  public.ln_play_type,
  text,
  numeric
) from public, anon, authenticated;

revoke execute on function public.ln_reserve_legacy_admin_number_limit(
  text,
  text,
  date,
  text,
  text,
  numeric,
  numeric
) from public, anon, authenticated;

revoke execute on function public.ln_apply_number_limit_reservation(text, uuid)
  from public, anon, authenticated;

revoke execute on function public.ln_apply_legacy_admin_number_limit_reservation(text, uuid)
  from public, anon, authenticated;

revoke execute on function public.ln_release_number_limit(text)
  from public, anon, authenticated;

revoke execute on function public.ln_release_number_limit(text, boolean)
  from public, anon, authenticated;

revoke execute on function public.ln_release_legacy_admin_number_limit(text)
  from public, anon, authenticated;

revoke execute on function public.ln_release_legacy_admin_number_limit(text, boolean)
  from public, anon, authenticated;

revoke execute on function public.lotterynet_pay_ticket_server_first(
  uuid,
  text,
  text,
  text,
  text,
  text,
  text
) from public, anon, authenticated;
