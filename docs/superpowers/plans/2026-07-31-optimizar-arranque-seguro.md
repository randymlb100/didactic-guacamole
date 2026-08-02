# Optimizar arranque seguro de la app - Plan de implementación

> **Para agentes de implementación:** ejecutar las tareas una por una y revisar el diff después de cada fase. No cambiar reglas de negocio.

**Objetivo:** reducir el tiempo visible de entrada de LotteryNet mostrando primero la sesión y la pantalla principal local, mientras las sincronizaciones no críticas continúan después del primer frame.

**Arquitectura:** conservar la autenticación y los datos locales como fuente inicial. Separar el trabajo de arranque en dos grupos: datos mínimos para pintar la pantalla y tareas diferibles como métricas, configuración remota, OTA y sincronización. La app no venderá, autorizará, pagará ni cambiará límites hasta que sus validaciones existentes terminen.

**Tecnologías:** Kotlin, AppCompat, Jetpack Compose, Compose Material 3, coroutines, WorkManager, AndroidX SplashScreen, Baseline Profile y Perfetto/Macrobenchmark.

## Restricciones globales

- No modificar Auth, PostgreSQL, Realtime, Edge Functions, endpoints, payloads ni migraciones.
- No modificar las reglas de venta, premios, límites pool/personales, cobros, recargas, deportes, servicios ni videojuegos.
- No eliminar sincronización: solo mover trabajo no crítico después del primer frame o ejecutarlo en paralelo cuando sea seguro.
- La sesión local y el estado cacheado deben permitir entrar aunque la red esté lenta o temporalmente fuera de servicio.
- Toda llamada remota debe conservar sus timeouts, manejo de 401, reintentos y fallback existentes.
- No ejecutar `gradlew` durante la implementación hasta que el usuario autorice el test final.

---

## Mapa actual confirmado

- `app/src/main/java/com/lotterynet/pro/LotteryNetApp.kt`: inicializa Sentry, WorkManager, callbacks de ciclo de vida y OTA antes de crear la actividad.
- `app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt`: lee sesión/crash/usuarios locales y decide si abrir Login o Shell.
- `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`: lee sesión, usuarios y configuración local antes de `setContent`; después realiza dashboard, Realtime, configuración remota y catch-up.
- `app/src/main/res/values/themes.xml`: ya contiene `Theme.LotteryNetPro.Startup`, pero el manifest usa `Theme.LotteryNetPro` directamente.
- `app/src/main/java/com/lotterynet/pro/baselineprofile/BaselineProfileGenerator.kt`: ya existe perfil base, pero debe medirse que cubra el camino real de launcher y sesión.

## Fases

### Fase 0: Línea base sin cambios

**Archivos:** ninguno.

- Medir tres escenarios en el mismo dispositivo: arranque en frío, retorno desde segundo plano y entrada con sesión guardada.
- Registrar TTID y TTFD; no usar solo la sensación visual.
- Revisar Logcat/Perfetto para separar tiempo de `LotteryNetApp.onCreate`, `LoginActivity.onCreate`, `ShellActivity.onCreate` y primer frame Compose.
- Confirmar que una entrada offline y una entrada con red lenta siguen mostrando la pantalla local.
- Criterio de salida: identificar el bloque que retrasa el primer frame y guardar la medición antes de editar.

### Fase 1: Proteger el primer frame

