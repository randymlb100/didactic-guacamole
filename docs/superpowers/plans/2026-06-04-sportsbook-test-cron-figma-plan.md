# Sportsbook Test Cron And Figma Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dejar Deportes funcionando en modo prueba controlado, con beisbol disponible, logos desde TheSportsDB, cron deportivo seguro para no quemar la cuota de 1000 requests y una guia clara para mejorar el diseno con Figma sin romper el flujo actual.

**Architecture:** La app Android nunca llama Odds API ni TheSportsDB directo. Render/Supabase sincronizan datos, Supabase guarda cache en `sports_*`, Android solo lee el tablero cacheado y vende por Edge Functions autenticadas. El cron queda apagado por defecto y solo se abre con limites pequenos durante pruebas.

**Tech Stack:** Kotlin Compose, Supabase Edge Functions, Render Cron Jobs, odds-api.net REST API, TheSportsDB API, Figma MCP/design system, Node QA.

---

## Fuentes tecnicas revisadas

- Odds API docs: usar `https://api.odds-api.net/v1`, autenticar con `X-API-Key`, descubrir deportes/ligas antes de pedir cuotas, mantener ventanas pequenas y manejar `429` con backoff: https://odds-api.net/docs
- Odds API GitHub: REST simple, mock mode y ejemplos de SDK, util para pruebas sin exponer llave en Android: https://github.com/odds-api/odds-api
- TheSportsDB docs: usar API para metadata/logos/equipos, no para cuotas ni pagos: https://www.thesportsdb.com/documentation
- Render Cron Jobs: cada cron tiene schedule y environment variables propias: https://render.com/docs/cronjobs
- Supabase Edge Function secrets: secretos se configuran en Supabase y se leen desde Edge Functions: https://supabase.com/docs/guides/functions/secrets
- Figma MCP: usar design system y componentes existentes al escribir en canvas: https://developers.figma.com/docs/figma-mcp-server

## Estado actual del proyecto

Archivos ya existentes:

- `tools/render/sync_sports_odds.py`: cron Python que llama `sports-sync-odds`.
- `render.yaml`: define `lotterynet-sports-odds-sync`.
- `supabase/functions/sports-sync-odds/index.ts`: sincroniza deportes/eventos/cuotas desde odds-api.net.
- `supabase/functions/sports-sync-team-assets/index.ts`: sincroniza logos desde TheSportsDB.
- `supabase/functions/sports-get-board/index.ts`: Android lee tablero cacheado.
- `app/src/main/java/com/lotterynet/pro/ui/sportsbook/SportsbookActivity.kt`: UI principal Deportes.
- `tools/qa/sportsbook-ui-contract.node.test.mjs`: contrato de seccion Deportes.

Reglas de seguridad:

- No poner `ODDS_API_KEY` ni `THESPORTSDB_API_KEY` en Android.
- No prender cron en modo `always` durante prueba.
- No bajar todos los deportes todavia.
- Beisbol primero, `limit=3` o `limit=5`.
- Logos se cachean en servidor; si falta logo, Android usa iniciales.

## Como abrir y cerrar cron deportivo sin gastar requests

### Modo cerrado recomendado

En Render, para `lotterynet-sports-odds-sync`:

```text
SPORTS_ODDS_SYNC_ENABLED=false
SPORTS_ODDS_SYNC_MODE=test-paused
SPORTS_ODDS_SYNC_SPORTS=baseball
SPORTS_ODDS_SYNC_LIMIT=3
```

Con eso el cron puede correr cada 5 minutos, pero el script sale rapido y no llama Odds API.

### Modo prueba controlada

Abrir solo una ventana corta:

```text
SPORTS_ODDS_SYNC_ENABLED=true
SPORTS_ODDS_SYNC_MODE=smart
SPORTS_ODDS_SYNC_SPORTS=baseball
SPORTS_ODDS_SYNC_LIMIT=3
SPORTS_ODDS_SYNC_UTC_HOURS=<hora UTC actual>
```

Ejemplo: si son las 10:00 AM Santo Domingo y UTC es 14, usar:

```text
SPORTS_ODDS_SYNC_UTC_HOURS=14
```

Cuando termine la prueba, volver a:

```text
SPORTS_ODDS_SYNC_ENABLED=false
SPORTS_ODDS_SYNC_MODE=test-paused
```

### Calculo simple de cuota

Con el flujo actual, un sync con `limit=3` consume aproximadamente:

- 1 request para buscar eventos de beisbol.
- 3 requests para snapshots de cuotas, una por evento.
- Puede duplicarse si el snapshot filtrado viene vacio y el servidor hace fallback sin filtro.

Regla practica:

- `limit=3`: estimar 4 a 7 requests por corrida.
- 3 corridas de prueba: estimar 12 a 21 requests.
- No usar `limit=25` en modo prueba porque puede subir rapido.

## Lineas PowerShell para prueba manual sin prender cron fijo

Estas lineas hacen una corrida manual. No pegues secretos en chat; ponlos solo en tu PowerShell local.

