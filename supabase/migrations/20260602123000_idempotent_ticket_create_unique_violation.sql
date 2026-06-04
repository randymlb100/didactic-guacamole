begin;

do $$
declare
  v_sql text;
  v_old text := $old$
exception
  when others then
    if v_sorteo_id is not null then
      perform public.ln_release_number_limit(v_client_request_id);
    end if;
$old$;
  v_new text := $new$
exception
  when unique_violation then
    select * into v_existing
    from public.tickets
    where client_request_id = v_client_request_id
    limit 1;

    if found then
      return jsonb_build_object(
        'ok', true,
        'status', 200,
        'duplicate', true,
        'message', 'Venta ya procesada',
        'ticket', jsonb_build_object(
          'id', v_existing.id,
          'ticket_code', v_existing.ticket_code,
          'total_amount', v_existing.total_amount,
          'status', v_existing.status,
          'server_created_at', v_existing.server_created_at,
          'void_until', v_existing.void_until,
          'qr_payload', v_existing.qr_payload
        )
      );
    end if;

    if v_sorteo_id is not null then
      perform public.ln_release_number_limit(v_client_request_id);
    end if;
    return jsonb_build_object('ok', false, 'status', 500, 'message', SQLERRM);
  when others then
    if v_sorteo_id is not null then
      perform public.ln_release_number_limit(v_client_request_id);
    end if;
$new$;
begin
  select pg_get_functiondef('public.ln_create_ticket_legacy(jsonb)'::regprocedure) into v_sql;

  if position('when unique_violation then' in v_sql) = 0 then
    if position(v_old in v_sql) = 0 then
      raise exception 'Expected ln_create_ticket_legacy exception block not found';
    end if;
    v_sql := replace(v_sql, v_old, v_new);
  end if;

  execute v_sql;
end $$;

commit;
