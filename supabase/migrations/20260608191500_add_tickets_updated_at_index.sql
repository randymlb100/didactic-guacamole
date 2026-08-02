-- Supports ticket delta/stamp queries that order or filter by updated_at.
-- Existing owner-specific indexes remain in place; this covers global updated_at access paths.
create index if not exists tickets_updated_at_idx
  on public.tickets using btree (updated_at desc);
