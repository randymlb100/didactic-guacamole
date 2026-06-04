begin;

do $$
declare
  v_sql text;
  v_old_lookup text := $old$
  where (id::text = nullif(p_body ->> 'ticketId', ''))
     or (legacy_ticket_id = nullif(p_body ->> 'localTicketId', ''))
     or (client_request_id = nullif(p_body ->> 'clientRequestId', ''))
$old$;
  v_new_lookup text := $new$
  where (id::text = nullif(p_body ->> 'ticketId', ''))
     or (legacy_ticket_id = nullif(p_body ->> 'localTicketId', ''))
     or (client_request_id = nullif(p_body ->> 'clientRequestId', ''))
     or (ticket_code = nullif(p_body ->> 'ticketId', ''))
     or (ticket_code = nullif(p_body ->> 'localTicketId', ''))
     or (ticket_code = nullif(p_body ->> 'clientRequestId', ''))
$new$;
  v_old_update text := $old$
  insert into public.movimientos_balance(
$old$;
  v_new_update text := $new$
  perform public.lotterynet_sync_ticket_owner_payload(v_ticket.id);

  insert into public.movimientos_balance(
$new$;
begin
  select pg_get_functiondef('public.ln_void_ticket_legacy(jsonb)'::regprocedure) into v_sql;

  if position('or (ticket_code = nullif(p_body ->> ''ticketId'', ''''))' in v_sql) = 0 then
    if position(v_old_lookup in v_sql) = 0 then
      raise exception 'Expected ln_void_ticket_legacy lookup block not found';
    end if;
    v_sql := replace(v_sql, v_old_lookup, v_new_lookup);
  end if;

  if position('lotterynet_sync_ticket_owner_payload(v_ticket.id)' in v_sql) = 0 then
    if position(v_old_update in v_sql) = 0 then
      raise exception 'Expected ln_void_ticket_legacy update tail not found';
    end if;
    v_sql := replace(v_sql, v_old_update, v_new_update);
  end if;

  execute v_sql;
end $$;

commit;
