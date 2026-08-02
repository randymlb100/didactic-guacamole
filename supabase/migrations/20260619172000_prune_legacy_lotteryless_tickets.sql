create schema if not exists private;

create table if not exists private.lotterynet_invalid_ticket_cleanup_backup (
  ticket_id uuid primary key,
  ticket_row jsonb not null,
  items jsonb not null,
  backed_up_at timestamptz not null default now()
);

delete from private.lotterynet_invalid_ticket_cleanup_backup
where ticket_id in (
  '6bb543cb-c65e-4af7-90d8-bd2ff90392b7',
  '6c9c32df-03fe-413d-b5da-955d7a5e7ee4',
  '826f4583-512c-4b15-b402-5854d0f7568a',
  'deffb92a-5d61-468e-858c-63b2a2723ff2',
  '28634097-ef0c-4531-8e56-085502065b63',
  '0c90879f-129c-4f84-9a03-5b789163c132',
  '035e2598-c3b6-42db-84c0-36f6796dc354',
  'd3ef9f2c-176b-4300-98a0-3d65b942ca45'
);

insert into private.lotterynet_invalid_ticket_cleanup_backup (ticket_id, ticket_row, items)
select
  t.id,
  to_jsonb(t),
  coalesce(
    jsonb_agg(to_jsonb(ti) order by ti.created_at),
    '[]'::jsonb
  ) as items
from public.tickets t
left join public.ticket_items ti on ti.ticket_id = t.id
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
group by t.id;

delete from public.tickets
where id in (
  '6bb543cb-c65e-4af7-90d8-bd2ff90392b7',
  '6c9c32df-03fe-413d-b5da-955d7a5e7ee4',
  '826f4583-512c-4b15-b402-5854d0f7568a',
  'deffb92a-5d61-468e-858c-63b2a2723ff2',
  '28634097-ef0c-4531-8e56-085502065b63',
  '0c90879f-129c-4f84-9a03-5b789163c132',
  '035e2598-c3b6-42db-84c0-36f6796dc354',
  'd3ef9f2c-176b-4300-98a0-3d65b942ca45'
);

