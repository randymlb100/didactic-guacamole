# LotteryNet Admin UX Implementation Rules

Fecha: 2026-06-01

## Objetivo

Cada funcion nueva del panel web debe sentirse como una app administrativa moderna, no como una pagina con informacion pegada. La interfaz debe ayudar al usuario a decidir, editar, guardar y confirmar sin perder contexto.

## Referencias Usadas

- Apple Human Interface Guidelines - Motion: usar movimiento con proposito, breve, cancelable y respetando reduccion de movimiento.
- Material Design - Bottom sheets: usar sheets para acciones contextuales iniciadas por el usuario; en desktop preferir superficies cerca del punto de accion cuando el sheet aleja demasiado la atencion.
- WCAG 2.2: labels visibles, errores identificables, foco visible, estados y mensajes accesibles.
- `ui-ux-pro-max`: dashboard operacional/fintech, denso pero escaneable, estados de carga, botones con loading, validacion en blur, foco visible, movimiento 150-300ms y `prefers-reduced-motion`.

Fuentes:

- https://developer.apple.com/design/human-interface-guidelines/motion
- https://m1.material.io/components/bottom-sheets.html
- https://www.w3.org/TR/WCAG22/

## Principios Para Este Proyecto

### 1. Accion Cerca Del Campo

Si se agrega una configuracion, debe traer su accion principal al lado o dentro del mismo bloque.

Ejemplos obligatorios:

- Campo `Comision cajero (%)` debe tener boton `Guardar comision` o guardarse desde el mismo panel con estado `Sincronizando`.
- Campo `Limite por recarga` debe mostrar si esta sincronizado con Android/Supabase.
- Cambios MASTER deben mostrar de inmediato si se guardaron en `lotterynet_master_state`.

No hacer:

- Crear muchos inputs y dejar un unico boton escondido al final de una pagina larga.
- Depender de que el usuario pregunte donde guardar.

### 2. Jerarquia Visual Operativa

Cada seccion administrativa debe tener:

- titulo claro,
- estado actual,
- ultima sincronizacion o fuente de dato cuando aplique,
- accion primaria visible,
- accion secundaria menos prominente,
- feedback de exito/error en el mismo bloque.

Patron recomendado:

```text
[Titulo de seccion]        [Estado: Sincronizado]
Descripcion corta

Dato actual / resumen / impacto

[Control principal] [Guardar / Sincronizar]
Mensaje de validacion o exito
```

### 3. Paneles, Modales Y Sheets

Usar segun contexto:

- **Inline panel:** configuracion frecuente o que debe verse mientras se compara informacion.
- **Side panel / drawer:** editar una banca, cajero o supervisor sin salir de la lista.
- **Modal:** confirmaciones destructivas como eliminar, bloquear o resetear clave.
- **Bottom sheet:** solo en pantallas pequenas o acciones contextuales simples. En desktop, preferir drawer o inline expansion para no separar accion y contexto.

Reglas:

- Todo modal/sheet debe tener cerrar visible.
- Si hay cambios sin guardar, confirmar antes de cerrar.
- El foco debe ir al primer campo o al titulo del modal.
- Escape/click fuera debe cerrar solo cuando no haya cambios sin guardar.

### 4. Estados Inteligentes

Cada accion remota debe tener:

- loading en el boton,
- boton deshabilitado durante guardado,
- exito visible,
- error con causa y proximo paso,
- reintento cuando aplique.

Texto recomendado:

- Guardando: `Sincronizando...`
- Exito: `Sincronizado con Android`
- Error: `No se pudo sincronizar. Revisa conexion e intenta de nuevo.`

### 5. Formularios De Administracion

Campos nuevos deben cumplir:

- label visible, nunca solo placeholder,
- helper text cuando el campo afecta ventas reales,
- validacion en blur y antes de guardar,
- error debajo del campo,
- `type="number"` para montos/porcentajes,
- rango visible para porcentajes: `0-100%`,
- montos con unidad clara: `RD$`.

Ejemplo para comision:

```text
Comision cajero (%)
Define cuanto se descuenta en cuadre y reportes.
[ 8.0 ] [Guardar comision]
```

### 6. Sincronizacion Web + Android

Toda configuracion operacional debe indicar fuente:

- `Fuente: lotterynet_master_state`
- `Aplicara en Android al refrescar/sincronizar`
- `Ultimo cambio: fecha/hora` cuando exista dato.

Esto aplica a:

- limites,
- premios,
- modos normal/Pick,
- loterias deshabilitadas,
- recargas,
- sportsbook,
- comisiones.

### 7. Movimiento Y Transiciones

Usar movimiento solo para explicar cambio de estado:

- entrada de panel: opacity + translateY/translateX corto,
- modal/drawer: 180-240ms,
- guardado exitoso: flash sutil del borde o check,
- listas: evitar animaciones largas por fila.

CSS obligatorio:

```css
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
```

### 8. Layout App, No Landing Page

El panel debe priorizar flujo de trabajo:

- sidebar estable,
- header compacto,
- secciones con tabs o segment controls,
- tabla/lista + panel de detalle,
- filtros arriba de listas,
- accion primaria fija dentro del panel activo,
- densidad media/alta pero legible.

Evitar:

- bloques grandes tipo landing,
- tarjetas repetidas sin accion,
- texto explicativo largo,
- metricas sin accion,
- decoracion que compita con datos.

### 9. Light/Dark Mode

El proyecto puede tener claro/oscuro, pero ambos deben compartir tokens.

Reglas:

- contraste 4.5:1 para texto normal,
- bordes visibles en ambos modos,
- estados focus/hover/disabled definidos,
- no usar solo color para estado: acompanar con texto o icono.

### 10. Checklist Antes De Entregar Una Pantalla

- Hay una accion principal clara.
- Cada cambio editable tiene guardado cercano.
- El usuario ve si se sincronizo.
- Los errores aparecen junto al campo o bloque afectado.
- No hay controles sin feedback.
- MASTER, ADMIN y SUPERVISOR no ven acciones fuera de rol.
- Funciona en ancho actual del browser y en movil.
- Respeta reduccion de movimiento.
- No hay scroll horizontal.
- La fuente de dato coincide con Android/Supabase.

