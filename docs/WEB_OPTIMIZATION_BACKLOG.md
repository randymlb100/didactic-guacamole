# LotteryNet Web Optimization Backlog

Fecha: 2026-06-07

Este documento deja guardado el analisis de la pagina web para retomarlo despues. La prioridad actual queda en Supabase/Android.

## Estado Actual

- Stack moderno actualizado: React 19, Vite 8, TypeScript 6, Supabase JS 2, Tailwind CSS 4.
- Tailwind 4 ya esta conectado con `@tailwindcss/vite` y tokens `ln-*` enlazados a las variables visuales actuales.
- Fase 1 UI iniciada con componentes Tailwind reutilizables:
  - `Panel`
  - `MetricCard`
  - `StatusBadge`
  - `DataToolbar`
  - `ActionButton`
  - `FormGrid`
  - `FieldGroup`
  - `TableActionButton`
- Primeras secciones migradas sin cambiar logica de negocio:
  - `AdminOperationsConsole`
  - `ClosingAutomationPanel`
  - `SupervisorConsole`
  - `AdminCommissionsPanel`
  - `AdminLotteryLimitsPanel`
  - `CashierOperationSheet`
  - `RechargeModal`
  - `AssignCashiersModal`
  - `LimitsConfirmModal`
  - `AnnulTicketModal`
  - `DeleteTicketModal`
- Segunda pasada UI operativa migrada sin cambiar logica de negocio:
  - `TicketsTab`
  - `ReportesTab`
  - `GanadoresTab`
  - `AdminsTab`
  - `SupervisoresTab`
  - `CajerosTab`
  - controles superiores de `MonitoreoTab`
  - `UserFormModal`
  - `CredsShareModal`
  - `LimitsEditor`
- Pasada visual senior aplicada:
  - Superficies tipo Admin One: cards solidas, borde limpio, sombra funcional y menos blur pesado.
  - Métricas con acento lateral y halo suave por estado.
  - Badges con punto de estado, uppercase compacto y mejor lectura.
  - Botones con estados hover/active más claros.
  - Inputs y tablas con jerarquía más densa y legible.
  - Toggles con foco visible y estado disabled.
  - `prefers-reduced-motion` para PCs lentas.
- Ya existe lazy loading para `Login`, `Dashboard` y las tabs principales.
- Vite ya separa chunks de `react-vendor`, `supabase`, `icons` y tabs.
- El bundle no esta excesivo:
  - `supabase`: ~200 KB
  - `react-vendor`: ~199 KB
  - `Dashboard`: ~91 KB
  - CSS: ~61.6 KB / gzip ~11.3 KB despues de la pasada visual senior
- Ya se agrego cache de 30 segundos para `fetchTickets`.

## Problemas Principales

1. `Dashboard.tsx` esta demasiado cargado.
   - Mezcla estado, carga de datos, acciones, UI y refrescos.
   - Esto hace mas dificil controlar cuando se llama a Supabase.

2. Varias cargas no tienen cache estandar.
   - `fetchUsers`
   - `fetchDrawResults`
   - config de modos/limites/bloqueos
   - auditoria/reportes

3. Hay demasiados `loadData()` completos.
   - Algunas acciones solo necesitan refrescar una parte.
   - Refrescar todo causa parpadeo, mas llamadas y mas espera.

4. Auth puede generar llamadas repetidas.
   - Revisar `setSession`, `getSession` y restauracion desde `localStorage`.
   - Mantener renovacion de token segura, pero evitar llamadas innecesarias.

5. Visualmente hay mucho blur/glass.
   - `backdrop-filter` y sombras grandes pueden sentirse lentos en PC debil.

## Recomendaciones De Implementacion

### Fase 1 - Baja Riesgo

- Agregar cache + in-flight request para:
  - usuarios
  - resultados
  - configuracion de admin
  - limites/bloqueos
- Pintar primero desde `localStorage`.
- Refrescar en segundo plano sin bloquear pantalla.
- Evitar loader completo cuando ya hay datos calientes.
- Agregar `prefers-reduced-motion` para bajar animaciones si el equipo esta lento.

### Fase 2 - Datos Mas Ordenados

- Dividir `Dashboard.tsx` en hooks:
  - `useDashboardData`
  - `useTicketsData`
  - `useResultsData`
  - `useAdminConfig`
  - `useCashierReports`
- Cambiar acciones para refrescar solo su area:
  - usuarios -> solo usuarios
  - tickets -> solo tickets
  - resultados -> solo resultados
  - limites -> solo limites

### Fase 3 - UI/UX

- Componentes compartidos ya creados:
  - `Panel`
  - `MetricCard`
  - `StatusBadge`
  - `DataToolbar`
  - `ActionButton`
  - `ModalShell`
  - `ModalCard`
- Siguiente conversion por pantalla:
  - cuerpos internos de `MonitoreoTab` (`lotteries`, `plays`, `ranking`, `cajeros`)
  - `AppShell` completo
  - `CuadreTab`, `AuditoriaTab`, `ResultadosTab` y wrappers de graficas
  - vista termica de `TicketDetailModal` solo cuando se revise con captura visual
- Reducir glass/blur en pantallas operativas.
- Mantener apariencia de panel serio, parecido al flujo Android.
- Usar animaciones solo con `transform` y `opacity`.

### Fase 4 - Listas Grandes

- Si Tickets/Ranking pasa de cientos de filas, agregar virtualizacion.
- Candidato: `@tanstack/react-virtual`.
- Objetivo: pintar solo filas visibles.

### Fase 5 - Medicion Real

- Agregar Vercel Speed Insights.
- Medir:
  - primer pintado
  - interaccion al cambiar tabs
  - tiempo hasta mostrar Tickets
  - llamadas repetidas a Supabase

## Cosas Que No Conviene Hacer Ahora

- No reemplazar todo el CSS por Tailwind en una sola pasada.
  - Tailwind ya esta instalado, pero la conversion visual completa debe hacerse por pantalla.
  - Riesgo de romper pantallas ya conocidas si se cambia todo el sistema visual de golpe.
- No agregar mas animaciones antes de reducir blur pesado.
- No abrir tablas cerradas directo desde web.
  - Mantener Edge Functions/RPC seguras.

## Documentacion Revisada

- React `useMemo`: https://react.dev/reference/react/useMemo
- React `useTransition`: https://react.dev/reference/react/useTransition
- Vite build/code splitting: https://vite.dev/guide/build
- CSS y Core Web Vitals: https://web.dev/articles/css-web-vitals
- Vercel Speed Insights: https://vercel.com/docs/speed-insights

## Proxima Accion Cuando Se Retome Web

Implementar cache/in-flight para usuarios, resultados y config. Luego revisar `loadData()` para que no refresque todo cada vez. En paralelo, seguir migrando pantallas pequenas al nuevo sistema Tailwind antes de tocar pantallas grandes como `Dashboard`.
