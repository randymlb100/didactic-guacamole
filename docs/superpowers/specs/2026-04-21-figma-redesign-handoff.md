# LotteryNet Android Figma Redesign Handoff

## Estado

- Objetivo: rediseñar la app completa en Figma con dirección `lotería premium`.
- Archivo objetivo: `LotteryNet Android Redesign`
- Estado del conector Figma en esta sesión: bloqueado por `401 Unauthorized`.
- Este documento deja el trabajo `decision complete` para ejecutarlo en Figma en cuanto la autenticación esté disponible.

## Dirección visual

- Estética: `lotería premium`
- Base: clara, compacta, táctil, colorida pero no ruidosa
- Referencias válidas:
  - Shopify POS para ritmo operativo y estructura
  - apps de betting para jerarquía de información y energía visual
  - ticket oficial tipo Mega Millions para presencia, dorado y legitimidad del comprobante
- Reglas `uncodixfy` obligatorias:
  - no hero sections
  - no dashboards SaaS genéricos
  - no glows ni glassmorphism
  - no cards gigantes
  - no copy decorativo
  - no pills exageradas
  - color solo para prioridad, estado o acción
  - radios pequeños y spacing compacto

## Foundations

### Colores base desde código

- Fondo principal: `CanvasSand` `#EDF5FF`
- Superficie: `Paper` `#FFFFFF`
- Texto fuerte: `Ink` `#07111F`
- Texto soporte: `InkSoft` `#324763`
- Borde: `Line` `#BFD1E4`
- Éxito/acción positiva: `Coal` `#0F8F78`
- Acento frío: `CoalSoft` `#4F46E5`
- Error/pérdida: `Clay` `#B91C1C`
- Error surface: `ClaySurface` `#FEF2F2`
- Éxito surface: `MintSurface` `#E1F8F1`
- Surface fría: `SkySurface` `#F3F8FF`
- Acento premium nuevo para tickets y totales:
  - `GoldStrong` `#C69214`
  - `GoldSoft` `#F4D768`
  - `NavyTicket` `#0A2A66`
  - `GreenBall` `#1DAA51`

### Tipografía

- Base del proyecto actual:
  - `headlineLarge` 19/24 extra bold
  - `headlineMedium` 17/22 bold
  - `titleLarge` 16/21 extra bold
  - `titleMedium` 16/20 bold
  - `titleSmall` 14/18 bold
  - `bodyLarge` 13/18 semibold
  - `bodyMedium` 13/18 regular
  - `bodySmall` 11/15 regular
  - `labelLarge` 12/16 bold
  - `labelMedium` 11/14 bold
  - `labelSmall` 10/13 bold
- En Figma, mantener familia sans simple y compacta. No mezclar serif.
- Definir dos escalas:
  - `mobile-standard`
  - `pos-tight`

### Tamaños y spacing

- Spacing fijo:
  - `4, 8, 12, 16, 24`
- Radios:
  - `8` para POS
  - `10` para mobile
- Sombras:
  - `0 1 3` muy suave o ninguna

### Profiles desde código

- `POS_TIGHT`
  - `actionHeight` 28
  - `actionIcon` 13
  - `screenPaddingH` 7
  - `screenPaddingV` 2
  - `sectionGap` 5
  - `panelRadius` 8
  - `bottomNavHeight` 42
  - `bottomNavLabel` 7
- `POS`
  - `actionHeight` 30
  - `actionIcon` 14
  - `screenPaddingH` 8
  - `screenPaddingV` 4
  - `sectionGap` 5
  - `panelRadius` 8
  - `bottomNavHeight` 46
  - `bottomNavLabel` 8
- `Mobile / Tablet feeling`
  - `actionHeight` 36
  - `actionIcon` 16
  - `screenPaddingH` 15-16
  - `screenPaddingV` 11
  - `sectionGap` 10
  - `panelRadius` 10

## Archivo Figma

### Páginas

1. `00 Foundations`
2. `01 Components`
3. `02 Samsung A15`
4. `03 POS 5.5`
5. `04 Flows & Handoff`

### Frames raíz por dispositivo

- Samsung A15 base:
  - `393 x 852`
- POS 5.5 base:
  - `360 x 640`

### Secciones de `00 Foundations`

- `Colors`
- `Typography`
- `Spacing & Radius`
- `Elevation`
- `Device Profiles`
- `Interaction States`

### Secciones de `01 Components`

- `Navigation`
- `Sale System`
- `Ticket System`
- `Results System`
- `Forms & Status`
- `Thermal System`

## Componentes obligatorios

### Navigation

- `Bottom Nav / Samsung`
- `Bottom Nav / POS`
- `Top Bar / Standard`
- `Top Bar / Compact`
- `Section Header / Inline`
- `Section Header / Action`

### Sale System

- `Keypad Key / Number`
- `Keypad Key / Action`
- `Input Display / Jugada`
- `Input Display / Monto`
- `Lottery Selector Chip`
- `Stats Chip`
- `Total Box`
- `Sale Row`
- `Sale Empty State`
- `Print Delivery Menu`
- `Print Delivery Action Card`

### Ticket System

