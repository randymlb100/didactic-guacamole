# LotteryNet Android Figma Library Phase 0 Discovery

## Estado

- Objetivo: preparar el `Phase 0` del design system para ejecutar `figma-generate-library` con criterio `superpowers + uncodixfy`.
- Estado Figma en esta sesión: bloqueado por `401 Unauthorized`, por eso este discovery se hace desde código.
- Resultado esperado de este documento: `scope lock` de tokens, componentes, naming y pantallas antes de crear nada en Figma.

## Stacks usadas

- `superpowers`
  - orden por fases
  - checkpoints claros
  - salida lista para handoff
- `uncodixfy`
  - quitar UI SaaS genérica
  - reducir cards y ruido visual
  - mantener bloques compactos y táctiles
- `lotterynet-pro`
  - aterrizar el sistema a esta app POS de lotería
  - preservar flujo operativo real
- `mobile-ux-figma`
  - enfoque móvil Android + POS
  - priorizar notch, navegación inferior, targets táctiles y densidad útil

## Codebase audit

### Foundations ya existentes

#### Colors

Fuente: [Color.kt](E:\LOTT\lotterynet_android_studio\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\theme\Color.kt)

- `CanvasSand` `#EDF5FF`
- `Paper` `#FFFFFF`
- `Ink` `#07111F`
- `InkSoft` `#324763`
- `Line` `#BFD1E4`
- `Coal` `#0F8F78`
- `CoalSoft` `#4F46E5`
- `Clay` `#B91C1C`
- `ClaySurface` `#FEF2F2`
- `MintSurface` `#E1F8F1`
- `SkySurface` `#F3F8FF`

#### Typography

Fuente: [Type.kt](E:\LOTT\lotterynet_android_studio\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\theme\Type.kt)

- Familia base: `SansSerif`
- Pesos reales usados:
  - `ExtraBold`
  - `Bold`
  - `SemiBold`
  - `Normal`
- Escala real:
  - `headlineLarge` `19 / 24`
  - `headlineMedium` `17 / 22`
  - `titleLarge` `16 / 21`
  - `titleMedium` `16 / 20`
  - `titleSmall` `14 / 18`
  - `bodyLarge` `13 / 18`
  - `bodyMedium` `13 / 18`
  - `bodySmall` `11 / 15`
  - `labelLarge` `12 / 16`
  - `labelMedium` `11 / 14`
  - `labelSmall` `10 / 13`

#### Size profiles

Fuente: [NativeChrome.kt](E:\LOTT\lotterynet_android_studio\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\common\NativeChrome.kt)

Perfiles detectados:
- `POS_TIGHT`
- `POS`
- `COMPACT`
- `STANDARD`

Valores más importantes detectados:
- `POS_TIGHT`
  - `bottomNavHeight = 42`
  - `actionHeight = 28`
  - `screenPaddingH = 7`
  - `panelRadius = 8`
- `POS`
  - `bottomNavHeight = 46`
  - `actionHeight = 30`
  - `screenPaddingH = 8`
  - `panelRadius = 8`
- `COMPACT`
  - `bottomNavHeight = 64`
  - `actionHeight = 36`
  - `screenPaddingH = 15`
  - `panelRadius = 10`
- `STANDARD`
  - `bottomNavHeight = 64`
  - `actionHeight = 36`
  - `screenPaddingH = 16`
  - `panelRadius = 10`

Esto confirma que el sistema ya tiene dos familias visuales reales:
- `pos-tight`
- `mobile-standard`

## Shared UI primitives detectados

El design system no parte de cero. El código ya gira sobre primitives reutilizables:

- `AppTopBar`
- `BottomNavBar`
- `SectionHeader`
- `CompactPanel`
- `CompactActionButton`
- `CompactStatusBadge`
- `CompactEmptyState`
- `CompactAdaptiveGrid`
- `CompactRecordRow`

Conclusión:
- El design system de Figma debe nacer de estos primitives, no de pantallas sueltas.
- La librería v1 debe representar estos bloques antes de entrar a layouts completos.

## Core product surfaces

### Login

Fuente: [LoginActivity.kt](E:\LOTT\lotterynet_android_studio\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\login\LoginActivity.kt)

