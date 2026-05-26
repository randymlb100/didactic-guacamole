# LotteryNet Android Figma Library Phase 2 Component Spec

## Estado

- Este documento traduce los primitives y bloques operativos reales del código a una especificación de componentes Figma.
- Se apoya en:
  - [2026-04-21-figma-library-phase0-discovery.md](E:\LOTT\lotterynet_android_studio\lotterynet_android\docs\superpowers\specs\2026-04-21-figma-library-phase0-discovery.md)
  - [2026-04-21-figma-library-phase1-foundations.md](E:\LOTT\lotterynet_android_studio\lotterynet_android\docs\superpowers\specs\2026-04-21-figma-library-phase1-foundations.md)
  - [lotterynet-figma-tokens-v1.json](E:\LOTT\lotterynet_android_studio\lotterynet_android\docs\superpowers\specs\lotterynet-figma-tokens-v1.json)

## Page layout

Crear páginas de componentes en este orden:

1. `Components / Core Chrome`
2. `Components / Sale`
3. `Components / Tickets`
4. `Components / Results`
5. `Components / Printer`
6. `Components / Forms`

## Core Chrome

### App Top Bar

Origen: `AppTopBar`

Variantes:
- `Mode=mobile-standard`
- `Mode=pos-tight`
- `Subtitle=shown|hidden`
- `MenuAction=shown|hidden`

Props:
- `title`
- `subtitle`
- `activeBottomTab`

Reglas:
- sin hero treatment
- altura compacta
- título dominante, subtitle discreto

### Bottom Nav

Origen: `BottomNavBar`

Variantes:
- `Mode=mobile-standard|pos-tight`
- `State=default`
- `Selected=venta|tickets|panel|results|menu`

Items base:
- `Venta`
- `Tickets`
- `Panel`
- `Resultados`
- `Menú`

Reglas:
- selected item con mayor contraste
- label siempre visible
- iconos compactos

### Section Header

Origen: `SectionHeader`

Variantes:
- `Meta=shown|hidden`
- `Action=none|text`

Props:
- `title`
- `meta`

### Compact Panel

Origen: `CompactPanel`

Variantes:
- `Tone=default|alt|emphasis`
- `Mode=mobile-standard|pos-tight`

Reglas:
- radio ligado a token de panel
- padding corto
- sin sombras pesadas

### Compact Action Button

Origen: `CompactActionButton`

Variantes:
- `Tone=primary|success|secondary|danger`
- `State=idle|active|disabled`
- `Mode=mobile-standard|pos-tight`
- `Icon=shown|hidden`

Props:
- `label`
- `icon`

Reglas:
- altura ligada a `size/action-height`
- tono comunica intención, no decoración

### Compact Status Badge

Origen: `CompactStatusBadge`

Variantes:
- `Tone=neutral|success|warning|danger|info`
- `Mode=mobile-standard|pos-tight`

Props:
- `label`

### Compact Empty State

Origen: `CompactEmptyState`

Variantes:
- `Container=inline|panel`
- `Mode=mobile-standard|pos-tight`

Props:
- `message`

## Sale

### Input Display

Origen: `VentaInputDisplay`

Variantes:
- `State=active|idle|error`
- `Field=jugada|monto|limite`
- `Mode=mobile-standard|pos-tight`

Props:
- `label`
- `value`

### Lottery Selector Chip

Origen: `LotteryChip`

Variantes:
- `State=selected|idle|closed|warning`
- `Mode=mobile-standard|pos-tight`

Props:
- `lotteryName`
- `supporting`

### Stats Chip

Origen: `StatusPill` y `VentaMiniStatus`

Variantes:
- `Tone=primary|success|warning|neutral`
- `Mode=mobile-standard|pos-tight`

Props:
- `label`

### Total Box

Origen: `CompactTotalBar`

Variantes:
- `Mode=mobile-standard|pos-tight`
- `State=default`

Props:
- `label`
- `amount`

Reglas:
- monto fuerte
- label secundario
- siempre compacto

### Keypad Key

Origen: `VentaKeypad`

Variantes:
- `Type=number|double-zero|backspace|entnum|clear|print|ok`
- `State=idle|active|disabled`
- `Mode=mobile-standard|pos-tight`

Reglas:
- grid 4x4
- `PRINT` más neutro que `OK`
- `OK` dominante

### Mode Tabs

Origen: `ModeStrip` y `CompactModeButton`

Variantes:
- `State=active|idle`
- `Mode=mobile-standard|pos-tight`