- `Ticket Preview Shell`
- `Ticket Meta Card`
- `Ticket Status Badge`
- `Ticket Action Card`
- `Ticket Group Header`

### Results System

- `Result Card`
- `Result Ball`
- `Result Pick Badge`
- `Result Action Button`
- `Result State Chip`

### Forms & Status

- `Compact Panel`
- `Inline Status / Success`
- `Inline Status / Error`
- `Inline Status / Neutral`
- `Form Row`
- `Empty State`

### Thermal System

- `Thermal Setting Row`
- `Thermal Dropdown`
- `Thermal Preview Block`
- `Thermal Action Button`

## Pantallas core

### Login

- Meta:
  - formulario directo
  - nada de conteos de admins/cajeros
  - estado breve e inline
- Estructura:
  - logo pequeño
  - nombre de producto
  - título corto
  - usuario
  - contraseña
  - recordar
  - entrar
  - abrir web como acción secundaria ligera
- Variantes:
  - `idle`
  - `loading`
  - `error`

### Shell / Menu

- Meta:
  - ordenar por tareas reales
  - menos cards y menos copy
- Secciones:
  - `Operación hoy`
  - `Tickets`
  - `Gestión`
  - `Administración`
  - `Compatibilidad`
- Variantes por rol:
  - `cashier`
  - `admin`
  - `master`

### Venta

- Meta:
  - ancla del sistema visual
  - ultra usable en Samsung y POS
- Estructura:
  - tabla superior
  - lista de jugadas
  - bloque lotería actual
  - inputs `Jugada / Límite / Monto`
  - tabs de modo
  - estado de selección
  - `Total jugada`
  - stats chips discretos
  - teclado numérico
  - bottom nav
- Variantes:
  - `sin jugadas`
  - `con jugadas`
  - `pale`
  - `super pale`
  - `print menu abierto`

### Print Delivery Menu

- Meta:
  - compacto como index
  - preview real del ticket, no texto
- Estructura:
  - acciones rápidas:
    - `Rápido`
    - `Con QR`
    - `Imagen final`
  - ticket preview
  - acción `Impresora térmica`
  - acción `WhatsApp`
  - acción `Imagen PNG`
  - cancelar

### Ticket Oficial

- Meta:
  - hub operativo del ticket
  - no lista técnica
- Estructura:
  - header
  - grupos de acciones:
    - impresión y envío
    - operación
    - secundarias
  - meta cards
  - verificación
  - ticket preview real
- Variantes:
  - `search`
  - `pay`
  - `duplicate`
  - `void`

## Pantallas extendidas

### Resultados

- Quitar origen técnico como `Supabase`
- Source visible solo como `Servidor` o `Local`
- Acciones:
  - copiar
  - WhatsApp
  - compartir
  - guardar
  - imprimir
- Cards con bolas claras y estado legible

### Recargas

- Estilo operativo de caja
- Poca decoración
- foco en inputs, proveedor, monto y confirmación

### Usuarios

- Inputs simples
- `Comisión` visible como entero humano
- `Tope por recarga`, no `Tx`
- mensajes menos técnicos

### Impresora térmica

- Una sola columna
- controles traducidos:
  - `Pequeño`
  - `Mediano`
  - `Grande`
  - `Compacta`
  - `Balanceada`
  - `Amplia`
- separar claramente:
  - resumen
  - impresora
  - conexión
  - prueba
  - vista previa
  - acciones

### Tickets

- `Lookup`
- `Summary`
- `Detail`
- Alineados al mismo lenguaje del ticket oficial

### Admin / Finance

- coherencia visual, no rediseño experimental
- reducir densidad, ruido y duplicación

## Flows & Handoff

### Flujo principal

- `Login -> Menú -> Venta -> Print Menu -> Ticket Oficial`

### Flujo secundario

- `Menú -> Resultados`
- `Menú -> Usuarios`
- `Menú -> Impresora`

### Notas de implementación Kotlin

- Mantener dualidad:
  - `Samsung A15`
  - `POS 5.5`
- No esconder acciones primarias detrás de scroll innecesario
- `PRINT` desde venta siempre abre delivery menu
- `Térmica` debe seguir siendo acción directa
- `Pale` y `Super Pale` siempre muestran números separados visualmente
- tickets y resultados no deben mostrar copy técnico interno

## Checklist de ejecución en Figma cuando se reautentique

1. Crear archivo `LotteryNet Android Redesign`
2. Crear páginas según este documento
3. Levantar foundations
4. Crear componentes base
5. Diseñar `Login`, `Shell`, `Venta`, `Print Delivery Menu`, `Ticket Oficial`
6. Revisar captura de cada una
7. Extender al resto
8. Crear página `Flows & Handoff`
9. Revisar Samsung A15 y POS 5.5 lado a lado

## Criterios de aceptación

- La app se ve moderna y colorida sin verse genérica
- `Venta` cabe limpia en Samsung A15 y POS 5.5
- `Ticket` se siente oficial y premium
- `Resultados`, `Login`, `Usuarios` y `Printer` dejan de mostrar jerga interna
- El implementador Kotlin no necesita adivinar tamaños, agrupaciones ni prioridades
