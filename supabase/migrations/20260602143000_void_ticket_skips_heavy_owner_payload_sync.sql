begin;

do $$
declare
  v_sql text;
begin
  select pg_get_functiondef('public.ln_void_ticket_legacy(jsonb)'::regprocedure) into v_sql;

  if position('perform public.lotterynet_sync_ticket_owner_payload(v_ticket.id);' in v_sql) > 0 then
    v_sql := replace(
      v_sql,
      E'\n  perform public.lotterynet_sync_ticket_owner_payload(v_ticket.id);\n',
      E'\n'
    );
  end if;

  execute v_sql;
end $$;

commit;