**Archivos a modificar:**

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt`
- `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`

- Aplicar el tema `Theme.LotteryNetPro.Startup` únicamente a las actividades de entrada necesarias.
- Llamar `installSplashScreen()` antes de `super.onCreate()` en las actividades que usan el arranque principal.
- No mantener el splash esperando llamadas de red; solo permitir lectura local mínima si el flujo actual la necesita.
- Mantener la lógica actual que abre directamente `ShellActivity` cuando existe sesión válida.
- No añadir una pantalla intermedia ni una espera artificial.
- Criterio de salida: la primera pantalla aparece con la misma ruta y el mismo estado local que antes, pero sin esperar configuración remota.

### Fase 2: Reducir trabajo síncrono de `ShellActivity`

**Archivos a modificar:**

- `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`
- `app/src/test/java/com/lotterynet/pro/ui/shell/ShellStartupContractsTest.kt` (crear si no existe)

- Mantener síncronos únicamente: sesión activa, rol, banca y valores locales necesarios para construir el menú inicial.
- Mover lecturas locales secundarias de usuarios/configuración a una carga posterior controlada.
- Pintar inicialmente la visibilidad de módulos con el último valor cacheado; después actualizarla cuando llegue el valor remoto.
- Conservar exactamente los callbacks de navegación y las rutas existentes.
- Añadir contrato verificable: la lista inicial no requiere red y las tareas secundarias no bloquean la creación de `ShellRoute`.
- Criterio de salida: no cambia el menú autorizado para el usuario; solo puede aparecer una actualización posterior si el servidor cambió la configuración.

### Fase 3: Paralelizar configuración remota no crítica

**Archivos a modificar:**

- `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`
- `app/src/main/java/com/lotterynet/pro/core/master/SupabaseMasterConfigRemoteStore.kt` solo si se requiere una función existente de lectura paralela; no cambiar endpoints.

- Mantener el `LaunchedEffect` posterior al primer frame.
- Leer deportes, servicios y videojuegos con tareas independientes y controladas, sin bloquear entre módulos.
- No iniciar una segunda llamada si el valor cacheado ya es fresco según las políticas existentes.
- Si una lectura falla, mantener el valor cacheado y no ocultar módulos que ya estaban autorizados.
- Criterio de salida: una falla de un módulo no impide abrir la app ni afecta los otros módulos.

### Fase 4: Revisar trabajo de `Application.onCreate`

**Archivos a modificar solo si la medición demuestra impacto:**

- `app/src/main/java/com/lotterynet/pro/LotteryNetApp.kt`
- `app/src/main/java/com/lotterynet/pro/core/update/UpdateManager.kt`
- `app/src/main/java/com/lotterynet/pro/core/sync/LotteryNetCatchUpScheduler.kt`

- No quitar Sentry ni OTA.
- Mantener inicialización mínima y diferir registro de tareas que no sean necesarias para la primera pantalla.
- Conservar WorkManager como mecanismo de sincronización; solo evitar que su programación innecesariamente coincida con el primer dibujo si Android permite moverla a después del primer frame.
- Mantener el chequeo OTA en foreground, sin convertirlo en bloqueo de entrada.
- Criterio de salida: la app sigue registrando errores, programando catch-up y verificando OTA, pero ninguna de esas tareas bloquea la UI inicial.

### Fase 5: Medición de rendimiento y perfil

**Archivos a modificar:**

- `baselineprofile/src/main/java/com/lotterynet/pro/baselineprofile/BaselineProfileGenerator.kt`
- `app/src/main/java/com/lotterynet/pro/ui/login/LoginActivity.kt`
- `app/src/main/java/com/lotterynet/pro/ui/shell/ShellActivity.kt`

- Añadir medición de `reportFullyDrawn()` cuando el menú inicial realmente esté utilizable, sin esperar sincronizaciones opcionales.
- Ajustar el recorrido del Baseline Profile para cubrir launcher → sesión cacheada → Shell, sin abrir actividades sin contexto de autenticación.
- Mantener los recorridos existentes de venta, tickets, resultados, ganadores y finanzas.
- Criterio de salida: TTID y TTFD se comparan contra la Fase 0 y no se reporta como “completo” antes de tiempo.

### Fase 6: Validación sin riesgo

**Archivos:** ninguno adicional.

- Primero ejecutar `git diff --check` y revisión estática.
- Revisar que no haya cambios en migraciones, Edge Functions, payloads, Auth, Realtime ni PostgreSQL.
- Ejecutar los contratos Node existentes relacionados con sesión, startup, sincronización y navegación.
- Solo después de autorización explícita, ejecutar `testDebug` en el dispositivo/emulador y comprobar: login, sesión guardada, offline, red lenta, 401, venta, tickets, cobro, límites y acceso por rol.
- Comparar TTID/TTFD antes y después; si empeora o cambia el flujo, revertir únicamente la fase responsable.

## Resultado esperado

La app debe mostrar rápidamente la misma pantalla y menú que ya funcionan, usando sesión/cache local. La sincronización seguirá ocurriendo después, sin perder eventos ni cambiar reglas. El usuario no verá una pantalla bloqueada esperando llamadas de configuración remota.

## Documentación oficial usada

- Android: [App startup time](https://developer.android.com/topic/performance/vitals/launch-time)
- Android: [Splash screens](https://developer.android.com/develop/ui/views/launch/splash-screen)
- Android: [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview)
- Android: [Startup Profiles](https://developer.android.com/topic/performance/startupprofiles/dex-layout-optimizations)
