import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const sourcePath =
  "app/src/main/java/com/lotterynet/pro/ui/admin/AdminMonitorActivity.kt";
const source = fs.readFileSync(sourcePath, "utf8");

function functionBody(name, nextName) {
  const start = source.indexOf(`private fun ${name}(`);
  assert.notEqual(start, -1, `Missing ${name}`);
  const end = nextName
    ? source.indexOf(`private fun ${nextName}(`, start + 1)
    : source.length;
  assert.notEqual(end, -1, `Missing boundary ${nextName}`);
  return source.slice(start, end);
}

test("monitor header owns compact export and refresh actions", () => {
  const header = functionBody("SupervisorPanelHeader", "MonitorHeaderDateAction");
  assert.match(header, /onExport:\s*\(\)\s*->\s*Unit/);
  assert.match(header, /Icons\.Rounded\.Download/);
  assert.match(header, /Icons\.Rounded\.Sync/);
});

test("cashier search, filter and visible count share one toolbar", () => {
  const toolbar = functionBody("MonitorCashierToolbar", "SupervisorPanelHeader");
  assert.match(toolbar, /OutlinedTextField/);
  assert.match(toolbar, /visibleCount/);
  assert.match(toolbar, /onFilterClick/);
});

test("admin bulk controls are contextual instead of permanently occupying space", () => {
  const route = functionBody("AdminCashierPanelRoute", "CashierQuickActionSheet");
  assert.match(route, /MonitorCashierToolbar/);
  assert.match(route, /if\s*\(selectedIds\.isNotEmpty\(\)\)/);
});

test("supervisor uses the same compact cashier toolbar", () => {
  const route = functionBody("SupervisorPanelRoute", "AdminCashierPanelRoute");
  assert.match(route, /MonitorCashierToolbar/);
});
