begin;

select plan(3);

select ok(
  position('ticket_code = nullif(p_body ->> ''ticketId'', '''')' in pg_get_functiondef('public.ln_void_ticket_legacy(jsonb)'::regprocedure)) > 0,
  'ln_void_ticket_legacy resolves deletes by ticket_code'
);

select ok(
  position('estado = v_next_status' in pg_get_functiondef('public.ln_void_ticket_legacy(jsonb)'::regprocedure)) > 0,
  'ln_void_ticket_legacy keeps estado aligned with status'
);

select ok(
  position('jsonb_array_elements(coalesce(s.payload->''tickets'', ''[]''::jsonb))' in pg_get_functiondef('public.ln_mark_owner_snapshots_ticket_deleted(text[], text[])'::regprocedure)) = 0,
  'owner snapshot delete cleanup no longer rebuilds the full tickets array'
);

select * from finish();

rollback;
