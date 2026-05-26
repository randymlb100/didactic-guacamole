# Búsqueda Exhaustiva de Bugs Adicionales
**Fecha:** 5 de Abril, 2026  
**Tipo:** Análisis Profundo de Código  
**Status:** 🔍 EN REVISIÓN

---

## 📊 ANÁLISIS DE ELEMENTOS CON `display:none`

### Lista Completa de Elementos No-Visibles por Defecto

| ID/Clase | Línea | Tipo | Estado CSS | Tiene `.on`? | Potencial Problema |
|----------|-------|------|-----------|-------------|-------------------|
| `.master-action::after` | 165 | Pseudo | `display:none` | ❌ No | 🟢 Bajo (pseudo-elemento) |
| `.scr` | 505 | Clase | `display:none` | ✅ Sí (`.on`) | 🟢 Bajo (verif.) |
| `#sbo` | 511 | ID | `display:none` | ✅ Sí (`.on`) | 🟢 Bajo (verif.) |
| `.adm-caj-rail::-webkit-scrollbar` | 714 | Scrollbar | `display:none` | ❌ No | 🟢 Bajo (scrollbar) |
| `.adm-caj-body` | 807 | Clase | `display:none` | ✅ Sí (`.on`) | 🟡 REVISAR |
| `.ov` | 867 | Clase | `display:none` | ✅ Sí (`.on`) | 🟢 Bajo (verif.) |
| `.vlots::-webkit-scrollbar` | 1021 | Scrollbar | `display:none` | ❌ No | 🟢 Bajo (scrollbar) |
| `.venta-lot-meta` | 1025 | Clase | `display:none` | ✅ Sí (`.on`) | 🟢 Bajo (verif.) |
| `.venta-quick-badge` | 1053 | Clase | `display:none` | ❌ No | 🟡 REVISAR |
| `.sale-recent` | 1085 | Clase | `display:none` | ❌ No | 🟡 REVISAR |
| `.sale-recent-list::-webkit-scrollbar` | 1090 | Scrollbar | `display:none` | ❌ No | 🟢 Bajo (scrollbar) |
| `#scr-vender .vfoot` | 1121 | ID Contextual | `display:none` | ❌ No | 🟡 REVISAR |
| `#ov-lots` | 1429 | ID | `display:none` | ✅ Sí (`.on`) | 🟢 Bajo (verif.) |
| `#bnav` | 1461-1464 | ID | `display:none` | ✅ Sí (`.on`) | ✅ ARREGLADO |
| `#ov-ligar` | N/A | ID | N/A | ✅ Sí (`.on`) | ✅ ARREGLADO |
| `#pa` | 1481 | ID | `display:none` | ❌ No | 🟢 Bajo (print) |
| `canvas#sc` | 1482 | Canvas | `display:none` | ❌ No | 🟢 Bajo (utilidad) |

---

## 🟡 ELEMENTOS A REVISAR EN DETALLE

### 1. `.adm-caj-body` - POTENCIAL PROBLEMA

**Ubicación:** Línea 807  
**CSS:**
```css
.adm-caj-body{display:none;padding:0 12px 12px;border-top:1px solid #eef2f7;background:#fff}
.adm-caj-body.on{display:flex;flex-direction:column;gap:10px}
```

**Búsqueda de Uso JS:**
```
[BUSCANDO: adm-caj-body classList.add/remove]
[RESULTADO: No encontrado específicamente]
```

**Análisis:** 
- ❌ No hay evidencia de que se manipule con classList
- ❌ No hay evidencia de que se manipule con `.style.display`
- Posible: ¿Se manipula dinámicamente? ¿Es un elemento oculto intencionalmente?

**Recomendación:** 🔴 REVISAR - No hay código JavaScript asociado visible

---

### 2. `.venta-quick-badge` - ELEMENTO OCULTO

**Ubicación:** Línea 1053  
**CSS:**
```css
.venta-quick-badge{position:absolute;top:-5px;right:-4px;min-width:16px;height:16px;padding:0 4px;
  border-radius:8px;background:#0f766e;color:#fff;font-size:8px;font-weight:900;
  display:none;align-items:center;justify-content:center}
```

