import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const migration = readFileSync(
  "supabase/migrations/20260618222741_emergency_isolate_admin_ticket_cancel_from_snapshot.sql",
  "utf8",
);
const rollback = readFileSync(
  "supabase/migrations/rollback/20260618222741_emergency_isolate_admin_ticket_cancel_from_snapshot.rollback.sql",
  "utf8",
);

test("administrative cancellation disables only the legacy snapshot rewrite trigger", () => {
  assert.match(
    migration,
    /alter table public\.tickets\s+disable trigger trg_ln_ticket_cancel_snapshot;/i,
  );
  assert.doesNotMatch(migration, /disable trigger all/i);
  assert.doesNotMatch(migration, /trg_ln_ticket_cashier_day_sale_limit/i);
});

test("administrative cancellation isolation has a direct rollback", () => {
  assert.match(
    rollback,
    /alter table public\.tickets\s+enable trigger trg_ln_ticket_cancel_snapshot;/i,
  );
});
