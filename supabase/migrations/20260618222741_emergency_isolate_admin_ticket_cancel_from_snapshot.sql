-- Keep administrative ticket cancellation on the normalized ticket ledger.
-- The legacy owner snapshot trigger rewrites large JSONB payloads synchronously
-- and can time out the entire void/delete transaction.
alter table public.tickets
  disable trigger trg_ln_ticket_cancel_snapshot;
