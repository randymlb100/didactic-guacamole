begin;

do $$
declare
  v_sql text;
  v_old text := 'v_neto := v_total_vendido + v_total_recargas - v_total_anulado - v_total_invalidado - v_total_premios - v_comision - v_supervisor_comision;';
  v_new text := 'v_neto := v_total_vendido + v_total_recargas - v_total_premios - v_comision - v_supervisor_comision;';
begin
  select pg_get_functiondef('public.ln_legacy_report(jsonb)'::regprocedure)
    into v_sql;

  if position(v_old in v_sql) = 0 then
    raise exception 'ln_legacy_report net formula pattern not found';
  end if;

  v_sql := replace(v_sql, v_old, v_new);

  execute v_sql;
end $$;

revoke all on function public.ln_legacy_report(jsonb) from public, anon, authenticated;
grant execute on function public.ln_legacy_report(jsonb) to service_role;

commit;
