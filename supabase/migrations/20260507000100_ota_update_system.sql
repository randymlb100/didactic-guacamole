create extension if not exists pgcrypto with schema extensions;

create table if not exists public.ota_releases (
    id uuid primary key default gen_random_uuid(),
    package_name text not null default 'com.lotterynet.pro' check (package_name ~ '^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$'),
    release_channel text not null default 'production' check (release_channel in ('production', 'beta', 'internal')),
    version_code integer not null check (version_code > 0),
    version_name text not null check (length(trim(version_name)) > 0),
    minimum_version integer not null default 1 check (minimum_version > 0),
    force_update boolean not null default false,
    title text not null default 'Nueva actualizacion',
    changelog jsonb not null default '[]'::jsonb,
    allowed_roles text[] not null default array['master', 'admin', 'supervisor', 'cajero'],
    storage_bucket text not null default 'ota-apks',
    storage_path text not null check (length(trim(storage_path)) > 0 and lower(storage_path) like '%.apk'),
    apk_sha256 text not null check (apk_sha256 ~ '^[A-Fa-f0-9]{64}$'),
    apk_size_bytes bigint not null default 0 check (apk_size_bytes >= 0),
    active boolean not null default false,
    published_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists ota_releases_version_code_key
    on public.ota_releases(package_name, release_channel, version_code);

create index if not exists ota_releases_active_version_idx
    on public.ota_releases(package_name, release_channel, active, version_code desc, published_at desc);

create index if not exists ota_releases_allowed_roles_idx
    on public.ota_releases using gin(allowed_roles);

create unique index if not exists ota_releases_single_active_production_idx
    on public.ota_releases(package_name, release_channel)
    where active and release_channel = 'production';

create table if not exists public.ota_update_logs (
    id uuid primary key default gen_random_uuid(),
    event text not null check (event in (
        'check',
        'update_available',
        'download_started',
        'download_completed',
        'install_opened',
        'dismissed',
        'error'
    )),
    user_id text,
    username text,
    role text,
    package_name text,
    release_channel text,
    current_version_code integer,
    current_version_name text,
    target_version_code integer,
    status text,
    message text,
    created_at timestamptz not null default now()
);

create index if not exists ota_update_logs_created_idx
    on public.ota_update_logs(created_at desc);

create index if not exists ota_update_logs_user_idx
    on public.ota_update_logs(user_id, created_at desc);

create or replace function public.set_ota_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists set_ota_releases_updated_at on public.ota_releases;
create trigger set_ota_releases_updated_at
    before update on public.ota_releases
    for each row
    execute function public.set_ota_updated_at();

alter table public.ota_releases enable row level security;
alter table public.ota_update_logs enable row level security;

drop policy if exists "ota releases are not readable directly by clients" on public.ota_releases;
drop policy if exists "ota logs are not readable directly by clients" on public.ota_update_logs;

create policy "ota releases are not readable directly by clients"
    on public.ota_releases
    for select
    using (false);

create policy "ota logs are not readable directly by clients"
    on public.ota_update_logs
    for select
    using (false);

revoke all on public.ota_releases from anon, authenticated;
revoke all on public.ota_update_logs from anon, authenticated;
revoke execute on function public.set_ota_updated_at() from anon, authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'ota-apks',
    'ota-apks',
    false,
    250000000,
    array['application/vnd.android.package-archive', 'application/octet-stream']
)
on conflict (id) do update
set
    public = false,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;
