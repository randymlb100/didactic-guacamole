# FCM y SyncCenter en LotteryNet

## Rol de cada pieza

- Supabase sigue siendo la fuente de verdad para dinero, tickets, premios, pagos, limites y resultados.
- Broadcast avisa cambios cuando la app esta abierta.
- FCM despierta la app cuando esta en segundo plano o cerrada.
- WorkManager hace catch-up periodico por seguridad.
- SyncCenter decide que modulo actualizar: config, tickets, winners, results, finance o sports.

## Firebase requerido

Para activar FCM en Android:

1. Crear el proyecto Firebase.
2. Registrar la app Android `com.lotterynet.pro`.
3. Descargar `google-services.json`.
4. Colocarlo en `app/google-services.json`.

Si el archivo no existe, el plugin de Google Services no se aplica y la app debe seguir compilando sin FCM activo.

## Secretos backend para enviar push

La funcion `send-operational-push` solo envia si existen estos secretos:

- `FIREBASE_PROJECT_ID`
- `FIREBASE_CLIENT_EMAIL`
- `FIREBASE_PRIVATE_KEY`
- `LOTTERYNET_ADMIN_SHARED_SECRET`

Si faltan los secretos, responde `configured: false` y no rompe produccion.

## Payload permitido

FCM solo debe enviar senales:

```json
{
  "type": "result",
  "dayKey": "2026-06-05",
  "ownerKeyHash": "hash",
  "reason": "server_changed"
}
```

Nunca enviar por FCM: jugadas, dinero, nombre de cliente, ticket completo, serial completo, token o password.

## Flujo esperado

1. El servidor detecta cambio real.
2. Supabase Broadcast avisa si la app esta viva.
3. Si hace falta, backend manda FCM.
4. Android recibe la senal y encola `LotteryNetCatchUpScheduler`.
5. SyncCenter hace lectura autorizada con JWT fresco.
6. Tickets, Ganadores, Resultados y Finanzas se refrescan desde Supabase.
