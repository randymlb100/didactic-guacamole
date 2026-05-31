begin;

do $$
declare
  v_next text;
begin
  v_next := pg_get_functiondef('public.lotterynet_preserve_terminal_ticket_state()'::regprocedure);

  v_next := replace(
    v_next,
    '    if incoming_status = any(paid_statuses) or incoming_status = ''winner'' or incoming_prize > 0 then
      calculated_prize := public.lotterynet_classic_ticket_prize(incoming_ticket);',
    '    if incoming_status = any(paid_statuses) or incoming_status = ''winner'' or incoming_prize > 0 then
      if coalesce((incoming_ticket->>''serverPrizeAuthoritative'')::boolean, false) then
        calculated_prize := incoming_prize;
      else
        calculated_prize := public.lotterynet_classic_ticket_prize(incoming_ticket);
      end if;'
  );

  if v_next not like '%serverPrizeAuthoritative%' then
    raise exception 'Expected preserve_terminal_ticket_state prize branch was not patched';
  end if;

  execute v_next;
end $$;

do $$
declare
  v_next text;
begin
  v_next := pg_get_functiondef('public.lotterynet_sync_ticket_owner_payload(uuid)'::regprocedure);

  v_next := replace(
    v_next,
    '''updatedAt'', updated_epoch_ms',
    '''updatedAt'', updated_epoch_ms,
    ''serverPrizeAuthoritative'', true'
  );

  if v_next not like '%serverPrizeAuthoritative%' then
    raise exception 'Expected sync_ticket_owner_payload patch branch was not patched';
  end if;

  execute v_next;
end $$;

commit;
