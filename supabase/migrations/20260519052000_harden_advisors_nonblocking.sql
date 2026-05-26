-- Harden Supabase advisor warnings without blocking existing app flows.
-- This migration removes broad public writes while preserving public reads used by
-- compatibility screens, Realtime notifications, and result caches.

drop policy if exists "LotteryNet compatibility write kv" on public.lotterynet_kv;
drop policy if exists "anon_upsert" on public.lotterynet_kv;
drop policy if exists "LotteryNet compatibility write master" on public.lotterynet_master_state;
drop policy if exists "LotteryNet compatibility write tickets" on public.lotterynet_tickets_by_owner;
drop policy if exists "LotteryNet compatibility write results" on public.lotterynet_results_by_day;
drop policy if exists "LotteryNet compatibility write pick results" on public.lotterynet_pick_results_by_day;
drop policy if exists "LotteryNet compatibility write recharges" on public.lotterynet_recharges_by_owner;

revoke execute on function public.install_results_server_refresh_cron() from public, anon, authenticated;
revoke execute on function public.ln_actor_from_legacy_state(text) from public, anon, authenticated;
revoke execute on function public.ln_block_master_admin_ticket_insert() from public, anon, authenticated;
revoke execute on function public.ln_ticket_server_clock(uuid, date, text, boolean) from public, anon, authenticated;

grant execute on function public.install_results_server_refresh_cron() to service_role;
grant execute on function public.ln_actor_from_legacy_state(text) to service_role;
grant execute on function public.ln_block_master_admin_ticket_insert() to service_role;
grant execute on function public.ln_ticket_server_clock(uuid, date, text, boolean) to service_role;

alter function public.lotterynet_digits_only(text) set search_path = public;
alter function public.lotterynet_pick_box_way(text) set search_path = public;
alter function public.lotterynet_is_permutation_match(text, text) set search_path = public;
alter function public.lotterynet_resolve_ticket_prize(jsonb) set search_path = public;
alter function public.lotterynet_ticket_json_from_tables(uuid) set search_path = public;
alter function public.lotterynet_calculate_ticket_prize(uuid) set search_path = public;
alter function public.ln_cashier_play_sale_limit(jsonb, public.ln_play_type) set search_path = public;
alter function public.ln_touch_updated_at() set search_path = public;
alter function public.ln_sync_legacy_users_array(jsonb) set search_path = public;
alter function public.ln_lotterynet_users_state_sync_users_trigger() set search_path = public;
alter function public.lotterynet_reconcile_ticket_prize(uuid) set search_path = public;
alter function public.lotterynet_pay_ticket_server_first(uuid, text, text, text, text, text, text) set search_path = public;
alter function public.ln_jsonb_number(jsonb, text[], numeric) set search_path = public;
alter function public.ln_cashier_day_sale_limit(jsonb) set search_path = public;
alter function public.ln_play_type_from_text(text) set search_path = public;
alter function public.ln_sort_digits(text) set search_path = public;
alter function public.ln_sale_limit_bucket(public.ln_play_type, text) set search_path = public;
alter function public.ln_normalize_ticket_play_input(text, text, text, text) set search_path = public;
alter function public.ln_is_master() set search_path = public;
alter function public.ln_same_admin_network(uuid) set search_path = public;

do $$
begin
  if exists (
    select 1
    from pg_policies
    where schemaname = 'public'
      and tablename = 'profiles'
      and policyname = 'profiles_select_network'
  ) then
    execute $policy$
      alter policy "profiles_select_network"
      on public.profiles
      using (
        (select public.ln_is_master())
        or id = (select auth.uid())
        or public.ln_same_admin_network(coalesce(admin_owner_id, id))
      )
    $policy$;
  end if;
end $$;
