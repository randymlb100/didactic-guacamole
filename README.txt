LotteryNet Pro - Android Studio ZIP

Que trae:
- Proyecto Android Studio con WebView
- `app/src/main/assets/index.html` con panel visual
- Jerarquia: master > admin > cajero
- El master crea admins o cajeros
- El admin solo crea cajeros
- El master define `max_cajeros` de cada admin (10 o menos)
- El master puede bloquear admin y sus cajeros
- Limite de recarga por usuario
- Ventas, premios y recargas
- Conexion a Supabase por REST directo, sin CDN externo

Credenciales:
- La URL y la publishable key de Supabase siguen definidas dentro del HTML.

IMPORTANTE EN SUPABASE:
Pega este SQL completo en SQL Editor antes de abrir la app.

create extension if not exists "uuid-ossp";

drop table if exists recargas cascade;
drop table if exists premios cascade;
drop table if exists ventas cascade;
drop table if exists ticket_items cascade;
drop table if exists tickets cascade;
drop table if exists usuarios cascade;

create table usuarios (
  id uuid primary key default uuid_generate_v4(),
  nombre text not null,
  usuario text not null unique,
  password text not null,
  rol text not null check (rol in ('master', 'admin', 'cajero')),
  banca_id integer not null default 1,
  parent_user_id uuid references usuarios(id) on delete set null,
  estado text not null default 'activo' check (estado in ('activo', 'bloqueado')),
  limite_recarga numeric(12,2) not null default 0,
  max_cajeros integer not null default 0 check (max_cajeros >= 0 and max_cajeros <= 10),
  offline_ventas_habilitado boolean not null default false,
  created_at timestamp without time zone default now()
);

create table ventas (
  id uuid primary key default uuid_generate_v4(),
  usuario_id uuid not null references usuarios(id) on delete cascade,
  banca_id integer not null,
  total numeric(12,2) not null default 0 check (total >= 0),
  fecha timestamp without time zone not null default now(),
  created_at timestamp without time zone default now()
);

create table tickets (
  id uuid primary key default uuid_generate_v4(),
  usuario_id uuid not null references usuarios(id) on delete cascade,
  banca_id integer not null,
  lottery_name text not null,
  lottery_endpoint text not null,
  draw_date text not null,
  total_amount numeric(12,2) not null default 0 check (total_amount >= 0),
  status text not null default 'valido',
  result_number text not null default '',
  payout_amount numeric(12,2) not null default 0 check (payout_amount >= 0),
  created_at timestamp without time zone default now()
);

create table ticket_items (
  id uuid primary key default uuid_generate_v4(),
  ticket_id uuid not null references tickets(id) on delete cascade,
  play_numbers text not null,
  amount numeric(12,2) not null default 0 check (amount >= 0),
  potential_payout numeric(12,2) not null default 0 check (potential_payout >= 0),
  is_winner boolean not null default false,
  payout_amount numeric(12,2) not null default 0 check (payout_amount >= 0),
  hit_position text not null default ''
);

create table premios (
  id uuid primary key default uuid_generate_v4(),
  usuario_id uuid not null references usuarios(id) on delete cascade,
  banca_id integer not null,
  monto numeric(12,2) not null default 0 check (monto >= 0),
  fecha timestamp without time zone not null default now(),
  created_at timestamp without time zone default now()
);

create table recargas (
  id uuid primary key default uuid_generate_v4(),
  usuario_id uuid not null references usuarios(id) on delete cascade,
  banca_id integer not null,
  monto numeric(12,2) not null default 0 check (monto >= 0),
  fecha timestamp without time zone not null default now(),
  created_at timestamp without time zone default now()
);

insert into usuarios (
  nombre, usuario, password, rol, banca_id, parent_user_id, estado, limite_recarga, max_cajeros
) values (
  'MASTER ADMIN', 'master', 'pass123', 'master', 1, null, 'activo', 0, 10
);

