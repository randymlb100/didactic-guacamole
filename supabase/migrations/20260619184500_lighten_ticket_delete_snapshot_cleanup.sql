begin;

create or replace function public.ln_mark_owner_snapshots_ticket_deleted(
  p_identifiers text[],
  p_owner_keys text[] default array[]::text[]
)
returns void
language plpgsql
security definer
set search_path to 'public'
as $function$
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
         coalesce(s.payload, '{}'::jsonb),
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
$function$;

comment on function public.ln_mark_owner_snapshots_ticket_deleted(text[], text[])
is 'Marks deleted ticket identifiers in owner snapshots without rebuilding the full tickets array.';

commit;
