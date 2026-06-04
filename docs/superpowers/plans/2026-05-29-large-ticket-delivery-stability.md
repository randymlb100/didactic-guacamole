# Large Ticket Delivery Stability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tickets con muchas jugadas deben poder previsualizarse, compartirse por WhatsApp e imprimirse sin congelar el POS, sin timeout repetido, sin duplicados y sin posibilidad de guardar un ticket sin jugadas.

**Architecture:** Crear una capa de entrega de ticket que clasifique el tamano del ticket y decida entre imagen unica, imagenes paginadas, plantilla compacta de texto/HTML o impresion termica por bloques. Todo render pesado, guardado de imagen y envio a impresora debe correr fuera del hilo principal. La venta debe tener guardas de idempotencia y rechazo de jugadas vacias en cliente y servidor.

**Tech Stack:** Kotlin, Jetpack Compose, Android coroutines, Android FileProvider, Supabase Edge Functions/Postgres RPC, Node.js QA scripts.

---

## Contexto Tecnico Encontrado

- `SalesActivity.kt` renderiza el bitmap del preview con `remember { NativeBitmapExport.renderOfficialTicketBitmap(...) }` dentro de Compose. Eso puede congelar la pantalla cuando el ticket es grande.
- `TicketOfficialActivity.kt` genera el bitmap para compartir/guardar al tocar el boton, tambien de forma sincronica.
- La impresion termica ya tiene timeout dinamico, pero envia el ticket completo como un solo contenido. En tickets largos eso aumenta riesgo de timeout, reintentos y confusion.
- La venta usa `clientRequestId`, pero el flujo debe reforzarse para que un timeout no permita reintentar como ticket nuevo ni limpiar jugadas antes de confirmar estado real.

## Documentacion Usada

- Android recomienda mover trabajo pesado y de red/disco a `Dispatchers.IO` para mantener la UI fluida.
  Fuente: https://developer.android.com/kotlin/coroutines
- Compose recomienda usar `LazyColumn` para listas grandes porque solo compone lo visible.
  Fuente: https://developer.android.com/develop/ui/compose/lists
- Android advierte que mucho bitmap puede provocar problemas de memoria; por eso no conviene crear una imagen enorme.
  Fuente: https://developer.android.com/topic/performance/graphics/manage-memory
- Para compartir archivos con otras apps, Android recomienda `FileProvider` con permisos temporales.
  Fuente: https://developer.android.com/training/secure-file-sharing
- Compose recomienda sacar calculos costosos fuera del cuerpo composable cuando sea posible.
  Fuente: https://developer.android.com/develop/ui/compose/performance/bestpractices

## UX y Reglas Visuales

- Mantener el ticket como recibo real: encabezado, vendedor, codigo, fecha, secciones por loteria, jugadas con monto, total por loteria y total final.
- En tickets ganadores, mostrar bloque claro de premios: loteria, resultado, numero jugado, posicion/acierto, monto apostado, premio por jugada y total premio.
- Para tickets grandes en WhatsApp/snapshot oficial, no enviar varias imagenes. Usar una sola imagen compacta tipo ticket termico, sin logo, con desglose por loteria.
- Botones de imprimir, WhatsApp, compartir y guardar deben mostrar estado "preparando..." y quedar deshabilitados mientras el trabajo corre.
- Si el ticket es demasiado grande para imagen unica, el preview debe mostrar resumen profesional y aviso: "Ticket grande: se enviara en 3 imagenes".

## Implementacion

