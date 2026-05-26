# LotteryNet Production Readiness Audit

Fecha: 2026-05-05

## Estado

APK release candidato generado:

- `app/build/outputs/apk/release/lotterynet-kotlin-v1.0.2-kotlin-release.apk`
- Package: `com.lotterynet.pro`
- Version: `1.0.2-kotlin`
- Version code: `3`
- Min SDK: `24`
- Target SDK: `36`

## Bloqueos

### Critico

- Recargas Rapidas no tiene HTTPS disponible en el host probado. La app ya no empaqueta endpoint HTTP para produccion, pero el proveedor debe habilitar HTTPS o entregar sandbox/host seguro antes de activar compras reales.

### Alto

- QA manual en dispositivo real POS Android 8 / 1 GB RAM no ejecutado en esta maquina. No habia dispositivo ADB conectado y el AVD local no completo arranque.

### Medio

- Supabase respondio a la prueba base con rechazo HTTP esperado para rutas no validas/no autorizadas. Requiere una prueba de mesa con rutas operativas reales antes de liberar a tiendas o clientes.

## Fix Aplicado

- `RecargasRapidasEndpoints.baseUrl` cambio de HTTP a HTTPS.
- Se agrego contrato unitario para impedir que Recargas Rapidas vuelva a usar `http://`.

## Evidencia

- `testDebugUnitTest`: BUILD SUCCESSFUL.
- `assembleDebug`: BUILD SUCCESSFUL.
- `assembleRelease`: BUILD SUCCESSFUL.
- `apksigner verify --print-certs`: firma valida con certificado `CN=LotteryNet Pro`.
- Inspeccion APK: no se encontraron archivos locales sensibles como `KEY RECARGA RAPIDA.txt`, keystore, `.env` o `local.properties` empaquetados.
- `network_security_config`: `cleartextTrafficPermitted="false"`.
- `AndroidManifest`: `allowBackup="false"` y `send-default-pii=false`.
- Recargas Rapidas HTTP probe: HTTP 200 en el host del proveedor.
- Recargas Rapidas HTTPS probe: fallo SSL; no se ejecutaron compras reales.

## Checklist De Flujos

- Venta local/offline: cubierto por pruebas unitarias y build; falta QA en dispositivo.
- Tickets anular/borrar: cubierto por contratos recientes; no debe revivir como activo al sincronizar.
- Resultados/ganadores/finanza: cubierto por pruebas unitarias existentes; falta QA en dispositivo.
- Roles master/admin/cajero: rutas protegidas por navegación nativa; falta recorrido manual completo.
- Impresion/WhatsApp/cache: cubierto por checklist existente; falta validacion en POS real.
- Recargas: catalogo y finanza cubiertos por contratos; compra real bloqueada hasta HTTPS/sandbox.

## Recomendacion

El APK puede usarse como candidato tecnico interno. No debe declararse listo para produccion con recargas reales hasta que el proveedor entregue HTTPS valido o un ambiente sandbox seguro y se complete QA manual en POS real.
