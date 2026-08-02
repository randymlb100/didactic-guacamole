# Diseño de sincronización escalable de tickets para 150 clientes

**Fecha:** 18 de junio de 2026  
**Estado:** Aprobado para planificación  
**Proyecto Supabase:** `unhoulkujbtsypccpirc`

## Objetivo

Soportar 150 clientes simultáneos con la configuración actual del servidor, a un ritmo estimado de una venta cada 30 segundos por cliente —5 ventas por segundo— sin reescribir snapshots JSONB completos, duplicar trabajo, bloquear conexiones ni alterar el contrato público actual de la aplicación.

La solución debe superar además una prueba de resistencia de 450 clientes equivalentes —15 ventas por segundo— fuera de producción.

## Problema confirmado

El flujo anterior trataba `lotterynet_tickets_by_owner.payload` como almacenamiento y caché al mismo tiempo. Cada actualización podía:

- leer y reconstruir cientos de tickets históricos;
- reescribir documentos JSONB de hasta aproximadamente 260 KB;
- actualizar varios aliases del mismo propietario;
- ejecutar dos triggers `BEFORE`;
- escanear todos los tickets activos para validar IDs eliminados;
- esperar un advisory lock y después un row lock;
- generar WAL y TOAST desproporcionados;
- competir con reconciliación de premios y cron jobs.

En producción esto produjo checkpoints de 244–269 segundos, esperas de locks, solicitudes de 90–150 segundos y agotamiento temporal de conexiones.

## Principios de arquitectura

1. Una venta modifica solamente el ticket y los items correspondientes.
2. `tickets` y `ticket_items` son la fuente oficial.
3. El JSONB de propietario deja de participar en la escritura crítica de ventas.
4. Realtime comunica señales pequeñas; no transporta snapshots completos.
5. Los clientes descargan deltas acotados mediante cursores estables.
6. Los procesos de resultados y premios no se ejecutan dentro de lecturas de tickets.
7. Los trabajos en segundo plano usan lotes pequeños y locks no bloqueantes.
8. Toda operación tiene límites explícitos de tiempo, filas y concurrencia.
9. El contrato actual se conserva durante una migración gradual y reversible.

## Arquitectura propuesta

### Escritura de ventas

`create-ticket-v2` será la entrada oficial para los clientes migrados. La función legacy `create-ticket` se mantendrá temporalmente como adaptador de compatibilidad, pero ambas deberán terminar en la misma primitiva idempotente de escritura para evitar dos implementaciones del flujo de venta.

Una solicitud:

1. valida identidad, owner y límites;
2. inserta o confirma idempotentemente una fila en `tickets`;
3. inserta los `ticket_items` en un lote;
4. registra un evento pequeño de cambio;
5. responde sin reconstruir snapshots históricos.

La clave de idempotencia existente —`client_request_id` o su equivalente estable— evita duplicar una venta cuando el dispositivo reintenta.

No se actualizará `lotterynet_tickets_by_owner.payload` dentro de esta transacción.

### Lectura incremental

`get-ticket-list` mantendrá su contrato de respuesta, pero obtendrá la información oficial directamente de `tickets` y `ticket_items`.

Las lecturas se paginarán usando un cursor compuesto:

```text
(updated_at, id)
```

El cursor evita páginas inconsistentes cuando varios tickets comparten timestamp y permite consultar únicamente filas modificadas.

Límites:

- página normal: máximo 150 tickets;
- página histórica: máximo 500 tickets bajo solicitud explícita;
- items consultados únicamente para los IDs de la página;
- ninguna lectura sin rango, límite o cursor;
- timeout de lectura objetivo: 3 segundos.

### Sincronización offline

El dispositivo mantiene su cola local actual. Al recuperar conexión:

- envía tickets pendientes en lotes máximos de 20;
- conserva una sola operación en vuelo por owner;
- aplica backoff con jitter ante errores temporales;
- no vuelve a subir el historial local;
- confirma cada ticket individualmente;
- conserva pendientes únicamente los tickets no confirmados.

La reconexión de 150 clientes no debe convertirse en 150 snapshots completos simultáneos.

### Compatibilidad con snapshots

`lotterynet_tickets_by_owner` se conserva temporalmente para clientes o pantallas todavía dependientes del contrato anterior.

Durante la transición:

- será una caché derivada, no la fuente oficial;
- no se regenerará en cada venta;
- se actualizará de manera asíncrona y acotada solo cuando sea necesario;
- tendrá límites de cantidad y antigüedad;
- nunca contendrá aliases inválidos como `null`, `undefined`, `none` o `nil`;
- podrá desactivarse mediante feature flag sin afectar la venta.

El trigger que construye un mapa de todos los tickets activos en cada escritura debe revertirse antes de cualquier nueva optimización.

### Resultados y premios

La reconciliación de premios se separará completamente de:

- `get-ticket-list`;
- hidratación de pantallas;
- venta de tickets;
- comprobaciones `updated-at`.

Los jobs procesarán tickets mediante una cola durable con:

- `FOR UPDATE SKIP LOCKED`;
- lotes iniciales de 10 tickets;
- máximo una ejecución por worker;
- `statement_timeout` de 5 segundos por lote;
- `lock_timeout` de 500 ms;
- reintentos limitados;
- estado y contador de intentos por trabajo.

Si otro worker ya posee el trabajo, el proceso lo salta en lugar de esperar.

### Cron

Los cron jobs no podrán superponerse.

Cada ejecución utilizará un try-lock no bloqueante. Si el job anterior sigue activo, la nueva ejecución terminará inmediatamente y registrará `skipped_busy`.

