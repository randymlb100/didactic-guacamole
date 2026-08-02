begin;

select plan(5);

select has_table(
  'private',
  'lotterynet_invalid_ticket_cleanup_backup',
  'invalid ticket cleanup has a private backup table'
);

select is(
  (
    select count(*)
    from private.lotterynet_invalid_ticket_cleanup_backup
  ),
  8::bigint,
  'all legacy lotteryless tickets are preserved in the backup table'
);

select is(
  (
    select count(*)
    from public.tickets t
    where t.id in (
      '6bb543cb-c65e-4af7-90d8-bd2ff90392b7',
      '6c9c32df-03fe-413d-b5da-955d7a5e7ee4',
      '826f4583-512c-4b15-b402-5854d0f7568a',
      'deffb92a-5d61-468e-858c-63b2a2723ff2',
      '28634097-ef0c-4531-8e56-085502065b63',
      '0c90879f-129c-4f84-9a03-5b789163c132',
      '035e2598-c3b6-42db-84c0-36f6796dc354',
      'd3ef9f2c-176b-4300-98a0-3d65b942ca45'
    )
  ),
  0::bigint,
  'legacy lotteryless tickets are removed from public.tickets'
);

select is(
  (
    select count(*)
    from public.ticket_items ti
    where ti.ticket_id in (
      '6bb543cb-c65e-4af7-90d8-bd2ff90392b7',
      '6c9c32df-03fe-413d-b5da-955d7a5e7ee4',
      '826f4583-512c-4b15-b402-5854d0f7568a',
      'deffb92a-5d61-468e-858c-63b2a2723ff2',
      '28634097-ef0c-4531-8e56-085502065b63',
      '0c90879f-129c-4f84-9a03-5b789163c132',
      '035e2598-c3b6-42db-84c0-36f6796dc354',
      'd3ef9f2c-176b-4300-98a0-3d65b942ca45'
    )
  ),
  0::bigint,
  'legacy lotteryless ticket items are removed from public.ticket_items'
);

select is(
  (
    select count(*)
    from public.lotterynet_tickets_by_owner owner_row
    cross join lateral jsonb_array_elements(coalesce(owner_row.payload->'tickets', '[]'::jsonb)) as ticket(ticket)
    where ticket->>'id' in (
      '6bb543cb-c65e-4af7-90d8-bd2ff90392b7',
      '6c9c32df-03fe-413d-b5da-955d7a5e7ee4',
      '826f4583-512c-4b15-b402-5854d0f7568a',
      'deffb92a-5d61-468e-858c-63b2a2723ff2',
      '28634097-ef0c-4531-8e56-085502065b63',
      '0c90879f-129c-4f84-9a03-5b789163c132',
      '035e2598-c3b6-42db-84c0-36f6796dc354',
      'd3ef9f2c-176b-4300-98a0-3d65b942ca45'
    )
  ),
  0::bigint,
  'legacy lotteryless tickets are removed from owner snapshots'
);

select * from finish();

rollback;
