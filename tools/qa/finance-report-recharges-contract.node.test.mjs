import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const migration = readFileSync(
  "supabase/migrations/20260609071907_include_recharges_in_legacy_report.sql",
  "utf8",
);

const ticketCountMigration = readFileSync(
  "supabase/migrations/20260609073747_correct_legacy_report_ticket_counts.sql",
  "utf8",
);

const netFormulaMigration = readFileSync(
  "supabase/migrations/20260609073900_correct_legacy_report_net_formula.sql",
  "utf8",
);

const remoteReportRepository = readFileSync(
  "app/src/main/java/com/lotterynet/pro/core/finance/RemoteOperationalReportRepository.kt",
  "utf8",
);

test("legacy report includes completed recharge requests without double counting owner snapshots", () => {
  assert.match(migration, /public\.lotterynet_recharge_requests/);
  assert.match(migration, /distinct on \(recharge_id\)/);
  assert.match(migration, /lower\(coalesce\(r\.status, ''\)\) in \('completed'/);
  assert.match(migration, /totalRecargas/);
  assert.match(migration, /'recargas', x\.recargas/);
});

test("legacy report net does not subtract voided tickets twice", () => {
  assert.match(netFormulaMigration, /v_total_vendido \+ v_total_recargas - v_total_premios - v_comision - v_supervisor_comision/);
});

test("legacy report ticket counts exclude voided and invalid tickets like local finance", () => {
  assert.match(ticketCountMigration, /count\(\*\) filter \(where upper\(status\) in \(''VALIDO'',''VALID'',''GANADOR'',''PERDEDOR'',''PAGADO''\)\)::integer/);
  assert.match(ticketCountMigration, /count\(\*\) filter \(where upper\(t\.status\) in \(''VALIDO'',''VALID'',''GANADOR'',''PERDEDOR'',''PAGADO''\)\)::integer as tickets/);
});

test("Android remote report parser uses server recargas in caja disponible", () => {
  assert.match(remoteReportRepository, /val recargas = optDouble\("totalRecargas", optDouble\("recargas", 0\.0\)\)/);
  assert.match(remoteReportRepository, /val recargas = optDouble\("recargas", optDouble\("totalRecargas", 0\.0\)\)/);
  assert.match(remoteReportRepository, /recargas = recargas/);
  assert.match(remoteReportRepository, /cajaDisponible = ventas \+ recargas - premiosPagados - premiosPendientes - comision - supervisorComision/);
  assert.match(remoteReportRepository, /cajaDisponible = ventas \+ recargas - premiosPagados - pending - comision/);
});
