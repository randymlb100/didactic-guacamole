begin;

drop trigger if exists lotterynet_reject_invalid_ticket_owner_key_trigger
  on public.lotterynet_tickets_by_owner;
drop function if exists public.lotterynet_reject_invalid_ticket_owner_key();

insert into public.lotterynet_tickets_by_owner (owner_key, payload, updated_at)
select quarantine.owner_key, quarantine.payload, quarantine.updated_at
from private.lotterynet_ticket_owner_snapshot_quarantine quarantine
where not exists (
  select 1
  from public.lotterynet_tickets_by_owner current_row
  where current_row.owner_key = quarantine.owner_key
);

do $function$
begin
  if exists (
    select 1
    from private.lotterynet_ticket_owner_snapshot_quarantine quarantine
    join public.lotterynet_tickets_by_owner restored
      on restored.owner_key = quarantine.owner_key
    where encode(extensions.digest(restored.payload::text, 'sha256'), 'hex')
      <> quarantine.payload_sha256
  ) then
    raise exception 'Rollback checksum mismatch for invalid ticket owner snapshot';
  end if;
end;
$function$;

drop function if exists private.lotterynet_invalid_owner_snapshot_report(text);
drop table if exists private.lotterynet_ticket_owner_snapshot_quarantine;

commit;