Hallazgos:
- ya existe `LoginLayoutContract`
- usa `AdaptiveScreenContract`
- el formulario ya fue compactado
- soporta panel opcional y acción secundaria inline
- evita mostrar conteos técnicos

Necesidades Figma:
- `Login / Direct form / Mobile`
- `Login / Direct form / POS`
- `Inline status`
- `Remember row`
- `Primary CTA`
- `Secondary text action`

### Shell / Menu

Fuente: [ShellActivity.kt](E:\LOTT\lotterynet_android_studio\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\shell\ShellActivity.kt)

Hallazgos:
- usa `AppTopBar`, `CompactPanel`, `CompactStatusBadge`, `CompactActionButton`
- concentra navegación por rol
- mezcla áreas operativas, admin, printer, tickets y resultados
- visualmente todavía es un hotspot de cards repetidas

Necesidades Figma:
- `Menu section block`
- `Role shortcut card`
- `Summary badge row`
- `Recent tickets strip`
- `Bottom nav / menu active`

### Venta

Fuente: [SalesActivity.kt](E:\LOTT\lotterynet_android_studio\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\sales\SalesActivity.kt)

Contratos detectados:
- `SaleEntryCarryState`
- `VentaKeypadLayoutContract`
- `TicketPreviewAccessContract`
- `resolveVentaKeypadLayout`
- `applySaleKeypadInput`

Bloques funcionales detectados:
- selector de lotería
- picker para `Super Pale`
- inputs de jugada y monto
- tabs de tipo de jugada
- total encima del teclado
- keypad numérico
- overlay de picker de lotería
- overlay de `print preview`
- acceso directo a térmica, WhatsApp y compartir

Necesidades Figma:
- `Sale row header`
- `Lottery selector chip`
- `Number input display`
- `Amount input display`
- `Mode tabs`
- `Total box`
- `Numeric keypad`
- `Print delivery menu`
- `Lottery picker overlay`
- `Pale`
- `Super Pale`

### Ticket oficial

Fuente: [TicketOfficialActivity.kt](E:\LOTT\lotterynet_android_studio\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\tickets\TicketOfficialActivity.kt)

Contratos detectados:
- `TicketOfficialMode`
- `TicketPreviewAction`
- `TicketPreviewSection`
- `TicketPreviewActionGroup`
- `resolveTicketPreviewActionGroups`

Grupos funcionales detectados:
- `PRINTING`
  - `Térmica`
  - `WhatsApp`
  - `Compartir`
- `OPERATIONS`
  - `Cobrar`
  - `Duplicar`
  - `Anular`
- `SECONDARY`
  - `Guardar`

Necesidades Figma:
- `Ticket preview shell`
- `Ticket action group`
- `Ticket action card`
- `Ticket status badge`
- `Prize status badge`
- `Payout blocked badge`

### Resultados

Fuente: [ResultsActivity.kt](E:\LOTT\lotterynet_android_studio\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\results\ResultsActivity.kt)

Bloques detectados:
- `ResultsDateChips`
- `ResultsDateNavigator`
- `ResultsActionPanel`
- `ResultCard`
- `ResultBall`
- `StatusChip`
- `EmptyResultsCard`

Estados detectados:
- `PUBLISHED`
- `PENDING`
- `WAITING_SYNC`
- `MISSING`

Necesidades Figma:
- `Date chip`
- `Result card`
- `Result ball`
- `Result action row`
- `Result state chip`

### Impresora térmica

Fuente: [PrinterActivity.kt](E:\LOTT\lotterynet_android_studio\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\printer\PrinterActivity.kt)

Contratos detectados:
- `PrinterLayoutContract`
- `PrinterDeviceOption`
- `PrinterConnectionSnapshot`

Términos ya normalizados:
- `Pequeño`
- `Mediano`
- `Grande`
- `Media`

Necesidades Figma:
- `Printer summary block`
- `Printer setting row`
- `Printer connection card`
- `Printer test action row`
- `Printer preset row`

### Usuarios

Fuente: [UserAccountsActivity.kt](E:\LOTT\lotterynet_android_studio\lotterynet_android\app\src\main\java\com\lotterynet\pro\ui\users\UserAccountsActivity.kt)

Hallazgos:
- formularios dentro de `CompactPanel`
- comisión ya se tradujo a entero humano
- label real: `Tope por recarga`
- CTA principal: `Guardar configuración`

