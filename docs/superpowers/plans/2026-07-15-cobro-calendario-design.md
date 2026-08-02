# Cobro por fecha exacta

**Objetivo:** agregar calendario a la sección de cobro sin cambiar la lógica actual de premios, pagos, límites ni sincronización.

**Regla:** `Hoy`, `Ayer` y `Todos` conservan su comportamiento actual. La nueva opción `Fecha exacta` comparará la fecha seleccionada contra la misma fecha de venta (`createdAtEpochMs`) que hoy usa correctamente el filtro diario.

**Cambios:**

- `TicketLookupActivity.kt`: estado del día exacto, `DatePickerDialog` Material 3 y opción visual `Fecha exacta`.
- `filterTicketLookupPaymentView`: reconocer el valor `date:yyyy-MM-dd`.
- `TicketLookupContractsTest.kt`: probar fecha exacta, pendientes/pagados y búsqueda por texto.
- `tools/qa/ticket-payout-date-contract.node.test.mjs`: smoke test explícito para asegurar que el calendario se limite al modo cobro y preserve los presets.

**No se modifica:** backend de pago, cálculo de premios, `Paga todo`, límites del cajero, sincronización, ventas o navegación.