insert into usuarios (
  nombre, usuario, password, rol, banca_id, parent_user_id, estado, limite_recarga, max_cajeros
)
select 'ADMIN 1', 'admin1', 'admin123', 'admin', 1, id, 'activo', 5000, 5
from usuarios where usuario='master';

insert into usuarios (
  nombre, usuario, password, rol, banca_id, parent_user_id, estado, limite_recarga, max_cajeros
)
select 'CAJERO 1', 'cajero1', 'cajero123', 'cajero', 1, id, 'activo', 1000, 0
from usuarios where usuario='admin1';

Despues:
- Abre el proyecto en Android Studio.
- Espera el sync de Gradle.
- Ejecuta en un telefono o emulador.
- Usa el boton "Probar conexion" o la insignia superior para confirmar acceso a Supabase.
- Si quieres resultados reales, levanta tu scraper Flask y configura su URL en la pestaña `Resultados`.

Resultados reales:
- La app ahora espera una API base compatible con tu proyecto `LotteryScraping-RD`.
- La app viene configurada por defecto con `https://didactic-guacamole.onrender.com`
- En emulador Android usa normalmente `http://10.0.2.2:5000`
- En telefono fisico usa la IP LAN de tu PC, por ejemplo `http://192.168.x.x:5000`
- Si lo despliegas en internet, usa esa URL publica
- La fecha para tu scraper debe ir como `dd-mm-yyyy`

Notas:
- Para una prueba rapida, la app puede funcionar con RLS desactivado.
- Para produccion, lo correcto es activar RLS y mover autenticacion/permisos al backend.
- La primera vez cada usuario debe iniciar sesion con internet.
- El modo offline aplica solo a ventas y solo si el admin activa `offline_ventas_habilitado` para su cajero.
- La logica actual toma como referencia la proxima hora en punto: online cierra 5 minutos antes y offline 15 minutos antes.
- Los tickets usan `tickets` y `ticket_items`. Cada jugada guarda su premio potencial y el sistema liquida automaticamente cuando encuentra el resultado real.
- El ticket nace como `valido`, cambia a `ganado` si resulta premiado, a `perdido` si no sale ganador y a `anulado` si se invalida antes del cierre permitido.
- El admin puede anular tickets de su banca mientras la loteria no haya pasado; el cajero solo puede anular sus propios tickets dentro de los primeros 5 minutos, tanto online como offline.
- Si el master crea un admin con `max_cajeros` mayor que 0, el sistema genera automaticamente ese numero de cajeros con credenciales aleatorias.
- El admin puede renombrar sus cajeros desde el panel de usuarios.
- El boton `Exportar PDF credenciales` abre una vista lista para imprimir o guardar como PDF con admin y cajeros creados en el ultimo lote.
- La app Android usa compartir e impresion nativos para enviar credenciales desde el telefono.
- Master y admin pueden regenerar claves desde `Usuarios` y volver a compartir/exportar el acceso actualizado.
- El cajero no ve pestañas administrativas; cada perfil solo muestra las secciones que puede usar.
- El perfil `master` tiene un bloque `Control master` con resumen global de bancas, admins, cajeros, bloqueos y tickets pendientes.
- El master puede compartir o imprimir ese resumen y sugerir automaticamente el proximo `banca_id` libre para nuevas bancas.
- El perfil `admin` tiene un bloque `Control admin` con resumen de su banca, sus cajeros, offline activos, bloqueos y tickets pendientes.
- El perfil `cajero` tiene un panel propio con su estado operativo, tickets, ventas, premios y pendientes de sincronizacion.
- Admin y cajero tambien pueden compartir o imprimir su resumen directo desde la app.
- Los botones `Compartir` de paneles, credenciales, tickets y cuadre financiero generan una imagen visual lista para WhatsApp y otras apps cuando el dispositivo lo permite.
- Los botones `Imprimir` siguen enviando una version lista para impresora o PDF desde Android.

Accesos de prueba:
- master / pass123
- admin1 / admin123
- cajero1 / cajero123
