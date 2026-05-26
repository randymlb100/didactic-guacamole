-- LotteryNet Pro backend migration blueprint
-- Safe rules for the live project:
-- - Do not drop existing tables.
-- - Do not delete data.
-- - Additive changes only: CREATE IF NOT EXISTS, ALTER ADD COLUMN IF NOT EXISTS,
--   indexes, RLS, triggers, functions.

create extension if not exists pgcrypto;

do $$
begin
  create type public.ln_role as enum ('master', 'admin', 'supervisor', 'cajero');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.ln_user_status as enum ('activo', 'bloqueado', 'suspendido');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.ln_ticket_status as enum ('VALIDO', 'ANULADO', 'INVALIDADO', 'GANADOR', 'PERDEDOR', 'PAGADO');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.ln_play_type as enum ('QUINIELA', 'PALE', 'TRIPLETA', 'SUPER_PALE');
exception when duplicate_object then null;
end $$;

do $$
begin
  create type public.ln_balance_movement_type as enum ('MASTER_TO_ADMIN', 'ADMIN_TO_SUPERVISOR', 'ADMIN_TO_CAJERO', 'SUPERVISOR_TO_CAJERO', 'TICKET_SALE', 'TICKET_VOID', 'PAYOUT', 'ADJUSTMENT', 'RECHARGE_PROVIDER');
exception when duplicate_object then null;
end $$;

create table if not exists public.roles (
  code public.ln_role primary key,
  label text not null,
  rank int not null unique
);

insert into public.roles (code, label, rank) values
  ('master', 'Master', 100),
  ('admin', 'Admin', 80),
  ('supervisor', 'Supervisor', 60),
  ('cajero', 'Cajero', 40)
on conflict (code) do nothing;

create table if not exists public.permisos (
  code text primary key,
  label text not null,
  description text,
  created_at timestamptz not null default now()
);

insert into public.permisos (code, label, description) values
  ('ticket.invalidate', 'Invalidar tickets', 'Permite invalidar tickets fuera del flujo normal'),
  ('ticket.void.approve', 'Aprobar anulaciones', 'Permite aprobar anulaciones de cajeros'),
  ('cashier.block', 'Bloquear cajeros', 'Permite bloquear cajeros asignados'),
  ('recharge.cashier', 'Recargar cajeros', 'Permite transferir balance a cajeros'),
  ('reports.network', 'Reportes de red', 'Permite ver reportes de usuarios debajo de su red')
on conflict (code) do nothing;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  usuario_legacy_id uuid,
  role public.ln_role not null,
  username text not null unique,
  display_name text not null,
  status public.ln_user_status not null default 'activo',
  parent_user_id uuid references public.profiles(id),
  admin_owner_id uuid references public.profiles(id),
  banca_id uuid,
  phone text,
  commission_rate numeric(8,4) not null default 0,
  max_recharge_limit numeric(14,2) not null default 0,
  can_void_after_window boolean not null default false,
  created_by uuid references public.profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.user_permissions (
  user_id uuid not null references public.profiles(id) on delete cascade,
  permission_code text not null references public.permisos(code) on delete cascade,
  granted_by uuid references public.profiles(id),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  primary key (user_id, permission_code)
);

create table if not exists public.bancas (
  id uuid primary key default gen_random_uuid(),
  admin_id uuid not null references public.profiles(id),
  name text not null,
  address text,
  phone text,
  active boolean not null default true,
  timezone text not null default 'America/Santo_Domingo',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'profiles_banca_fk' and conrelid = 'public.profiles'::regclass
  ) then
    alter table public.profiles
      add constraint profiles_banca_fk foreign key (banca_id) references public.bancas(id) not valid;
  end if;
end $$;

