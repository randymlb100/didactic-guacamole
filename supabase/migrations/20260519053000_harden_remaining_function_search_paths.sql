-- Continue advisor hardening by fixing remaining public functions that rely on
-- the caller's mutable search_path. No business logic or permissions change.

alter function public.ln_create_policy_if_missing(text, text, text) set search_path = public;
alter function public.ln_current_profile_id() set search_path = public;
alter function public.ln_current_role() set search_path = public;
alter function public.ln_day_key_to_iso(text) set search_path = public;
alter function public.ln_iso_to_day_key(text) set search_path = public;
alter function public.ln_ticket_code() set search_path = public;
alter function public.ln_touch_identity_bridge_updated_at() set search_path = public;
alter function public.lotterynet_classic_ticket_prize(jsonb) set search_path = public;
alter function public.lotterynet_preserve_terminal_ticket_state() set search_path = public;
alter function public.lotterynet_safe_jsonb_numeric(jsonb, text[], numeric) set search_path = public;
alter function public.lotterynet_ticket_date_aliases(text) set search_path = public;
alter function public.lotterynet_ticket_payout_config(jsonb) set search_path = public;
alter function public.set_ota_updated_at() set search_path = public;
