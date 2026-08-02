# Android + Supabase Production Hardening Runbook

## Objetivo

Mantener la app multi-banca rápida y confiable sin volver al problema de refrescos en loop, tickets ganadores que tardan en reflejarse o lecturas grandes repetidas.

## Reglas De Producción

- Venta, pago, borrado, premios y límites finales son siempre server-first.
- Cache y Redis solo se usan para lectura visual o configuración no monetaria.
- Realtime Broadcast es una señal: avisa que cambió algo, pero Android confirma con `updated-at` o `delta`.
- Postgres Changes queda como fallback temporal hasta ver producción estable.
- Nunca se mandan tokens, contraseñas, payload completo de ticket ni nombres completos a logs/Sentry.

## Realtime Broadcast

Topics activos:

- `ln:tickets:owner:{ownerKey}` para tickets, ganadores y finanzas del dueño operativo.
- `ln:results:{yyyy-MM-dd}` para resultados de un día.

Flujo correcto:

1. Supabase escribe el cambio real.
2. Trigger SQL llama `realtime.send`.
3. Android recibe Broadcast privado.
4. Android hace catch-up liviano con token fresco.
5. Solo si cambió `updated_at`, pide delta/snapshot.

## Redis / Upstash

Variables esperadas:

- `UPSTASH_REDIS_REST_URL`
- `UPSTASH_REDIS_REST_TOKEN`
- `UPSTASH_REDIS_DEFAULT_TTL_SECONDS` opcional

Permitido cachear:

- Configuración master leída como `lotterynet_kv`.
- Catálogos.
- Stamps de resultados.
- Board deportivo.

Prohibido cachear como verdad:

- Ventas.
- Pagos.
- Borrado.
- Premios.
- Límites finales de jugada.

Si Redis no existe o falla, la función sigue leyendo Supabase.

## Sentry

Registrar solo contexto seguro:

- función;
- acción;
- duración;
- código de error;
- topic o owner hash si se agrega después.

No registrar:

- token JWT;
- service role;
- teléfono;
- contraseña;
- ticket completo;
- jugadas completas.

## Checklist Antes De Build Release

- `deno task check:edge` pasa o se documenta por qué no corrió.
- Prueba Node de contrato Broadcast/Redis/Sentry pasa.
- Tickets abre 20 veces sin forzar 20 snapshots completos.
- Resultados hoy/ayer/anteayer no se re-renderizan en loop.
- App en segundo plano vuelve con un catch-up único.
- Sin internet temporal no borra sesión.

## Monitoreo

Vigilar en Supabase:

- errores `statement timeout`;
- llamadas repetidas a `get-ticket-list`;
- jobs de premios pendientes demasiado tiempo;
- logs de `results-server-refresh`;
- errores 401 repetidos en Edge Functions.

Vigilar en Android/Sentry:

- `TicketSummary`;
- `ResultsActivity`;
- `pay-ticket`;
- `delete-ticket`;
- `realtime`;
- `timeout`.
