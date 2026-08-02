import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const monitor = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt",
  "utf8",
);
const lotteryMonitor = readFileSync(
  "app/src/main/java/com/lotterynet/pro/ui/admin/AdminLotteryMonitorActivity.kt",
  "utf8",
);

test("monitor watchdog checks the remote stamp even while realtime has handles", () => {
  for (const source of [monitor, lotteryMonitor]) {
    assert.match(source, /syncPollRunnable[\s\S]*runForegroundCatchUp\(force = false\)/);
    assert.doesNotMatch(
      source,
      /syncPollRunnable[\s\S]{0,220}if \(realtimeSubscriptions\.isNotEmpty\(\)\) return/,
    );
    assert.match(
      source,
      /subscribeRealtime\(reset = false\)[\s\S]{0,220}postDelayed\(syncPollRunnable/,
    );
  }
});

test("main monitor coalesces skipped realtime events into one trailing refresh", () => {
  assert.match(monitor, /scheduleDeferredMonitorSync\(\)/);
  assert.match(
    monitor,
    /if \(!monitorSyncInFlight\.compareAndSet\(false, true\)\) \{[\s\S]*scheduleDeferredMonitorSync\(\)/,
  );
  assert.match(
    monitor,
    /shouldSkipAdminMonitorRemoteRefresh\([\s\S]*scheduleDeferredMonitorSync\(\)/,
  );
});

test("admin and supervisor monitor header exposes the active operational date", () => {
  assert.match(monitor, /private fun SupervisorPanelHeader\([\s\S]*dayKey: String/);
  assert.match(monitor, /MonitorHeaderDateAction\(/);
  assert.match(monitor, /dayKey = dayKey/);
});

test("admin selection has real bulk actions while supervisor remains read only", () => {
  assert.match(monitor, /onActivateSelected/);
  assert.match(monitor, /onBlockSelected/);
  assert.match(monitor, /selected: Boolean\?/);
  assert.match(monitor, /SupervisorCajeroCard\([\s\S]*selected = null/);
});

test("admin and supervisor can reach one compact export menu", () => {
  assert.match(monitor, /private fun AdminMonitorExportSheet/);
  assert.match(monitor, /contentDescription = "Exportar monitor"/);
  assert.match(monitor, /"WhatsApp"/);
  assert.match(monitor, /"Guardar"/);
  assert.match(monitor, /"Imprimir"/);
});

test("all-ticket winner history is loaded only for the role that renders it", () => {
  assert.match(
    monitor,
    /monitorWinnerTicketsState = if \(session\.role == UserRole\.MASTER\)/,
  );
});

test("monitor reports refresh success or local fallback instead of failing silently", () => {
  assert.match(monitor, /monitorSyncStatusState/);
  assert.match(monitor, /"Actualizado"/);
  assert.match(monitor, /"Sin conexión · copia local"/);
});