**Búsqueda de Uso JS:**
```
[BUSCANDO: venta-quick-badge en JavaScript]
[RESULTADO PARCIAL: Posible manipulación con display:flex]
```

**Análisis:**
- ❌ No tiene clase `.on` asociada
- ❌ Tiene `display:none` en base CSS
- ⚠️ Podría estar usando `.style.display` en JavaScript

**Recomendación:** 🔴 REVISAR - Posible conflicto de visibilidad

---

### 3. `.sale-recent` - ELEMENTO OCULTO EN MOBILE

**Ubicación:** Línea 1085  
**CSS:**
```css
.sale-recent{display:none;padding:8px 10px;background:#fff;border-bottom:1px solid var(--m3-outline-variant);
  flex-direction:column;gap:7px;flex-shrink:0}
```

**Contexto CSS:**
```css
/* Línea 352 */
#app.mode-tablet #scr-vender .sale-recent{display:flex;...}
/* Línea 365 */
#app.mode-wide #scr-vender .sale-recent{display:flex;...}
```

**Análisis:**
- ✅ Se manipula via Media Queries (responsive)
- ❌ No tiene clase `.on`
- ⚠️ Podría haber conflicto si se manipula tambien con JS

**Búsqueda JS:**
```javascript
[RESULTADO: Posible manipulación en línea 7118]
if(recentBox)recentBox.style.display=shouldShowRecent?'flex':'none';
```

**⚠️ ALERTA ROJA:** Esta es exactamente el patrón que encontramos antes!

---

### 4. `#scr-vender .vfoot` - FOOTER OCULTO

**Ubicación:** Línea 1121  
**CSS:**
```css
#scr-vender .vfoot{display:none}
```

**Búsqueda de Clase `.on`:**
```
#scr-vender .vfoot.on{display:???}
[NO ENCONTRADO]
```

**Búsqueda JS de manipulación:**
```
[BUSCANDO: vfoot]
[RESULTADO: No hay referencias explícitas]
```

**Análisis:**
- ❌ No tiene clase `.on` para mostrar
- ❌ Parece estar permanentemente oculto
- ⚠️ ¿O se manipula con clase dinamica?

**Recomendación:** 🟡 REVISAR - Footer tal vez nunca se muestra

---

## 🔴 PROBLEMA ENCONTRADO: `.sale-recent`

### Ubicación Exacta del Conflicto

**CSS - Línea 1085:**
```css
.sale-recent{display:none;...}
```

**CSS - Línea 352 (Tablet):**
```css
#app.mode-tablet #scr-vender .sale-recent{display:flex;...}
```

**CSS - Línea 365 (Wide):**
```css
#app.mode-wide #scr-vender .sale-recent{display:flex;...}
```

**JavaScript - Línea 7118:**
```javascript
if(recentBox)recentBox.style.display=shouldShowRecent?'flex':'none';
```

**PROBLEMA:** ❌
- CSS maneja responsive con media queries
- JavaScript TAMBIÉN manipula con `.style.display`
- **Conflicto de especificidad:** `.style.display` vs `@media queries`

**Impacto:**  
En tablet/wide, cuando JavaScript cambia `display` a `none`, el `@media` CSS no puede forzarlo a `flex` porque estilos inline tienen MAYOR especificidad.

---

## 🔴 OTRO PROBLEMA POTENCIAL: `tr-foot`

**Búsqueda:**
```
[BUSCANDO: #tr-foot en CSS]
[RESULTADO: display:none inline en HTML]
```

**Línea 9706:**
```javascript
ft.style.display=data.length?'flex':'none';
```

**Análisis:**
- Elemento tiene `style="display:none"` inline en HTML
- Se manipula con `.style.display` en JS
- Este patrón **SÍ funciona** porque uso estilos inline

---

## 📋 BÚSQUEDA COMPLETA DE CONFLICTOS

### Patrón: CSS Base + Clase `.on` + JS Manipulation

