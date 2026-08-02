begin;

do $$
declare
  v_sql text;
  v_summary_old text := '         count(*)::integer
    into v_total_vendido, v_total_anulado, v_total_invalidado, v_total_pagado, v_total_premios, v_comision, v_ticket_count';
  v_summary_new text := '         count(*) filter (where upper(status) in (''VALIDO'',''VALID'',''GANADOR'',''PERDEDOR'',''PAGADO''))::integer
    into v_total_vendido, v_total_anulado, v_total_invalidado, v_total_pagado, v_total_premios, v_comision, v_ticket_count';
  v_cashier_old text := '      count(*)::integer as tickets,';
  v_cashier_new text := '      count(*) filter (where upper(t.status) in (''VALIDO'',''VALID'',''GANADOR'',''PERDEDOR'',''PAGADO''))::integer as tickets,';
begin
  select pg_get_functiondef('public.ln_legacy_report(jsonb)'::regprocedure)
    into v_sql;

  if position(v_summary_old in v_sql) = 0 then
    raise exception 'ln_legacy_report summary ticket count pattern not found';
  end if;

  if position(v_cashier_old in v_sql) = 0 then
    raise exception 'ln_legacy_report cashier ticket count pattern not found';
  end if;

  v_sql := replace(v_sql, v_summary_old, v_summary_new);
  v_sql := replace(v_sql, v_cashier_old, v_cashier_new);

  execute v_sql;
end $$;

revoke all on function public.ln_legacy_report(jsonb) from public, anon, authenticated;
grant execute on function public.ln_legacy_report(jsonb) to service_role;

commit;
