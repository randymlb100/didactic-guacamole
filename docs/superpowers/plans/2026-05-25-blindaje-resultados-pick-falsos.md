# Blindaje Resultados Pick Falsos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Evitar que un resultado Pick falso, temprano o ligado al turno equivocado se quede pegado si luego llega el resultado correcto.

**Architecture:** El backend debe tratar cada resultado Pick por `id` exacto, estado, juego, turno y hora oficial IANA. Un resultado recibido antes de su hora oficial queda suprimido/pendiente y no bloquea venta; si luego llega una version correcta despues de la hora oficial, el sistema debe reemplazar/publicar la fila correcta.

**Tech Stack:** Python scraper/backend Render, Supabase `lotterynet_pick_results_by_day`, Android Kotlin schedule parity, Node.js QA monitors.

---

## Estado Actual

- NJ Dia y NJ Noche ya se validan por hora oficial.
- NJ Dia publicado no debe bloquear NJ Noche.
- Resultado temprano no debe bloquear venta.
- El scraper/Render ya no debe caer por llamadas live desde la app.
- Las pruebas Node actuales confirmaron venta, bloqueo correcto, pago, reportes y jornada simulada.

## Riesgo Que Queda

Si Supabase recibe una fila mala con el mismo `id` exacto, marcada como publicada, y luego llega el resultado correcto, necesitamos asegurar que la fila correcta gane y que no se quede pegado el dato falso.

---

### Task 1: Agregar Metadatos De Auditoria A Resultados Pick

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\didactic-guacamole-render\scraper\scrape_and_save.py`
- Modify: `C:\Users\Randy Cordero\Desktop\didactic-guacamole-render\app.py`
- Test: `C:\Users\Randy Cordero\Desktop\didactic-guacamole-render\scraper\scrape_and_save_test.py`

- [ ] **Step 1: Agregar prueba para payload auditado**

Validar que cada fila Pick pueda incluir:

```json
{
  "source": "lotteryusa.com",
  "observedAt": "2026-05-25T00:00:00Z",
  "officialDrawUtc": "2026-05-25T02:57:00Z",
  "suppressedUntil": "2026-05-25T02:57:00Z",
  "status": "pending",
  "suppressedReason": "early-result-before-official-draw"
}
```

- [ ] **Step 2: Implementar metadatos sin cambiar comportamiento de venta**

Cuando una fila sea temprana, conservar la fila pero limpiar numero/pick3/pick4 para respuesta publica y marcar `status = pending`.

- [ ] **Step 3: Ejecutar pruebas**

```powershell
cd "C:\Users\Randy Cordero\Desktop\didactic-guacamole-render"
$env:SUPABASE_KEY='test-key'
& "C:\Users\Randy Cordero\Desktop\lotterynet_android\.venv\Scripts\python.exe" -m unittest
```

---

### Task 2: Reemplazo Inteligente De Fila Suprimida

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\didactic-guacamole-render\scraper\scrape_and_save.py`
- Test: `C:\Users\Randy Cordero\Desktop\didactic-guacamole-render\scraper\scrape_and_save_test.py`

- [ ] **Step 1: Escribir prueba de resultado falso temprano seguido por resultado correcto**

Caso:
- Fila NJ Noche llega antes de `10:57 PM ET`.
- Se guarda como `pending/suppressed`.
- Luego llega NJ Noche despues de `10:57 PM ET` con numero distinto.
- El numero correcto reemplaza el anterior.

- [ ] **Step 2: Ajustar merge**

Regla:
- mismo `id`
- mismo `stateCode`
- mismo `game`
- mismo `draw`
- si nuevo `observedAt >= officialDrawUtc`, gana el nuevo publicado.

- [ ] **Step 3: Proteger contra mezcla Dia/Noche**

Una fila `MIDDAY` nunca reemplaza `EVENING`, aunque el nombre sea parecido.

---

### Task 3: Monitor Node De Resultados Sospechosos

