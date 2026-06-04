import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";

const root = process.cwd();

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), "utf8");
}

function migrationText(namePart) {
  const dir = path.join(root, "supabase", "migrations");
  return fs.readdirSync(dir)
    .filter((name) => name.endsWith(".sql") && name.includes(namePart))
    .map((name) => read(path.join("supabase", "migrations", name)))
    .join("\n");
}

test("server create ticket handles same clientRequestId as idempotent sale", () => {
  const migration = migrationText("idempotent_ticket_create_unique_violation");
  assert.match(migration, /unique_violation/i);
  assert.match(migration, /client_request_id\s*=\s*v_client_request_id/i);
  assert.match(migration, /duplicate/i);
  assert.match(migration, /Venta ya procesada/i);
  assert.match(migration, /ln_create_ticket_legacy\(jsonb\)/i);
});

test("Android sale keeps one stable request id until server accepts or rows change", () => {
  const contracts = read("app/src/main/java/com/lotterynet/pro/ui/sales/SalesUiContracts.kt");
  const tests = read("app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt");
  assert.match(contracts, /data class SaleSubmissionIdentity/);
  assert.match(contracts, /buildSaleSubmissionFingerprint/);
  assert.match(contracts, /current\.fingerprint == nextFingerprint/);
  assert.match(tests, /blocks a second print while server validation is running/);
  assert.match(tests, /retry keeps same client request id/);
  assert.match(tests, /retry gets new client request id when staged rows change/);
});

test("Android sale stores local ticket only after server ok", () => {
  const activity = read("app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt");
  const createIndex = activity.indexOf(".createTicket(");
  const okIndex = activity.indexOf("if (!backendResponse.optBoolean(\"ok\", false))");
  const saveIndex = activity.indexOf("salesRepository.saveTicket(securedTicket)");
  assert.ok(createIndex > 0, "createTicket call must exist");
  assert.ok(okIndex > createIndex, "server ok check must happen after createTicket");
  assert.ok(saveIndex > okIndex, "local save must happen after server ok");
  assert.match(activity, /saleSaveInFlight\s*=\s*true/);
  assert.match(activity, /pendingSaleSubmission\s*=\s*saleSubmission/);
});

test("server delete accepts admin authority and syncs owner snapshot", () => {
  const current = [
    read("supabase/migrations/20260519003000_enforce_ticket_delete_roles_and_two_minute_window.sql"),
    read("supabase/migrations/20260601183000_void_ticket_code_lookup_and_snapshot_sync.sql"),
    read("supabase/migrations/20260602104000_void_ticket_sets_legacy_estado.sql"),
  ].join("\n");
  assert.match(current, /v_role in \('admin','admins'\)/i);
  assert.match(current, /admin_key/i);
  assert.match(current, /ticket_code = nullif\(p_body ->> 'ticketId'/i);
  assert.match(current, /estado = v_next_status/i);

  const liveOptimized = read("supabase/migrations/20260602015748_optimize_prize_reconcile_owner_sync.sql");
  assert.match(liveOptimized, /lotterynet_sync_ticket_owner_payload/);
});
