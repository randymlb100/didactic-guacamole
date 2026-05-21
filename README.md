# LotteryNet Results Service

Servicio Python/Flask usado por LotteryNet para publicar resultados de loterias normales y Pick en Render.

## Archivos principales

- `app.py`: API HTTP que consume la app Android.
- `scraper/scrape_and_save.py`: scraper y sincronizacion con Supabase.
- `scraper/us_pick_catalog.json`: catalogo activo de juegos Pick.
- `.github/workflows/scrape.yml`: tarea programada para actualizar resultados.
- `render.yaml`: configuracion del servicio web en Render.

## Ejecutar pruebas

```powershell
python -m unittest test_app
Push-Location scraper
python -m unittest scrape_and_save_test
Pop-Location
```

## Render

Render usa:

```text
Build Command: pip install -r requirements.txt
Start Command: gunicorn app:app --config gunicorn.conf.py
```

Las variables requeridas son `SUPABASE_URL` y `SUPABASE_KEY`.
