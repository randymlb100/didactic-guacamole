# Auditoría crítica de la sección Límites

## Diagnóstico

La sección funciona y ya separa cuatro áreas visibles: pool por lotería, venta del admin, cobros/recargas y modo POS. El problema principal no es que falten campos; es que el administrador debe entender demasiados conceptos al mismo tiempo y puede confundir tres alcances distintos:

1. **Pool:** exposición compartida por banca/admin, jugada, número y lotería.
2. **Cajero:** cuánto puede vender un usuario por día y por tipo de jugada.
3. **Caja:** cuánto puede pagar o recargar una operación.

### Hallazgos concretos

- La pantalla usa un `Column.verticalScroll` con más de 15 campos y varias tarjetas extensas. Es difícil saber qué bloque afecta a qué usuario.
- `sales` mezcla el pool global y los límites base de cajero en la misma pantalla. Aunque el texto los diferencia, visualmente parecen parte del mismo formulario.
- El pool se configura por tipo de jugada (`Q`, `P`, `SP`, `T`, Pick 3 y Pick 4), pero el cálculo de venta aplica ese valor al bucket `lotería + número + jugada`. Por eso el campo no es un “límite por lotería configurable”; es un mismo tope de tipo aplicado a cada combinación de lotería y número.
- Los límites propios del admin están separados correctamente en datos, pero la pantalla los presenta junto a los límites base del cajero y puede hacer creer que el admin hereda automáticamente esos valores.
- El botón “Guardar límites” aparece dentro de `system`, pero guarda todos los bloques: pool, cajero, admin, cobro y recarga. Esto es funcionalmente válido, pero poco predecible para el usuario.
- El significado de `0` cambia según el bloque: sin tope de pool, sin límite de cajero, sin tope de pago o sin tope de recarga. Debe mostrarse siempre junto al campo, no solo en una nota general.
- Los campos permiten escribir valores, pero no muestran suficientemente el alcance efectivo después de combinar pool, cajero y ventas ya realizadas.

## Recomendación UX

Usar una pantalla de resumen y subpantallas, igual que la organización recomendada para Ajustes de Android:

```text
Límites
Resumen de reglas activas

Pool de banca                         Ver / editar
Cajeros                               Ver / editar
Límites propios del admin             Ver / editar
Cobro y recargas                      Ver / editar
Modo POS                              Ver / editar
```

Cada fila debe mostrar una segunda línea clara:

- `Pool de banca` — “Por lotería, número y jugada; compartido por todos los cajeros”.
- `Cajeros` — “Venta diaria y máximo por jugada; aplica por usuario”.
- `Admin` — “Solo cuando el admin realiza ventas”.
- `Cobro y recargas` — “Pago de premios y fondos; no limita ventas”.
- `Modo POS` — “Solo cambia la interfaz; no cambia límites”.

Dentro de cada subpantalla:

- encabezado con alcance y ejemplo;
- campos agrupados por tipo de jugada;
- estado actual y “sin límite” explícito;
- botón Guardar únicamente para ese bloque;
- confirmación si se modifica un límite que puede bloquear ventas;
- resumen “efectivo” después de guardar.

## Qué componente usar en cada caso

| Caso | Componente recomendado |
|---|---|
| Acceder a Pool/Cajeros/Caja | `ListItem` con supporting text y chevron |
| Elegir el bloque de límites | Lista de subpantallas, no cuatro botones grandes |
| Campo monetario | `OutlinedTextField` con prefijo/ayuda y validación inline |
| Activar/desactivar modo POS | `Switch` o fila de preferencia; no campo monetario |
| Confirmar cambio sensible | `AlertDialog` con alcance y ejemplo |
| Mostrar “sin límite” | `AssistChip`/estado textual, no valor vacío ambiguo |
| Muchos campos equivalentes | Lista agrupada o secciones colapsables; no una cuadrícula larga |
| Pantalla amplia | Lista-detalle; lista de categorías a la izquierda y formulario a la derecha |

