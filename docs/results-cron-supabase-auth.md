# Diagnóstico del cron de resultados

## Síntoma

Render ejecutaba `scraper/scrape_and_save.py`, pero Supabase devolvía `401` al leer o guardar `lotterynet_kv`. Por eso las fuentes podían responder y aun así los resultados no llegaban a la aplicación.

## Causa

El proyecto usa las nuevas claves `sb_publishable_...` y `sb_secret_...`. Estas claves no son JWT. En la implementación se mezclaron dos modelos de autenticación:

- clave nueva en `apikey`;
- la misma clave nueva en `Authorization: Bearer`.

Supabase documenta que las claves publishable/secret deben enviarse únicamente en `apikey`; no deben duplicarse como Bearer. El cron debe mantener la clave secreta solo en variables protegidas de Render y nunca en Android, navegador, logs o URLs.

## Contrato correcto del cron

```text
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_KEY=sb_secret_...   # solo en Render

Headers:
  apikey: SUPABASE_KEY
  Content-Type: application/json
```

El cron no debe usar `Authorization: Bearer sb_secret_...`.

## Verificación operativa

1. Confirmar que el cron ejecute el commit más reciente.
2. Ejecutar una sola corrida controlada.
3. Verificar que no aparezca `401 lotterynet_kv`.
4. Confirmar filas nuevas en `result_draws` y `lotterynet_kv`.
5. Confirmar que `get-results-v2` responda `200`.
6. Revisar Realtime por separado; los errores de `ln:users:global` no prueban fallo del canal `result_draws`.

Referencia oficial: https://supabase.com/docs/guides/getting-started/migrating-to-new-api-keys
