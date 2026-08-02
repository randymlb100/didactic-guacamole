import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const policyPath =
  "app/src/main/java/com/lotterynet/pro/core/finance/OperationalReportLoadPolicy.kt";
const activityPath =
  "app/src/main/java/com/lotterynet/pro/ui/report/OperationalReportActivity.kt";
const modelsPath =
  "app/src/main/java/com/lotterynet/pro/core/finance/OperationalReportModels.kt";

test("report endpoint policy keeps the server as the financial authority", async () => {
  const source = await readFile(policyPath, "utf8");
  assert.match(source, /A local projection or an old decision can never replace the authoritative report/);
  assert.match(source, /return true/);
  assert.match(source, /activeRequestId == completedRequestId/);
  assert.doesNotMatch(
    source,
    /ventas|recargas|comision|premios|cajaDisponible|resolveOperationalReportNet/,
  );
});

test("Reporte shows only an official cached copy before remote verification", async () => {
  const source = await readFile(activityPath, "utf8");
  assert.match(source, /val localReport = buildOperationalReportViewState/);
  assert.match(source, /serverReportCache\.read/);
  assert.match(source, /reportState = serverCachedReport\?\.copy/);
  assert.doesNotMatch(source, /reportState = localReport/);
  assert.match(source, /shouldFetchOperationalReportEndpoint/);
  assert.match(source, /isOperationalReportRequestCurrent/);
});

test("Reporte does not block on cashier limits", async () => {
  const source = await readFile(activityPath, "utf8");
  assert.doesNotMatch(source, /CashierLimitCloudSyncCoordinator/);
});

test("Remote report endpoint remains the final authority", async () => {
  const source = await readFile(activityPath, "utf8");
  assert.match(
    source,
    /if \(\s*shouldFetchOperationalReportEndpoint\([\s\S]*?RemoteOperationalReportRepository/,
  );
});

test("Reporte applies each filter selection once and dismissal does not duplicate it", async () => {
  const source = await readFile(activityPath, "utf8");
  assert.match(source, /primaryActionLabel = "Aplicar filtro"/);
  assert.match(source, /onPrimaryAction = onApply/);
  assert.match(source, /if \(draftManualTarget == OperationalReportManualTarget\.TO\)/);
  assert.match(source, /draftManualTarget == OperationalReportManualTarget\.TO[\s\S]*?onApplyFilters\(/);
  assert.match(source, /filtersSheetVisible = false/);
  assert.match(source, /onApplyFilters = \{ selection ->/);
  assert.match(source, /onFilterSelected = \{\s*draftActorFilter = it[\s\S]*?onApplyFilters\(/);
  assert.match(source, /onDismiss = \{ filtersSheetVisible = false \}/);
  assert.doesNotMatch(source, /onDismiss = ::applyDraftFilters/);
  assert.match(
    source,
    /onPeriodSelected = \{ period ->[\s\S]*?period != OperationalReportPeriod\.MANUAL[\s\S]*?onApplyFilters\(/,
  );
});

test("historical manual report skips the heavy session synchronization", async () => {
  const policy = await readFile(policyPath, "utf8");
  const activity = await readFile(activityPath, "utf8");
  assert.match(policy, /shouldSynchronizeOperationalReportDependencies/);
  assert.match(policy, /request\.forceRemote/);
  assert.match(policy, /request\.toDayKey/);
  assert.match(activity, /shouldSynchronizeOperationalReportDependencies\(/);
  assert.match(activity, /if \(shouldSynchronizeDependencies\)/);
});

test("a newer report request cancels the previous lifecycle job", async () => {
  const source = await readFile(activityPath, "utf8");
  assert.match(source, /private var reportLoadJob: Job\?/);
  assert.match(source, /reportLoadJob\?\.cancel\(\)/);
  assert.match(source, /lifecycleScope\.launch\(Dispatchers\.IO\)/);
  assert.match(source, /ensureActive\(\)/);
});

test("Reporte keeps one refresh action and one actor surface", async () => {
  const source = await readFile(activityPath, "utf8");
  assert.equal(
    source.match(/contentDescription = "Actualizar servidor"/g)?.length,
    1,
  );
  assert.doesNotMatch(source, /label = "Actualizar servidor"/);
  assert.match(source, /private fun OperationalReportStatusRow/);
  assert.match(source, /private fun OperationalReportActorList/);
});

test("Reporte keeps the existing financial result formula", async () => {
  const source = await readFile(modelsPath, "utf8");
  assert.match(
    source,
    /return summary\.ventas \+ summary\.recargas - summary\.comision - summary\.supervisorComision - summary\.premiosPagados - summary\.premiosPendientes/,
  );
});

test("Reporte Kotlin source remains structurally balanced", async () => {
  const source = await readFile(activityPath, "utf8");
  assert.equal(
    [...source].filter((character) => character === "{").length,
    [...source].filter((character) => character === "}").length,
  );
  assert.equal(
    [...source].filter((character) => character === "(").length,
    [...source].filter((character) => character === ")").length,
  );
});
