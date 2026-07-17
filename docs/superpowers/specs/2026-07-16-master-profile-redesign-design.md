# Perfil Master: diseño de administración y fondos

## Objetivo

Reorganizar el Perfil Master para que bancas, cajeros, credenciales, servidor, fondos de recargas y auditoría sean comprensibles y adaptables a teléfono, POS y pantallas grandes, corrigiendo la separación entre fondo asignado y saldo disponible sin cambiar ventas, tickets, límites ni sincronización existente.

## Reglas de negocio preservadas

- `recargasAssignedBalance` representa el fondo asignado por Master.
- `recargasBalance` representa el saldo disponible real.
- Un consumo modifica únicamente `recargasBalance`.
- Un reemplazo explícito de fondo puede modificar ambos valores.
- El cambio de día no reinicia automáticamente el saldo.
- La pantalla no escribe payloads por recomposición o refresco visual.
- Una operación se muestra confirmada solo después de confirmación remota.
- Si falla la confirmación remota, se restaura el estado anterior.

## Arquitectura de pantalla

El Perfil Master tendrá seis destinos: Resumen, Bancas, Recargas, Seguridad, Servidor y Auditoría. En teléfono se usará navegación compacta; en pantallas medianas o grandes, navegación lateral. Las tarjetas agruparán una sola responsabilidad y las operaciones sensibles usarán diálogo de confirmación.

Cada operación será un evento explícito del estado de pantalla. El modelo de usuario seguirá siendo la fuente de datos; el estado visual no podrá reutilizar el saldo disponible como borrador de fondo asignado.

## Diseño de fondos

La tarjeta de fondo mostrará Fondo asignado, Saldo disponible, Consumido y última actualización. Las acciones serán separadas: Reemplazar fondo, Agregar saldo, Bloquear recargas y Ver movimientos. Reemplazar fondo será la única acción que reinicie el saldo disponible al monto nuevo.

## Accesibilidad y adaptación

Se usarán tokens de `MaterialTheme`, componentes Material 3, estados de foco, etiquetas descriptivas y layouts adaptativos. No se dependerá de tamaños fijos ni de una sola orientación. Los botones sensibles explicarán su efecto antes de confirmar.

## Validación

Se cubrirán pruebas de contrato de payload, persistencia local, confirmación remota, rollback, consumo parcial, reapertura de pantalla, cambio de día, sincronización lenta, dos administradores con fondos distintos y vistas compactas para POS.

## Referencias oficiales

- Android Compose: state hoisting: https://developer.android.com/develop/ui/compose/state-hoisting
- Android Compose: layouts adaptativos: https://developer.android.com/develop/ui/compose/build-adaptive-apps
- Android Compose Material 3: https://developer.android.com/develop/ui/compose/designsystems/material3
- Material 3 Cards: https://m3.material.io/components/cards
- Material 3 Dialogs: https://m3.material.io/components/dialogs
- Material 3 Navigation Rail: https://m3.material.io/components/navigation-rail/overview
