# LotteryNet Produccion Server-First Multi-Banca

## Objetivo

 Supabase es la fuente oficial de verdad para ventas, premios, pagos, movimientos y reportes. La app Android funciona como POS conectado al servidor con cache local de lectura/impresion, pero no decide la contabilidad final de finanzas.

Politica de produccion: sin internet no se vende, no se paga, no se anula y no se guardan cambios administrativos.

Para investigar o cambiar flujos criticos, seguir tambien `docs/supabase/multibanca-engineering-playbook.md`. Ese playbook define las reglas de identidad canonica, cache/realtime, jobs por cola, pruebas reales QA y gates antes de release.

## Fuente de verdad

- Oficiales: `tickets`, `ticket_items`, `pagos`, `movimientos_balance`, `lotterynet_results_by_day`, `lotterynet_kv`.
- Compatibilidad/cache: `lotterynet_tickets_by_owner`.
- El pago oficial siempre pasa por `lotterynet_pay_ticket_server_first`.
- Los snapshots nunca deben bajar un ticket terminal `PAGADO` a activo ni reducir un premio ya confirmado.

## Flujo de venta

1. Android crea ticket por `create-ticket-v2`.
2. Supabase guarda `tickets` y `ticket_items` con `client_request_id` idempotente.
3. Si Supabase rechaza la venta por usuario bloqueado, limite, sorteo cerrado, duplicado o falta de JWT, Android no guarda ticket local oficial.
4. Android guarda copia local solo despues de respuesta `ok` del servidor para impresion/cache.
5. `lotterynet_tickets_by_owner` se actualiza como vista de compatibilidad para pantallas antiguas.

## Flujo de resultados y premios

1. `results-server-refresh` actualiza `lotterynet_results_by_day`.
2. El motor SQL `lotterynet_resolve_ticket_prize` calcula premios con la tabla del admin/cajero.
3. Tipos soportados:
   - Quiniela
   - Pale
   - Tripleta
   - Super Pale
   - Pick 3 straight
   - Pick 3 box
   - Pick 4 straight
   - Pick 4 box
   - Pick 3 back pair
   - Pick 4 back pair
4. `lotterynet_reconcile_ticket_prize(ticket_id)` actualiza `tickets.payout_amount` y los campos ganadores de `ticket_items`.

## Flujo de pago

1. Android valida preview local solo para UX.
2. Android llama `pay-ticket`.
3. `pay-ticket` verifica alcance basico del actor y llama `lotterynet_pay_ticket_server_first`.
4. La RPC bloquea el ticket, recalcula premio, impide doble pago y rechaza tickets anulados/borrados/invalidos.
5. Supabase marca `tickets.status/estado = PAGADO`, crea `pagos`, crea `movimientos_balance` tipo `PAYOUT`.
6. Android actualiza cache local solo despues de respuesta correcta del servidor.

## Flujo de anulacion/eliminacion

1. Android valida permisos locales solo como preview UX.
2. Android llama `void-ticket` con JWT real de Supabase Auth.
3. Supabase valida actor, rol, banca/cajero, estado de ticket, sorteo y ventana permitida.
4. Si el servidor confirma, Android cambia cache local a anulado o elimina la copia local.
5. Si el servidor falla o no hay internet, Android deja el ticket sin cambios.

## Bloqueo offline y sesion

- Las operaciones server-first requieren `authAccessToken` real; no se permite usar `sb_publishable`, anon key o secret key como `Authorization: Bearer`.
- `auth-legacy-login` sigue siendo la entrada publica controlada para obtener sesion, porque todavia crea el puente entre usuarios legacy y Supabase Auth.
- Un usuario sin JWT queda limitado a pantallas/cache que no ejecutan dinero.
- Usuario bloqueado o inactivo debe ser rechazado en login y tambien dentro de cada Edge Function critica.

## Roles

- Master: puede auditar todas las bancas desde reportes server-first.
- Admin: ve y opera su banca.
- Supervisor: ve cajeros asignados por alcance.
- Cajero: ve y cobra solo tickets de su caja.

## Verificacion operativa

Calcular premio oficial:

```sql
select public.lotterynet_calculate_ticket_prize('<ticket_uuid>');
```

Reconciliar ticket sin pagarlo:

```sql
select public.lotterynet_reconcile_ticket_prize('<ticket_uuid>');
```

Pagar ticket desde servidor:

