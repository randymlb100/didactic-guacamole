create or replace function public.ln_enforce_ticket_void_until_two_minutes()
returns trigger
language plpgsql
set search_path to 'public'
as $function$
declare
  v_created_at timestamptz;
begin
  v_created_at := coalesce(new.server_created_at, new.created_at, now());
  new.void_until := v_created_at + interval '2 minutes';
  return new;
end;
$function$;

drop trigger if exists trg_ln_ticket_void_until_two_minutes on public.tickets;

create trigger trg_ln_ticket_void_until_two_minutes
before insert or update of server_created_at, void_until
on public.tickets
for each row
execute function public.ln_enforce_ticket_void_until_two_minutes();

update public.tickets
   set void_until = coalesce(server_created_at, created_at, now()) + interval '2 minutes',
       updated_at = now()
 where void_until is null
    or void_until > coalesce(server_created_at, created_at, now()) + interval '2 minutes';

create or replace function public.ln_void_ticket_legacy(p_body jsonb)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_actor_key text := nullif(coalesce(p_body ->> 'actorKey', p_body ->> 'adminKey', p_body ->> 'cashierKey'), '');
  v_actor jsonb;
  v_role text;
  v_actor_user_keys text[];
  v_actor_admin_keys text[];
  v_actor_supervisor_keys text[];
  v_ticket public.tickets%rowtype;
  v_action text := lower(coalesce(p_body ->> 'action', 'void'));
  v_next_status text := case when lower(coalesce(p_body ->> 'action', 'void')) = 'delete' then 'BORRADO' else 'ANULADO' end;
  v_now timestamptz := now();
  v_created_at timestamptz;
  v_cashier_deadline timestamptz;
begin
  if v_actor_key is null then
    return jsonb_build_object('ok', false, 'status', 400, 'message', 'Usuario requerido');
  end if;

  v_actor := public.ln_actor_from_legacy_state(v_actor_key);
  if v_actor is null then
    return jsonb_build_object('ok', false, 'status', 403, 'message', 'Usuario no autorizado');
  end if;
  if coalesce((v_actor ->> 'activo')::boolean, true) = false then
    return jsonb_build_object('ok', false, 'status', 403, 'message', 'Usuario bloqueado');
  end if;

  v_role := lower(coalesce(v_actor ->> 'role', v_actor ->> '_source'));
  v_actor_user_keys := array_remove(array[
    lower(trim(coalesce(v_actor ->> 'id', ''))),
    lower(trim(coalesce(v_actor ->> 'user', ''))),
    lower(trim(coalesce(v_actor ->> 'username', ''))),
    lower(trim(coalesce(v_actor ->> 'displayName', '')))
  ], '');
  v_actor_admin_keys := array_remove(v_actor_user_keys || array[
    lower(trim(coalesce(v_actor ->> 'adminId', ''))),
    lower(trim(coalesce(v_actor ->> 'adminUser', ''))),
    lower(trim(coalesce(v_actor ->> 'ownerAdminId', ''))),
    lower(trim(coalesce(v_actor ->> 'parentAdminId', ''))),
    lower(trim(coalesce(v_actor ->> 'banca', '')))
  ], '');
  v_actor_supervisor_keys := array_remove(v_actor_user_keys || array[
    lower(trim(coalesce(v_actor ->> 'supervisorId', ''))),
    lower(trim(coalesce(v_actor ->> 'supervisorUser', ''))),
    lower(trim(coalesce(v_actor ->> 'territory', '')))
  ], '');

  select * into v_ticket
  from public.tickets
  where (id::text = nullif(p_body ->> 'ticketId', ''))
     or (legacy_ticket_id = nullif(p_body ->> 'localTicketId', ''))
     or (client_request_id = nullif(p_body ->> 'clientRequestId', ''))
  order by server_created_at desc nulls last
  limit 1
  for update;

  if not found then
    return jsonb_build_object('ok', false, 'status', 404, 'message', 'Ticket no encontrado');
  end if;

  if v_ticket.deleted_at is not null or upper(v_ticket.status) in ('BORRADO','ANULADO','INVALIDADO') then
    return jsonb_build_object('ok', true, 'status', 200, 'message', 'Ticket ya procesado', 'ticketId', v_ticket.id, 'state', v_ticket.status);
  end if;

  v_created_at := coalesce(v_ticket.server_created_at, v_ticket.created_at);
  v_cashier_deadline := least(
    coalesce(v_ticket.void_until, v_created_at + interval '2 minutes'),
    v_created_at + interval '2 minutes'
  );

  if v_role in ('master','masters') then
    null;
  elsif v_role in ('cashier','cajero','cajeros','cashiers') then
    if not lower(coalesce(v_ticket.cashier_key, '')) = any(v_actor_user_keys) then
      return jsonb_build_object('ok', false, 'status', 403, 'message', 'No puede anular ticket de otro cajero');
    end if;
    if v_cashier_deadline is null or v_now > v_cashier_deadline then
      return jsonb_build_object('ok', false, 'status', 403, 'message', 'Tiempo de anulacion vencido');
    end if;
  elsif v_role in ('admin','admins') then
    if not lower(coalesce(v_ticket.admin_key, '')) = any(v_actor_admin_keys) then
      return jsonb_build_object('ok', false, 'status', 403, 'message', 'No puede tocar ticket de otro admin');
    end if;
  elsif v_role in ('supervisor','supervisores','supervisors') then
    if not lower(coalesce(v_ticket.supervisor_key, '')) = any(v_actor_supervisor_keys) then
      return jsonb_build_object('ok', false, 'status', 403, 'message', 'No puede tocar ticket fuera de su grupo');
    end if;
  else
    return jsonb_build_object('ok', false, 'status', 403, 'message', 'Rol no permitido');
  end if;

  update public.tickets
     set status = v_next_status,
         deleted_at = case when v_action = 'delete' then v_now else deleted_at end,
         deleted_by_key = case when v_action = 'delete' then v_actor_key else deleted_by_key end,
         voided_at = case when v_action <> 'delete' then v_now else voided_at end,
         voided_by = case when v_action <> 'delete' then v_actor_key else voided_by end,
         updated_at = v_now
   where id = v_ticket.id;

  insert into public.movimientos_balance(
    movement_type, amount, status, reference,
    legacy_from_key, legacy_to_key, admin_key, cashier_key, supervisor_key, ticket_id, day_key, metadata
  ) values (
    'TICKET_VOID', v_ticket.total_amount, 'COMPLETED',
    case when v_action = 'delete' then 'Ticket borrado' else 'Ticket anulado' end,
    v_ticket.cashier_key, v_ticket.admin_key, v_ticket.admin_key, v_ticket.cashier_key, v_ticket.supervisor_key,
    v_ticket.id, v_ticket.legacy_day_key,
    jsonb_build_object('actorKey', v_actor_key, 'previousStatus', v_ticket.status, 'action', v_action)
  );

  insert into public.auditoria(actor_id, action, entity_table, entity_id, metadata)
  values (null, upper('ticket_' || v_action), 'tickets', v_ticket.id::text, jsonb_build_object('actorKey', v_actor_key, 'ticketCode', v_ticket.ticket_code));

  return jsonb_build_object('ok', true, 'status', 200, 'message', case when v_action = 'delete' then 'Ticket borrado del servidor' else 'Ticket anulado' end, 'ticketId', v_ticket.id, 'state', v_next_status);
exception when others then
  return jsonb_build_object('ok', false, 'status', 500, 'message', 'No se pudo procesar el ticket', 'details', sqlerrm);
end;
$function$;
