begin;

create schema if not exists private;

revoke all on schema private from public, anon, authenticated;

create table if not exists private.lotterynet_ticket_owner_snapshot_quarantine (
  owner_key text primary key,
  payload jsonb not null,
  updated_at timestamptz,
  payload_sha256 text not null,
  quarantined_at timestamptz not null default now(),
  reconciliation jsonb not null
);

revoke all on private.lotterynet_ticket_owner_snapshot_quarantine
  from public, anon, authenticated;

create or replace function private.lotterynet_invalid_owner_snapshot_report(
  p_owner_key text
)
returns jsonb
language sql
security definer
stable
set search_path = public, private, extensions, pg_temp
as $function$
  with snapshot as (
    select
      owner_row.owner_key,
      case
        when jsonb_typeof(owner_row.payload) = 'array' then owner_row.payload
        when jsonb_typeof(owner_row.payload->'tickets') = 'array' then owner_row.payload->'tickets'
        else '[]'::jsonb
      end as tickets
    from public.lotterynet_tickets_by_owner owner_row
    where owner_row.owner_key = p_owner_key
  ),
  snapshot_ticket as (
    select
      coalesce(
        nullif(trim(ticket->>'id'), ''),
        nullif(trim(ticket->>'clientRequestId'), ''),
        nullif(trim(ticket->>'client_request_id'), ''),
        nullif(trim(ticket->>'serial'), '')
      ) as identity
    from snapshot
    cross join lateral jsonb_array_elements(snapshot.tickets) ticket
  ),
  matched as (
    select distinct
      snapshot_ticket.identity,
      official.id,
      official.admin_key,
      official.cashier_key
    from snapshot_ticket
    left join public.tickets official
      on official.id::text = snapshot_ticket.identity
      or official.client_request_id = snapshot_ticket.identity
      or official.legacy_ticket_id = snapshot_ticket.identity
      or official.ticket_code = snapshot_ticket.identity
    where snapshot_ticket.identity is not null
  )
  select jsonb_build_object(
    'snapshotTicketCount', (select count(*) from snapshot_ticket where identity is not null),
    'matchedOfficialCount', (select count(*) from matched where id is not null),
    'unmatchedCount', (select count(*) from matched where id is null),
    'adminKeys', coalesce((
      select jsonb_agg(distinct admin_key order by admin_key)
      from matched
      where nullif(trim(admin_key), '') is not null
    ), '[]'::jsonb),
    'cashierKeys', coalesce((
      select jsonb_agg(distinct cashier_key order by cashier_key)
      from matched
      where nullif(trim(cashier_key), '') is not null
    ), '[]'::jsonb)
  );
$function$;

revoke all on function private.lotterynet_invalid_owner_snapshot_report(text)
  from public, anon, authenticated;

insert into private.lotterynet_ticket_owner_snapshot_quarantine (
  owner_key,
  payload,
  updated_at,
  payload_sha256,
  reconciliation
)
select
  owner_row.owner_key,
  owner_row.payload,
  owner_row.updated_at,
  encode(extensions.digest(owner_row.payload::text, 'sha256'), 'hex'),
  private.lotterynet_invalid_owner_snapshot_report(owner_row.owner_key)
from public.lotterynet_tickets_by_owner owner_row
where lower(trim(owner_row.owner_key)) in ('null', 'undefined')
on conflict (owner_key) do nothing;

create or replace function public.lotterynet_reject_invalid_ticket_owner_key()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $function$
begin
  if nullif(trim(new.owner_key), '') is null
     or lower(trim(new.owner_key)) in ('null', 'undefined') then
    raise exception 'Owner de tickets invalido';
  end if;
  return new;
end;
$function$;

drop trigger if exists lotterynet_reject_invalid_ticket_owner_key_trigger
  on public.lotterynet_tickets_by_owner;
create trigger lotterynet_reject_invalid_ticket_owner_key_trigger
before insert or update of owner_key
on public.lotterynet_tickets_by_owner
for each row
execute function public.lotterynet_reject_invalid_ticket_owner_key();

revoke all on function public.lotterynet_reject_invalid_ticket_owner_key()
  from public, anon, authenticated;

comment on table private.lotterynet_ticket_owner_snapshot_quarantine
is 'Immutable backup and reconciliation evidence for invalid ticket-owner snapshots. Authoritative tickets are never changed.';

comment on function public.lotterynet_reject_invalid_ticket_owner_key()
is 'Rejects creation or reassignment of owner snapshots to blank, null, or undefined keys without touching existing payloads.';

commit;
