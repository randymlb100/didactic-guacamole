begin;

do $$
declare
  v_sql text;
begin
  select pg_get_functiondef('public.ln_protect_ticket_owner_snapshot()'::regprocedure)
  into v_sql;

  if position('lotterynet.skip_preserve_terminal_ticket_state' in v_sql) = 0 then
    v_sql := replace(
      v_sql,
      E'begin\n  merged_deleted := coalesce((',
      E'begin\n  if current_setting(''lotterynet.skip_preserve_terminal_ticket_state'', true) = ''on'' then\n    return new;\n  end if;\n\n  merged_deleted := coalesce(('
    );
    execute v_sql;
  end if;
end;
$$;

comment on function public.ln_protect_ticket_owner_snapshot()
is 'Protects app-submitted owner snapshots; server-authoritative ticket patches bypass this full JSON cleanup to avoid result reconcile timeouts.';

commit;
