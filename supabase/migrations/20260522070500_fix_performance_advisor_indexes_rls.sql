begin;

create index if not exists admin_networks_master_id_idx on public.admin_networks(master_id);
create index if not exists app_config_updated_by_idx on public.app_config(updated_by);
create index if not exists bancas_admin_id_idx on public.bancas(admin_id);
create index if not exists cajeros_supervisor_id_idx on public.cajeros(supervisor_id);
create index if not exists horarios_cierre_sorteo_id_idx on public.horarios_cierre(sorteo_id);
create index if not exists limites_numeros_banca_id_idx on public.limites_numeros(banca_id);
create index if not exists limites_numeros_lottery_id_idx on public.limites_numeros(lottery_id);
create index if not exists limites_numeros_sorteo_id_idx on public.limites_numeros(sorteo_id);
create index if not exists limites_numeros_consumo_sorteo_id_idx on public.limites_numeros_consumo(sorteo_id);
create index if not exists messages_sender_id_idx on public.messages(sender_id);
create index if not exists movimientos_balance_from_user_id_idx on public.movimientos_balance(from_user_id);
create index if not exists notificaciones_recipient_id_idx on public.notificaciones(recipient_id);
create index if not exists pagos_paid_by_idx on public.pagos(paid_by);
create index if not exists profiles_admin_owner_id_idx on public.profiles(admin_owner_id);
create index if not exists profiles_banca_id_idx on public.profiles(banca_id);
create index if not exists profiles_created_by_idx on public.profiles(created_by);
create index if not exists profiles_parent_user_id_idx on public.profiles(parent_user_id);
create index if not exists recargas_from_user_id_idx on public.recargas(from_user_id);
create index if not exists recargas_to_user_id_idx on public.recargas(to_user_id);
create index if not exists recargas_usuario_id_idx on public.recargas(usuario_id);
create index if not exists resultados_lottery_id_idx on public.resultados(lottery_id);
create index if not exists supervisores_admin_id_idx on public.supervisores(admin_id);
create index if not exists ticket_items_lottery_id_idx on public.ticket_items(lottery_id);
create index if not exists ticket_limit_reservations_sorteo_id_idx on public.ticket_limit_reservations(sorteo_id);
create index if not exists tickets_banca_uuid_idx on public.tickets(banca_uuid);
create index if not exists tickets_supervisor_id_idx on public.tickets(supervisor_id);
create index if not exists tickets_usuario_id_idx on public.tickets(usuario_id);
create index if not exists user_permissions_granted_by_idx on public.user_permissions(granted_by);
create index if not exists user_permissions_permission_code_idx on public.user_permissions(permission_code);

drop policy if exists "read limites consumo realtime" on public.limites_numeros_consumo;

drop policy if exists "resultados_no_client_write" on public.resultados;
drop policy if exists "read resultados" on public.resultados;
drop policy if exists resultados_no_client_insert on public.resultados;
drop policy if exists resultados_no_client_update on public.resultados;
drop policy if exists resultados_no_client_delete on public.resultados;
create policy resultados_no_client_insert on public.resultados for insert to public with check (false);
create policy resultados_no_client_update on public.resultados for update to public using (false) with check (false);
create policy resultados_no_client_delete on public.resultados for delete to public using (false);

drop policy if exists "tickets_select_scope" on public.tickets;

drop policy if exists audit_select_scope on public.auditoria;
create policy audit_select_scope on public.auditoria
for select to public
using ((select public.ln_is_master()) or actor_id = (select auth.uid()));

drop policy if exists balances_select_scope on public.balances;
create policy balances_select_scope on public.balances
for select to public
using (
  (select public.ln_is_master())
  or owner_id = (select auth.uid())
  or exists (
    select 1
    from public.profiles p
    where p.id = balances.owner_id
      and public.ln_same_admin_network(coalesce(p.admin_owner_id, p.id))
  )
);

drop policy if exists kv_insert_allowed on public.lotterynet_kv;
drop policy if exists kv_results_cache_insert_public on public.lotterynet_kv;
drop policy if exists kv_update_allowed on public.lotterynet_kv;
drop policy if exists kv_results_cache_update_public on public.lotterynet_kv;
drop policy if exists kv_select_allowed on public.lotterynet_kv;
drop policy if exists kv_insert_combined on public.lotterynet_kv;
drop policy if exists kv_update_combined on public.lotterynet_kv;

create policy kv_insert_combined on public.lotterynet_kv
for insert to public
with check (
  (
    (select auth.role()) = 'anon'
    and (
      key = any(array['sys_users_v4','sys_audit_v4','sys_alerts_v4','sys_presence_v1'])
      or key like 'admin:%'
      or key like 'admin_sync:%'
      or key like 'ticket_deleted:%'
    )
  )
  or key ~ '^(lot_results_cache_by_day|pick_results_cache_by_day):[0-9]{2}-[0-9]{2}-[0-9]{4}$'
);

create policy kv_update_combined on public.lotterynet_kv
for update to public
using (
  (
    (select auth.role()) = 'anon'
    and (
      key = any(array['sys_users_v4','sys_audit_v4','sys_alerts_v4','sys_presence_v1'])
      or key like 'admin:%'
      or key like 'admin_sync:%'
      or key like 'ticket_deleted:%'
    )
  )
  or key ~ '^(lot_results_cache_by_day|pick_results_cache_by_day):[0-9]{2}-[0-9]{2}-[0-9]{4}$'
)
with check (
  (
    (select auth.role()) = 'anon'
    and (
      key = any(array['sys_users_v4','sys_audit_v4','sys_alerts_v4','sys_presence_v1'])
      or key like 'admin:%'
      or key like 'admin_sync:%'
      or key like 'ticket_deleted:%'
    )
  )
  or key ~ '^(lot_results_cache_by_day|pick_results_cache_by_day):[0-9]{2}-[0-9]{2}-[0-9]{4}$'
);

commit;