```powershell
$env:SUPABASE_URL="https://unhoulkujbtsypccpirc.supabase.co"
$env:SUPABASE_KEY="TU_SERVICE_ROLE_O_SECRET_KEY"
$env:LOTTERYNET_ADMIN_SHARED_SECRET="TU_ADMIN_SHARED_SECRET"

$startTo = [int][double]::Parse((Get-Date -Date (Get-Date).ToUniversalTime().AddHours(36) -UFormat %s))
$body = @{
  sports = @("baseball")
  limit = 3
  marketKeys = "moneyline,runline,total"
  periods = "full time"
  startTo = $startTo
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Method Post `
  -Uri "$env:SUPABASE_URL/functions/v1/sports-sync-odds" `
  -Headers @{
    apikey = $env:SUPABASE_KEY
    Authorization = "Bearer $env:SUPABASE_KEY"
    "x-lotterynet-admin-secret" = $env:LOTTERYNET_ADMIN_SHARED_SECRET
  } `
  -ContentType "application/json" `
  -Body $body
```

Despues de traer eventos, sincronizar logos:

```powershell
$body = @{ limit = 12 } | ConvertTo-Json -Depth 3

Invoke-RestMethod `
  -Method Post `
  -Uri "$env:SUPABASE_URL/functions/v1/sports-sync-team-assets" `
  -Headers @{
    apikey = $env:SUPABASE_KEY
    Authorization = "Bearer $env:SUPABASE_KEY"
    "x-lotterynet-admin-secret" = $env:LOTTERYNET_ADMIN_SHARED_SECRET
  } `
  -ContentType "application/json" `
  -Body $body
```

## Task 1: Dejar Render cron seguro por defecto

**Files:**

- Modify: `render.yaml`
- Test: `tools/qa/sportsbook-ui-contract.node.test.mjs`
- Create optional: `tools/qa/sportsbook-cron-quota-contract.node.test.mjs`

- [ ] **Step 1: Cambiar variables por defecto en `render.yaml`**

En `lotterynet-sports-odds-sync`, cambiar `SPORTS_ODDS_SYNC_LIMIT` de `25` a `3` y agregar modo cerrado:

```yaml
      - key: SPORTS_ODDS_SYNC_ENABLED
        value: "false"
      - key: SPORTS_ODDS_SYNC_MODE
        value: "test-paused"
      - key: SPORTS_ODDS_SYNC_SPORTS
        value: "baseball"
      - key: SPORTS_ODDS_SYNC_LIMIT
        value: "3"
      - key: SPORTS_ODDS_SYNC_UTC_HOURS
        value: "15,18,21,23"
```

- [ ] **Step 2: Crear test de contrato para cuota**

Crear `tools/qa/sportsbook-cron-quota-contract.node.test.mjs`:

```js
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const renderYaml = readFileSync("render.yaml", "utf8");
const syncScript = readFileSync("tools/render/sync_sports_odds.py", "utf8");

test("sports odds cron is paused by default for quota safety", () => {
  assert.match(renderYaml, /name:\s+lotterynet-sports-odds-sync/);
  assert.match(renderYaml, /SPORTS_ODDS_SYNC_ENABLED[\s\S]*value:\s+"false"/);
  assert.match(renderYaml, /SPORTS_ODDS_SYNC_MODE[\s\S]*value:\s+"test-paused"/);
  assert.match(renderYaml, /SPORTS_ODDS_SYNC_SPORTS[\s\S]*value:\s+"baseball"/);
  assert.match(renderYaml, /SPORTS_ODDS_SYNC_LIMIT[\s\S]*value:\s+"3"/);
});

