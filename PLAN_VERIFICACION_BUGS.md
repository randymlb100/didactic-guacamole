# Plan de Verificación de Bugs - LotteryNet

**Fecha:** 5 de Abril, 2026  
**Estado:** En Ejecución

---

## 1. BUGS ARREGLADOS (Verificación Requerida)

### Bug #1: Barra de Menú Inferior No Aparecía (`#bnav`)
**Descripción:** La barra de navegación inferior (Vender, Tickets, Inicio, Resultados, Menu) no se mostraba.

**Causa Raíz:** 
- CSS definía `#bnav { display:none }` y `#bnav.on { display:flex }`
- JavaScript intentaba mostrarla con `.style.display='flex'` (conflicto de especificidad)
- Solución aplicada: Cambiar a `.classList.add('on')` / `.classList.remove('on')`

**Archivos Modificados:**
- `app/src/main/assets/index.html` - Líneas 5217, 5505, 12407

**Verificación Requerida:**
- [ ] Pantalla de Login: `#bnav` NO debe aparecer
- [ ] Pantalla de Vender (usuario no-master): `#bnav` DEBE aparecer con 5 botones
- [ ] Pantalla de Tickets: `#bnav` DEBE aparecer correctamente
- [ ] Pantalla de Inicio: `#bnav` DEBE aparecer correctamente
- [ ] Pantalla de Resultados: `#bnav` DEBE aparecer correctamente
- [ ] Scroll: Verificar que la barra no interfiera con el contenido
- [ ] Padding: Contenido debe tener padding-bottom para no quedar bajo la barra

---

### Bug #2: Panel de Ligadas/Números No Se Abría (`#ov-ligar`)
**Descripción:** El panel para seleccionar ligadas/números no se mostraba al hacer clic.

**Causa Raíz:**
- CSS definía `.ov { display:none }` y `.ov.on { display:flex }`
- JavaScript intentaba mostrar con `.style.display='flex'` (conflicto de especificidad)
- Solución aplicada: Cambiar a `.classList.add('on')` / `.classList.remove('on')`

**Archivos Modificados:**
- `app/src/main/assets/index.html` - Líneas 11571, 11575

**Verificación Requerida:**
- [ ] Pantalla Vender: Botón para abrir ligadas DEBE funcionar
- [ ] Panel de ligadas DEBE aparecer con overlay oscuro
- [ ] Inputs de pale y tripleta DEBEN ser visibles
- [ ] Botón "Cerrar" DEBE ocultar el panel
- [ ] Overlay DEBE permitir cerrar al hacer clic afuera
- [ ] Preview y resumen DEBEN funcionar dentro del panel

---

## 2. ELEMENTOS CON `.on` CLASS VERIFICADOS (Sin Cambios Necesarios)

✅ `.scr.on` - Pantallas (Login, Vender, etc.) - Usa classList correctamente  
✅ `#sbo.on` - Overlay de fondo del menú - Usa classList correctamente  
✅ `.ov.on` - Overlays generales - Usa classList correctamente  
✅ `.venta-lot-meta.on` - Meta de loterias en venta - Usa classList correctamente  
✅ `#ov-lots.on` - Panel de selección de loterias - Usa classList correctamente  

---

## 3. PLAN DE PRUEBAS FUNCIONALES

### A. Verificación de Navegación

**Escenario A1: Login y Acceso**
```
1. Abrir app
2. Ir a Login
3. ✓ #bnav NO debe aparecer
4. Ingresar credenciales válidas
5. ✓ #bnav DEBE aparecer después de login
```

**Escenario A2: Navegación entre pantallas**
```
1. En pantalla Vender, hacer clic en botón "Tickets" de #bnav
   ✓ Debe ir a pantalla de Tickets
   ✓ Botón "Tickets" del bnav debe estar highlighted
2. Hacer clic en "Inicio"
   ✓ Debe ir a pantalla Dashboard
3. Hacer clic en "Resultados"
   ✓ Debe ir a pantalla de Resultados
4. Hacer clic en "Menu"
   ✓ Debe abrir sidebar del menú
```

**Escenario A3: Master Admin**
```
1. Login como Master Admin
2. ✓ #bnav NO debe aparecer (solo en modo master)
3. ✓ Contenido debe ocupar pantalla completa
```

### B. Verificación de Panel de Ligadas

**Escenario B1: Abrir Panel**
```
1. En pantalla Vender
2. Hacer clic en botón para agregar ligadas/números
3. ✓ Modal #ov-ligar DEBE aparecer
4. ✓ Overlay oscuro DEBE mostrarse detrás
5. ✓ Inputs de pale y tripleta DEBEN ser visibles
```

