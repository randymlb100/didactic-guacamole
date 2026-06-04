begin;

do $$
declare
  v_sql text;
begin
  select pg_get_functiondef('public.lotterynet_preserve_terminal_ticket_state()'::regprocedure)
  into v_sql;

  if position('lotterynet.skip_preserve_terminal_ticket_state' in v_sql) = 0 then
    v_sql := replace(
      v_sql,
      E'begin\n  if new.payload is null then',
      E'begin\n  if current_setting(''lotterynet.skip_preserve_terminal_ticket_state'', true) = ''on'' then\n    return new;\n  end if;\n\n  if new.payload is null then'
    );
    execute v_sql;
  end if;
end;
$$;

do $$
declare
  v_sql text;
begin
  select pg_get_functiondef('public.lotterynet_sync_ticket_owner_payload(uuid)'::regprocedure)
  into v_sql;

  if position('set_config(''lotterynet.skip_preserve_terminal_ticket_state''' in v_sql) = 0 then
    v_sql := replace(
      v_sql,
      E'begin\n  select * into ticket_row from public.tickets where id = p_ticket_id;',
      E'begin\n  perform set_config(''lotterynet.skip_preserve_terminal_ticket_state'', ''on'', true);\n\n  select * into ticket_row from public.tickets where id = p_ticket_id;'
    );
    execute v_sql;
  end if;
end;
$$;

comment on function public.lotterynet_preserve_terminal_ticket_state()
is 'Preserves app-submitted terminal states; server-authoritative owner ticket patches can bypass the expensive full-snapshot merge.';

commit;