El watchdog 6 permanecerá apagado hasta que la nueva cola supere las pruebas de carga.

## Reducción de llamadas

El objetivo no es solamente bajar el número total, sino evitar trabajo duplicado.

Por cliente:

- la venta realiza una solicitud de escritura;
- una señal Realtime agrupa cambios durante una ventana de debounce;
- múltiples señales dentro de 2 segundos producen una sola lectura delta;
- `updated-at` no se consulta mientras exista una suscripción Realtime saludable;
- la hidratación no ejecuta `flush`;
- el `flush` no ejecuta hidratación completa;
- abrir varias pantallas comparte el mismo coordinador de sincronización.

Se añadirá un registro local de solicitudes en vuelo por `(owner, operación)` para deduplicar llamadas simultáneas.

## Índices compatibles

Se validarán o crearán solamente índices que soporten los accesos nuevos:

```sql
create index concurrently if not exists tickets_owner_updated_id_idx
on public.tickets (admin_key, updated_at desc, id desc)
where deleted_at is null;

create index concurrently if not exists tickets_cashier_updated_id_idx
on public.tickets (cashier_key, updated_at desc, id desc)
where deleted_at is null;

create index concurrently if not exists ticket_items_ticket_id_idx
on public.ticket_items (ticket_id);
```

La creación en producción será concurrente, uno por vez, después de verificar índices equivalentes existentes.

## Presupuesto de rendimiento

### Carga objetivo

- 150 clientes simultáneos;
- una venta cada 30 segundos por cliente;
- 5 ventas por segundo sostenidas durante 30 minutos.

### Resistencia

- 450 clientes equivalentes;
- 15 ventas por segundo durante 10 minutos;
- pruebas fuera de producción o contra una réplica controlada.

### Criterios obligatorios

- p95 de creación de venta menor de 750 ms;
- p99 menor de 1.5 segundos;
- p95 de lectura delta menor de 500 ms;
- cero respuestas 5xx durante la carga objetivo;
- tasa de error total menor de 0.1%;
- cero deadlocks;
- ninguna espera de lock superior a 500 ms;
- conexiones utilizadas por debajo del 60% del límite;
- WAL menor de 100 KB por venta en promedio;
- ningún checkpoint superior a 30 segundos;
- cero pérdida o duplicación de tickets;
- una venta produce una escritura lógica principal, más sus items y un evento pequeño.

Una mejora no se despliega si incumple cualquiera de estos límites, aunque una llamada aislada sea más rápida.

## Pruebas

### Contratos

- hidratación nunca escribe snapshots;
- venta nunca reconstruye snapshots;
- lectura nunca procesa premios;
- aliases placeholder son rechazados;
- cursores no omiten ni duplican filas;
- reintentos conservan idempotencia;
- jobs usan `SKIP LOCKED`;
- cron usa try-lock y no se superpone.

### Integración

- dos dispositivos venden simultáneamente para el mismo owner;
- reconexión con tickets offline;
- pago y anulación mientras otro dispositivo hidrata;
- resultados y ventas ejecutándose simultáneamente;
- alias de admin y cajero resuelven el mismo ticket sin duplicarlo;
- cliente anterior continúa leyendo el contrato compatible.

### Carga

Las pruebas capturarán:

- latencia p50, p95 y p99;
- throughput;
- errores HTTP y SQL;
- conexiones activas y en espera;
- locks y deadlocks;
- WAL por transacción;
- duración y distancia de checkpoints;
- CPU, memoria e I/O;
- crecimiento de tablas y TOAST.

No se ejecutarán benchmarks de escritura masivos en producción.

## Despliegue

1. Revertir de forma aislada el trigger global costoso.
2. Verificar estabilidad durante una ventana operativa completa.
3. Crear índices concurrentes necesarios.
4. Desplegar escritura incremental detrás de feature flag.
5. Activar para un owner interno o de prueba.
6. Ampliar a 5%, 25%, 50% y 100%.
7. Observar al menos 15 minutos entre etapas y una hora antes de 100%.
8. Mantener el camino anterior disponible hasta completar la validación.

## Rollback

El rollback debe requerir solamente:

- desactivar el feature flag;
- restaurar la Edge Function anterior;
- mantener los datos normalizados ya escritos;
- pausar workers nuevos;
- conservar snapshots existentes sin reconstrucción masiva.

No se eliminarán columnas, tablas ni datos durante la primera fase. Las migraciones destructivas quedan fuera de este proyecto.

## Monitoreo y protección

Se alertará por:

- p95 superior a 1 segundo durante 5 minutos;
- más de tres errores 5xx en 5 minutos;
- conexiones superiores al 60%;
- cualquier lock superior a 1 segundo;
- checkpoint superior a 30 segundos;
- job atrasado más de 5 minutos;
- crecimiento anormal de WAL;
- más de una ejecución simultánea del mismo job.

Ante una alerta crítica, el sistema desactiva workers y caché derivada antes de afectar la creación de ventas.

## Fuera de alcance

- cambiar el proveedor de base de datos;
- aumentar el plan del servidor para ocultar consultas ineficientes;
- eliminar inmediatamente todos los snapshots;
- rediseñar interfaces de usuario;
- modificar formatos de tickets impresos;
- ejecutar carga de 450 clientes en producción.

## Decisión

Se adoptará una migración incremental compatible. El almacenamiento normalizado existente será la fuente oficial, el snapshot JSONB quedará como caché temporal, las escrituras serán por ticket y los procesos pesados estarán limitados, separados y observables.

