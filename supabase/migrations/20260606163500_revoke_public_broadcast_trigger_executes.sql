begin;

-- These are trigger-only SECURITY DEFINER functions. Clients should not call
-- them through PostgREST RPC; table triggers execute them internally.

revoke execute on function public.lotterynet_broadcast_ticket_owner_touch()
from public, anon, authenticated;

revoke execute on function public.lotterynet_broadcast_result_draw_touch()
from public, anon, authenticated;

grant execute on function public.lotterynet_broadcast_ticket_owner_touch()
to service_role;

grant execute on function public.lotterynet_broadcast_result_draw_touch()
to service_role;

commit;

-- Rollback, if an external caller is later proven to depend on direct RPC:
-- grant execute on function public.lotterynet_broadcast_ticket_owner_touch() to anon, authenticated;
-- grant execute on function public.lotterynet_broadcast_result_draw_touch() to anon, authenticated;
