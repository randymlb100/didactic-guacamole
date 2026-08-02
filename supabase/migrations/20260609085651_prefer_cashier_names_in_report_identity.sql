create or replace function public.ln_legacy_report_actor_identity(p_actor_key text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public, pg_temp
as $function$
declare
  v_actor jsonb;
  v_raw text := trim(coalesce(p_actor_key, ''));
  v_raw_norm text := lower(trim(coalesce(p_actor_key, '')));
  v_id text;
  v_user text;
  v_username text;
  v_display text;
  v_nombre text;
  v_banca text;
  v_canonical_key text;
  v_canonical_norm text;
begin
  if v_raw_norm = '' or v_raw_norm = 'sin-cajero' then
    return jsonb_build_object(
      'canonicalKey', 'sin-cajero',
      'canonicalNorm', 'sin-cajero',
      'displayName', 'Sin cajero',
      'rawKey', v_raw
    );
  end if;

  v_actor := public.ln_actor_from_legacy_state(v_raw);
  v_id := nullif(trim(coalesce(v_actor ->> 'id', '')), '');
  v_user := nullif(trim(coalesce(v_actor ->> 'user', '')), '');
  v_username := nullif(trim(coalesce(v_actor ->> 'username', '')), '');
  v_display := nullif(trim(coalesce(v_actor ->> 'displayName', '')), '');
  v_nombre := nullif(trim(coalesce(v_actor ->> 'nombre', '')), '');
  v_banca := nullif(trim(coalesce(v_actor ->> 'banca', '')), '');

  v_canonical_key := coalesce(v_id, v_user, v_username, v_display, v_nombre, v_raw);
  v_canonical_norm := lower(trim(v_canonical_key));
  v_display := coalesce(v_display, v_nombre, v_banca, v_user, v_username, v_id, v_raw);

  return jsonb_build_object(
    'canonicalKey', v_canonical_key,
    'canonicalNorm', v_canonical_norm,
    'displayName', v_display,
    'rawKey', v_raw,
    'actorId', v_id,
    'actorUser', coalesce(v_user, v_username),
    'source', coalesce(v_actor ->> '_source', ''),
    'aliases', to_jsonb(public.ln_legacy_actor_aliases(v_raw))
  );
end;
$function$;

revoke all on function public.ln_legacy_report_actor_identity(text) from public, anon, authenticated;
grant execute on function public.ln_legacy_report_actor_identity(text) to service_role;
