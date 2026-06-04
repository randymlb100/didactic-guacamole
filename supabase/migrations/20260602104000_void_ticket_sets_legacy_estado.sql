begin;

do $$
declare
  v_sql text;
  v_old text := $old$
  update public.tickets
     set status = v_next_status,
         deleted_at = case when v_action = 'delete' then v_now else deleted_at end,
$old$;
  v_new text := $new$
  update public.tickets
     set status = v_next_status,
         estado = v_next_status,
         deleted_at = case when v_action = 'delete' then v_now else deleted_at end,
$new$;
begin
  select pg_get_functiondef('public.ln_void_ticket_legacy(jsonb)'::regprocedure) into v_sql;

  if position('estado = v_next_status' in v_sql) = 0 then
    if position(v_old in v_sql) = 0 then
      raise exception 'Expected ln_void_ticket_legacy status update block not found';
    end if;
    v_sql := replace(v_sql, v_old, v_new);
  end if;

  execute v_sql;
end $$;

update public.tickets
   set estado = status,
       updated_at = now()
 where upper(coalesce(status, '')) in ('BORRADO', 'ANULADO', 'INVALIDADO')
   and upper(coalesce(estado, '')) <> upper(coalesce(status, ''));

commit;