Items base:
- `Loterías`
- `Ligar`
- `Super Pale`
- y modos clásicos de juego

### Sale Row

Origen: `SaleRowCard`

Variantes:
- `State=default`
- `Mode=mobile-standard|pos-tight`

Props:
- `lottery`
- `playNumber`
- `amount`
- `type`

### Print Delivery Menu

Origen: `SalePrintPreviewOverlay` + `SalePrintActionCard`

Variantes:
- `Mode=mobile-standard|pos-tight`
- `Preview=real-ticket`

Actions base:
- `Térmica`
- `WhatsApp`
- `Compartir`
- `Ticket oficial`

Reglas:
- preview real del ticket arriba
- cards de acción debajo
- nada de texto técnico largo

### Lottery Picker Overlay

Origen: `VentaLotteryPickerOverlay`

Variantes:
- `Target=primary|secondary`
- `Mode=mobile-standard|pos-tight`
- `SuperPale=on|off`

Reglas:
- header compacto fijo
- lista interna scrollable
- sin cortar acciones abajo

## Tickets

### Ticket Preview Shell

Origen: preview de `TicketOfficialActivity`

Variantes:
- `Mode=mobile-standard|pos-tight`
- `Theme=official`

Props:
- `serial`
- `datetime`
- `securityCode`
- `lotteryRows`
- `total`

Reglas:
- fondo claro
- header navy
- dorado para montos y total
- verde para bolas/jugadas destacadas

### Ticket Action Card

Origen: `TicketQuickActionSpec` + grupos de acciones

Variantes:
- `Tone=primary|success|secondary|danger`
- `State=idle|emphasized|disabled`
- `Mode=mobile-standard|pos-tight`

Props:
- `label`
- `icon`
- `supporting`

### Ticket Meta Card

Origen: `TicketMetaCard`

Variantes:
- `Emphasis=off|on`
- `Mode=mobile-standard|pos-tight`

Props:
- `label`
- `value`
- `supporting`

### Ticket Action Groups

Origen: `resolveTicketPreviewActionGroups`

Secciones obligatorias:
- `Impresión y envío`
  - `Térmica`
  - `WhatsApp`
  - `Compartir`
- `Operación`
  - `Cobrar`
  - `Duplicar`
  - `Anular`
- `Secundarias`
  - `Guardar`

## Results

### Result Ball

Origen: `ResultBall`

Variantes:
- `State=active|inactive`
- `Mode=mobile-standard|pos-tight`

Props:
- `label`

### Result Card

Origen: `ResultCard`

Variantes:
- `State=published|pending|waiting-sync|missing`
- `Mode=mobile-standard|pos-tight`

Props:
- `lottery`
- `drawTime`
- `numbers`
- `pick3`
- `pick4`

### Result Action Row

Origen: `ResultsActionPanel`

Actions base:
- `Copiar`
- `WhatsApp`
- `Compartir`
- `Guardar`
- `Imprimir`

## Printer

### Printer Setting Row

Origen: `PrinterActivity`

Variantes:
- `Type=dropdown|status|action`
- `Mode=mobile-standard|pos-tight`

Options base:
- tamaño:
  - `Pequeño`
  - `Mediano`
  - `Grande`
- densidad/preset:
  - `Compacta`
  - `Media`
  - `Amplia`

### Printer Summary Block

Bloques:
- `Resumen`
- `Impresora`
- `Conexión y estado`
- `Prueba`
- `Vista previa`
- `Acciones`

## Forms

### Form Row

Origen: formularios compactos de `Login` y `UserAccounts`

Variantes:
- `Field=text|password|numeric|toggle`
- `State=idle|focused|error`
- `Mode=mobile-standard|pos-tight`

### Numeric Config Form

Origen: `UserAccountsActivity`

Campos obligatorios de referencia:
- `Comisión`
- `Tope por recarga`
- `Guardar configuración`

## Device frame set

Crear masters de pantalla:
- `Samsung A15 / 393x852`
- `POS 5.5 / 360x640`

Todo componente debe mostrar al menos:
- una variante `mobile-standard`
- una variante `pos-tight`

## Validation checklist

Phase 2 se considera terminada solo si:

- todos los componentes listados existen en Figma o en spec lista para Figma
- cada componente tiene naming estable
- cada componente declara variantes mínimas
- cada componente usa solo tokens de `Phase 1`
- ninguna pantalla nueva obliga a inventar primitives fuera de esta lista
