import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const migration = readFileSync(
  "supabase/migrations/20260609070050_canonicalize_finance_cashier_aliases.sql",
  "utf8",
);

const remoteReportRepository = readFileSync(
  "app/src/main/java/com/lotterynet/pro/core/finance/RemoteOperationalReportRepository.kt",
  "utf8",
);

test("finance report migration groups cashier aliases under one canonical identity", () => {
  assert.match(migration, /ln_legacy_report_actor_identity/);
  assert.match(migration, /resolved_cashiers as/);
  assert.match(migration, /cashier_totals as/);
  assert.match(migration, /canonical_norm/);
  assert.match(migration, /raw_cashier_keys/);
  assert.match(migration, /cashier_label/);
});

test("Android remote finance parser displays the server label while keeping the stable key", () => {
  assert.match(remoteReportRepository, /optString\("cashier_key"\)/);
  assert.match(remoteReportRepository, /optString\("cashier_label"\)/);
  assert.match(remoteReportRepository, /actorKey = key/);
  assert.match(remoteReportRepository, /actorDisplay = label/);
});
