begin;

create index if not exists ticket_prize_items_result_draw_id_idx
on public.ticket_prize_items (result_draw_id)
where result_draw_id is not null;

comment on index public.ticket_prize_items_result_draw_id_idx
is 'Covers ticket_prize_items.result_draw_id foreign key for faster result/prize maintenance.';

commit;
