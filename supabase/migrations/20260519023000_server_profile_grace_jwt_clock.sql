set check_function_bodies = off;

create or replace function public.ln_actor_is_admin_profile(
  p_users_payload jsonb,
  p_actor_key text,
  p_admin_key text,
  p_actor_role text
)
returns boolean
language plpgsql
stable
set search_path to 'public'
as $function$
declare
  v_actor_key text := lower(trim(coalesce(p_actor_key, '')));
  v_admin_key text := lower(trim(coalesce(p_admin_key, '')));
  v_actor_role text := lower(trim(coalesce(p_actor_role, '')));
  v_actor jsonb;
  v_tokens text[];
begin
  if v_actor_key = '' or v_actor_role not in ('admin', 'administrador') then
    return false;
  end if;

  if v_admin_key <> '' and v_actor_key = v_admin_key then
    return true;
  end if;

  if p_users_payload is null then
    return false;
  end if;

  select item into v_actor
  from (
    select jsonb_array_elements(coalesce(p_users_payload->'admins','[]'::jsonb)) item
    union all select jsonb_array_elements(coalesce(p_users_payload->'users','[]'::jsonb))
  ) all_users
  where lower(trim(coalesce(item->>'id',''))) = v_actor_key
     or lower(trim(coalesce(item->>'user',''))) = v_actor_key
     or lower(trim(coalesce(item->>'username',''))) = v_actor_key
     or lower(trim(coalesce(item->>'displayName',''))) = v_actor_key
  limit 1;

  if v_actor is null or lower(coalesce(v_actor->>'role','')) not like '%admin%' then
    return false;
  end if;

  v_tokens := array_remove(array[
    lower(trim(coalesce(v_actor->>'id',''))),
    lower(trim(coalesce(v_actor->>'user',''))),
    lower(trim(coalesce(v_actor->>'username',''))),
    lower(trim(coalesce(v_actor->>'adminId',''))),
    lower(trim(coalesce(v_actor->>'adminUser',''))),
    lower(trim(coalesce(v_actor->>'ownerAdminId',''))),
    lower(trim(coalesce(v_actor->>'parentAdminId',''))),
    lower(trim(coalesce(v_actor->>'banca','')))
  ], '');

  return v_admin_key <> '' and v_admin_key = any(v_tokens);
end $function$;

create or replace function public.ln_ticket_server_clock(
  p_sorteo_id uuid,
  p_draw_date date,
  p_actor_role text,
  p_allow_admin_grace boolean default false
)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_sorteo record;
  v_close record;
  v_tz text;
  v_server_now timestamptz := now();
  v_local_now timestamp;
  v_draw_ts timestamp;
  v_draw_at timestamptz;
  v_close_ts timestamp;
  v_close_at timestamptz;
  v_grace_until timestamptz;
  v_result_exists boolean := false;
  v_admin_grace boolean := false;
