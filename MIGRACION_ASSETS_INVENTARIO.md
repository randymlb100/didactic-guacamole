# Inventario De Assets Para Migracion Kotlin

## Conclusión rápida

Sí, varios assets actuales sí son necesarios para la migración. No conviene copiar todo ciegamente, pero tampoco conviene ignorarlos.

Lo crítico hoy es:

- logos de loterías
- logos de operadoras de recargas
- ticket oficial background
- referencias de QR / WhatsApp / printer
- sonidos POS
- fuentes y set de iconos del flujo web actual
- assets del ticket térmico y del ticket oficial

## Assets detectados con Python

Ruta base:

- [app/src/main/assets](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets)

### Catálogo y marca

- [icon.svg](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/icon.svg)
- [lot-logos](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/lot-logos)

### Recargas

- [logo_altice.svg](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/logo_altice.svg)
- [logo_claro.svg](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/logo_claro.svg)
- [logo_digicel.svg](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/logo_digicel.svg)
- [logo_natcom.svg](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/logo_natcom.svg)
- [logo_viva.svg](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/logo_viva.svg)
- [logo_wind.svg](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/logo_wind.svg)

### Ticket y compartir

- [ticket_official_bg.svg](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/ticket_official_bg.svg)
- [printer_ref.png](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/printer_ref.png)
- [qr_ref.png](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/qr_ref.png)
- [whatsapp_ref.png](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/whatsapp_ref.png)

### Sonidos POS

- [pos-sfx](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/pos-sfx)

### Fuentes e iconografía del flujo web legado

- [fonts](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/fonts)
- [vendor/phosphor](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/vendor/phosphor)

### Librerías del flujo web

- [qrcode.min.js](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/qrcode.min.js)
- [supabase-js-v2.min.js](/E:/LOTT/lotterynet_android_studio/lotterynet_android/app/src/main/assets/supabase-js-v2.min.js)

## Qué sí conviene migrar

### Necesarios desde ya

- `lot-logos`
- logos de recargas
- `ticket_official_bg.svg`
- `pos-sfx`

### Necesarios mientras conviva el WebView

- `fonts`
- `vendor/phosphor`
- `qrcode.min.js`
- `supabase-js-v2.min.js`

### De referencia para rehacer en nativo

- `printer_ref.png`
- `qr_ref.png`
- `whatsapp_ref.png`

## Ticket térmico editable con preview

Python confirmó que el bloque actual de ticket térmico sí es amplio y editable. No es un ajuste simple.

Hoy el sistema permite:

- ancho 58 / 80
- tipografía local
- etiqueta de jugada
- ancho visual
- caracteres por línea
- densidad
- separadores
- escala de encabezado
- escala de serial
- escala de jugadas y montos
- escala del total
- zoom de preview
- mostrar u ocultar:
  - ORIGINAL
  - dirección
  - teléfono
  - fecha y hora
  - hora de sorteo
  - código de seguridad
  - banca al final

Funciones reales detectadas en el HTML:

- `loadThermalPrinterPrefs()`
- `saveThermalPrinterPrefs()`
- `applyClassicThermalPreset()`
- `renderThermalTicketPreview()`
- `buildTicketThermalText(tkt)`
- `imprimirUltimoTkt()`
- `renderOfficialTicketPreview(...)`

## Decisión correcta para la migración

### Sí migrar a Kotlin

- preferencias térmicas
- repositorio local de impresora
- render térmico
- política de preview por rol
- render oficial del ticket

### No rehacer todavía

- todas las referencias visuales del WebView
- fuentes del flujo web si la pantalla ya va a quedar 100% Compose

## Orden recomendado

1. dejar repositorio nativo de preferencias térmicas
2. dejar modelos nativos para ticket térmico y ticket oficial
3. reusar logos y backgrounds críticos
4. reusar `pos-sfx` si el POS Compose conserva feedback de sonido
5. retirar assets del WebView solo cuando la pantalla equivalente ya exista en Kotlin
