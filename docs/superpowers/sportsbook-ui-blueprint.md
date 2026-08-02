# Blueprint Visual Para Deportiva

Estado: documentado solamente. No activa operacion deportiva ni cambia cuotas, tickets, pagos o funciones de servidor.

## Regla De Seguridad

- Si Deportiva esta apagada, la pantalla debe mostrar estado claro y no cargar cartelera/tickets.
- La venta deportiva sigue separada de loteria.
- Cualquier cambio operativo real requiere autorizacion separada.

## Pantalla Principal Futura

- Header: `Deportiva`.
- Card de estado: `No operativa`, `Activa`, o `Sin permiso`.
- Tabs reales:
  - `Cartelera`
  - `Ticket`
  - `Cobros`
  - `Finanza`
  - `Config` solo para master.

## Filtros

Usar sheet, no dropdown largo:

- Deporte.
- Liga.
- Mercado.
- Estado.
- Fecha.

El sheet debe tener busqueda si hay mas de 8 opciones y lista con `LazyColumn`.

## Flujo De Jugada

- Tocar partido abre sheet de mercados.
- Elegir seleccion agrega al ticket.
- Ticket muestra monto, cuota combinada y pago potencial.
- Confirmar venta debe quedar en un boton claro y con estado de servidor.

## Lo Que No Se Toca Ahora

- Odds.
- Tickets deportivos reales.
- Pagos deportivos.
- Edge Functions deportivas.
- Configuracion global real.