**Files:**
- Create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\tools\qa\pick-result-integrity-monitor.mjs`
- Test/Run: Node manual contra Supabase/Render

- [ ] **Step 1: Leer resultados actuales**

Consultar:
- Render `/system-results?mode=pick&live=1`
- Supabase `lotterynet_pick_results_by_day`

- [ ] **Step 2: Validar estados criticos**

Estados iniciales:
- NJ
- ME
- NY
- FL
- TX
- CA

Detectar:
- `published` antes de hora oficial
- `MIDDAY` bloqueando `EVENING`
- `EVENING` bloqueando `MIDDAY`
- fila con numero pero `nowUtc < officialDrawUtc`
- mismo nombre pero `id` incorrecto

- [ ] **Step 3: Salida simple**

Formato:

```text
OK NJ Pick 3 Midday
RIESGO NJ Pick 3 Evening published antes de 10:57 PM ET
```

- [ ] **Step 4: No escribir nada en produccion**

El monitor solo lee y reporta.

---

### Task 4: Prueba Node De Correccion Completa

**Files:**
- Modify: `C:\Users\Randy Cordero\Desktop\lotterynet_android\tools\verify-pick-result-gate.mjs`
- Or create: `C:\Users\Randy Cordero\Desktop\lotterynet_android\tools\qa\pick-false-result-replacement-smoke.mjs`

- [ ] **Step 1: Simular falso temprano**

Confirmar:
- venta sigue abierta
- resultado no bloquea
- estado queda `pending/suppressed`

- [ ] **Step 2: Simular resultado correcto despues de hora**

Confirmar:
- resultado se publica
- venta se bloquea
- ticket ganador calcula con numero correcto

- [ ] **Step 3: Confirmar que Dia no toca Noche**

NJ Dia publicado no cambia NJ Noche.

---

### Task 5: Despliegue Seguro

**Files:**
- Backend repo: `C:\Users\Randy Cordero\Desktop\didactic-guacamole-render`
- Android repo: `C:\Users\Randy Cordero\Desktop\lotterynet_android`

- [ ] **Step 1: Ejecutar pruebas backend completas**

```powershell
cd "C:\Users\Randy Cordero\Desktop\didactic-guacamole-render"
$env:SUPABASE_KEY='test-key'
& "C:\Users\Randy Cordero\Desktop\lotterynet_android\.venv\Scripts\python.exe" -m unittest
```

- [ ] **Step 2: Ejecutar pruebas Node criticas**

```powershell
cd "C:\Users\Randy Cordero\Desktop\lotterynet_android"
$env:LOTTERYNET_CREDENTIAL_FILE='C:\Users\Randy Cordero\Documents\LotteryNet-Secrets\contraseña de prueba.txt'
node tools/verify-pick-result-gate.mjs
node tools/qa/pick-mode-results-smoke.mjs
node tools/qa/bugfix-regression-smoke.mjs
```

- [ ] **Step 3: Subir backend primero**

```powershell
cd "C:\Users\Randy Cordero\Desktop\didactic-guacamole-render"
git status --short
git add app.py scraper/scrape_and_save.py scraper/scrape_and_save_test.py
git commit -m "fix: replace suppressed pick results with official rows"
git push origin main
```

- [ ] **Step 4: Verificar Render**

```powershell
Invoke-RestMethod "https://didactic-guacamole.onrender.com/health"
Invoke-RestMethod "https://didactic-guacamole.onrender.com/build-info"
Invoke-RestMethod "https://didactic-guacamole.onrender.com/system-results?mode=both&live=1"
```

---

## Criterios De Aceptacion

- [ ] Resultado temprano no bloquea venta.
- [ ] Resultado temprano queda marcado como pendiente/suprimido.
- [ ] Resultado correcto posterior reemplaza al falso.
- [ ] NJ Dia no bloquea NJ Noche.
- [ ] NJ Noche no bloquea NJ Dia.
- [ ] Maine DAY/MIDDAY no afecta turno incorrecto.
- [ ] Node monitor detecta cualquier `published` antes de hora oficial.
- [ ] Backend tests pasan.
- [ ] Node smoke tests pasan.

## Nota Operativa

Este plan queda pendiente. No se debe ejecutar durante venta activa sin correr primero el monitor en modo solo lectura.
