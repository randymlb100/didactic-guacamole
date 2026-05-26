# LotteryNet Android Figma Screen Spec v1

## Core screens first

### 1. Login

Objetivo:
- acceso directo
- sin ruido técnico
- sin conteos internos

Bloques:
- `App Top Bar` sin subtitle pesado
- `Brand mark`
- `Username`
- `Password`
- `Remember row`
- `Inline status`
- `Entrar`
- acción secundaria web discreta

### 2. Shell / Menu

Objetivo:
- navegación principal por tareas
- menos cards repetidas

Bloques:
- `App Top Bar`
- resumen corto
- shortcuts por rol
- accesos recientes
- `Bottom Nav`

### 3. Venta

Objetivo:
- pantalla ancla
- compacta
- rápida para cajero

Bloques:
- `Stage header row`
- `Staged list`
- `Lottery meta block`
- `Input displays`
- `Mode tabs`
- `Total box`
- `Keypad`
- `Bottom Nav`

Estados obligatorios:
- `Sin jugadas`
- `Con jugadas`
- `Pale`
- `Super Pale`
- `Print menu`
- `Lottery picker open`

### 4. Print Delivery Menu

Objetivo:
- salir desde venta con preview real
- entregar o imprimir sin editor innecesario

Bloques:
- `Ticket preview shell`
- acciones:
  - `Térmica`
  - `WhatsApp`
  - `Compartir`
  - `Ticket oficial`

### 5. Ticket Oficial

Objetivo:
- hub de entrega y operación

Bloques:
- `Top summary`
- `Ticket preview`
- `Action groups`
- `Status badges`
- `Meta cards`

Secciones:
- `Impresión y envío`
- `Operación`
- `Secundarias`

## Extended screens

### Resultados

Bloques:
- `Date chips`
- `Date navigator`
- `Action row`
- `Result cards`

### Usuarios

Bloques:
- tabs o agrupación de cuentas
- formulario de configuración
- badges de estado

### Impresora térmica

Bloques:
- una sola columna
- `Resumen`
- `Conexión y estado`
- `Prueba`
- `Vista previa`
- `Acciones`

### Tickets

Pantallas:
- `Ticket Lookup`
- `Ticket Summary`
- `Ticket Detail`

Regla:
- mismo lenguaje visual de `Ticket Oficial`

## Cross-screen rules

- base clara
- sin dark mode en v1
- sin hero sections
- sin dashboards SaaS
- sin badges gigantes
- sin sombras pesadas
- color solo para estado o prioridad
- compactación fuerte en `pos-tight`

## Acceptance

El paquete visual queda listo si:

- `Venta` y `Ticket Oficial` se ven del mismo sistema
- `Print menu` usa preview real
- `Resultados` deja de verse técnico
- `Impresora` y `Usuarios` ya no parecen subapps aparte
- cada pantalla puede implementarse en Kotlin sin decidir layout desde cero
