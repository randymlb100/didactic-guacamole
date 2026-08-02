# Canary controlado de concurrencia en producción

Fecha: 19 de junio de 2026  
Proyecto: `unhoulkujbtsypccpirc`  
Alcance: solo lectura; ninguna venta, anulación, pago o cambio de configuración.

## Resultado

La prueba completa de 150 clientes **no fue aprobada ni ejecutada en producción**. La escalada se detuvo correctamente en 60 lecturas autenticadas concurrentes porque el p95 excedió el límite de seguridad.

| Fase | Flujo | Resultado | p50 | p95 | Máximo |
| --- | --- | ---: | ---: | ---: | ---: |
| 10 | `get-ticket-list / updated-at` anónimo | 10/10 HTTP 200 | 467 ms | 510 ms | 525 ms |
| 30 | `get-ticket-delta`, `podero02` | 30/30 HTTP 200 | 994 ms | 1,316 ms | 1,405 ms |
| 60 | `get-ticket-delta`, `podero02` | 60/60 HTTP 200 | 1,469 ms | 1,643 ms | 1,995 ms |
| Enfriamiento | `get-ticket-list / updated-at` | 10/10 HTTP 200 | 567 ms | 594 ms | 619 ms |

No hubo:

- respuestas HTTP 5xx;
- timeouts de red;
- deadlocks;
- sesiones `idle in transaction`;
- esperas por locks;
- tickets sintéticos creados.

La comprobación final encontró `synthetic_test_rows = 0`.

## Conexiones

- Antes de la carga: 22/60 sesiones, 2 activas.
- Inmediatamente después: 47/60 sesiones, 1 activa.
- Después de 60 segundos: 40/60 sesiones, 1 activa.
- Las conexiones de Auth bajaron de 8 a 1 durante el enfriamiento.
- PostgREST conservó 21 conexiones ociosas en el pool.

El servidor permaneció disponible, pero la reserva de conexiones es insuficiente para combinar esta concurrencia con trabajos pesados adicionales.

## Cron

Estado verificado:

- Job 3 `lotterynet-results-server-refresh`: apagado.
- Job 6 `lotterynet-results-prize-watchdog`: apagado.
- Job 7 `lotterynet-safe-maintenance`: activo, diario, últimas ejecuciones exitosas de aproximadamente 0.1 segundos.

Decisión:

- mantener apagados los jobs 3 y 6;
- mantener activo el job 7;
- no activar reconstrucción periódica de snapshots JSONB;
- no ejecutar 100/150 lecturas ni escrituras masivas en producción;
- ejecutar la prueba completa de 150 clientes en una rama Supabase o staging comparable.

## Documentación aplicada

- Supabase recomienda pruebas de carga preferiblemente en staging.
- `pg_stat_activity` es la fuente para observar conexiones directas y detectar clientes ociosos.
- En proyectos que usan intensamente PostgREST debe conservarse capacidad para Auth y otros servicios al configurar el pool.

## Veredicto

Producción está operativa y no sufrió corrupción ni bloqueo durante el canary. La ruta autenticada funciona correctamente, incluida la separación por propietario, pero **todavía no demuestra capacidad para 150 clientes simultáneos**. La fase de 60 clientes incumplió el p95 máximo de 1.5 segundos y activó el freno del plan.