begin
  select s.id, s.active as sorteo_active, s.draw_time_local, s.timezone as sorteo_timezone,
         l.active as lottery_active, l.timezone as lottery_timezone, l.code as lottery_code
    into v_sorteo
    from public.sorteos s
    join public.lotteries l on l.id = s.lottery_id
   where s.id = p_sorteo_id;

  if not found then
    return jsonb_build_object('allowed', false, 'reason', 'Sorteo no existe', 'serverNow', v_server_now);
  end if;

  if coalesce(v_sorteo.lottery_active, false) = false or coalesce(v_sorteo.sorteo_active, false) = false then
    return jsonb_build_object('allowed', false, 'reason', 'Loteria o sorteo inactivo', 'serverNow', v_server_now);
  end if;

  v_tz := coalesce(v_sorteo.sorteo_timezone, v_sorteo.lottery_timezone, 'America/Santo_Domingo');
  v_local_now := timezone(v_tz, v_server_now);

  if exists (
    select 1
      from public.cierres_sorteos
     where sorteo_id = p_sorteo_id
       and draw_date = p_draw_date
       and coalesce(reason, '') <> 'auto_close_by_server_time'
  ) then
    return jsonb_build_object(
      'allowed', false,
      'reason', 'Sorteo cerrado por servidor',
      'code', 'LOTTERY_CLOSED_NEXT_OR_DELETE',
      'serverNow', v_server_now,
      'timezone', v_tz
    );
  end if;

  select exists (
    select 1 from public.resultados where sorteo_id = p_sorteo_id and draw_date = p_draw_date
  ) into v_result_exists;

  if v_result_exists then
    return jsonb_build_object(
      'allowed', false,
      'reason', 'Resultado ya publicado en servidor',
      'code', 'RESULT_ALREADY_PUBLISHED',
      'serverNow', v_server_now,
      'timezone', v_tz
    );
  end if;

  select hc.close_minutes_before, hc.close_time_override
    into v_close
    from public.horarios_cierre hc
   where hc.sorteo_id = p_sorteo_id
     and hc.active = true
     and (hc.weekday is null or hc.weekday = extract(dow from p_draw_date)::int)
   order by hc.weekday nulls last
   limit 1;

  v_draw_ts := p_draw_date::timestamp + coalesce(v_close.close_time_override, v_sorteo.draw_time_local);
  v_draw_at := v_draw_ts at time zone v_tz;
  v_close_ts := v_draw_ts - make_interval(mins => coalesce(v_close.close_minutes_before, 5));
  v_close_at := v_close_ts at time zone v_tz;
  v_grace_until := v_draw_at + interval '10 minutes';
  v_admin_grace := coalesce(p_allow_admin_grace, false)
    and lower(trim(coalesce(p_actor_role, ''))) in ('admin', 'administrador')
    and v_server_now >= v_close_at
    and v_server_now <= v_grace_until;

  if v_server_now >= v_close_at and not v_admin_grace then
    insert into public.cierres_sorteos (sorteo_id, draw_date, reason, closed_by)
    values (p_sorteo_id, p_draw_date, 'auto_close_by_server_time', 'ln_ticket_server_clock')
    on conflict (sorteo_id, draw_date) do nothing;

    return jsonb_build_object(
      'allowed', false,
      'reason', 'Sorteo cerrado. Pasa la jugada al siguiente sorteo o borrala.',
      'code', 'LOTTERY_CLOSED_NEXT_OR_DELETE',
      'serverNow', v_server_now,
      'localNow', v_local_now,
      'closeAt', v_close_at,
      'drawAt', v_draw_at,
      'graceUntil', v_grace_until,
      'timezone', v_tz
    );
  end if;

  return jsonb_build_object(
    'allowed', true,
    'reason', case when v_admin_grace then 'Gracia admin 10 minutos' else 'Venta permitida' end,
    'adminGrace', v_admin_grace,
    'serverNow', v_server_now,
    'localNow', v_local_now,
    'closeAt', v_close_at,
    'drawAt', v_draw_at,
    'graceUntil', v_grace_until,
    'timezone', v_tz
  );
end $function$;

create or replace function public.ln_ticket_server_clock(p_sorteo_id uuid, p_draw_date date)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
begin
  return public.ln_ticket_server_clock(p_sorteo_id, p_draw_date, null, false);
end $function$;

do $patch$
declare
  v_def text;
  v_next text;
begin
  select pg_get_functiondef('public.ln_create_ticket_legacy(jsonb)'::regprocedure) into v_def;

  v_next := replace(
    v_def,
    '  v_cashier_key text := trim(coalesce(p_body->>''cashierKey'', p_body->>''cashierUser'', p_body->>''actorKey'', ''''));',
    '  v_cashier_key text := trim(coalesce(p_body->>''cashierKey'', p_body->>''cashierUser'', p_body->>''actorKey'', ''''));
  v_request_actor_key text := trim(coalesce(p_body->>''actorKey'', v_cashier_key));
  v_request_actor_role text := lower(trim(coalesce(p_body->>''actorRole'', '''')));
  v_actor_admin_profile boolean := false;'
  );
  if v_next = v_def then
    raise exception 'No se pudo inyectar actor profile en ln_create_ticket_legacy';
  end if;
  v_def := v_next;

  v_next := replace(
    v_def,
    '  if v_sorteo_id is not null then
    v_clock := public.ln_ticket_server_clock(v_sorteo_id, v_iso_date::date);',
    '  if v_sorteo_id is not null then
    v_actor_admin_profile := public.ln_actor_is_admin_profile(
      v_users_payload,
      v_request_actor_key,
      v_admin_key,
      v_request_actor_role
    );
    v_clock := public.ln_ticket_server_clock(
      v_sorteo_id,
      v_iso_date::date,
      v_request_actor_role,
      v_actor_admin_profile
    );'
  );
  if v_next = v_def then
    raise exception 'No se pudo conectar ln_create_ticket_legacy con la regla de gracia admin';
  end if;

  execute v_next;
end $patch$;
