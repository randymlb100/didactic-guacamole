begin;

create or replace function public.lotterynet_cleanup_ticket_owner_snapshot_on_delete()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
declare
  ticket_row public.tickets%rowtype;
  owner_keys text[];
  identifiers text[];
  status_text text;
begin
  if tg_op = 'DELETE' then
    ticket_row := old;
  else
    ticket_row := new;
  end if;
  if ticket_row.id is null then
    if tg_op = 'DELETE' then
      return old;
    end if;
    return new;
  end if;

  status_text := lower(coalesce(ticket_row.status, ticket_row.estado, ''));
  if ticket_row.deleted_at is null
     and status_text not in ('deleted', 'borrado', 'removed')
  then
    if tg_op = 'DELETE' then
      return old;
    end if;
    return new;
  end if;

  owner_keys := public.lotterynet_ticket_owner_aliases(ticket_row);
  identifiers := array_remove(array[
    nullif(trim(ticket_row.id::text), ''),
    nullif(trim(ticket_row.legacy_ticket_id), ''),
    nullif(trim(ticket_row.client_request_id), ''),
    nullif(trim(ticket_row.ticket_code), '')
  ], null);

  perform public.ln_mark_owner_snapshots_ticket_deleted(identifiers, owner_keys);
  if tg_op = 'DELETE' then
    return old;
  end if;
  return new;
end;
$function$;

drop trigger if exists lotterynet_ticket_delete_snapshot_cleanup on public.tickets;
create trigger lotterynet_ticket_delete_snapshot_cleanup
after update of status, estado, deleted_at, updated_at or delete
on public.tickets
for each row
execute function public.lotterynet_cleanup_ticket_owner_snapshot_on_delete();

revoke all on function public.lotterynet_cleanup_ticket_owner_snapshot_on_delete() from public, anon, authenticated;
grant execute on function public.lotterynet_cleanup_ticket_owner_snapshot_on_delete() to service_role;

comment on function public.lotterynet_cleanup_ticket_owner_snapshot_on_delete()
is 'Removes deleted tickets from owner snapshots so cashier deletes propagate to admin views immediately.';

commit;
