-- Preserve the authoritative sports result used for settlement.
-- Isolated to sports_events; lottery and Pick schemas are untouched.
alter table public.sports_events
  add column if not exists result_source text,
  add column if not exists result_payload jsonb not null default '{}'::jsonb,
  add column if not exists result_updated_at timestamptz;

create index if not exists sports_events_result_status_idx
  on public.sports_events(status, result_updated_at desc);