test("sports odds script can skip without calling provider", () => {
  assert.match(syncScript, /SPORTS_ODDS_SYNC_ENABLED/);
  assert.match(syncScript, /sports odds sync disabled/);
  assert.match(syncScript, /test-paused/);
});
```

- [ ] **Step 3: Ejecutar test**

```powershell
node --test tools\qa\sportsbook-cron-quota-contract.node.test.mjs
node --test tools\qa\sportsbook-ui-contract.node.test.mjs
```

Expected: PASS.

## Task 2: Descubrir beisbol sin quemar cuota

**Files:**

- Use: `supabase/functions/sports-sync-odds/index.ts`
- Use: Supabase Dashboard Edge Function logs

- [ ] **Step 1: Descubrir deportes disponibles**

Ejecutar una sola vez:

```powershell
$body = @{ discover = "sports"; limit = 100 } | ConvertTo-Json -Depth 3
Invoke-RestMethod -Method Post -Uri "$env:SUPABASE_URL/functions/v1/sports-sync-odds" -Headers @{ apikey=$env:SUPABASE_KEY; Authorization="Bearer $env:SUPABASE_KEY"; "x-lotterynet-admin-secret"=$env:LOTTERYNET_ADMIN_SHARED_SECRET } -ContentType "application/json" -Body $body
```

Expected: respuesta con `source="odds-api.net/sports"` y lista donde exista beisbol/baseball.

- [ ] **Step 2: Descubrir ligas de beisbol**

```powershell
$body = @{ discover = "leagues"; sport = "baseball" } | ConvertTo-Json -Depth 3
Invoke-RestMethod -Method Post -Uri "$env:SUPABASE_URL/functions/v1/sports-sync-odds" -Headers @{ apikey=$env:SUPABASE_KEY; Authorization="Bearer $env:SUPABASE_KEY"; "x-lotterynet-admin-secret"=$env:LOTTERYNET_ADMIN_SHARED_SECRET } -ContentType "application/json" -Body $body
```

Expected: respuesta con ligas soportadas. Usar esas ligas para UI/filtros, no inventarlas.

## Task 3: Sincronizar beisbol y logos en modo prueba

**Files:**

- Use: `supabase/functions/sports-sync-odds/index.ts`
- Use: `supabase/functions/sports-sync-team-assets/index.ts`
- Use: Supabase tables `sports_events`, `sports_markets`, `sports_odds`, `sports_team_assets`

- [ ] **Step 1: Correr beisbol con `limit=3`**

Usar el comando manual de la seccion PowerShell. Expected:

- `ok=true`
- `eventsSaved >= 0`
- `oddsItemsSeen >= 0`
- Si no hay eventos, no es error; la UI debe mostrar estado vacio claro.

- [ ] **Step 2: Sincronizar logos**

Usar el comando `sports-sync-team-assets`. Expected:

- `ok=true`
- `assetsSaved` o `skippedFresh` mayor o igual a 0.
- No falla si un logo no existe.

- [ ] **Step 3: Verificar tablero**

Llamar `sports-get-board` desde la app o Node QA. Expected:

- Juegos con `homeTeam`, `awayTeam`, hora, liga.
- Si hay logos, `homeTeamLogoUrl` y `awayTeamLogoUrl`.
- Si no hay logos, Android muestra iniciales.

## Task 4: Plan Figma para mejorar la seccion Deportes

**Files:**

- Read: `app/src/main/java/com/lotterynet/pro/ui/sportsbook/SportsbookActivity.kt`
- Target Figma: requiere URL o file key del archivo Figma del proyecto.

- [ ] **Step 1: Pedir archivo Figma objetivo**

No modificar canvas sin un `figma.com/design/...` o file key. Esto evita crear pantallas en el archivo equivocado.

- [ ] **Step 2: Mapear componentes actuales**

Componentes Compose que deben reflejarse en Figma:

- `SportsbookGameRow`: tarjeta de juego.
- `SportsbookGameSheet`: bottom sheet de seleccion.
- `SportsbookOddChip`: chip de cuota.
- `SportsbookTicketPreview`: resumen del ticket.
- `SportsbookFinancePreview`: ganancia/perdida.
- `SportsbookAdminDropdown`: filtro/admin control.

- [ ] **Step 3: Diseno recomendado para tablero**

Estructura visual:

- Header: `Deportes`, estado `Prueba`, badge `Baseball`.
- Filtros: deporte, liga, estado, fecha.
- Card de juego:
  - logos local/visitante;
  - nombres de equipos;
  - hora de inicio;
  - badge `Abierto`, `Cerrado`, `En vivo` o `Suspendido`;
  - mercados visibles: Moneyline, Runline, Alta/Baja, F5 si aplica.
- Bottom sheet:
  - titulo del juego;
  - cuotas agrupadas por mercado;
  - monto;
  - premio estimado;
  - boton `Agregar`.
- Ticket:
  - selecciones claras;
  - cuota congelada;
  - total apostado;
  - premio posible;
  - imprimir/WhatsApp.

- [ ] **Step 4: Reglas visuales**

- Dropdown solo para filtros.
- Chips para cuotas.
- Bottom sheet para detalle de juego, no pantalla nueva.
- Badges de estado con color sobrio.
- Ganancia/perdida con estilo fintech separado de loteria.
- No mezclar colores de resultados de loteria en Deportes.
- En POS pequeno, priorizar legibilidad de equipo/cuota/monto.

## Task 5: QA final sin gastar cuota

**Files:**

- Test: `tools/qa/sportsbook-ui-contract.node.test.mjs`
- Test: `tools/qa/sportsbook-cron-quota-contract.node.test.mjs`

- [ ] **Step 1: Tests Node sin API externa**

```powershell
node --test tools\qa\sportsbook-ui-contract.node.test.mjs
node --test tools\qa\sportsbook-cron-quota-contract.node.test.mjs
```

- [ ] **Step 2: Una prueba real controlada**

Solo cuando el usuario confirme:

- abrir cron manualmente o correr PowerShell una vez;
- beisbol solamente;
- `limit=3`;
- sincronizar logos;
- abrir app y vender un ticket QA;
- cerrar cron otra vez.

- [ ] **Step 3: Confirmar que no se gasto de mas**

Revisar en Upstash/Odds API/Supabase logs:

- cantidad de requests usadas;
- no hay cron corriendo en modo `always`;
- no hay errores `429`;
- no hay loops en Android.

## Resultado esperado

- Deportes puede probar beisbol con cuotas reales sin gastar la cuota mensual.
- Si no hay juegos, la app lo muestra limpio.
- Si hay juegos, aparecen organizados con logos o iniciales.
- El cron queda cerrado despues de prueba.
- Figma queda listo para crear pantalla profesional cuando exista archivo objetivo.
