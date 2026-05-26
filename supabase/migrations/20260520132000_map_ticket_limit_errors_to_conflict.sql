do $$
declare
  v_sql text;
  v_old text := $old$exception
  when others then
    if v_sorteo_id is not null then
      perform public.ln_release_number_limit(v_client_request_id);
    end if;
    return jsonb_build_object('ok', false, 'status', 500, 'message', SQLERRM);
end $function$
$old$;
  v_new text := $new$exception
  when others then
    if v_sorteo_id is not null then
      perform public.ln_release_number_limit(v_client_request_id);
    end if;
    if SQLERRM ilike '%limite agotado%' or SQLERRM ilike '%tope diario%' or SQLERRM ilike '%limite no disponible%' then
      return jsonb_build_object('ok', false, 'status', 409, 'message', SQLERRM);
    end if;
    return jsonb_build_object('ok', false, 'status', 500, 'message', SQLERRM);
end $function$
$new$;
begin
  select pg_get_functiondef('public.ln_create_ticket_legacy(jsonb)'::regprocedure) into v_sql;
  if position('SQLERRM ilike ''%limite agotado%''' in v_sql) > 0 then
    return;
  end if;
  if position(v_old in v_sql) = 0 then
    raise exception 'Expected exception block not found in ln_create_ticket_legacy';
  end if;
  execute replace(v_sql, v_old, v_new);
end $$;
