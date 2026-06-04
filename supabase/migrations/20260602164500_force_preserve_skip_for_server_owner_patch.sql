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
      E'begin\n  if tg_op = ''UPDATE'' and new.payload is not distinct from old.payload then',
      E'begin\n  if current_setting(''lotterynet.skip_preserve_terminal_ticket_state'', true) = ''on'' then\n    return new;\n  end if;\n\n  if tg_op = ''UPDATE'' and new.payload is not distinct from old.payload then'
    );
    execute v_sql;
  end if;
end;
$$;

comment on function public.lotterynet_preserve_terminal_ticket_state()
is 'Preserves app-submitted terminal states; server-authoritative owner ticket patches bypass the expensive full-snapshot merge.';

commit;
