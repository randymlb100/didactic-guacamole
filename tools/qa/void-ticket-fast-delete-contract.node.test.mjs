import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

function read(path) {
  return readFileSync(path, "utf8");
}

test("void ticket delete path does not force heavy owner payload rebuild", () => {
  const migration = read("supabase/migrations/20260602143000_void_ticket_skips_heavy_owner_payload_sync.sql");

  assert.match(migration, /ln_void_ticket_legacy\(jsonb\)/);
  assert.match(migration, /lotterynet_sync_ticket_owner_payload\(v_ticket\.id\)/);
  assert.match(migration, /replace\(/);
});

test("terminal snapshot preservation skips touch-only updates", () => {
  const migration = read("supabase/migrations/20260602144000_skip_terminal_snapshot_preserve_on_touch_only.sql");

  assert.match(migration, /lotterynet_preserve_terminal_ticket_state\(\)/);
  assert.match(migration, /new\.payload is not distinct from old\.payload/);
  assert.match(migration, /return new/);
});
