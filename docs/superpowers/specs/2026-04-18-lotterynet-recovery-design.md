# LotteryNet Recovery Design

## Objective

Restaurar una sola app LotteryNet usable en Android POS aunque parte del nativo siga inestable. La prioridad es acceso seguro por rol, navegación consistente y un `master` que funcione como centro de recuperación.

## Navigation Model

- `LoginActivity` ya no debe abrir pantallas nativas frágiles de forma directa.
- Todos los roles entran primero a `ShellActivity` en modo seguro.
- `Venta` y `Resultados` usan la ruta web estable dentro de `MainActivity` hasta que el nativo quede comprobado.
- `MASTER` permanece en shell nativo y desde ahí abre paneles master o `Panel web`.

## Screen Ownership

- `ShellActivity`: hub primario de recuperación y menú compacto por rol.
- `MainActivity`: consola web/legado explícita, nunca un destino accidental.
- `SalesActivity` y `ResultsActivity`: quedan blindadas, pero no son la ruta primaria mientras sigan generando cierres.
- `MasterDashboardActivity` y `MasterCreateBankActivity`: deben tener fallback al shell en cualquier fallo.

## Information Architecture

### Master

- `Operación master`
- `Compatibilidad`
- `Sesión`

### Admin/Cashier

- `Operación hoy`
- `Gestión`
- `Compatibilidad`

## Naming Rules

- `Abrir legado` pasa a `Panel web`
- `Soporte` no se usa como categoría ambigua
- los accesos de compatibilidad deben explicar que abren el modo web estable

## Figma Follow-Up

Cuando la recuperación funcional esté estable, Figma se usará para:

- compactar el shell móvil
- definir jerarquía visual del menú
- separar acciones primarias de compatibilidad
- dejar el flujo `master` legible en una sola vista
