# Deportes - Revision de Figma Make

Fecha: 2026-06-04

Archivo revisado:

- Figma Make: `https://www.figma.com/make/3wQ0Y6dAWC1tkav0vUBWXy/Secci%C3%B3n-deportiva-para-app`

## Estado de acceso

El archivo es Figma Make, no Figma Design normal.

El MCP pudo leer el codigo fuente del Make, pero `get_libraries` no soporta archivos Make. La cuenta conectada aparece con seat `View`, asi que para escribir pantallas reales dentro de un archivo Figma Design se necesita un archivo `/design/...` con permiso de edicion.

## Archivos de Figma Make leidos

- `src/app/App.tsx`
- `src/app/components/EventCard.tsx`
- `src/app/components/BetSlip.tsx`
- `src/app/components/SportsNav.tsx`
- `src/app/components/Header.tsx`
- `src/styles/theme.css`

## Lo bueno que se puede aprovechar

- La estructura basica de deportes esta clara:
  - header;
  - filtros por deporte;
  - lista de eventos;
  - card de juego;
  - seleccion de cuota;
  - ticket/boleta.
- `EventCard` separa liga, hora, equipos y cuotas.
- `BetSlip` muestra monto, cantidad de selecciones, cuota total y premio posible.
- `SportsNav` usa chips horizontales, que encajan bien con Android Compose.

## Lo que no debe copiarse directo

### 1. Es web, no POS Android

El layout usa grid desktop (`lg:grid-cols-3`) y boleto lateral sticky. En un telefono POS eso queda apretado.

Adaptacion recomendada:

- Lista de juegos en una sola columna.
- Al tocar un juego, abrir `ModalBottomSheet`.
- El ticket queda como barra/resumen inferior o panel dentro de la pantalla, no como sidebar.

### 2. Usa datos falsos

El Make usa equipos como Real Madrid, Barcelona, Lakers, Nadal y odds hardcoded.

Adaptacion recomendada:

- UI debe leer solo `sports-get-board`.
- No inventar deportes ni ligas.
- Baseball primero, con ligas descubiertas desde `sports-sync-odds`.
- Logos vienen de `sports_team_assets`, no de emoji.

### 3. Mezcla muchos deportes al inicio

Tiene futbol, basket y tenis. Para prueba real con cuota de 1000 requests conviene no abrir todo.

Adaptacion recomendada:

- Mostrar primero `Baseball`.
- Otros deportes pueden salir como tabs bloqueados o desactivados si no hay datos.
- El filtro `Todos` no debe disparar sincronizacion externa.

### 4. El ticket no esta pensado para impresion/WhatsApp

`BetSlip` esta bien para una web, pero no para ticket oficial, termico o WhatsApp.

Adaptacion recomendada:

- Ticket oficial deportivo debe mostrar:
  - codigo;
  - banca/vendedor;
  - deporte/liga;
  - juego;
  - mercado;
  - seleccion;
  - cuota congelada;
  - monto;
  - posible premio;
  - estado.
- Ticket termico debe ser compacto como loteria, pero con columnas claras.
- WhatsApp debe usar plantilla propia, no screenshot de pantalla completa.

### 5. Falta control de estados reales

El Make solo maneja `live` true/false.

Adaptacion recomendada:

- Estados minimos:
  - `Abierto`;
  - `Cerrado`;
  - `Iniciado`;
  - `Suspendido`;
  - `Sin cuota`;
  - `Prueba`.

## Traduccion a Compose

Mapeo recomendado:

- `SportsNav.tsx` -> chips/filtros en `SportsbookActivity.kt`.
- `EventCard.tsx` -> `SportsbookGameRow`.
- `OddsButton` -> `SportsbookOddChip`.
- `BetSlip.tsx` -> `SportsbookTicketPreview`.
- Evento seleccionado -> `SportsbookGameSheet`.
- Header web -> no copiar; usar header actual de LotteryNet.

## Diseno recomendado para la app

### Pantalla Juegos

- Header compacto: `Deportes`, badge `Prueba`, fecha.
- Fila de filtros:
  - Deporte;
  - Liga;
  - Estado;
  - Fecha.
- Cards de juegos:
  - logo/inicial equipo local;
  - equipo local;
  - hora;
  - equipo visitante;
  - logo/inicial visitante;
  - badge estado;
  - primeras cuotas visibles: ML, Runline, Total.

### Bottom sheet de juego

- Titulo: equipos + liga + hora.
- Secciones por mercado:
  - Moneyline;
  - Runline;
  - Total;
  - F5 si viene del proveedor.
- Cada cuota como chip grande.
- Monto y premio posible.
- Boton `Agregar`.

### Ticket deportivo

- Resumen fijo:
  - selecciones;
  - monto total;
  - cuota total;
  - premio posible.
- Acciones:
  - `Vender`;
  - `Imprimir`;
  - `WhatsApp`.

## Pendiente para Figma real

Para crear o modificar pantalla en Figma necesito un archivo Figma Design editable, no solo Make:

```text
https://www.figma.com/design/...
```

Permiso recomendado:

- `can edit`, si quieres que cree pantallas.
- `can view`, si solo quieres que revise.

Con ese archivo se puede crear:

- pantalla `Deportes - Juegos`;
- bottom sheet `Detalle de juego`;
- ticket oficial deportivo;
- ticket WhatsApp deportivo;
- version POS pequena.
