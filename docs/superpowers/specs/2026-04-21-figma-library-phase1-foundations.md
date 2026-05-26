# LotteryNet Android Figma Library Phase 1 Foundations

## Estado

- Este documento ejecuta la parte `decision complete` de `Phase 1 Foundations`.
- Está preparado para usarse con `figma-generate-library` en cuanto Figma quede autenticado.
- Fuente de verdad: Kotlin actual + `Phase 0 discovery`.

## Variable collections

Crear estas colecciones en Figma, en este orden:

1. `Primitives / Color`
2. `Semantic / Color`
3. `Spacing`
4. `Radius`
5. `Size`
6. `Typography`

## Modes

Definir exactamente dos modos donde aplique:

- `mobile-standard`
- `pos-tight`

Reglas:
- `Primitives / Color` usa un solo modo si el valor no cambia por dispositivo.
- `Semantic / Color` usa un solo modo en v1.
- `Spacing` usa un solo modo.
- `Radius` usa un solo modo.
- `Size` usa ambos modos.
- `Typography` usa ambos modos.

## Variables v1

### Primitives / Color

Valores fijos:

- `color/base/canvas-sand = #EDF5FF`
- `color/base/paper = #FFFFFF`
- `color/base/ink = #07111F`
- `color/base/ink-soft = #324763`
- `color/base/line = #BFD1E4`
- `color/base/coal = #0F8F78`
- `color/base/coal-soft = #4F46E5`
- `color/base/clay = #B91C1C`
- `color/base/clay-surface = #FEF2F2`
- `color/base/mint-surface = #E1F8F1`
- `color/base/sky-surface = #F3F8FF`
- `color/base/gold-strong = #C69214`
- `color/base/gold-soft = #F4D768`
- `color/base/navy-ticket = #0A2A66`
- `color/base/green-ball = #1DAA51`

Scopes:
- dejar sin scopes explícitos de UI para primitives base
- se usan como fuente de alias

### Semantic / Color

Crear como alias a primitives:

- `color/bg/app -> color/base/canvas-sand`
- `color/bg/panel -> color/base/paper`
- `color/bg/panel-alt -> color/base/sky-surface`
- `color/bg/positive-soft -> color/base/mint-surface`
- `color/bg/negative-soft -> color/base/clay-surface`
- `color/bg/info-soft -> color/base/sky-surface`
- `color/text/primary -> color/base/ink`
- `color/text/secondary -> color/base/ink-soft`
- `color/text/inverse -> color/base/paper`
- `color/text/positive -> color/base/coal`
- `color/text/negative -> color/base/clay`
- `color/border/default -> color/base/line`
- `color/border/strong -> color/base/ink-soft`
- `color/action/primary -> color/base/coal-soft`
- `color/action/success -> color/base/coal`
- `color/action/danger -> color/base/clay`
- `color/action/secondary -> color/base/ink`
- `color/ticket/header -> color/base/navy-ticket`
- `color/ticket/gold -> color/base/gold-strong`
- `color/ticket/ball -> color/base/green-ball`

Scopes:
- fondos: `FRAME_FILL`, `SHAPE_FILL`
- texto: `TEXT_FILL`
- bordes: `STROKE_COLOR`

### Spacing

Variables:

- `space/4 = 4`
- `space/8 = 8`
- `space/12 = 12`
- `space/16 = 16`
- `space/24 = 24`

Scopes:
- `GAP`
- usar también para padding cuando Figma lo permita en componentes/layouts

### Radius

Variables:

- `radius/pos = 8`
- `radius/mobile = 10`
- `radius/full = 999`

Scopes:
- `CORNER_RADIUS`

### Size

Variables con dos modos:

- `size/action-height`
  - `mobile-standard = 36`
  - `pos-tight = 28`
- `size/bottom-nav-height`
  - `mobile-standard = 64`
  - `pos-tight = 42`
- `size/screen-padding-h`
  - `mobile-standard = 16`
  - `pos-tight = 7`
- `size/panel-radius`
  - `mobile-standard = 10`
  - `pos-tight = 8`

Scopes:
- geometry/layout

### Typography

Crear variables numéricas por tamaño base con dos modos:

- `type/headline-lg/font-size`
  - `mobile-standard = 19`
  - `pos-tight = 19`
- `type/headline-lg/line-height`
  - `mobile-standard = 24`
  - `pos-tight = 24`
- `type/headline-md/font-size`
  - `mobile-standard = 17`
  - `pos-tight = 17`
- `type/headline-md/line-height`
  - `mobile-standard = 22`
  - `pos-tight = 22`
