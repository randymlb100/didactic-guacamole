-- Avoid touching the same ticket-owner snapshot multiple times for one ticket update.
-- This keeps Realtime/snapshot behavior intact while reducing duplicate upserts/broadcasts.
create or replace function public.lotterynet_touch_ticket_owners_from_ticket()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
declare
  owner_key text;
  owner_keys text[];
begin
  if tg_op = 'UPDATE' then
    select coalesce(array_agg(distinct key), array[]::text[])
      into owner_keys
    from unnest(
      public.lotterynet_ticket_owner_aliases(new)
      || public.lotterynet_ticket_owner_aliases(old)
    ) as key
    where nullif(trim(key), '') is not null;
  else
    select coalesce(array_agg(distinct key), array[]::text[])
      into owner_keys
    from unnest(public.lotterynet_ticket_owner_aliases(new)) as key
    where nullif(trim(key), '') is not null;
  end if;

  foreach owner_key in array owner_keys
  loop
    perform public.lotterynet_touch_ticket_owner(owner_key);
  end loop;

  return new;
end;
$function$;

revoke all on function public.lotterynet_touch_ticket_owners_from_ticket() from public, anon, authenticated;
grant execute on function public.lotterynet_touch_ticket_owners_from_ticket() to service_role;
