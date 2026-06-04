begin;

create or replace function public.ln_mark_owner_snapshots_ticket_deleted(
  p_identifiers text[],
  p_owner_keys text[] default array[]::text[]
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_owner_keys text[] := coalesce(p_owner_keys, array[]::text[]);
begin
  if p_identifiers is null or cardinality(p_identifiers) = 0 then
    return;
  end if;

  if cardinality(v_owner_keys) = 0 then
    return;
  end if;

  update public.lotterynet_tickets_by_owner s
  set payload = jsonb_set(
      jsonb_set(
        coalesce(s.payload, '{}'::jsonb),
        '{tickets}',
        coalesce((
          select jsonb_agg(ticket order by coalesce((ticket->>'createdAtMs')::bigint, (ticket->>'createdAtEpochMs')::bigint, 0) desc)
          from jsonb_array_elements(coalesce(s.payload->'tickets','[]'::jsonb)) as t(ticket)
          where coalesce(ticket->>'id', ticket->>'clientRequestId', ticket->>'client_request_id', '') <> all(p_identifiers)
        ), '[]'::jsonb),
        true
      ),
      '{deletedIds}',
      coalesce((
        select jsonb_agg(distinct id order by id)
        from (
          select jsonb_array_elements_text(coalesce(s.payload->'deletedIds','[]'::jsonb)) as id
          union
          select unnest(p_identifiers) as id
        ) d
        where nullif(trim(id), '') is not null
      ), '[]'::jsonb),
      true
    ),
    updated_at = now()
  where s.owner_key = any(v_owner_keys);
end;
$$;

commit;