- [x] Crear `TicketDeliveryPolicy`
  - Archivo nuevo: `app/src/main/java/com/lotterynet/pro/core/delivery/TicketDeliveryPolicy.kt`.
  - Definir modos:
    - `SingleImage` para tickets pequenos.
    - `PagedImages` para tickets medianos/grandes.
    - `TextSummaryFallback` para casos extremos o bajo memoria.
    - `ThermalChunked` para impresion.
  - Clasificar por cantidad de jugadas, cantidad de loterias y alto estimado del bitmap.
  - La paginacion debe ser inteligente por loteria:
    - Si el ticket tiene 1 a 4 loterias y el alto estimado cabe, sale una sola imagen/ticket.
    - Si el ticket tiene varias loterias, se divide primero por loteria completa para que no quede confuso.
    - Si una sola loteria tiene demasiadas jugadas para una pagina, entonces esa loteria se divide internamente en pagina 1/2, 2/2, etc.
    - No partir una loteria pequena entre dos paginas solo por contar filas; mantenerla completa siempre que quepa.
  - Proponer limites iniciales:
    - Imagen unica: hasta 60 jugadas o alto estimado menor a 3600 px.
    - Paginas: agrupar por loteria y usar bloques de 45 a 60 jugadas solo cuando una loteria individual sea muy larga.
    - Extremo: mas de 220 jugadas usa resumen + paginas compactas.

- [x] Paginacion de snapshot oficial
  - Modificar `NativeBitmapExport`.
  - Agregar `renderOfficialTicketBitmaps(...) : List<Bitmap>`.
  - Reutilizar el diseno actual para tickets pequenos.
  - Para tickets grandes, renderizar varias paginas compactas:
    - Encabezado en cada pagina.
    - Secciones por loteria completas siempre que quepan.
    - Cuando hay muchas loterias, cada pagina puede contener una o varias loterias completas segun el alto disponible.
    - Cuando una loteria sola es enorme, esa loteria se parte con etiqueta clara: "Loteka 1/2" y "Loteka 2/2".
    - Monto de cada jugada visible.
    - Total por loteria visible.
    - Total general y QR solo en la ultima pagina, o resumen en cada pagina si hace falta.
  - No duplicar jugadas arriba y abajo: si hay bloques por loteria, la lista general plana se oculta o se reemplaza por un resumen.

- [x] Compartir WhatsApp robusto
  - Modificar `LocalRenderCacheRepository`.
  - Agregar guardado de multiples bitmaps por una misma llave: `saveBitmaps(...)`.
  - Usar `ACTION_SEND_MULTIPLE` o `ShareCompat.IntentBuilder` para multiples imagenes.
  - Mantener `FileProvider` y permisos temporales.
  - Agregar plantilla texto corta para WhatsApp:
    - Codigo, vendedor, total, cantidad de jugadas, resumen por loteria.
    - Para ticket ganador: total premio y desglose ganador.
  - Si WhatsApp no acepta demasiadas imagenes, enviar texto + primeras paginas y mostrar mensaje claro para abrir ticket oficial.

- [x] Preview de venta sin congelar
  - Modificar `SalePrintPreviewOverlay` en `SalesActivity.kt`.
  - Quitar render pesado de `remember`.
  - Usar `LaunchedEffect(ticket.id)` + `withContext(Dispatchers.IO)` para preparar preview pequeno.
  - Para tickets grandes, no renderizar bitmap en el preview; mostrar resumen liviano con `LazyColumn`.
  - Deshabilitar botones mientras `deliveryJob` esta activo.

- [x] Ticket oficial sin congelar
  - Modificar `TicketOfficialActivity.kt`.
  - Cambiar `renderBitmapForAction` a flujo suspendido en `Dispatchers.IO`.
  - Soportar multiples paginas para WhatsApp/compartir/guardar.
  - Mostrar progreso: "Preparando 3 imagenes..." y error recuperable si falla.

- [x] Impresion termica por bloques
  - Modificar `ThermalTicketRenderer`.
  - Agregar `renderTicketChunks(ticket, maxLinesPerChunk)` para tickets largos.
  - Mantener encabezado completo en el primer bloque.
  - Separar por loteria; no cortar en medio de una loteria si se puede evitar.
  - Si son pocas loterias y cabe en un solo ticket, imprimir una sola pieza.
  - Si son muchas loterias, imprimir por bloques de loterias completas.
  - Si una loteria sola es demasiado larga, partir solo esa loteria en sub-bloques con encabezado repetido de esa loteria.
  - En bloque final imprimir total jugado, total premio si aplica, codigo y QR.
  - Modificar `BluetoothThermalPrinter` e impresora integrada para enviar chunks en secuencia, con pausa corta y progreso.
  - Bloquear doble toque de imprimir hasta terminar o fallar.

