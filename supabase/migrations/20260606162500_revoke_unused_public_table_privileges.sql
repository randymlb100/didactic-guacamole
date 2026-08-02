begin;

-- Safe hardening pass:
-- Keep compatibility SELECT/read paths that the current APK can still need.
-- Remove privileges that client REST flows should never require.

revoke references, trigger, truncate
on table public.tickets
from anon, authenticated;

revoke references, trigger, truncate
on table public.lotterynet_users_state
from anon, authenticated;

revoke references, trigger, truncate
on table public.lotterynet_kv
from anon, authenticated;

revoke insert, update, delete, references, trigger, truncate
on table public.result_draws
from anon, authenticated;

revoke all
on table public.lotterynet_push_tokens
from anon, authenticated;

comment on table public.lotterynet_push_tokens
is 'Server-managed push token registry. Direct anon/authenticated table access is revoked; clients must use register-push-token.';

commit;

-- Rollback, if an old compatibility path unexpectedly needs these grants:
-- grant references, trigger, truncate on table public.tickets to anon, authenticated;
-- grant references, trigger, truncate on table public.lotterynet_users_state to anon, authenticated;
-- grant references, trigger, truncate on table public.lotterynet_kv to anon, authenticated;
-- grant insert, update, delete, references, trigger, truncate on table public.result_draws to anon, authenticated;
-- grant select, insert, update, delete, references, trigger, truncate on table public.lotterynet_push_tokens to anon, authenticated;
