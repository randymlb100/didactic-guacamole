begin;

create or replace function public.lotterynet_reconcile_owner_tickets_for_day(
  p_owner_key text,
  p_day_key text,
  p_limit int default 500
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $function$
declare
  ticket_row record;
  checked_count int := 0;
  winner_count int := 0;
  total_prize numeric := 0;
  calc jsonb;
  day_aliases text[];
  day_iso text;
  day_legacy text;
  day_date date;
begin
  day_aliases := public.lotterynet_ticket_date_aliases(p_day_key);
  day_iso := (
    select alias
    from unnest(day_aliases) as alias
    where alias ~ '^\d{4}-\d{2}-\d{2}$'
    limit 1
  );
  day_legacy := (
    select alias
    from unnest(day_aliases) as alias
    where alias ~ '^\d{2}-\d{2}-\d{4}$'
    limit 1
  );
  day_date := nullif(day_iso, '')::date;

  for ticket_row in
    with candidate_draws as (
      select
        rd.result_day_key,
        rd.lottery_legacy_id,
        lower(coalesce(rd.lottery_name, '')) as lottery_name,
        rd.number_raw,
        rd.number_digits
      from public.result_draws rd
      where rd.result_day_key = any(day_aliases)
    )
    select distinct t.id
    from public.tickets t
    where (
        (day_date is not null and t.draw_date_real = day_date)
        or (day_legacy is not null and t.legacy_day_key = day_legacy)
        or (day_iso is not null and t.legacy_day_key = day_iso)
        or (day_legacy is not null and t.draw_date = day_legacy)
        or (day_iso is not null and t.draw_date = day_iso)
      )
      and (
        t.admin_key = p_owner_key
        or t.cashier_key = p_owner_key
        or t.admin_id::text = p_owner_key
        or t.profile_id::text = p_owner_key
      )
      and t.deleted_at is null
      and t.voided_at is null
      and t.invalidated_at is null
      and exists (
        select 1
        from public.ticket_items ti
        join candidate_draws rd
          on (
            rd.lottery_legacy_id = ti.lottery_legacy_id
            or rd.lottery_name = lower(coalesce(ti.lottery_name, ''))
          )
        left join candidate_draws secondary_rd
          on (
            secondary_rd.lottery_legacy_id = ti.secondary_lottery_legacy_id
            or secondary_rd.lottery_name = lower(coalesce(ti.secondary_lottery_name, ''))
          )
        where ti.ticket_id = t.id
          and public.lotterynet_ticket_item_can_win_against_draw(
            to_jsonb(ti),
            to_jsonb(rd),
            case
              when upper(coalesce(ti.play_type::text, '')) in ('SP', 'SUPER_PALE', 'SUPERPALE') then to_jsonb(secondary_rd)
              else null
            end
          )
      )
    order by t.id
    limit greatest(coalesce(p_limit, 500), 1)
  loop
    checked_count := checked_count + 1;
    calc := public.lotterynet_reconcile_ticket_prize_v2(ticket_row.id);
    if coalesce((calc->>'totalPrize')::numeric, 0) > 0 then
      winner_count := winner_count + 1;
      total_prize := total_prize + coalesce((calc->>'totalPrize')::numeric, 0);
    end if;
  end loop;

  return jsonb_build_object(
    'ok', true,
    'ownerKey', p_owner_key,
    'dayKey', p_day_key,
    'checked', checked_count,
    'winners', winner_count,
    'totalPrize', total_prize
  );
end;
$function$;

revoke all on function public.lotterynet_reconcile_owner_tickets_for_day(text, text, int) from public, anon, authenticated;
grant execute on function public.lotterynet_reconcile_owner_tickets_for_day(text, text, int) to service_role;

comment on function public.lotterynet_reconcile_owner_tickets_for_day(text, text, int)
is 'Force-reconciles only candidate winner tickets for one owner and day, using ticket-item draw matching instead of scanning every ticket for the day.';

commit;
