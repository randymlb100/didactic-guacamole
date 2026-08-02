import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const migrationPath = "supabase/migrations/20260618143000_optimize_ticket_snapshot_write_path.sql";
const edgePath = "supabase/functions/get-ticket-list/index.ts";

function read(path) {
  return readFileSync(path, "utf8");
}

test("ticket snapshot migration serializes and skips unchanged owner writes", () => {
  const migration = read(migrationPath);

  assert.match(migration, /create or replace function public\.lotterynet_upsert_ticket_owner_snapshot/i);
  assert.match(migration, /pg_advisory_xact_lock\s*\(\s*hashtextextended/i);
  assert.match(migration, /is not distinct from/i);
  assert.doesNotMatch(migration, /on conflict\s*\(\s*owner_key\s*\)/i);
  assert.match(migration, /set_config\(\s*'lotterynet\.skip_terminal_ticket_recalculation'\s*,\s*'on'/i);
  assert.match(migration, /grant execute on function public\.lotterynet_upsert_ticket_owner_snapshot/i);
  assert.match(migration, /to service_role/i);
});

test("terminal-state preservation uses one previous-ticket lookup map", () => {
  const migration = read(migrationPath);

  assert.match(migration, /previous_ticket_map/i);
  assert.match(migration, /jsonb_object_agg/i);
  assert.match(
    migration,
    /previous_status\s*=\s*any\s*\(\s*paid_statuses\s*\)\s*or\s+previous_status\s*=\s*'winner'[\s\S]*not\s+server_authoritative[\s\S]*then\s+previous_ticket/i,
  );
  assert.doesNotMatch(
    migration,
    /for\s+incoming_ticket[\s\S]*jsonb_array_elements\s*\(\s*previous_tickets\s*\)/i,
  );
});

test("snapshot deletion protection builds active identifier and deleted-id maps once", () => {
  const migration = read(migrationPath);

  assert.match(migration, /active_ticket_identifiers/i);
  assert.match(migration, /deleted_id_map/i);
  assert.doesNotMatch(
    migration,
    /from\s+public\.tickets\s+tk[\s\S]*tk\.client_request_id\s*=\s*d\.id[\s\S]*or\s+tk\.legacy_ticket_id\s*=\s*d\.id/i,
  );
});

test("deleted identifier lookup has a partial legacy ticket index", () => {
  const migration = read(migrationPath);

  assert.match(migration, /create index if not exists tickets_legacy_ticket_id_active_idx/i);
  assert.match(migration, /\(\s*legacy_ticket_id\s*\)/i);
  assert.match(migration, /where\s+legacy_ticket_id\s+is\s+not\s+null/i);
});

test("get-ticket-list keeps legacy snapshot writes behind the emergency gate", () => {
  const edge = read(edgePath);
  const upsertAction = edge.slice(
    edge.indexOf('if (action === "upsert")'),
    edge.indexOf("const boundedFetchRange"),
  );

  assert.match(upsertAction, /deferred:\s*true/);
  assert.match(upsertAction, /status:\s*503/);
  assert.match(upsertAction, /Retry-After/);
  assert.doesNotMatch(upsertAction, /\.from\(|\.rpc\(/);
});
test("get-ticket-list rejects placeholder owner aliases before database queries", () => {
  const edge = read(edgePath);

  assert.match(edge, /validIdentityKey/);
  assert.match(edge, /ownerKey = validIdentityKey\(body\.ownerKey \?\? body\.owner_key\)/);
  assert.match(
    edge,
    /ownerKeys[\s\S]*\.map\(validIdentityKey\)\.filter\(Boolean\)/,
  );
});
