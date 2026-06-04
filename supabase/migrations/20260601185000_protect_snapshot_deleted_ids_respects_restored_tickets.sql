begin;

create or replace function public.ln_protect_ticket_owner_snapshot()
returns trigger
language plpgsql
set search_path to 'public'
as $function$
declare
  merged_deleted jsonb;
  filtered_tickets jsonb;
begin
  merged_deleted := coalesce((
    select jsonb_agg(distinct id order by id)
    from (
      select jsonb_array_elements_text(coalesce(old.payload->'deletedIds','[]'::jsonb)) as id
      union
      select jsonb_array_elements_text(coalesce(new.payload->'deletedIds','[]'::jsonb)) as id
    ) d
    where nullif(trim(id),'') is not null
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
          and upper(coalesce(tk.status, tk.estado, '')) not in ('BORRADO','DELETED','ANULADO','VOIDED','INVALIDADO','INVALID')
      )
  ), '[]'::jsonb);

  filtered_tickets := coalesce((
    select jsonb_agg(ticket order by coalesce((ticket->>'createdAtMs')::bigint, (ticket->>'createdAtEpochMs')::bigint, 0) desc)
    from jsonb_array_elements(coalesce(new.payload->'tickets','[]'::jsonb)) as t(ticket)
    where coalesce(ticket->>'id', ticket->>'clientRequestId', ticket->>'client_request_id', '') <> ''
      and not (coalesce(ticket->>'id', ticket->>'clientRequestId', ticket->>'client_request_id', '') in (select jsonb_array_elements_text(merged_deleted)))
      and lower(coalesce(ticket->>'status', ticket->>'st', ticket->>'estado', '')) not in ('deleted','borrado','removed')
      and exists (
        select 1
        from public.tickets tk
        where (tk.client_request_id = coalesce(ticket->>'id', ticket->>'clientRequestId', ticket->>'client_request_id', '')
               or tk.legacy_ticket_id = coalesce(ticket->>'id', ticket->>'clientRequestId', ticket->>'client_request_id', '')
               or tk.ticket_code = coalesce(ticket->>'id', ticket->>'clientRequestId', ticket->>'client_request_id', '')
               or tk.id::text = coalesce(ticket->>'id', ticket->>'clientRequestId', ticket->>'client_request_id', ''))
          and tk.deleted_at is null
          and upper(coalesce(tk.status, tk.estado, '')) not in ('BORRADO','DELETED','ANULADO','VOIDED','INVALIDADO','INVALID')
      )
  ), '[]'::jsonb);

  new.payload := jsonb_set(
    jsonb_set(
      coalesce(new.payload, '{}'::jsonb),
      '{deletedIds}',
      merged_deleted,
      true
    ),
    '{tickets}',
    filtered_tickets,
    true
  );
  return new;
end;
$function$;

commit;
