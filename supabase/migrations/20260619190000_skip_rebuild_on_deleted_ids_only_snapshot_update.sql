begin;

do $$
declare
  v_sql text;
  v_old text := $old$
  if tg_op='UPDATE' and new.payload is not distinct from old.payload then return new; end if;
$old$;
  v_new text := $new$
  if tg_op='UPDATE' then
    if jsonb_typeof(new.payload) = 'object'
       and jsonb_typeof(old.payload) = 'object'
       and (new.payload - 'deletedIds') is not distinct from (old.payload - 'deletedIds') then
      return new;
    end if;
    if new.payload is not distinct from old.payload then return new; end if;
  end if;
$new$;
begin
  select pg_get_functiondef('public.lotterynet_preserve_terminal_ticket_state()'::regprocedure) into v_sql;
  if position(v_new in v_sql) = 0 then
    if position(v_old in v_sql) = 0 then
      raise exception 'Expected preserve_terminal_ticket_state early-return block not found';
    end if;
    v_sql := replace(v_sql, v_old, v_new);
  end if;
  execute v_sql;
end $$;

comment on function public.lotterynet_preserve_terminal_ticket_state()
is 'Preserves terminal ticket states while skipping deletedIds-only snapshot updates.';

commit;
