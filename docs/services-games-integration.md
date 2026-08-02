# Servicios y Videojuegos: contrato de integración

## Alcance

Estos módulos son independientes de lotería normal, Pick, Recargas y Deportes. IPTV queda fuera: no tiene módulo, permiso, payload ni reporte.

### Organización visual de la app

La pantalla usa dos destinos principales: `Servicios` y `Videojuegos`. Las categorías
de servicios se presentan como filtros secundarios y el catálogo conserva el foco
principal. La búsqueda filtra nombre, categoría, tipo de servicio o proveedor sin
modificar la consulta ni el contrato del backend. Los assets locales tienen prioridad
sobre URLs remotas para que la venta no dependa de que el logo esté disponible en
internet.

Cada tarjeta comunica el proveedor, categoría, precio cuando aplica y acción esperada
(`Consultar`, `Cotizar`, `Enviar remesa`, `Activar SIM` o `Procesar`). El formulario
operativo se abre en una hoja inferior desplazable y permanece conectado a los mismos
payloads; la reorganización visual no altera las rutas de consulta o confirmación.

La entrega añade control de acceso, catálogo y operaciones por un adaptador Edge separado. La persistencia de configuración queda por módulo:

- `services:global`
- `video_games:global`

El valor remoto contiene `enabled`, `allowedAdminKeys`, `allowedCashierKeys`, `cashierAdminKeys`, `updatedAt` y `updatedBy`.

## Reglas de autorización

- Master puede habilitar o deshabilitar cada módulo.
- Master puede asignar un admin individual.
- Master puede asignar un cajero individual o todos los cajeros de un admin.
- Un cajero nunca recibe la acción `add_funds` ni la carga de comprobantes.
- El fondo sigue siendo una operación de Admin y conserva el flujo existente de Recargas.
- Si el módulo está apagado, ninguna asignación abre la sección.

## Flujo implementado

Android llama únicamente estas funciones autenticadas; nunca llama directamente al proveedor ni almacena credenciales:

- `recargas-rapidas-services-games`: catálogo, consulta y confirmación.
- `get-services-games-report`: reporte separado por módulo, administrador o cajero.

El contrato de la función es:

```json
{
  "action": "catalog|query|confirm",
  "module": "services|video_games",
  "providerId": "edenorte",
  "productId": "provider-product-id",
  "adminKey": "admin-id",
  "cashierKey": "cashier-id",
  "customerInput": {"value": "..."},
  "quotedPrice": 100,
  "clientRequestId": "uuid",
  "serviceType": "bills_lookup|bills_pay|insurance_sale|sim_activation|energy|remittance_calculate|remittance_send",
  "amount": 100,
  "providerPayload": {}
}
```

`customerInput` es una envoltura interna de la app. Para facturas el adaptador
extrae `value` y llama al proveedor con el texto plano; nunca se envía el
objeto como identificador.

## Contratos reales por servicio

El adaptador conserva la misma secuencia y los mismos contratos del portal:

- Facturas: `GET bills/{customerId}/{providerId}` para consultar y `POST
  bills/{customerId}/{providerId}` con `{ "amount": number }` para pagar. La
  consulta no afecta caja; el pago usa el monto pendiente devuelto por el
  proveedor.
- Videojuegos: `GET gamecategories/active`, `GET gameproducts/active` y `POST
  gamesales` con `categoryId`, `productId`, `playerId`, `zoneId`, `clientName`
  y `notes`.
- Seguros: `POST insurance/create` con los datos del propietario, vehículo,
  forma de pago y las dos imágenes requeridas en Base64.
- Activación SIM: `POST simcard/activate` con compañía, cliente, documento,
  fecha de nacimiento, datos de padres e ICCID.
- Remesas: primero `POST money-transfer/calculate` con servicio, monto y
  `remittanceType`; solo después de confirmar se llama `POST
  money-transfer/send` con remitente, destinatario, teléfonos, direcciones,
  monto y tipo de remesa.

La función rechaza operaciones de seguros, SIM o remesas si faltan campos
propios del contrato. Esto evita que el formulario genérico envíe datos
incorrectos al proveedor.

El adaptador resuelve credenciales por usuario, luego por administrador y finalmente por la cuenta por defecto configurada por el Master. La operación confirmada usa `clientRequestId` único y se registra en `services_games_operations`. Una consulta de factura (`bills_lookup`) es solo lectura y no se registra como venta; el pago (`bills_pay`) sí genera el asiento. La reserva se crea antes de llamar al proveedor: si se pierde la respuesta después de iniciar el cobro, la operación queda `unknown` y el mismo identificador no puede repetirla automáticamente.

Para videojuegos se usan las rutas reales del proveedor: `gamecategories/active`, `gameproducts/active`, `gamesales` y confirmación server-side. Para servicios se mantiene el catálogo explícito y se enrutan las operaciones a los endpoints del proveedor sin exponer sus credenciales.

El perfil Master expone estos permisos desde `Centro Master > Módulos`. La selección siempre comienza por el admin y luego limita la lista a los cajeros que pertenecen a ese admin. La pantalla conserva cambios pendientes al alternar entre Servicios y Videojuegos, refresca la lista local de usuarios al reanudarse y solo vuelve a consultar configuración remota al entrar por primera vez a cada módulo durante esa apertura.

Los logos de servicios se empaquetan localmente en `app/src/main/assets/services/`. Los archivos de Edenorte, Edesur, Edeeste, CAASD, CORAAPPLATA, CORAAVEGA, CORAASAN, Aster, StarCable, Skymax, COAAROM y Luz y Fuerza se obtuvieron de sus portales institucionales o fuentes de marca verificables; MonCash y Paso Rápido se obtuvieron de sus sitios oficiales; los assets existentes de Altice, Viva, Wind y Moun se reutilizan desde el proyecto. CEB usa `services/ceb.png` con la marca oficial de CEPM, según la decisión del producto de tratar CEB como su subsidiaria; no se presenta como un logotipo independiente de CEB. Los servicios sin compañía única (seguros, activación SIM y remesas) usan el fallback del servicio. La app no descarga imágenes durante la venta ni depende de URLs externas para renderizarlas.

## Por qué no se mezcló con el reporte actual

Los servicios y videojuegos usan el ledger propio `services_games_operations` para distinguir precio cobrado, costo del proveedor, comisión, estado y referencia. No se agregan registros sintéticos al reporte de loterías/recargas. La migración y las Edge Functions deben desplegarse juntas antes de habilitar el módulo en producción.

## Referencias

- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations?hl=en)
- [Android offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first?hl=en)
- [Material 3 adaptive layouts](https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive)
- [Portal público de Recargas Rápidas](https://recargasrapidas.com/)
