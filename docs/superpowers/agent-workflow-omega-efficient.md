# Agent Workflow Omega Efficient

## Objetivo

Usar agentes sin leer el proyecto entero ni gastar trabajo doble. Cada agente debe entrar por mapa, tomar una zona pequeña y devolver hallazgos accionables.

## Orden Recomendado

1. Understand map para ubicar archivos.
2. Agente Supabase: SQL, Edge Functions, RLS, Redis, Broadcast.
3. Agente Android: lifecycle, sesión, realtime, pantallas afectadas.
4. Agente Observabilidad: Sentry, logs seguros, métricas.
5. Agente QA contrato: verifica que lo prometido existe y no invade dinero.

## Límites Por Agente

- Máximo 3 a 5 archivos principales.
- No implementar si solo se pidió análisis.
- No tocar venta/pago/borrado sin prueba de contrato.
- No leer archivos de UI completos si el cambio es servidor.
- Siempre devolver: archivos revisados, riesgo, cambio sugerido, prueba mínima.

## Contratos Que No Se Rompen

- Admin ve su negocio y sus cajeros.
- Cajero ve solo su flujo.
- Master administra, pero no invade tickets del negocio.
- Dinero sigue server-first.
- Cache-first solo aplica a lectura visual.
- Broadcast no reemplaza la verdad del servidor.

## Pruebas Mínimas Por Cambio Grande

- Node contrato para estructura.
- Deno check para funciones Edge tocadas.
- Gradle compile si se toca Kotlin.
- Una prueba real solo si el cambio afecta flujo de venta/pago/premios.

## Cómo Pedir Un Agente

Formato recomendado:

```text
Revisa solo estos archivos: A, B, C.
Objetivo: confirmar si X rompe Y.
No edites.
Devuelve hallazgos con severidad y prueba mínima.
```

Para implementación:

```text
Implementa solo el cambio X en archivos A y B.
No cambies flujo de dinero.
Agrega prueba de contrato.
Verifica con comando mínimo.
```