Necesidades Figma:
- `Account config row`
- `Numeric settings form`
- `Config status badge`

## Tokens v1 lock

### Color tokens

#### Primitive

- `color/base/canvas-sand`
- `color/base/paper`
- `color/base/ink`
- `color/base/ink-soft`
- `color/base/line`
- `color/base/coal`
- `color/base/coal-soft`
- `color/base/clay`
- `color/base/clay-surface`
- `color/base/mint-surface`
- `color/base/sky-surface`
- `color/base/gold-strong`
- `color/base/gold-soft`
- `color/base/navy-ticket`
- `color/base/green-ball`

#### Semantic

- `color/bg/app`
- `color/bg/panel`
- `color/bg/panel-alt`
- `color/bg/positive-soft`
- `color/bg/negative-soft`
- `color/bg/info-soft`
- `color/text/primary`
- `color/text/secondary`
- `color/text/inverse`
- `color/text/positive`
- `color/text/negative`
- `color/border/default`
- `color/border/strong`
- `color/action/primary`
- `color/action/success`
- `color/action/danger`
- `color/action/secondary`
- `color/ticket/header`
- `color/ticket/gold`
- `color/ticket/ball`

### Typography tokens

- `type/headline/lg`
- `type/headline/md`
- `type/title/lg`
- `type/title/md`
- `type/title/sm`
- `type/body/lg`
- `type/body/md`
- `type/body/sm`
- `type/label/lg`
- `type/label/md`
- `type/label/sm`

### Space tokens

- `space/4`
- `space/8`
- `space/12`
- `space/16`
- `space/24`

### Radius tokens

- `radius/pos = 8`
- `radius/mobile = 10`
- `radius/chip = 999`

### Size tokens

- `size/action/pos-tight = 28`
- `size/action/pos = 30`
- `size/action/mobile = 36`
- `size/bottom-nav/pos-tight = 42`
- `size/bottom-nav/pos = 46`
- `size/bottom-nav/mobile = 64`

## Components v1 lock

### Foundations pages

- `Color`
- `Typography`
- `Spacing`
- `Radii`
- `Size profiles`

### Components pages

- `Top Bar`
- `Bottom Nav`
- `Section Header`
- `Compact Panel`
- `Action Button`
- `Status Badge`
- `Empty State`
- `Keypad`
- `Input Display`
- `Lottery Selector`
- `Stats Chip`
- `Total Box`
- `Ticket Preview`
- `Ticket Action Card`
- `Result Card`
- `Result Ball`
- `Printer Setting Row`
- `Form Row`

## Naming rules for Figma

- Pages:
  - `Cover`
  - `Getting Started`
  - `Foundations`
  - `---`
  - `Components`
  - `---`
  - `Utilities`
- Variables:
  - slash naming
  - example: `color/text/primary`
- Components:
  - title case
  - example: `Bottom Nav`
- Variants:
  - `Property=Value`
  - example: `Mode=POS, State=Active`

## Device scope lock

### Samsung A15

- frame base: `393 x 852`
- focus:
  - notch safe area
  - bottom navigation spacing
  - mobile readability

### POS 5.5

- frame base: `360 x 640`
- focus:
  - compact density
  - keypad always usable
  - actions visible without wasted height

## Conflicts and decisions

- Figma file conventions could not be inspected because auth is blocked.
- Source of truth for v1 is the Kotlin codebase, not a preexisting Figma file.
- If Figma later contains contradictory naming or token structure:
  - typography and spacing can adapt
  - business-critical component API should stay aligned to Kotlin primitives listed here

## Phase 0 approval target

If this scope is accepted, the next execution order in Figma is:

1. `Phase 1 Foundations`
   - variable collections
   - primitive tokens
   - semantic tokens
   - text styles
2. `Phase 2 File structure`
   - pages and documentation surfaces
3. `Phase 3 Components`
   - atoms first
   - operational molecules second
4. `Phase 4 Screens`
   - `Login`
   - `Shell`
   - `Venta`
   - `Print menu`
   - `Ticket oficial`
   - then `Resultados`, `Usuarios`, `Printer`, `Tickets`

## Immediate next deliverable

Preparar `Phase 1 Foundations` en formato listo para `figma-generate-library`:
- collections
- modes
- variable names
- scopes
- code syntax mapping
