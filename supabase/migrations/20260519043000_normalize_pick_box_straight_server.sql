create or replace function public.ln_play_type_from_text(p_value text)
returns public.ln_play_type
language plpgsql
immutable
as $function$
declare
  v text := upper(regexp_replace(translate(coalesce(p_value,''), 'ÁÉÍÓÚáéíóú', 'AEIOUaeiou'), '[ _-]', '', 'g'));
begin
  if v in ('Q','QUINIELA') then return 'QUINIELA'::public.ln_play_type; end if;
  if v in ('P','PALE') then return 'PALE'::public.ln_play_type; end if;
  if v in ('T','TRIPLETA') then return 'TRIPLETA'::public.ln_play_type; end if;
  if v in ('SP','SUPERPALE') then return 'SUPER_PALE'::public.ln_play_type; end if;
  if v in ('P3','P3S','PICK3','PICK3S','PICK3STRAIGHT') then return 'PICK3_STRAIGHT'::public.ln_play_type; end if;
  if v in ('P3BOX','P3B','PICK3BOX','PICK3B') then return 'PICK3_BOX'::public.ln_play_type; end if;
  if v in ('P4','P4S','PICK4','PICK4S','PICK4STRAIGHT') then return 'PICK4_STRAIGHT'::public.ln_play_type; end if;
  if v in ('P4BOX','P4B','PICK4BOX','PICK4B') then return 'PICK4_BOX'::public.ln_play_type; end if;
  return null;
end;
$function$;

create or replace function public.ln_sort_digits(p_digits text)
returns text
language sql
immutable
as $function$
  select coalesce(string_agg(ch, '' order by ch), '')
  from regexp_split_to_table(regexp_replace(coalesce(p_digits, ''), '\D', '', 'g'), '') as ch;
$function$;

create or replace function public.ln_sale_limit_bucket(p_play_type public.ln_play_type, p_number_value text)
returns text
language plpgsql
immutable
as $function$
declare
  v_digits text := regexp_replace(coalesce(p_number_value, ''), '\D', '', 'g');
begin
  case p_play_type
    when 'QUINIELA'::public.ln_play_type then
      return right('00' || v_digits, 2);
    when 'PALE'::public.ln_play_type,
         'SUPER_PALE'::public.ln_play_type,
         'TRIPLETA'::public.ln_play_type then
      return v_digits;
    when 'PICK3_STRAIGHT'::public.ln_play_type,
         'PICK4_STRAIGHT'::public.ln_play_type then
      return v_digits;
    when 'PICK3_BOX'::public.ln_play_type,
         'PICK4_BOX'::public.ln_play_type then
      return public.ln_sort_digits(v_digits);
    else
      return trim(coalesce(p_number_value, ''));
  end case;
end;
$function$;

create or replace function public.ln_normalize_ticket_play_input(
  p_play_type text,
  p_number text,
  p_pick_mode text default null,
  p_pick_game text default null
)
returns jsonb
language plpgsql
immutable
as $function$
declare
  v_play_type public.ln_play_type := public.ln_play_type_from_text(p_play_type);
  v_digits text := regexp_replace(coalesce(p_number, ''), '\D', '', 'g');
  v_raw_number text := upper(regexp_replace(coalesce(p_number, ''), '\s+', '', 'g'));
  v_suffix text := case when right(v_raw_number, 1) in ('B','S') then right(v_raw_number, 1) else '' end;
  v_mode text := upper(regexp_replace(coalesce(p_pick_mode, ''), '[ _-]', '', 'g'));
  v_game text := upper(regexp_replace(coalesce(p_pick_game, ''), '[ _-]', '', 'g'));
  v_is_pick boolean := false;
  v_expected int := 0;
begin
  v_is_pick := v_play_type in (
    'PICK3_STRAIGHT'::public.ln_play_type,
    'PICK3_BOX'::public.ln_play_type,
    'PICK4_STRAIGHT'::public.ln_play_type,
    'PICK4_BOX'::public.ln_play_type
  );

  if not v_is_pick then
    return jsonb_build_object(
      'playType', v_play_type,
      'number', replace(regexp_replace(trim(coalesce(p_number, '')), '\s+', '', 'g'), '-', '/')
    );
  end if;

  if v_game in ('PICK4','P4') or v_play_type in ('PICK4_STRAIGHT'::public.ln_play_type, 'PICK4_BOX'::public.ln_play_type) or length(v_digits) = 4 then
    v_expected := 4;
  else
    v_expected := 3;
  end if;

  if v_suffix = 'B' or v_mode in ('BOX','B') then
    v_play_type := case when v_expected = 4 then 'PICK4_BOX'::public.ln_play_type else 'PICK3_BOX'::public.ln_play_type end;
  elsif v_suffix = 'S' or v_mode in ('STRAIGHT','S','DIRECTO') then
    v_play_type := case when v_expected = 4 then 'PICK4_STRAIGHT'::public.ln_play_type else 'PICK3_STRAIGHT'::public.ln_play_type end;
  else
    v_play_type := case
      when v_expected = 4 and v_play_type = 'PICK4_BOX'::public.ln_play_type then 'PICK4_BOX'::public.ln_play_type
      when v_expected = 4 then 'PICK4_STRAIGHT'::public.ln_play_type
      when v_play_type = 'PICK3_BOX'::public.ln_play_type then 'PICK3_BOX'::public.ln_play_type
      else 'PICK3_STRAIGHT'::public.ln_play_type
    end;
  end if;

  if length(v_digits) <> v_expected then
    return jsonb_build_object('playType', null, 'number', v_digits);
  end if;

  return jsonb_build_object('playType', v_play_type, 'number', v_digits);
end;
$function$;

do $$
declare
  v_def text;
begin
  select pg_get_functiondef('public.ln_create_ticket_legacy(jsonb)'::regprocedure) into v_def;

  v_def := replace(
    v_def,
    '  v_play_type public.ln_play_type;
  v_number text;',
    '  v_play_type public.ln_play_type;
  v_normalized_play jsonb;
  v_number text;'
  );

  v_def := replace(
    v_def,
    '    v_play_type := public.ln_play_type_from_text(coalesce(v_play->>''playType'', v_play->>''type''));
    v_number := replace(regexp_replace(trim(coalesce(v_play->>''number'', v_play->>''numbers'','''')), ''\s+'', '''', ''g''), ''-'', ''/'');',
    '    v_normalized_play := public.ln_normalize_ticket_play_input(
      coalesce(v_play->>''playType'', v_play->>''type'', v_play->>''localPlayType''),
      coalesce(v_play->>''number'', v_play->>''numbers'', ''''),
      coalesce(v_play->>''pickMode'', ''''),
      coalesce(v_play->>''pickGame'', '''')
    );
    v_play_type := public.ln_play_type_from_text(v_normalized_play->>''playType'');
    v_number := coalesce(v_normalized_play->>''number'', '''');'
  );

  if v_def not like '%ln_normalize_ticket_play_input%' then
    raise exception 'No se pudo conectar ln_create_ticket_legacy con normalizacion Pick';
  end if;

  execute v_def;
end $$;
