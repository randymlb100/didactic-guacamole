# Sentry Android Setup

La app usa configuracion manual de Sentry para Android.

## Runtime

Agrega estas claves en `local.properties`:

```properties
sentry.dsn=__PUBLIC_DSN__
sentry.environment=production
sentry.traces.sample-rate=0.2
sentry.logs.enabled=true
```

El `DSN` se inyecta en `BuildConfig` y `AndroidManifest.xml`.

## CLI / build upload

Si luego quieres usar `sentry-cli` o plugins de build, crea un archivo local `sentry.properties`
basado en `sentry.properties.example`:

```properties
defaults.url=https://sentry.io/
defaults.org=rlm-system-up
defaults.project=android
auth.token=__SET_SENTRY_AUTH_TOKEN__
```

Ese archivo no se sube al repo.

## Verificacion

1. Ejecuta la app con un `DSN` real.
2. Provoca un error manejado o no manejado.
3. Revisa que aparezca en Sentry con tags:
   - `app_layer=android-native`
   - `current_activity=<Activity>`