**Escenario B2: Navegar en el Panel**
```
1. Panel abierto
2. Ingresar números
3. ✓ Vista previa debe mostrar números
4. ✓ Resumen debe actualizarse
5. ✓ Limitaciones deben validarse
```

**Escenario B3: Cerrar Panel**
```
1. Panel abierto
2. Hacer clic en botón "Cancelar"
   ✓ Panel debe cerrarse
3. Hacer clic en overlay oscuro
   ✓ Panel debe cerrarse
4. Evento ESC
   ✓ Panel debe cerrarse
```

### C. Verificación de Responsive

**Escenario C1: Mobile (Altura pequeña)**
```
1. Verificar en móvil o viewport pequeño
2. ✓ #bnav debe verse completo
3. ✓ Contenido no debe quedar tapado
4. ✓ Padding-bottom debe ser suficiente
```

**Escenario C2: Tablets**
```
1. Verificar en tablet
2. ✓ Barra de navegación debe funcionar
3. ✓ Botones deben ser clickeables
```

---

## 4. BÚSQUEDA DE BUGS ADICIONALES

### A. Elementos Similares a Revisar

- [ ] Todos los overlays dinámicos
- [ ] Todos los paneles modales
- [ ] Sidebar del menú (`#sb`, `#sbo`)
- [ ] Pantallas de carga
- [ ] Notificaciones/Toast

### B. Casos Edge

- [ ] Cambio de orientación en mobile
- [ ] App en background/foreground
- [ ] Fast navigation (cambios rápidos de pantalla)
- [ ] Redimensionamiento de ventana

### C. Problemas Potenciales Relacionados

**Problema 1: Visible pero no clickeable**
```
El elemento aparece pero clicks no funcionan
- Verificar z-index
- Verificar pointer-events
- Verificar position
```

**Problema 2: Aparece pero en posición incorrecta**
```
Elemento se muestra pero mal posicionado
- Verificar fixed vs absolute positioning
- Verificar inset propiedades
- Verificar transform
```

**Problema 3: Parpadeo al cambiar visibility**
```
Elemento parpadea al aparecer/desaparecer
- Verificar transiciones CSS
- Verificar animations
- Verificar timing
```

---

## 5. CHECKLIST DE VALIDACIÓN

### Pre-Deploy
- [ ] Todos los bugs arreglados verificados en Chrome
- [ ] Verificados en Firefox
- [ ] Verificados en Safari (si es posible)
- [ ] Verificados en mobile (Android Chrome)
- [ ] Verificados en tablet
- [ ] Sin console errors
- [ ] Sin console warnings críticos
- [ ] Performance aceptable (< 100ms reflow)

### Funcional
- [ ] Barra de menú aparece/desaparece correctamente
- [ ] Panel de ligadas abre/cierra correctamente
- [ ] Navegación fluida sin lag
- [ ] Responsive en todos los tamaños
- [ ] Accesibilidad básica OK

### Regresión
- [ ] Otras pantallas siguen funcionando
- [ ] Otros overlays siguen funcionando
- [ ] Otros menús siguen funcionando
- [ ] Performance no degradado

---

## 6. PROCEDIMIENTO DE TESTING

### Paso 1: Setup
```bash
# Construir APK o ejecutar en emulador
# Abrir Chrome DevTools si es web
```

### Paso 2: Pruebas Básicas
```javascript
// En console, verificar:
console.log(document.getElementById('bnav').classList); 
// Debe mostrar lista con 'on' si está visible

console.log(document.getElementById('ov-ligar').classList);
// Debe mostrar lista con 'on' si está visible
```

### Paso 3: Pruebas Visuales
- Navegar por cada pantalla
- Verificar que elementos esperados aparecen
- Verificar que elementos no esperados no aparecen

### Paso 4: Pruebas de Interacción
- Click en each botón
- Navegar entre pantallas
- Abrir/cerrar panels

### Paso 5: Pruebas de Rendimiento
```javascript
// En console:
performance.mark('start');
// Hacer acción
performance.mark('end');
performance.measure('action', 'start', 'end');
console.log(performance.getEntriesByName('action')[0]);
```

---

## 7. REGISTRO DE EJECUCIÓN

| Fecha | Probador | Escenario | Estado | Notas |
|-------|----------|-----------|--------|-------|
| | | | | |
| | | | | |
| | | | | |

---

## 8. PRÓXIMOS PASOS Si Se Encuentran Bugs

1. Documentar bug con detalles
2. Identificar causa raíz
3. Buscar patrones similares en código
4. Crear fix
5. Verificar no haya regresión
6. Actualizar este plan

---

**Última Actualización:** 5 de Abril, 2026  
**Responsable:** Equipo de QA  
**Estado:** En Curso ⏳
