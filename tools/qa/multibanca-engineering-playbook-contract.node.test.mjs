import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { test } from "node:test";

const playbookPath = "docs/supabase/multibanca-engineering-playbook.md";
const serverFirstPath = "docs/supabase/production-server-first-multibanca.md";
const callVolumePath = "docs/supabase/production-call-volume-hardening.md";
const planPath = "docs/superpowers/plans/2026-06-02-ticket-duplicates-and-fast-sale-feedback.md";
const resultsContractPath = "tools/qa/results-migration-contract.node.test.mjs";
const winnerAliasContractPath = "tools/qa/winner-owner-snapshot-alias-sync.node.test.mjs";
const resultsRefreshFunctionPath = "supabase/functions/results-server-refresh/index.ts";

function read(path) {
  return readFileSync(path, "utf8");
}

test("multi-banca engineering playbook exists and is linked from production docs", () => {
  assert.equal(existsSync(playbookPath), true);

  const serverFirst = read(serverFirstPath);
  const plan = read(planPath);

  assert.match(serverFirst, /multibanca-engineering-playbook\.md/);
  assert.match(plan, /multibanca-engineering-playbook\.md/);
  assert.match(plan, /production-server-first-multibanca\.md/);
  assert.match(plan, /production-call-volume-hardening\.md/);
});

test("playbook keeps money server-first and reads cache-first with catch-up", () => {
  const playbook = read(playbookPath);

  assert.match(playbook, /Server-first para dinero/);
  assert.match(playbook, /Cache-first para lectura/);
  assert.match(playbook, /Supabase es la fuente final/);
  assert.match(playbook, /Android puede mostrar cache local primero/);
  assert.match(playbook, /vuelve de segundo plano/);
  assert.match(playbook, /reconciliar con servidor/);
  assert.match(playbook, /no debe inventar un ticket oficial/);
});

test("playbook requires canonical owner identity instead of visible names", () => {
  const playbook = read(playbookPath);
  const plan = read(planPath);

  assert.match(playbook, /Identidad Canonica/);
  assert.match(playbook, /Nunca usar nombre visible como identidad principal/);
  assert.match(playbook, /admin_key/);
  assert.match(playbook, /cashier_key/);
  assert.match(playbook, /supervisor_key/);
  assert.match(playbook, /aliases legacy/);
  assert.match(playbook, /Cambiar el nombre de un cajero no debe mover tickets viejos/);

  assert.match(plan, /Canonical Identity, Owner Routing, And Multi-Banca Reconciliation/);
  assert.match(plan, /visible names, usernames, legacy ids, or display labels differ/);
  assert.match(plan, /username\/display name changes do not move old tickets/);
});

test("playbook and plan require durable bounded jobs instead of all-day scans", () => {
  const playbook = read(playbookPath);
  const plan = read(planPath);

  assert.match(playbook, /Jobs Y Colas/);
  assert.match(playbook, /No procesar "todo el dia" en una sola llamada/);
  assert.match(playbook, /max jobs por corrida/);
  assert.match(playbook, /max tickets por job/);
  assert.match(playbook, /retry_after/);
  assert.match(playbook, /idempotencia/);

  assert.match(plan, /Server Backpressure, Durable Queue, And No-Stall Ticket Processing/);
  assert.match(plan, /max jobs per run/);
  assert.match(plan, /max tickets per job/);
  assert.match(plan, /FOR UPDATE SKIP LOCKED|PGMQ visibility timeout/);
  assert.match(plan, /retry\/backoff fields exist/);
});

test("playbook requires realtime recovery after background instead of refresh-only UX", () => {
  const playbook = read(playbookPath);
  const plan = read(planPath);

  assert.match(playbook, /Realtime No Es Garantia Final/);
  assert.match(playbook, /al volver debe hacer catch-up/);
  assert.match(playbook, /revisar estado de canal\/reconectar/);
  assert.match(playbook, /pedir stamps\/versiones/);
  assert.match(playbook, /Estados terminales que local nunca debe degradar/);

  assert.match(plan, /Android Foreground\/Background Freshness QA/);
  assert.match(plan, /ProcessLifecycleOwner/);
  assert.match(plan, /foreground catch-up/);
  assert.match(plan, /empty local cache forces server load on first app entry/);
});

test("production docs keep call-volume hardening and bounded result job contracts", () => {
  const callVolume = read(callVolumePath);
  const resultsRefreshFunction = read(resultsRefreshFunctionPath);
  const winnerAliasContract = read(winnerAliasContractPath);

  assert.match(callVolume, /Realtime no debe cargar datos completos/);
  assert.match(callVolume, /SyncGovernor/);
  assert.match(callVolume, /Maximo una descarga grande normal por `ownerKey` cada 30 segundos/);
  assert.match(callVolume, /get-results-status/);

  assert.match(resultsRefreshFunction, /p_job_limit:\s*10/);
  assert.match(resultsRefreshFunction, /p_ticket_limit:\s*300/);
  assert.match(winnerAliasContract, /optimized prize sync avoids global owner snapshot scans/);
  assert.match(winnerAliasContract, /fast lookup indexes/);
});

test("playbook requires broader testing than Node-only checks", () => {
  const playbook = read(playbookPath);
  const plan = read(planPath);

  assert.match(playbook, /No basta una prueba Node aislada/);
  assert.match(playbook, /SQL\/contract/);
  assert.match(playbook, /Node real QA/);
  assert.match(playbook, /Kotlin unit/);
  assert.match(playbook, /Instrumentation\/manual POS/);

  assert.match(plan, /Full Production Readiness Test Suite/);
  assert.match(plan, /SQL contract tests/);
  assert.match(plan, /Node real API tests/);
  assert.match(plan, /Kotlin unit tests/);
  assert.match(plan, /Android instrumentation tests/);
  assert.match(plan, /Manual POS checklist/);
});