Android recomienda una vista general, grupos relacionados y subpantallas para configuraciones extensas; además recomienda que la etiqueta que abre un grupo coincida con el título de la subpantalla. [Guía oficial de Settings](https://developer.android.com/design/ui/mobile/guides/patterns/settings). Para los campos y estados se pueden conservar los componentes Compose actuales, usando validación y texto de apoyo. [Text fields](https://developer.android.com/develop/ui/compose/text/user-input). Para confirmaciones sensibles, `AlertDialog` es el patrón oficial. [Dialogs](https://developer.android.com/develop/ui/compose/components/dialog).

## Ejemplo de comprensión

Si el admin define:

```text
Pool Quiniela: 10.000
Límite cajero A Quiniela: 2.000
Límite cajero B Quiniela: 10.000
```

La pantalla debe explicar:

```text
Cajero A puede vender hasta 2.000 en cada combinación válida,
pero nunca superar el pool compartido de 10.000.

Cajero B puede vender hasta 10.000 en esa combinación,
pero comparte el mismo pool y lo consume junto con A.
```

La suma efectiva debe mostrarse en el monitor o venta como “vendido / restante”, porque configurar el límite no significa que el cajero conserve siempre ese monto disponible.

## Alcance seguro para una futura implementación

- Solo reorganizar la presentación y separar subpantallas.
- Mantener `CashierSalesLimitInputs`, payloads, claves remotas y `SaleExposureEngine`.
- No cambiar la fórmula de pool ni la precedencia de límites.
- No convertir el pool en límites por lotería sin una decisión explícita de modelo de datos y servidor.
- Guardar por bloque visual, pero serializar con el mismo contrato existente.
- Agregar pruebas de contrato para alcance, `0`, precedencia y ejemplo A/B.

No se modificó código durante esta auditoría.

## Estado de implementación

La primera fase visual ya está aplicada: el Centro de límites tiene resumen y destinos independientes para Pool, Cajeros, Admin, Cobros/Recargas y POS. Los contratos remotos y el motor de exposición permanecen sin cambios. La compilación `testDebug` queda pendiente de ejecución manual.

La segunda fase visual hace visible el alcance antes de editar: `GLOBAL`,
`POR USUARIO`, `SOLO ADMIN`, `OPERACIÓN` e `INTERFAZ`. El resumen usa iconos
reales, separadores y una única superficie; el editor conserva un regreso explícito
a la vista general. Esto reduce la confusión sin cambiar `CashierSalesLimitInputs`,
las claves remotas, los payloads ni la precedencia del motor de exposición.

La tercera fase elimina la navegación con apariencia de varias ventanas. El Centro
de límites mantiene un selector `Área de límites` en la misma pantalla y reemplaza
el contenido inferior según la elección. Se eliminaron el panel intermedio de
“volver al resumen”, las explicaciones duplicadas y el menú decorativo sin acción.
El resumen continúa disponible como una opción del selector.

## Comparación con productos POS y pagos

### Shopify POS

Shopify separa los permisos por contexto y rol POS, en lugar de presentar todas las capacidades en un único formulario. Su patrón útil para LotteryNet es: categoría clara, alcance visible y permisos/controles granulares. [Shopify POS permissions](https://help.shopify.com/en/manual/your-account/users/roles/permissions/pos-permissions).

### Toast POS

Toast organiza los permisos por roles de trabajo y evita entregar a un usuario más control del que tiene el administrador. El principio aplicable es que una persona edita únicamente las capacidades que ella misma puede administrar. [Toast permissions reference](https://support.toasttab.com/en/article/Access-Permissions-Reference).

### Stripe Dashboard

Stripe separa resumen financiero, balance, transacciones y permisos del equipo. Para Límites, el patrón útil es mostrar primero un resumen de estado y abrir el detalle solo cuando se va a editar; no mezclar balance, cobros y permisos en la misma tarjeta. [Stripe Dashboard](https://docs.stripe.com/dashboard/basics), [Stripe team roles](https://docs.stripe.com/get-started/account/teams/roles).

### Patrón profesional común

Los productos maduros repiten estas reglas:

- resumen arriba, edición abajo;
- un bloque por alcance, no por tipo de campo;
- permisos y límites separados;
- estado efectivo visible antes de editar;
- acciones sensibles con confirmación;
- guardado explícito del bloque que se está editando;
- lenguaje de negocio, no claves técnicas como `pool`, `defaults` o `byUser` sin explicación.

### Recomendación final para LotteryNet

La versión profesional debe llamarse **Centro de límites** y tener cinco destinos:

1. **Pool de banca** — exposición compartida por lotería, número y jugada.
2. **Límites de cajeros** — venta diaria y límites por jugada para cada usuario.
3. **Límite propio del admin** — solo las ventas hechas por el admin.
4. **Cobros y recargas** — pagos de premios y fondos.
5. **Modo POS** — presentación compacta, sin afectar reglas de venta.

No recomiendo agregar una pantalla “avanzada” con más campos todavía. La mejora es de jerarquía, alcance y explicación; la lógica existente ya contiene la separación técnica necesaria.
