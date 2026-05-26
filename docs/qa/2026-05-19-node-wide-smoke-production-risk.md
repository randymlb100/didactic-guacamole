# Prueba amplia Node.js antes de produccion

Fecha: 2026-05-19  
Estado: documentado para arreglar de noche  
Regla operativa: no aplicar cambios en produccion mientras hay gente vendiendo.

## Objetivo

Hacer una prueba amplia con Node.js contra Supabase y las funciones reales para revisar:

- Login y JWT.
- Que Supabase no bloquee usuarios validos.
- Creacion temporal de banca, cajero y supervisor.
- Borrado/restauracion de datos temporales.
- Flujo de venta, borrado y pago de tickets.
- Riesgos de RLS y permisos antes de produccion.

## Herramientas usadas

- Node.js local del entorno de Codex.
- Funciones Supabase reales.
- Credenciales de prueba existentes en el proyecto.
- Supabase Advisors de seguridad y performance.
- Logs de Supabase Edge Functions y Postgres.

No se compilo la app.  
No se modifico codigo de la app.  
No se dejo ningun usuario temporal activo.

## Pruebas realizadas

### 1. Login y JWT

Cuentas probadas:

- Master.
- Admin `podero02`.
- Cajero `bancae01`.

Resultado:

- Login correcto.
- Refresh token correcto.
- JWT generado correctamente.
- Admin y cajero pudieron consultar funciones protegidas.

Observacion:

- El master recibio `403 Usuario no autorizado` al llamar `get-admin-report` con el payload usado por el smoke test. Hay que decidir si master debe poder ver ese reporte o si ese bloqueo es intencional.

### 2. Venta real por API

Se probo crear ticket con jugadas normales y Pick:

- Quiniela.
- Pick 3 BOX.
- Pick 3 Straight.
- Pick 4 BOX.
- Pick 4 Straight.

Resultado:

- Admin pudo crear ticket.
- Cajero pudo crear ticket.
- Borrado de ticket funciono.
- Pago despues de borrar quedo bloqueado con `409`, correcto.

Falla encontrada:

- Pago antes de borrar fallo con `500`.
- Logs de Postgres indicaron: `El ticket no tiene premio confirmado.`

Riesgo:

- El flujo de pago de premios no esta listo si el ticket ganador no pasa por validacion/confirmacion previa.

### 3. Crear banca, cajero y supervisor temporal

Primero se probo crear usuarios en `sys_users_v4`, pero ese no era el flujo correcto para login/JWT.

Luego se probo por el flujo usado por la app:

- Endpoint `lotterynet-users-state`.
- Payload con banca temporal.
- Cajero temporal.
- Supervisor temporal asignado.

Resultado:

- Creacion temporal funciono.
- Admin temporal recibio JWT.
- Cajero temporal recibio JWT.
- Supervisor temporal recibio JWT.
- Despues se restauro el payload original.
- Se confirmo que los usuarios temporales desaparecieron.

Usuarios temporales usados:

- `qaadm911945`
- `qacaj911945`
- `qasup911945`

Estado final:

- Borrados/restaurados.
- No quedaron activos.

### 4. RLS / permisos

Prueba directa REST contra `lotterynet_users_state`:

- Escritura directa con llave publica devolvio `401`.
- Eso indica que la escritura REST directa esta bloqueada.

Problema encontrado:

- El endpoint `lotterynet-users-state` acepto `upsert` usando solo la llave publica.
- Esto permite modificar usuarios por funcion Edge sin JWT real de master/admin.

Riesgo:

- Alto. Antes de produccion, `lotterynet-users-state` debe exigir JWT valido de master/admin o secreto servidor.

### 5. Supabase Advisors

Seguridad:

- Muchas tablas publicas tienen RLS activo pero sin politicas.
- Esto normalmente bloquea acceso directo, pero puede romper flujos si alguna parte depende de REST directo.
- Hay advertencia de `pg_net` instalado en schema `public`.
- Leaked password protection esta desactivado.

Performance:

- Varias foreign keys sin indices.
- Varias politicas permisivas duplicadas.
- Warnings de RLS que recomiendan usar `(select auth.uid())` / `(select auth.jwt())` para mejor performance.

## Hallazgos principales

### Critico 1: endpoint de usuarios demasiado abierto

`lotterynet-users-state` permite modificar usuarios con llave publica.

Impacto:

- Crear usuarios no autorizados.
- Modificar cajeros/admins/supervisores.
- Bloquear o cambiar perfiles.
- Romper ventas si alguien manda un payload incorrecto.

Accion recomendada de noche:

- Cambiar `lotterynet-users-state` para exigir JWT.
- Permitir escritura solo a master/admin.
- Mantener fetch compatible si la app lo necesita, pero no permitir `upsert` anonimo.
- Agregar prueba Node que confirme:
  - upsert sin JWT devuelve `403`.
  - upsert con JWT master/admin funciona.
  - upsert con cajero/supervisor devuelve `403`.

### Critico 2: pago de premios falla con 500

`pay-ticket` devuelve `500` cuando el ticket no tiene premio confirmado.

Impacto:

- Cajero/admin ve error generico.
- Pago de premios puede fallar en produccion.

Accion recomendada de noche:

- Revisar `lotterynet_pay_ticket_server_first`.
- Decidir flujo correcto:
  - Si no hay premio confirmado, devolver `409` o `422` con mensaje claro.
  - Si el backend debe validar premio automaticamente, arreglar validacion antes del pago.
- Agregar prueba Node para ticket ganador real o fixture controlado.

### Alto 3: master report 403

El smoke test recibio `403` en `get-admin-report` usando master.

Accion recomendada:

- Confirmar regla de negocio.
- Si master debe ver reportes, ajustar alcance.
- Si no debe verlos, ajustar smoke test para no marcarlo como falla.

### Medio 4: advisors de seguridad/performance

No bloquea la venta ahora, pero antes de produccion conviene limpiar:

- RLS sin politicas en tablas que deberian ser accesibles.
- Indices faltantes en foreign keys importantes.
- Politicas permisivas duplicadas.
- Password leak protection desactivado.

## Lo que no se hizo

- No se compilo.
- No se abrio emulador.
- No se probaron todos los botones visuales de Compose.
- No se corrigio ningun bug.
- No se cambio ningun archivo de codigo.
- No se dejo data temporal en Supabase.

## Orden sugerido para arreglar de noche

1. Bloquear `lotterynet-users-state` para escritura anonima.
2. Corregir `pay-ticket` para no devolver `500` en premio no confirmado.
3. Repetir Node smoke completo.
4. Probar creacion y borrado de banca/supervisor con JWT real.
5. Revisar master report `403`.
6. Luego, si hay tiempo, limpiar advisors de Supabase.

