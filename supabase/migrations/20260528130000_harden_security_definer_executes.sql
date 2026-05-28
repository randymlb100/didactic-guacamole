begin;

-- Revoke execute permissions from public, anon, and authenticated roles
revoke execute on function public.ln_save_lotterynet_kv(text, text, timestamp with time zone) from public, anon, authenticated;
revoke execute on function public.ln_save_lotterynet_results_by_day(text, jsonb, timestamp with time zone) from public, anon, authenticated;

-- Grant execute permissions exclusively to the service_role for edge functions and internal scripts
grant execute on function public.ln_save_lotterynet_kv(text, text, timestamp with time zone) to service_role;
grant execute on function public.ln_save_lotterynet_results_by_day(text, jsonb, timestamp with time zone) to service_role;

commit;
