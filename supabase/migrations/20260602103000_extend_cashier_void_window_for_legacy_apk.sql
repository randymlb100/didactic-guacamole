begin;

do $$
declare
  v_sql text;
begin
  select pg_get_functiondef('public.ln_void_ticket_legacy(jsonb)'::regprocedure) into v_sql;

  if position('v_created_at + interval ''15 minutes''' in v_sql) = 0 then
    if position('v_created_at + interval ''2 minutes''' in v_sql) = 0 then
      raise exception 'Expected cashier void window not found in ln_void_ticket_legacy';
    end if;

    v_sql := replace(
      v_sql,
      'v_created_at + interval ''2 minutes''',
      'v_created_at + interval ''15 minutes'''
    );
  end if;

  execute v_sql;
end $$;

commit;
