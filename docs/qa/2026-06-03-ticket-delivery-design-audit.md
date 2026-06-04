# Auditoria de diseno: tickets WhatsApp, preview y entrega

Fecha: 2026-06-03  
Dispositivo: SM_A156U por ADB  
Cuenta probada: Banca El Fuerte / podero02  
Alcance: venta Pick, entrega por WhatsApp, ticket pequeno, ticket mediano, ticket gigante de 80 jugadas, pantalla Sistema/Cajeros.

## Capturas usadas

- Venta Pick abierta: `C:\Users\Randy Cordero\Desktop\lotterynet_android\.codex-artifacts\pick-whatsapp-qa-20260603\06-sale-open-correct.png`
- Pick pequeno agregado: `C:\Users\Randy Cordero\Desktop\lotterynet_android\.codex-artifacts\pick-whatsapp-qa-20260603\12-pick-small-added.png`
- Preview WhatsApp pequeno: `C:\Users\Randy Cordero\Desktop\lotterynet_android\.codex-artifacts\pick-whatsapp-qa-20260603\16-whatsapp-small-contact.png`
- Preview WhatsApp de 4 jugadas: `C:\Users\Randy Cordero\Desktop\lotterynet_android\.codex-artifacts\pick-whatsapp-qa-20260603\28-whatsapp-large-preview.png`
- Sistema principal: `C:\Users\Randy Cordero\Desktop\lotterynet_android\.codex-artifacts\pick-whatsapp-qa-20260603\33-system-screen.png`
- Control de venta en Sistema: `C:\Users\Randy Cordero\Desktop\lotterynet_android\.codex-artifacts\pick-whatsapp-qa-20260603\34-system-lower.png`
- Modo venta en Cajeros: `C:\Users\Randy Cordero\Desktop\lotterynet_android\.codex-artifacts\pick-whatsapp-qa-20260603\36-mode-tab.png`
- Ticket gigante 80 jugadas cargado: `C:\Users\Randy Cordero\Desktop\lotterynet_android\.codex-artifacts\large-80-ticket-qa-20260603-030309\04-80-lines-complete.png`
- Entrega de 80 jugadas: `C:\Users\Randy Cordero\Desktop\lotterynet_android\.codex-artifacts\large-80-ticket-qa-20260603-030309\05-after-print-80.png`
- Preview WhatsApp de 80 jugadas: `C:\Users\Randy Cordero\Desktop\lotterynet_android\.codex-artifacts\large-80-ticket-qa-20260603-030309\07-80-whatsapp-preview.png`

## Lo que esta bien

1. El modal de entrega esta claro para tickets pequenos y medianos: separa Imprimir, WhatsApp, Compartir y Ticket oficial.
2. El ticket pequeno de Pick sale moderno y facil de leer. La jerarquia principal esta bien: banca, serial, fecha, loteria, jugada y total.
3. El ticket de 4 jugadas mantiene buena lectura: los numeros en circulos verdes funcionan bien para Pick y el total grande se entiende.
4. Sistema tiene una pantalla mas ordenada que antes: agrupa operacion, caja, bloqueo de loteria y control de venta.
5. El modo de venta aparece en Cajeros > Modo venta con tres opciones claras: Solo Loteria, Solo Pick y Loteria + Pick.

## Problemas visuales encontrados

1. En WhatsApp, el footer azul del ticket pequeno y mediano queda cortado por los lados. El contenido importante se entiende, pero el cierre visual no se ve profesional.
2. En el ticket de 80 jugadas se usa formato compacto termico, pero la separacion entre columnas esta mal. El monto se pega a la siguiente jugada, por ejemplo `1P4BOX`, y eso hace que el cliente pueda leer mal la apuesta.
3. En el ticket gigante, el subtotal aparece abajo, pero en el preview de WhatsApp queda tapado por la barra de comentario y el boton de enviar. Hay que dejar mas aire vertical al final o usar una plantilla que no dependa de que el usuario vea la parte baja en el preview.
4. La plantilla grande pierde jerarquia: todo queda como texto monoespaciado uniforme. Funciona como recibo tecnico, pero no como ticket oficial facil de revisar.
5. El encabezado del ticket gigante se ve demasiado simple comparado con el ticket colorido. Para tickets muy grandes esta bien quitar logo/colores pesados, pero debe conservar una estructura limpia: titulo, serial, fecha, vendedor, loteria, tabla y total.

## Recomendacion de diseno

Para tickets pequenos y medianos, mantener la plantilla colorida actual, pero ajustar el footer:

- limitar ancho del texto del footer;
- centrar la linea de validez;
- no poner informacion critica pegada al borde inferior;
- dejar margen inferior suficiente para WhatsApp.

Para tickets grandes, usar una plantilla compacta especial:

- encabezado simple, sin logo grande;
- una sola loteria por bloque;
- tabla de 3 columnas, pero cada columna debe contener `tipo numero monto` con ancho fijo;
- no pegar monto con la siguiente columna;
- subtotal de loteria en una fila propia, centrada y en negrita;
- total general en bloque final grande;
- QR y verificacion despues del total, no antes.

## Criterio de aprobacion visual

Un ticket grande debe pasar estas reglas antes de produccion:

- ninguna columna puede juntar monto y jugada siguiente;
- el total general debe verse sin bajar ni ampliar la imagen;
- el subtotal de cada loteria debe estar dentro del bloque de esa loteria;
- WhatsApp no debe tapar informacion critica;
- si hay mas de 50 jugadas, usar plantilla compacta limpia, no la colorida completa.
