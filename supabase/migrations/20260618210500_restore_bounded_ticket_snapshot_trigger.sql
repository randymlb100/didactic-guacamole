begin;

create or replace function public.ln_protect_ticket_owner_snapshot()
returns trigger
language plpgsql
set search_path to 'public'
as $function$
declare
  v_old_payload jsonb := '{}'::jsonb;
  v_new_payload jsonb := '{}'::jsonb;
  merged_deleted jsonb := '[]'::jsonb;
  filtered_tickets jsonb := '[]'::jsonb;
  protected_meta jsonb := '{}'::jsonb;
begin
  if current_setting('lotterynet.skip_preserve_terminal_ticket_state', true) = 'on' then
    return new;
  end if;

  if tg_op = 'UPDATE' and new.payload is not distinct from old.payload then
    return new;
  end if;

  if tg_op = 'UPDATE' then
    v_old_payload := coalesce(old.payload, '{}'::jsonb);
  end if;

  v_new_payload := coalesce(new.payload, '{}'::jsonb);
  if jsonb_typeof(v_new_payload) = 'array' then
    v_new_payload := jsonb_build_object(
      'schemaVersion', 2,
      'tickets', v_new_payload,
      'deletedIds', '[]'::jsonb
    );
  end if;

  merged_deleted := coalesce((
    select jsonb_agg(distinct id order by id)
    from (
      select jsonb_array_elements_text(
        case
          when jsonb_typeof(v_old_payload->'deletedIds') = 'array'
            then v_old_payload->'deletedIds'
          else '[]'::jsonb
        end
      ) as id
      union
      select jsonb_array_elements_text(
        case
          when jsonb_typeof(v_new_payload->'deletedIds') = 'array'
            then v_new_payload->'deletedIds'
          else '[]'::jsonb
        end
      ) as id
      union
      select jsonb_array_elements_text(
        case
          when jsonb_typeof(v_new_payload->'deletedTicketIds') = 'array'
            then v_new_payload->'deletedTicketIds'
          else '[]'::jsonb
        end
      ) as id
      union
      select jsonb_array_elements_text(
        case
          when jsonb_typeof(v_new_payload->'removedIds') = 'array'
            then v_new_payload->'removedIds'
          else '[]'::jsonb
        end
      ) as id
    ) d
    where nullif(trim(id), '') is not null
      and not exists (
        select 1
        from public.tickets tk
        where (
            tk.client_request_id = d.id
            or tk.legacy_ticket_id = d.id
            or tk.ticket_code = d.id
            or tk.id::text = d.id
          )
          and tk.deleted_at is null
          and tk.voided_at is null
          and tk.invalidated_at is null
          and upper(coalesce(tk.status, tk.estado, '')) not in (
            'BORRADO',
            'DELETED',
            'ANULADO',
            'VOIDED',
            'INVALIDADO',
            'INVALID'
          )
      )
  ), '[]'::jsonb);

  filtered_tickets := coalesce((
    select jsonb_agg(ticket order by coalesce(
      case
        when coalesce(ticket->>'createdAtMs', '') ~ '^\d+$'
          then (ticket->>'createdAtMs')::bigint
      end,
      case
        when coalesce(ticket->>'createdAtEpochMs', '') ~ '^\d+$'
          then (ticket->>'createdAtEpochMs')::bigint
      end,
      0
    ) desc)
    from jsonb_array_elements(
      case
        when jsonb_typeof(v_new_payload->'tickets') = 'array'
          then v_new_payload->'tickets'
        else '[]'::jsonb
      end
    ) as t(ticket)
    where coalesce(
      ticket->>'id',
      ticket->>'clientRequestId',
      ticket->>'client_request_id',
      ''
    ) <> ''
      and not (
        coalesce(
          ticket->>'id',
          ticket->>'clientRequestId',
          ticket->>'client_request_id',
          ''
        ) in (select jsonb_array_elements_text(merged_deleted))
      )
      and lower(coalesce(
        ticket->>'status',
        ticket->>'st',
        ticket->>'estado',
        ''
      )) not in ('deleted', 'borrado', 'removed')
  ), '[]'::jsonb);

  protected_meta := coalesce(v_new_payload->'meta', '{}'::jsonb)
    || jsonb_build_object(
      'snapshotProtectedAt', now(),
      'deletedIdsCount', jsonb_array_length(merged_deleted),
      'ticketsCount', jsonb_array_length(filtered_tickets)
    );

  new.payload := jsonb_set(
    jsonb_set(
      jsonb_set(
        coalesce(v_new_payload, '{}'::jsonb),
        '{schemaVersion}',
        to_jsonb(greatest(
          coalesce(
            case
              when coalesce(v_new_payload->>'schemaVersion', '') ~ '^\d+$'
                then (v_new_payload->>'schemaVersion')::int
            end,
            2
          ),
          3
        )),
        true
      ),
      '{deletedIds}',
      merged_deleted,
      true
    ),
    '{tickets}',
    filtered_tickets,
    true
  );

  new.payload := jsonb_set(new.payload, '{meta}', protected_meta, true);
  return new;
end;
$function$;

comment on function public.ln_protect_ticket_owner_snapshot()
is 'Protects owner ticket snapshots with bounded candidate checks; no global active-ticket scan.';

commit;
