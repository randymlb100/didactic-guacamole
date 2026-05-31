-- Make official ticket inserts/updates wake the Android ticket screens quickly.
-- The app already listens to lotterynet_tickets_by_owner; this keeps that
-- realtime row fresh even when the ticket was written directly to tickets.

create index if not exists tickets_admin_updated_idx
on public.tickets (admin_key, updated_at desc)
where admin_key is not null;

create index if not exists tickets_cashier_updated_idx
on public.tickets (cashier_key, updated_at desc)
where cashier_key is not null;

create index if not exists tickets_admin_server_created_idx
on public.tickets (admin_key, server_created_at desc)
where admin_key is not null;

create index if not exists tickets_cashier_server_created_idx
on public.tickets (cashier_key, server_created_at desc)
where cashier_key is not null;

create or replace function public.lotterynet_touch_ticket_owner(p_owner_key text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_owner text := trim(coalesce(p_owner_key, ''));
begin
  if v_owner = '' then
    return;
  end if;

  insert into public.lotterynet_tickets_by_owner(owner_key, payload, updated_at)
  values (
    v_owner,
    jsonb_build_object('schemaVersion', 2, 'tickets', '[]'::jsonb, 'deletedIds', '[]'::jsonb),
    now()
  )
  on conflict (owner_key) do update
    set updated_at = excluded.updated_at;
end;
$$;

create or replace function public.lotterynet_touch_ticket_owners_from_ticket()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  perform public.lotterynet_touch_ticket_owner(new.admin_key);
  perform public.lotterynet_touch_ticket_owner(new.cashier_key);

  if tg_op = 'UPDATE' then
    if old.admin_key is distinct from new.admin_key then
      perform public.lotterynet_touch_ticket_owner(old.admin_key);
    end if;
    if old.cashier_key is distinct from new.cashier_key then
      perform public.lotterynet_touch_ticket_owner(old.cashier_key);
    end if;
  end if;

  return new;
end;
$$;

drop trigger if exists lotterynet_ticket_owner_realtime_touch on public.tickets;
create trigger lotterynet_ticket_owner_realtime_touch
after insert or update of status, estado, payout_amount, paid_at, voided_at, invalidated_at, deleted_at, updated_at, admin_key, cashier_key
on public.tickets
for each row
execute function public.lotterynet_touch_ticket_owners_from_ticket();

revoke all on function public.lotterynet_touch_ticket_owner(text) from public, anon, authenticated;
revoke all on function public.lotterynet_touch_ticket_owners_from_ticket() from public, anon, authenticated;
