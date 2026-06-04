begin;

do $$
declare
  v_sql text;
  v_marker text := 'if tg_op = ''UPDATE'' and new.payload is not distinct from old.payload then';
begin
  select pg_get_functiondef('public.lotterynet_preserve_terminal_ticket_state()'::regprocedure) into v_sql;

  if position(v_marker in v_sql) = 0 then
    v_sql := replace(
      v_sql,
      E'begin\n  if new.payload is null then',
      E'begin\n  if tg_op = ''UPDATE'' and new.payload is not distinct from old.payload then\n    return new;\n  end if;\n\n  if new.payload is null then'
    );
  end if;

  execute v_sql;
end $$;

commit;
