# Backend de tickets, limites y antifraude

Este flujo mueve la validacion critica de venta al servidor:

- La app genera `clientRequestId` unico por intento de venta.
- La app manda jugadas a la Edge Function `create-ticket`.
- Supabase valida hora real del servidor, cierre del sorteo, resultado ya publicado, usuario, limite del numero e idempotencia.
- Si el ticket se confirma, Supabase descuenta el consumo de `limites_numeros_consumo` en tiempo real.
- Si falla cualquier parte, el backend libera la reserva y no deja el numero consumido.

## Llamada Kotlin

```kotlin
val response = SupabaseTicketBackendClient().createTicket(
    BackendTicketRequest(
        clientRequestId = UUID.randomUUID().toString(),
        adminKey = adminId,
        cashierKey = cashierId,
        sorteoId = sorteoUuid,
        drawDate = "2026-05-06",
        lotteryName = "Quiniela Leidsa",
        phoneTimeIso = Instant.now().toString(),
        plays = listOf(
            BackendTicketPlay("QUINIELA", "15", 10.0),
            BackendTicketPlay("PALE", "14/15", 5.0),
        ),
    ),
)
```

## Realtime para limites

La tabla publicada es `public.limites_numeros_consumo`.

Filtro recomendado:

```text
admin_key = admin actual
sorteo_id = sorteo visible
draw_date = fecha del sorteo
```

Cuando llegue un cambio, la pantalla debe recalcular:

```text
disponible = limite.max_amount - consumo.sold_amount
```

Si `disponible <= 0`, el numero se muestra agotado y el backend tambien lo rechaza aunque la pantalla no se haya actualizado.

## Reglas antifraude activas

- La hora del celular no decide ventas; solo se guarda para auditoria.
- `ln_ticket_server_clock()` usa `now()` de PostgreSQL y la zona horaria de la loteria/sorteo.
- Dominicana queda en `America/Santo_Domingo`.
- Loterias USA deben guardarse con su zona IANA real, por ejemplo `America/New_York`; PostgreSQL maneja cambios de verano/invierno con esa zona.
- Si el sorteo ya cerro por hora del servidor, se crea cierre en `cierres_sorteos`.
- Si ya existe resultado en `resultados`, no se permite vender ese sorteo.
- Si un numero tiene limite, cada venta descuenta `sold_amount` en una transaccion bloqueada por fila.
- Si un cajero toca dos veces, `clientRequestId` evita doble ticket.

## Tablas principales

- `limites_numeros`: configuracion del limite por admin, sorteo, tipo y numero.
- `limites_numeros_consumo`: consumo actual en tiempo real.
- `ticket_limit_reservations`: idempotencia y reserva/aplicacion/liberacion.
- `ticket_antifraud_checks`: auditoria de permitidos y bloqueados.
- `cierres_sorteos`: cierres por servidor.
- `resultados`: resultados publicados que bloquean venta posterior.
