begin;

select plan(5);

select has_table(
  'private',
  'lotterynet_ticket_owner_snapshot_quarantine',
  'invalid owner snapshots have a private quarantine table'
);

select has_function(
  'private',
  'lotterynet_invalid_owner_snapshot_report',
  array['text'],
  'invalid owner reconciliation report exists'
);

select is(
  (
    select count(*)
    from public.lotterynet_tickets_by_owner owner_row
    left join private.lotterynet_ticket_owner_snapshot_quarantine quarantine
      on quarantine.owner_key = owner_row.owner_key
    where lower(trim(owner_row.owner_key)) in ('null', 'undefined')
      and quarantine.owner_key is null
  ),
  0::bigint,
  'every existing invalid owner snapshot is backed up'
);

select is(
  (
    select count(*)
    from private.lotterynet_ticket_owner_snapshot_quarantine quarantine
    where encode(extensions.digest(quarantine.payload::text, 'sha256'), 'hex')
      <> quarantine.payload_sha256
  ),
  0::bigint,
  'quarantine payload checksums are valid'
);

select is(
  (
    select count(*)
    from private.lotterynet_ticket_owner_snapshot_quarantine quarantine
    where coalesce((quarantine.reconciliation->>'unmatchedCount')::integer, 0) > 0
  ),
  0::bigint,
  'quarantined snapshots contain no unrecoverable ticket identities'
);

select * from finish();

rollback;
