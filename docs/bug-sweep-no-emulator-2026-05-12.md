# Barrido de Bugs sin Emulador - 2026-05-12

## Resumen

Barrido ejecutado con:

- check Node del repo;
- tests Python del scraper/API;
- tests Kotlin de resultados/venta;
- lectura real de Render y Supabase;
- reproducción dirigida de rutas `live`.

Base local: verde.  
Hallazgos reales: 2 bugs de rendimiento/integración y 2 observaciones esperadas del negocio/datos.

## Evidencia rápida

- `tools/check-project.mjs`: OK, 0 fallos.
- `python -m unittest discover -s didactic_guacamole_update -p 'test*.py'`: OK, 13 tests.
- `python -m unittest discover -s scraper -p '*test*.py'`: OK, 44 tests.
- `python tests_app_contracts.py`: OK.
- `gradlew testDebugUnitTest --tests "com.lotterynet.pro.core.results.*" --tests "com.lotterynet.pro.ui.results.*" --tests "com.lotterynet.pro.ui.sales.*" --tests "com.lotterynet.pro.core.sales.*"`: OK.

## Bugs reproducibles

### 1. `live=1` excede el SLA y puede dejar la app esperando demasiado

- Severidad: Alta
- Componente: `render api` + `scraper` + `app`
- Reproducción:
  - `system-results?date=11-05-2026&mode=both&live=1` supera 60s desde cliente externo.
  - `results?date=12-05-2026&live=1` tarda ~82s y devuelve `0`.
  - `results?date=11-05-2026&live=1` tarda ~52s.
  - `results?date=10-05-2026&live=1` tarda ~54s.
- Evidencia:
  - loterías locales: ~18-19s por fecha;
  - picks locales: ~13-21s por fecha;
  - en `both live`, Render ejecuta ambos caminos de forma secuencial.
- Raíz probable:
  - [`didactic_guacamole_update/app.py`](E:\LOTT\lotterynet_android_studio\lotterynet_android\didactic_guacamole_update\app.py:247) ejecuta `lottery_rows_for_request_date()` y `pick_rows_for_request_date()` en secuencia para `mode=both`;
  - ambos caminos llaman scraping vivo cuando `live=1`;
  - el scraper Pick además cae en fallback costoso ([`scrape_and_save.py`](E:\LOTT\lotterynet_android_studio\lotterynet_android\didactic_guacamole_update\scraper\scrape_and_save.py:995), [`scrape_and_save.py`](E:\LOTT\lotterynet_android_studio\lotterynet_android\didactic_guacamole_update\scraper\scrape_and_save.py:1031));
  - la app Android sigue teniendo timeout de 8s al pedir Render ([`SupabaseResultsRemoteStore.kt`](E:\LOTT\lotterynet_android_studio\lotterynet_android\app\src\main\java\com\lotterynet\pro\core\results\SupabaseResultsRemoteStore.kt:313)), así que una ruta viva fría casi siempre perderá contra el timeout del cliente.
- Propuesta de fix:
  - no usar `mode=both&live=1` para cajero;
  - servir `live` desde cache caliente o con refresh async;
  - para hoy sin resultados aún, cortar temprano antes de recorrer todas las fuentes;
  - bajar costo de fallback Pick cuando la fecha es hoy y todavía no hay sorteos publicados;
  - opcional: dividir refresh vivo en dos llamadas pequeñas y fusionar en servidor.

### 2. `live=1` para hoy vacío tarda demasiado solo para confirmar ausencia de resultados

- Severidad: Alta
- Componente: `scraper`
- Reproducción:
  - scraper local para `12-05-2026`:
    - loterías: `0` en ~19.27s
    - picks: `0` en ~21.14s
  - ruta Render `results?date=12-05-2026&live=1`: ~82.4s para devolver `0`.
- Raíz probable:
  - aunque no haya resultados todavía, el scraper sigue consultando fuentes dominicanas y fallback de picks;
  - además aparecen reintentos/404 de LotteryUSA para Indiana, que agregan costo sin valor.
- Propuesta de fix:
  - fast-path para “hoy antes de publicación”;
  - evitar fallback caro cuando ninguna fuente primaria muestra fecha actual;
  - memorizar fallos por fecha/fuente por ventana corta.

## Observaciones que NO son bug real

### A. `42` loterías en `system-results` vs `46` en cache no es inconsistencia por sí sola

- `lot_results_cache_by_day:11-05-2026` y `:10-05-2026` tienen `46` filas.
- Las 4 faltantes en “loterías” son IDs `19`, `20`, `21`, `22`.
- Esas 4 filas son NJ Pick 3/4 legacy guardadas en cache mixto y Render las reubica como Pick al exponer resultados.
- Resultado público correcto:
  - `42` loterías clásicas
  - `119` picks
  - `161` total en `results`

### B. Hoy `12-05-2026` en `0/0` es esperado si las fuentes aún no publicaron fecha de hoy

- Render cache:
  - `lot_results_cache_by_day:12-05-2026 = 0`
  - `pick_results_cache_by_day:12-05-2026 = 0`
- Scraper local:
  - también devuelve `0/0` para la fecha.
- El problema no es el vacío de hoy por sí mismo, sino cuánto tarda el sistema en llegar a esa conclusión.

## Prioridad sugerida

1. Arreglar latencia de `live=1` para hoy y `mode=both`.
2. Cortar temprano scraping cuando hoy aún no tiene resultados publicados.
3. Recalibrar el cliente Android para depender más de cache caliente y menos de live frío.
4. Mantener la reclasificación de `19-22` como comportamiento documentado, no como bug.
