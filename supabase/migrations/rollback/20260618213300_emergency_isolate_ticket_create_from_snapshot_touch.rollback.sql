alter table public.tickets
enable trigger lotterynet_ticket_owner_realtime_touch;

comment on function public.lotterynet_touch_ticket_owners_from_ticket()
is 'Touches deduplicated ticket-owner aliases after official ticket state changes.';