- [x] Evitar ticket vacio y duplicado por timeout
  - Modificar `SalesActivity.kt`.
  - Antes de llamar al server, validar que la lista normalizada tenga al menos una jugada con numero y monto mayor que cero.
  - Si una venta entra en estado "enviando", guardar `clientRequestId`, filas y total como operacion pendiente.
  - Si hay timeout, no limpiar jugadas ni generar otro `clientRequestId`.
  - En retry, consultar/usar el mismo `clientRequestId` para confirmar si el ticket ya existe antes de crear otro.
  - Mostrar mensaje claro: "La venta esta confirmandose. No cierre ni repita hasta verificar."

- [x] Guardia servidor Supabase
  - Crear migracion SQL o ajuste Edge Function para rechazar payload sin jugadas.
  - Reglas:
    - `plays/items` no puede estar vacio.
    - Cada jugada debe tener numero no vacio, tipo valido, loteria valida y monto mayor que cero.
    - `client_request_id` debe seguir siendo idempotente.
  - Esto evita que cualquier app vieja o bug local cree ticket vacio.

- [x] Pruebas Android
  - Agregar/actualizar `OfficialTicketShareContractsTest`.
    - Ticket de 150 jugadas genera varias paginas.
    - Ninguna pagina supera alto maximo.
    - No hay lista plana duplicada cuando se usa desglose por loteria.
  - Agregar/actualizar `SalesUiContractsTest`.
    - Ticket grande no renderiza bitmap sincronico en Compose.
    - Botones quedan deshabilitados durante render/print.
    - Venta vacia no llama al backend.
  - Agregar/actualizar `BluetoothThermalPrinterPolicyTest`.
    - Ticket largo se parte en bloques.
    - Timeout no deja trabajos duplicados corriendo.
  - Agregar/actualizar `ThermalTicketRendererTest`.
    - Cada loteria muestra monto jugado.
    - Ticket ganador muestra premio por jugada y total premio.

- [ ] Pruebas Node.js / servidor
  - Agregar `tools/qa/empty-ticket-guard.node.test.mjs`.
  - Casos:
    - Crear ticket sin jugadas debe fallar.
    - Crear ticket con numero vacio debe fallar.
    - Reintento con mismo `clientRequestId` no duplica.
    - Ticket grande real mantiene todas las jugadas y totales.
  - Ejecutar contra entorno configurado con los secretos locales.

- [ ] Verificacion manual en POS
  - Crear ticket de prueba con 20, 80, 150 y 250 jugadas.
  - Probar:
    - Preview no congela.
    - WhatsApp abre y recibe paginas.
    - Impresion no queda trabada.
    - Si se corta internet o impresora tarda, no se crea ticket vacio.
    - Reintentar no duplica venta.

## Riesgos y Decisiones

- WhatsApp puede variar por version y telefono. Por eso el plan tiene fallback texto + paginas.
- En impresoras POS viejas, enviar un ticket demasiado largo en un solo write es lo mas riesgoso. La solucion es partir por bloques, no subir timeout sin limite.
- La proteccion contra ticket vacio debe estar en dos capas: app y servidor. Si solo se arregla la app, una version vieja puede volver a fallar.

## Resultado Esperado

- Tickets pequenos siguen como ahora.
- Tickets grandes se ven como recibo real compacto en una sola imagen para WhatsApp/snapshot.
- WhatsApp no intenta cargar una imagen oficial pesada con logos ni varias imagenes cortadas.
- La impresion deja de bloquear el POS por trabajos enormes.
- Un timeout no permite vender dos veces ni crear un ticket sin numeros.
