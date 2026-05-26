# LotteryNet Colorido Pro

Base visual para llevar la app a Figma sin inventar un sistema aparte.

## Foundations

### Color
- `Venta`: verde operativo `#0E9B6C` / superficie `#E8F8F1`
- `Tickets`: azul profundo `#2154D6` / superficie `#E8F0FF`
- `Resultados`: ámbar `#C6891A` / superficie `#FFF5DA`
- `Cuadre`: verde control `#118263` / superficie `#E4F7F0`
- `Recargas`: coral `#E86F2E` / superficie `#FFEEE3`
- `Admin`: navy `#21427A` / superficie `#E9F0FF`
- `Printer`: ink `#162235` / superficie `#EDF3FA`
- `Chrome`: navy `#17345C`
- `Ink`: `#102033`
- `Muted`: `#55657A`
- `Border`: `#D2D9E4`
- `Background`: `#F6F5EF`

### Tipografía
- Dato operativo: peso `Bold` o `SemiBold`
- Texto secundario: mínimo, nunca protagonista
- Títulos de sección: cortos, sin copy decorativa

### Spacing y forma
- Radios moderados: 8-10 dp
- Una superficie principal por pantalla
- Botones compactos, sin pills gigantes ni bloques inflados

## Componentes

### Shared code source
- `app/src/main/java/com/lotterynet/pro/ui/common/NativeChrome.kt`
- `app/src/main/java/com/lotterynet/pro/ui/theme/Color.kt`
- `app/src/main/java/com/lotterynet/pro/ui/theme/Theme.kt`

### Componentes a representar en Figma
- `AppTopBar`
- `BottomNavBar`
- `CompactActionButton`
- `CompactPanel`
- `CompactStatusBadge`
- `MetricStrip`
- `CompactRecordRow`
- `CompactKeyValueRow`
- `Keypad POS`

### Regla visual
- color por intención, no por decoración
- paneles claros con contraste útil
- no repetir tarjetas blancas sin identidad
- no usar gradientes suaves para “parecer premium”

## Flows

Pantallas base para maquetar primero en Figma:
- `Venta`
- `Resultados`
- `Ticket oficial`
- `Recargas`
- `Cuadre`
- `AdminDashboard`
- `Printer`

## Mapeo con la app

### Venta
- protagonista: lotería activa, entrada, total, keypad
- color principal: `sale`

### Resultados
- protagonista: sorteos y estado de publicación
- color principal: `results`

### Ticket
- protagonista: ticket visual, serial, total, estado
- color principal: `tickets`

### Recargas
- protagonista: proveedor y acción de recarga
- color principal: `recharge`

### Cuadre
- protagonista: métricas, periodos y salida del reporte
- color principal: `finance`

### Admin
- protagonista: resumen operativo y accesos críticos
- color principal: `admin`

### Printer
- protagonista: conexión, preview y salida térmica
- color principal: `printer`
