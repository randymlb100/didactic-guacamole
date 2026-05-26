create or replace function public.lotterynet_ticket_payout_config(ticket jsonb)
returns jsonb
language plpgsql
stable
set search_path to 'public'
as $function$
declare
  admin_keys text[] := array[]::text[];
  seller_keys text[] := array[]::text[];
  users_payload jsonb;
  actor jsonb;
  raw_key text;
  identity_key text;
  config jsonb;
  raw_config text;
  defaults jsonb := '{}'::jsonb;
  by_user jsonb := '{}'::jsonb;
  user_config jsonb := '{}'::jsonb;
begin
  admin_keys := array_remove(array[
    nullif(trim(coalesce(ticket->>'adminId', '')), ''),
    nullif(trim(coalesce(ticket->>'adminUser', '')), ''),
    nullif(trim(coalesce(ticket->>'admin_key', '')), ''),
    nullif(trim(coalesce(ticket->>'adminKey', '')), ''),
    nullif(trim(coalesce(ticket->>'ownerKey', '')), '')
  ], null);

  seller_keys := array_remove(array[
    nullif(trim(coalesce(ticket->>'sellerUser', '')), ''),
    nullif(trim(coalesce(ticket->>'cashierUser', '')), ''),
    nullif(trim(coalesce(ticket->>'cashierKey', '')), ''),
    nullif(trim(coalesce(ticket->>'cashier_key', '')), ''),
    nullif(trim(coalesce(ticket->>'sellerId', '')), ''),
    nullif(trim(coalesce(ticket->>'vendedorId', '')), ''),
    nullif(trim(coalesce(ticket->>'cajeroId', '')), ''),
    nullif(trim(coalesce(ticket->>'cajeroNombre', '')), ''),
    nullif(trim(coalesce(ticket->>'vendedorNombre', '')), '')
  ], null);

  select payload into users_payload
  from public.lotterynet_users_state
  where scope = 'global'
  limit 1;

  if users_payload is not null then
    select item into actor
    from (
      select jsonb_array_elements(coalesce(users_payload->'users','[]'::jsonb)) item
      union all select jsonb_array_elements(coalesce(users_payload->'admins','[]'::jsonb))
      union all select jsonb_array_elements(coalesce(users_payload->'supervisores','[]'::jsonb))
      union all select jsonb_array_elements(coalesce(users_payload->'supervisors','[]'::jsonb))
      union all select jsonb_array_elements(coalesce(users_payload->'cajeros','[]'::jsonb))
      union all select jsonb_array_elements(coalesce(users_payload->'cashiers','[]'::jsonb))
    ) all_users
    where exists (
      select 1
      from unnest(admin_keys) x
      where lower(trim(x)) in (
        lower(trim(coalesce(item->>'id',''))),
        lower(trim(coalesce(item->>'user',''))),
        lower(trim(coalesce(item->>'username','')))
      )
    )
    limit 1;

    if actor is not null then
      admin_keys := admin_keys || array_remove(array[
        nullif(trim(coalesce(actor->>'id', '')), ''),
        nullif(trim(coalesce(actor->>'user', '')), ''),
        nullif(trim(coalesce(actor->>'username', '')), '')
      ], null);
    end if;

    actor := null;
    select item into actor
    from (
      select jsonb_array_elements(coalesce(users_payload->'users','[]'::jsonb)) item
      union all select jsonb_array_elements(coalesce(users_payload->'admins','[]'::jsonb))
      union all select jsonb_array_elements(coalesce(users_payload->'supervisores','[]'::jsonb))
      union all select jsonb_array_elements(coalesce(users_payload->'supervisors','[]'::jsonb))
      union all select jsonb_array_elements(coalesce(users_payload->'cajeros','[]'::jsonb))
      union all select jsonb_array_elements(coalesce(users_payload->'cashiers','[]'::jsonb))
    ) all_users
    where exists (
      select 1
      from unnest(seller_keys) x
      where lower(trim(x)) in (
        lower(trim(coalesce(item->>'id',''))),
        lower(trim(coalesce(item->>'user',''))),
        lower(trim(coalesce(item->>'username','')))
      )
    )
    limit 1;

    if actor is not null then
      seller_keys := seller_keys || array_remove(array[
        nullif(trim(coalesce(actor->>'id', '')), ''),
        nullif(trim(coalesce(actor->>'user', '')), ''),
        nullif(trim(coalesce(actor->>'username', '')), '')
      ], null);
    end if;
  end if;

  foreach raw_key in array coalesce((
    select array_agg(distinct trim(x))
    from unnest(admin_keys) x
    where nullif(trim(x), '') is not null
  ), array[]::text[]) loop
    select payload into config
    from public.lotterynet_master_state
    where config_key = 'cashier_prize_payouts:' || raw_key
    limit 1;

    if config is null or config = '{}'::jsonb then
      select value into raw_config
      from public.lotterynet_kv
      where key = 'cashier_prize_payouts:' || raw_key
      limit 1;

      if raw_config is not null and trim(raw_config) <> '' then
        begin
          config := raw_config::jsonb;
        exception when others then
          config := null;
        end;
      end if;
    end if;

    if config is not null and config <> '{}'::jsonb then
      exit;
    end if;
  end loop;

  defaults := coalesce(config->'defaults', '{}'::jsonb);
  by_user := coalesce(config->'byUser', '{}'::jsonb);

  foreach identity_key in array coalesce((
    select array_agg(distinct trim(x))
    from unnest(seller_keys) x
    where nullif(trim(x), '') is not null
  ), array[]::text[]) loop
    if by_user ? identity_key then
      user_config := coalesce(by_user->identity_key, '{}'::jsonb);
      exit;
    end if;

    select value into user_config
    from jsonb_each(by_user)
    where lower(trim(key)) = lower(trim(identity_key))
    limit 1;

    if user_config is not null and user_config <> '{}'::jsonb then
      exit;
    end if;

    user_config := '{}'::jsonb;
  end loop;

  return defaults || user_config;
exception when others then
  return '{}'::jsonb;
end;
$function$;

grant execute on function public.lotterynet_ticket_payout_config(jsonb) to anon, authenticated, service_role;