create table if not exists public.admin_networks (
  admin_id uuid primary key references public.profiles(id) on delete cascade,
  master_id uuid references public.profiles(id),
  active boolean not null default true,
  max_recharge_balance numeric(14,2) not null default 0,
  maintenance_locked boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.supervisores (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  admin_id uuid not null references public.profiles(id),
  can_approve_void boolean not null default false,
  can_block_cashier boolean not null default false,
  can_recharge_cashier boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.cajeros (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  admin_id uuid not null references public.profiles(id),
  supervisor_id uuid references public.profiles(id),
  recarga_tx_limit numeric(14,2) not null default 0,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.lotteries (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null,
  country_code text not null default 'DO',
  timezone text not null default 'America/Santo_Domingo',
  active boolean not null default true,
  result_source text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.sorteos (
  id uuid primary key default gen_random_uuid(),
  lottery_id uuid not null references public.lotteries(id),
  code text not null,
  name text not null,
  draw_time_local time not null,
  timezone text not null default 'America/Santo_Domingo',
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (lottery_id, code)
);

create table if not exists public.horarios_cierre (
  id uuid primary key default gen_random_uuid(),
  sorteo_id uuid not null references public.sorteos(id),
  weekday int check (weekday between 0 and 6),
  close_minutes_before int not null default 5,
  close_time_override time,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.cierres_sorteos (
  id uuid primary key default gen_random_uuid(),
  sorteo_id uuid not null references public.sorteos(id),
  draw_date date not null,
  closed_at timestamptz not null default now(),
  closed_by uuid references public.profiles(id),
  reason text not null default 'scheduled',
  unique (sorteo_id, draw_date)
);

alter table public.tickets
  add column if not exists ticket_code text,
  add column if not exists qr_payload text,
  add column if not exists sorteo_id uuid references public.sorteos(id),
  add column if not exists profile_id uuid references public.profiles(id),
  add column if not exists admin_id uuid references public.profiles(id),
  add column if not exists supervisor_id uuid references public.profiles(id),
  add column if not exists banca_uuid uuid references public.bancas(id),
  add column if not exists draw_date_real date,
  add column if not exists void_until timestamptz,
  add column if not exists printed_at timestamptz,
  add column if not exists paid_at timestamptz,
  add column if not exists invalidated_at timestamptz,
  add column if not exists updated_at timestamptz not null default now();

create unique index if not exists tickets_ticket_code_uq on public.tickets(ticket_code) where ticket_code is not null;
create index if not exists tickets_profile_created_idx on public.tickets(profile_id, created_at desc);
create index if not exists tickets_admin_draw_idx on public.tickets(admin_id, draw_date_real, sorteo_id);
create index if not exists tickets_status_idx on public.tickets(status);

alter table public.ticket_items
  add column if not exists play_type public.ln_play_type,
  add column if not exists normalized_number text,
  add column if not exists lottery_id uuid references public.lotteries(id),
  add column if not exists sorteo_id uuid references public.sorteos(id),
  add column if not exists created_at timestamptz not null default now();

create index if not exists ticket_items_ticket_idx on public.ticket_items(ticket_id);
create index if not exists ticket_items_number_sorteo_idx on public.ticket_items(sorteo_id, normalized_number, play_type);

create table if not exists public.limites_numeros (
  id uuid primary key default gen_random_uuid(),
  admin_id uuid references public.profiles(id),
  banca_id uuid references public.bancas(id),
  lottery_id uuid references public.lotteries(id),
  sorteo_id uuid references public.sorteos(id),
  play_type public.ln_play_type not null,
  number_value text not null,
  max_amount numeric(14,2) not null,
  blocked boolean not null default false,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists limites_lookup_idx on public.limites_numeros(admin_id, sorteo_id, play_type, number_value);

create table if not exists public.resultados (
  id uuid primary key default gen_random_uuid(),
  lottery_id uuid not null references public.lotteries(id),
  sorteo_id uuid not null references public.sorteos(id),
  draw_date date not null,
  primera text not null,
  segunda text,
  tercera text,
  source text not null default 'edge_function',
  raw_payload jsonb,
  published_at timestamptz not null default now(),
  processed_at timestamptz,
  unique (sorteo_id, draw_date)
);

create table if not exists public.premios_config (
  id uuid primary key default gen_random_uuid(),
  admin_id uuid references public.profiles(id),
  play_type public.ln_play_type not null,
  rule_code text not null,
  multiplier numeric(14,4) not null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (admin_id, play_type, rule_code)
);

create table if not exists public.pagos (
  id uuid primary key default gen_random_uuid(),
  ticket_id uuid not null references public.tickets(id),
  paid_by uuid not null references public.profiles(id),
  amount numeric(14,2) not null check (amount >= 0),
  status text not null default 'completed',
  reference text,
  created_at timestamptz not null default now(),
  unique (ticket_id)
);

create table if not exists public.balances (
  owner_id uuid primary key references public.profiles(id) on delete cascade,
  available numeric(14,2) not null default 0 check (available >= 0),
  locked numeric(14,2) not null default 0 check (locked >= 0),
  updated_at timestamptz not null default now()
);

create table if not exists public.movimientos_balance (
  id uuid primary key default gen_random_uuid(),
  from_user_id uuid references public.profiles(id),
  to_user_id uuid references public.profiles(id),
  movement_type public.ln_balance_movement_type not null,
  amount numeric(14,2) not null check (amount > 0),
  before_balance numeric(14,2),
  after_balance numeric(14,2),
  reference text,
  status text not null default 'completed',
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

alter table public.recargas
  add column if not exists from_user_id uuid references public.profiles(id),
  add column if not exists to_user_id uuid references public.profiles(id),
  add column if not exists reference text,
  add column if not exists status text not null default 'completed',
  add column if not exists metadata jsonb not null default '{}'::jsonb,
  add column if not exists updated_at timestamptz not null default now();

create table if not exists public.auditoria (
  id uuid primary key default gen_random_uuid(),
  actor_id uuid references public.profiles(id),
  actor_role public.ln_role,
  action text not null,
  entity_table text,
  entity_id text,
  ip inet,
  user_agent text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists public.sesiones_dispositivos (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  device_id text not null,
  device_name text,
  app_version text,
  authorized boolean not null default true,
  last_seen_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  unique (user_id, device_id)
);

create table if not exists public.app_config (
  key text primary key,
  value jsonb not null,
  updated_by uuid references public.profiles(id),
  updated_at timestamptz not null default now()
);

insert into public.app_config(key, value) values
  ('maintenance', '{"enabled": false}'::jsonb),
  ('min_app_version', '{"android": "1.0.0"}'::jsonb)
on conflict (key) do nothing;

create table if not exists public.notificaciones (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid references public.profiles(id),
  title text not null,
  body text not null,
  read_at timestamptz,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists public.api_logs (
  id uuid primary key default gen_random_uuid(),
  function_name text not null,
  actor_id uuid,
  request_id text,
  status int,
  duration_ms int,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create or replace function public.ln_touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create or replace function public.ln_current_profile_id()
returns uuid
language sql
stable
as $$
  select id from public.profiles where id = auth.uid()
$$;

create or replace function public.ln_current_role()
returns public.ln_role
language sql
stable
as $$
  select role from public.profiles where id = auth.uid()
$$;

create or replace function public.ln_is_master()
returns boolean
language sql
stable
as $$
  select coalesce(public.ln_current_role() = 'master', false)
$$;

create or replace function public.ln_same_admin_network(target_admin uuid)
returns boolean
language sql
stable
as $$
  select public.ln_is_master()
    or exists (
      select 1 from public.profiles p
      where p.id = auth.uid()
        and (
          p.id = target_admin
          or p.admin_owner_id = target_admin
          or p.parent_user_id = target_admin
        )
    )
$$;

do $$
declare t_name text;
begin
  foreach t_name in array array[
    'profiles','bancas','admin_networks','supervisores','cajeros','lotteries','sorteos',
    'horarios_cierre','tickets','ticket_items','limites_numeros','resultados','premios_config',
    'pagos','balances','movimientos_balance','auditoria','sesiones_dispositivos','app_config',
    'notificaciones','api_logs','cierres_sorteos'
  ]
  loop
    execute format('alter table public.%I enable row level security', t_name);
  end loop;
end $$;

do $$
declare t_name text;
begin
  foreach t_name in array array[
    'profiles','bancas','admin_networks','supervisores','cajeros','lotteries','sorteos',
    'horarios_cierre','tickets','ticket_items','limites_numeros','resultados','premios_config',
    'pagos','balances','movimientos_balance','auditoria','sesiones_dispositivos','app_config',
    'notificaciones','api_logs','cierres_sorteos'
  ]
  loop
    if exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = t_name and column_name = 'updated_at'
    ) then
      execute format('drop trigger if exists %I on public.%I', 'trg_' || t_name || '_touch_updated_at', t_name);
      execute format('create trigger %I before update on public.%I for each row execute function public.ln_touch_updated_at()', 'trg_' || t_name || '_touch_updated_at', t_name);
    end if;
  end loop;
end $$;

-- RLS policy helper: create only if missing.
create or replace function public.ln_create_policy_if_missing(
  p_table text,
  p_name text,
  p_sql text
) returns void
language plpgsql
as $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = p_table and policyname = p_name
  ) then
    execute p_sql;
  end if;
end;
$$;

select public.ln_create_policy_if_missing(
  'profiles',
  'profiles_select_network',
  'create policy profiles_select_network on public.profiles for select using (public.ln_is_master() or id = auth.uid() or public.ln_same_admin_network(coalesce(admin_owner_id, id)))'
);

select public.ln_create_policy_if_missing(
  'tickets',
  'tickets_select_scope',
  'create policy tickets_select_scope on public.tickets for select using (public.ln_is_master() or profile_id = auth.uid() or public.ln_same_admin_network(admin_id))'
);

select public.ln_create_policy_if_missing(
  'tickets',
  'tickets_no_client_mutation',
  'create policy tickets_no_client_mutation on public.tickets for all using (false) with check (false)'
);

select public.ln_create_policy_if_missing(
  'balances',
  'balances_select_scope',
  'create policy balances_select_scope on public.balances for select using (public.ln_is_master() or owner_id = auth.uid() or exists (select 1 from public.profiles p where p.id = balances.owner_id and public.ln_same_admin_network(coalesce(p.admin_owner_id, p.id))))'
);

select public.ln_create_policy_if_missing(
  'auditoria',
  'audit_select_scope',
  'create policy audit_select_scope on public.auditoria for select using (public.ln_is_master() or actor_id = auth.uid())'
);

select public.ln_create_policy_if_missing(
  'resultados',
  'resultados_public_read',
  'create policy resultados_public_read on public.resultados for select using (true)'
);

select public.ln_create_policy_if_missing(
  'resultados',
  'resultados_no_client_write',
  'create policy resultados_no_client_write on public.resultados for all using (false) with check (false)'
);

-- Important: writes for critical tables must use Edge Functions with service_role.
revoke insert, update, delete on public.tickets from anon, authenticated;
revoke insert, update, delete on public.ticket_items from anon, authenticated;
revoke insert, update, delete on public.resultados from anon, authenticated;
revoke insert, update, delete on public.balances from anon, authenticated;
revoke insert, update, delete on public.movimientos_balance from anon, authenticated;
revoke insert, update, delete on public.auditoria from anon, authenticated;

create index if not exists profiles_role_admin_idx on public.profiles(role, admin_owner_id);
create index if not exists cajeros_admin_supervisor_idx on public.cajeros(admin_id, supervisor_id);
create index if not exists movimientos_balance_to_created_idx on public.movimientos_balance(to_user_id, created_at desc);
create index if not exists auditoria_actor_created_idx on public.auditoria(actor_id, created_at desc);
create index if not exists resultados_sorteo_date_idx on public.resultados(sorteo_id, draw_date desc);