- `type/title-lg/font-size`
  - `mobile-standard = 16`
  - `pos-tight = 16`
- `type/title-lg/line-height`
  - `mobile-standard = 21`
  - `pos-tight = 21`
- `type/title-md/font-size`
  - `mobile-standard = 16`
  - `pos-tight = 16`
- `type/title-md/line-height`
  - `mobile-standard = 20`
  - `pos-tight = 20`
- `type/title-sm/font-size`
  - `mobile-standard = 14`
  - `pos-tight = 14`
- `type/title-sm/line-height`
  - `mobile-standard = 18`
  - `pos-tight = 18`
- `type/body-lg/font-size`
  - `mobile-standard = 13`
  - `pos-tight = 13`
- `type/body-lg/line-height`
  - `mobile-standard = 18`
  - `pos-tight = 18`
- `type/body-md/font-size`
  - `mobile-standard = 13`
  - `pos-tight = 13`
- `type/body-md/line-height`
  - `mobile-standard = 18`
  - `pos-tight = 18`
- `type/body-sm/font-size`
  - `mobile-standard = 11`
  - `pos-tight = 11`
- `type/body-sm/line-height`
  - `mobile-standard = 15`
  - `pos-tight = 15`
- `type/label-lg/font-size`
  - `mobile-standard = 12`
  - `pos-tight = 12`
- `type/label-lg/line-height`
  - `mobile-standard = 16`
  - `pos-tight = 16`
- `type/label-md/font-size`
  - `mobile-standard = 11`
  - `pos-tight = 11`
- `type/label-md/line-height`
  - `mobile-standard = 14`
  - `pos-tight = 14`
- `type/label-sm/font-size`
  - `mobile-standard = 10`
  - `pos-tight = 10`
- `type/label-sm/line-height`
  - `mobile-standard = 13`
  - `pos-tight = 13`

## Text styles

Crear estos text styles encima de las variables:

- `Headline / Large / ExtraBold`
- `Headline / Medium / Bold`
- `Title / Large / ExtraBold`
- `Title / Medium / Bold`
- `Title / Small / Bold`
- `Body / Large / SemiBold`
- `Body / Medium / Regular`
- `Body / Small / Regular`
- `Label / Large / Bold`
- `Label / Medium / Bold`
- `Label / Small / Bold`

Defaults:
- familia: sans compacta
- sin serif
- sin tracking decorativo

## Code syntax mapping

Definir sintaxis de código para variables con estas reglas:

- Web:
  - `var(--lotterynet-color-bg-app)`
  - `var(--lotterynet-color-text-primary)`
  - `var(--lotterynet-space-12)`
  - `var(--lotterynet-radius-mobile)`
- Android:
  - `LotteryNetTokens.Color.BgApp`
  - `LotteryNetTokens.Space.S12`
  - `LotteryNetTokens.Radius.Mobile`
  - `LotteryNetTokens.Size.ActionHeight`

No inventar iOS en v1.

## File structure to create right after foundations

Páginas exactas:

1. `Cover`
2. `Getting Started`
3. `Foundations`
4. `---`
5. `Components / Core Chrome`
6. `Components / Sale`
7. `Components / Tickets`
8. `Components / Results`
9. `Components / Printer`
10. `Components / Forms`
11. `---`
12. `Utilities`

## Documentation blocks inside Foundations

Crear y dejar visibles:

- `Color swatches`
- `Semantic color usage`
- `Typography scale`
- `Spacing bars`
- `Radius examples`
- `Size profile comparison`

## Component build order after foundations

Orden exacto:

1. `App Top Bar`
2. `Bottom Nav`
3. `Section Header`
4. `Compact Panel`
5. `Compact Action Button`
6. `Compact Status Badge`
7. `Compact Empty State`
8. `Input Display`
9. `Keypad Key`
10. `Lottery Selector Chip`
11. `Stats Chip`
12. `Total Box`
13. `Ticket Preview Shell`
14. `Ticket Action Card`
15. `Result Ball`
16. `Result Card`
17. `Printer Setting Row`
18. `Form Row`

## Validation checklist

Phase 1 se considera terminada solo si:

- existen las 6 collections
- existen los 2 modes requeridos
- todas las semantic colors son alias
- todas las variables tienen scopes definidos
- existen los text styles listados
- existe la página `Foundations` documentada
- no hay tokens duplicados o sin naming estable

## Immediate next step

Una vez Figma quede autenticado:

1. crear variables y styles
2. validar summary de foundations
3. crear page structure
4. empezar `App Top Bar`