```sql
select public.lotterynet_pay_ticket_server_first(
  p_ticket_id := '<ticket_uuid>',
  p_actor_key := '<usuario>',
  p_admin_key := '<admin>',
  p_cashier_key := '<cajero>',
  p_reference := 'manual-support'
);
```

Verificar finanzas de un pago:

```sql
select t.ticket_code, t.status, t.payout_amount, p.amount pago, m.amount movimiento
from public.tickets t
left join public.pagos p on p.ticket_id = t.id
left join public.movimientos_balance m on m.ticket_id = t.id and m.movement_type = 'PAYOUT'
where t.id = '<ticket_uuid>';
```

## Checklist de produccion

- Confirmar que el cambio cumple `docs/supabase/multibanca-engineering-playbook.md`.
- Confirmar que `pay-ticket` esta desplegada con `verify_jwt = true`.
- Confirmar que `create-ticket-v2`, `void-ticket`, `pay-ticket` y reportes estan con `verify_jwt = true`.
- Mantener `get-ticket-list` y snapshots legacy como compatibilidad hasta que todos los refrescos de Android pasen token; no usarlo como contabilidad final.
- Confirmar que endpoints legacy que sigan `verify_jwt = false` validan actor internamente y no escriben dinero final.
- Confirmar indices: `tickets_paid_at_idx`, `tickets_cashier_draw_status_idx`, `tickets_supervisor_draw_status_idx`, `pagos_created_status_idx`, `movimientos_balance_ticket_idx`.
- Confirmar que `pagos_ticket_id_key` existe para impedir doble pago.
- Confirmar que el cron de resultados esta activo.
- Confirmar que Realtime solo se usa para canales necesarios.
- Mantener backups antes de migraciones que cambien pagos o finanzas.

## Recuperacion

- Si un ticket ganador no aparece, ejecutar `lotterynet_calculate_ticket_prize` y revisar `didValidate`.
- Si el premio es correcto pero finanzas no, ejecutar `lotterynet_reconcile_ticket_prize` y revisar `pagos`/`movimientos_balance`.
- Si un snapshot viejo pisa datos, revisar `lotterynet_tickets_by_owner`; el trigger debe preservar `PAGADO` y premio mayor.
- Si Android no confirma pago, revisar que la Edge Function `pay-ticket` este desplegada y que el usuario tenga `authAccessToken`.
- Si Android no anula/elimina, revisar que `void-ticket` este desplegada con JWT y que el ticket no este pagado/cerrado.
- Si una venta no se guarda local, primero revisar la respuesta de `create-ticket-v2`; el local no debe inventar ticket oficial cuando el servidor rechaza.

## Estado de despliegue

- La migracion SQL server-first esta en `supabase/migrations/20260518230328_server_first_prize_payments.sql`.
- Las Edge Functions criticas locales estan en:
  - `supabase/functions/create-ticket-v2/index.ts`
  - `supabase/functions/void-ticket/index.ts`
  - `supabase/functions/pay-ticket/index.ts`
  - `supabase/functions/get-admin-report/index.ts`
  - `supabase/functions/get-cashier-report/index.ts`
  - `supabase/functions/get-supervisor-report/index.ts`
- Produccion requiere JWT en `create-ticket-v2`, `void-ticket`, `pay-ticket` y reportes.
- Los RPC publicos legacy quedan sin `EXECUTE` para `anon` y `authenticated`; solo Edge Functions con `service_role` deben usarlos.
- Si el CLI no tiene `SUPABASE_ACCESS_TOKEN`, desplegar con:

```powershell
$env:SUPABASE_ACCESS_TOKEN = '<token>'
node_modules\supabase\bin\supabase.exe functions deploy create-ticket-v2 --project-ref unhoulkujbtsypccpirc
node_modules\supabase\bin\supabase.exe functions deploy void-ticket --project-ref unhoulkujbtsypccpirc
node_modules\supabase\bin\supabase.exe functions deploy pay-ticket --project-ref unhoulkujbtsypccpirc
node_modules\supabase\bin\supabase.exe functions deploy get-admin-report --project-ref unhoulkujbtsypccpirc
node_modules\supabase\bin\supabase.exe functions deploy get-cashier-report --project-ref unhoulkujbtsypccpirc
node_modules\supabase\bin\supabase.exe functions deploy get-supervisor-report --project-ref unhoulkujbtsypccpirc
```
