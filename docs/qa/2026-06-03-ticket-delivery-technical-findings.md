# Hallazgos tecnicos: venta Pick, tickets grandes y modo de venta

Fecha: 2026-06-03  
Dispositivo: SM_A156U por ADB  
Cuenta probada en app: Banca El Fuerte / podero02  
Objetivo: probar flujo real antes de produccion, incluyendo venta Pick, WhatsApp, ticket grande, modo de venta y estabilidad.

## Pruebas ejecutadas

1. Venta Pick pequena desde el celular.
2. Envio WhatsApp a Juan Phili del ticket pequeno.
3. Venta Pick de 4 jugadas desde el celular.
4. Envio WhatsApp a Juan Phili del ticket de 4 jugadas.
5. Venta gigante de 80 jugadas desde el celular.
6. Generacion de imagen WhatsApp para el ticket de 80 jugadas.
7. Revision de Sistema y Cajeros > Modo venta.
8. Pruebas Node:
   - `node tools\qa\keyboard-sales-smoke.mjs`
   - `node tools\verify-pick-result-gate.mjs`

## Resultados positivos

1. Pick pequeno vendio correctamente.
2. El ticket pequeno se guardo y abrio el modal de entrega.
3. WhatsApp abrio preview y envio correctamente el ticket pequeno a Juan Phili.
4. El ticket de 4 jugadas tambien genero preview y se envio.
5. La venta gigante llego a 80 jugadas y el boton PRINT no congelo la app.
6. Despues de PRINT, la app mostro `Ticket guardado y sincronizado con servidor`.
7. La entrega de 80 jugadas mostro: `80 jugadas · 1 loterias · 80`.
8. La prueba Node de horarios Pick paso completa: `9/9 Node Pick gate checks passed`.
9. Las ventas Pick de cajeros por Node fueron aceptadas y conservaron loteria, numero y tipo.

## Problemas tecnicos encontrados

1. Cargar 80 jugadas por teclado ADB tardo mas de 3 minutos. No hubo crash, pero el flujo es pesado.
2. Durante la carga gigante, el comando se corto por tiempo y la pantalla quedo con una jugada pendiente `1039*`. La app permitio recuperarla, pero muestra que la entrada masiva es fragil si el usuario toca demasiado rapido.
3. En pruebas anteriores, cuando se ingresaban muchas teclas muy rapido, el campo podia acumular texto largo en vez de agregar lineas limpias. Ejemplo observado: `113571864219876111221334415566+1`.
4. El boton borrar solo elimina poco a poco. Para limpiar un campo largo hay que tocar muchas veces. En ticket grande eso es incomodo y peligroso.
5. El texto de ayuda de venta Pick sigue diciendo `Numero -> monto -> Q/P/T`, pero en Pick el flujo real es `numero -> tipo -> OK -> monto -> OK`.
6. Node detecto que `podero02` no pudo vender loteria normal: servidor respondio `Las jugadas de loteria estan bloqueadas para esta cuenta.` Esto indica que la cuenta esta en modo Solo Pick o que el modo no esta en Loteria + Pick.
7. El modo de venta no esta en la pantalla principal de Sistema. Esta dentro de Cajeros > Modo venta. Eso es correcto tecnicamente, pero puede confundir si el usuario espera verlo como control global.
8. En logs del telefono aparecieron reconexiones repetidas de Supabase Realtime: `Software caused connection abort`, luego reconecto. No rompio esta prueba, pero confirma que la app debe depender de catch-up al volver, no solo de realtime.

## Riesgo antes de produccion

1. El servidor y la app aceptan Pick, pero el UI de entrada grande no es suficientemente resistente para usuarios que tocan muy rapido.
2. El ticket gigante se guarda, pero la imagen de WhatsApp no es aceptable visualmente por columnas pegadas.
3. Si una cuenta queda en Solo Pick, la venta normal de loteria queda bloqueada. Eso puede parecer bug si el administrador no sabe donde cambiarlo.
4. Realtime puede reconectar solo, pero hay que mantener catch-up por pantalla para tickets/resultados/ganadores.

## Recomendaciones tecnicas

1. Agregar un boton de limpiar campo completo en Venta Pick.
2. Cambiar el texto de ayuda de Pick para explicar el flujo correcto.
3. Bloquear doble entrada mientras `OK` esta procesando una jugada, o poner un debounce corto.
4. Para ticket grande, no depender de la plantilla colorida ni de columnas sin ancho fijo. Usar una plantilla compacta con medicion real de texto.
5. En Cajeros > Modo venta, mostrar el usuario seleccionado arriba y el estado actual con badge claro.
6. Agregar una prueba automatica de render de ticket grande que verifique:
   - 80 jugadas;
   - total correcto;
   - subtotal visible;
   - columnas separadas;
   - imagen exportable sin cortar footer.
7. Mantener pruebas Node para servidor, pero complementar con ADB real para teclado, WhatsApp y render.

## Estado final de la prueba

El flujo server/app de Pick no se rompio con 80 jugadas, pero la plantilla de WhatsApp para ticket gigante no esta lista para produccion. No envie el ticket de 80 jugadas porque el preview salio visualmente defectuoso.