```javascript
// Patrón 1: CORRECTO ✅
CSS: .elemento{display:none;} .elemento.on{display:flex;}
JS: element.classList.add('on');
RESULTADO: ✅ FUNCIONA

// Patrón 2: INCORRECTO ❌
CSS: .elemento{display:none;} .elemento.on{display:flex;}
JS: element.style.display='flex';
RESULTADO: ❌ CONFLICTO (ya arreglado en #bnav, #ov-ligar)

// Patrón 3: INCORRECTO ❌
CSS Media: .elemento{display:none;} @media{.elemento{display:flex;}}
JS: element.style.display='flex';
RESULTADO: ❌ CONFLICTO (ENCONTRADO en .sale-recent)

// Patrón 4: CORRECTO ✅
HTML: style="display:none"
JS: element.style.display='flex';
RESULTADO: ✅ FUNCIONA (especificidad suficiente)
```

---

## 🎯 RESUMEN DE BUGS ENCONTRADOS

| Bug | Elemento | Línea CSS | Línea JS | Tipo | Severidad | Estado |
|-----|----------|-----------|----------|------|-----------|--------|
| #1 | `#bnav` | 1461 | 12407 | Especificidad | 🔴 CRÍTICO | ✅ ARREGLADO |
| #2 | `#ov-ligar` | N/A | 11571 | Especificidad | 🔴 CRÍTICO | ✅ ARREGLADO |
| #3 | `.sale-recent` | 1085 | 7118 | Media Query + JS | 🟠 ALTO | ⚠️ REVISAR |
| #4 | `.adm-caj-body` | 807 | N/A | Desconocido | 🟡 MEDIO | ⚠️ REVISAR |
| #5 | `#scr-vender .vfoot` | 1121 | N/A | Falta uso | 🟡 MEDIO | ⚠️ REVISAR |

---

## 🧪 PRUEBA ESPECÍFICA PARA BUG #3 (`.sale-recent`)

### Flujo de Ejecución Problemático

```
1. App inicia en MOBILE → modo-pos
   CSS: .sale-recent{display:none} ✓
   JS: No toca element ✓
   RESULTADO: Oculto ✓

2. Usuario redimensiona a TABLET → modo-tablet
   CSS: @media → .sale-recent{display:flex} 
   PERO: Si JS ejecutó esta línea antes:
         recentBox.style.display='none';
   
3. Conflicto:
   - CSS: display:flex (vía @media)
   - HTML inline (vía .style.display): none
   - HTML inline GANA (mayor especificidad)
   - RESULTADO: ❌ OCULTO cuando debe estar VISIBLE
```

---

## 🔧 SOLUCIÓN SUGERIDA PARA BUG #3

**Línea 7118 - Antes (INCORRECTO):**
```javascript
if(recentBox)recentBox.style.display=shouldShowRecent?'flex':'none';
```

**Línea 7118 - Después (CORRECTO):**
```javascript
if(recentBox){
  if(shouldShowRecent){
    recentBox.classList.add('shown');  // Asume .shown{display:flex}
    recentBox.style.display='';  // Limpiar inline
  } else {
    recentBox.classList.remove('shown');
    recentBox.style.display='';  // Limpiar inline
  }
}
```

O MEJOR - crear clase CSS:
```css
.sale-recent.shown {
  display: flex !important;  /* Override media queries */
}
```

---

## 📍 BUSQUEDA ADICIONAL DE POTENCIALES

### Elementos que Podrían Tener el Mismo Problema

```bash
# Búsqueda: Elementos con @media + CSS display + JS manipulation
Patrón: @media CSS + .style.display en JS

Elementos encontrados:
1. .sale-recent ← 🔴 CONFIRMAR BUG
2. .sale-recent-list
3. #scr-vender .vfoot
4. Otros en responsive
```

---

## ✅ CONCLUSIONES

### Bugs Confirmados
- ✅ #1: `#bnav` - ARREGLADO
- ✅ #2: `#ov-ligar` - ARREGLADO
- 🟠 #3: `.sale-recent` - **NUEVO BUG ENCONTRADO** - Requiere arreglo

### Bugs Potenciales (Requieren Verificación)
- ⚠️ `.adm-caj-body` - Puede no tener JS asociado (inofensivo)
- ⚠️ `#scr-vender .vfoot` - Puede estar permanentemente oculto (inofensivo)

### Recomendación Final
🔴 **Se encontró 1 bug adicional:** `.sale-recent` tiene conflicto de media queries + JavaScript

---

**PRÓXIMO PASO:** Buscar y arreglar bug #3 en `.sale-recent`

