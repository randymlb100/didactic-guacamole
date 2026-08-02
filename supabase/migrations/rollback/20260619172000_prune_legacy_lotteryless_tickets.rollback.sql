insert into public.tickets
select (jsonb_populate_record(null::public.tickets, backup.ticket_row)).*
from private.lotterynet_invalid_ticket_cleanup_backup backup
where backup.ticket_id in (
  '6bb543cb-c65e-4af7-90d8-bd2ff90392b7',
  '6c9c32df-03fe-413d-b5da-955d7a5e7ee4',
  '826f4583-512c-4b15-b402-5854d0f7568a',
  'deffb92a-5d61-468e-858c-63b2a2723ff2',
  '28634097-ef0c-4531-8e56-085502065b63',
  '0c90879f-129c-4f84-9a03-5b789163c132',
  '035e2598-c3b6-42db-84c0-36f6796dc354',
  'd3ef9f2c-176b-4300-98a0-3d65b942ca45'
)
on conflict (id) do nothing;

insert into public.ticket_items
select item.*
from private.lotterynet_invalid_ticket_cleanup_backup backup
cross join lateral jsonb_populate_recordset(null::public.ticket_items, backup.items) as item
where backup.ticket_id in (
  '6bb543cb-c65e-4af7-90d8-bd2ff90392b7',
  '6c9c32df-03fe-413d-b5da-955d7a5e7ee4',
  '826f4583-512c-4b15-b402-5854d0f7568a',
  'deffb92a-5d61-468e-858c-63b2a2723ff2',
  '28634097-ef0c-4531-8e56-085502065b63',
  '0c90879f-129c-4f84-9a03-5b789163c132',
  '035e2598-c3b6-42db-84c0-36f6796dc354',
  'd3ef9f2c-176b-4300-98a0-3d65b942ca45'
)
on conflict (id) do nothing;

do $$
declare
  v_ticket_id uuid;
begin
  for v_ticket_id in
    select ticket_id
    from private.lotterynet_invalid_ticket_cleanup_backup
    where ticket_id in (
      '6bb543cb-c65e-4af7-90d8-bd2ff90392b7',
      '6c9c32df-03fe-413d-b5da-955d7a5e7ee4',
      '826f4583-512c-4b15-b402-5854d0f7568a',
      'deffb92a-5d61-468e-858c-63b2a2723ff2',
      '28634097-ef0c-4531-8e56-085502065b63',
      '0c90879f-129c-4f84-9a03-5b789163c132',
      '035e2598-c3b6-42db-84c0-36f6796dc354',
      'd3ef9f2c-176b-4300-98a0-3d65b942ca45'
    )
  loop
    perform public.lotterynet_sync_ticket_owner_payload(v_ticket_id);
  end loop;
end $$;
