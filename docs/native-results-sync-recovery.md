## Native Results Sync Recovery

Fecha: 2026-04-19

### Fuente remota

- Archivo: `.github/workflows/scrape.yml`
- Frecuencia: cada 10 minutos
- Acción principal: ejecuta `python scraper/scrape_and_save.py`
- Objetivo: publicar o persistir resultados remotos para que la app no dependa solo del scraping manual local

### Contrato que debe respetar la app nativa

- `ResultsActivity` sigue siendo la vista nativa de resultados
- el botón de `sync` en nativo debe disparar refresh manual sin romper el estado actual
- la UI debe reflejar estados coherentes:
  - pendiente
  - esperando resultado
  - publicado
- el refresco manual no debe contradecir el dato ya sincronizado por el cron remoto

### Reglas de integración

1. El cron remoto es la fuente periódica de resultados.
2. El refresh nativo es una aceleración manual, no una fuente paralela con reglas distintas.
3. Si el dato local ya coincide con el remoto, la app debe mantener el resultado y solo actualizar sello de sincronización.
4. Si el dato remoto cambia, la app debe refrescar el listado y mantener visible el estado actualizado sin cerrar la pantalla.

### Estado de recuperación aplicado

- `Venta` y `Resultados` se mantienen en ruta nativa.
- el `bottom nav` ya puede convivir con estas pantallas sin volver a WebView.
- el shell nativo quedó como entrada tipo `Inicio` para `CASHIER` y `ADMIN`.

### Próximo criterio de trabajo

- cualquier nueva migración visual o funcional debe verificarse contra `app/src/main/assets/index.html`
- cualquier cambio de sync debe conservar la compatibilidad con `.github/workflows/scrape.yml`
