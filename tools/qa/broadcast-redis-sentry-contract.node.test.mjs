import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { test } from "node:test";

const migrationPath = "supabase/migrations/20260604072458_realtime_broadcast_redis_sentry_foundation.sql";
const realtimeClientPath = "app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeClient.kt";
const realtimeSubscriptionPath = "app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeSubscription.kt";
const realtimeOrchestratorPath = "app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeOrchestrator.kt";
const upstashPath = "supabase/functions/_shared/upstash-redis.ts";
const sentryPath = "supabase/functions/_shared/sentry-edge.ts";
const adminPath = "supabase/functions/_shared/lotterynet-admin.ts";
const runbookPath = "docs/supabase/android-supabase-production-hardening-runbook.md";
const agentPath = "docs/superpowers/agent-workflow-omega-efficient.md";
const fcmServicePath = "app/src/main/java/com/lotterynet/pro/core/push/LotteryNetFirebaseMessagingService.kt";
const pushRegistrarPath = "app/src/main/java/com/lotterynet/pro/core/push/PushTokenRegistrar.kt";
const syncSchedulerPath = "app/src/main/java/com/lotterynet/pro/core/sync/LotteryNetCatchUpScheduler.kt";
const syncWorkerPath = "app/src/main/java/com/lotterynet/pro/core/sync/LotteryNetCatchUpWorker.kt";
const syncCoordinatorPath = "app/src/main/java/com/lotterynet/pro/core/sync/LotteryNetCatchUpCoordinator.kt";
const root = "C:/Users/Randy Cordero/Desktop/lotterynet_android";

function read(path) {
  return readFileSync(path, "utf8");
}

test("realtime broadcast migration emits private lightweight ticket and result signals", () => {
  assert.equal(existsSync(migrationPath), true);
  const sql = read(migrationPath);

  assert.match(sql, /realtime\.send/);
  assert.match(sql, /ln:tickets:owner:/);
  assert.match(sql, /ln:results:/);
  assert.match(sql, /lotterynet_broadcast_ticket_owner_touch/);
  assert.match(sql, /lotterynet_broadcast_result_draw_touch/);
  assert.match(sql, /lotterynet_can_receive_realtime_topic/);
  assert.match(sql, /realtime\.topic\(\)/);
  assert.match(sql, /realtime\.messages enable row level security/);
  assert.match(sql, /lotterynet_receive_private_broadcasts/);
});

test("Android realtime client uses private broadcasts for result signals", () => {
  const client = read(realtimeClientPath);
  const subscriptions = read(realtimeSubscriptionPath);
  const orchestrator = read(realtimeOrchestratorPath);
  const ticketOwnerSignals = client.slice(
    client.indexOf("fun subscribeTicketOwnerSignals"),
    client.indexOf("fun subscribeResultsSignals"),
  );

  assert.match(client, /broadcastFlow<JsonObject>/);
  assert.match(client, /subscribeBroadcast/);
  assert.match(client, /client\.realtime\.setAuth\(token\)/);
  assert.match(client, /subscribeTicketOwnerSignals/);
  assert.match(client, /subscribeResultsSignals/);
  assert.doesNotMatch(ticketOwnerSignals, /LotterynetRealtimeSubscription\.ticketOwner\(/);
  assert.match(ticketOwnerSignals, /ticketOwnerBroadcastTopic/);
  assert.doesNotMatch(client, /subscribe\(LotterynetRealtimeSubscription\.resultsDraws/);
  assert.match(subscriptions, /ticketOwnerBroadcastTopic/);
  assert.match(subscriptions, /resultsBroadcastTopic/);
  assert.match(orchestrator, /ln:tickets:owner:/);
  assert.match(orchestrator, /ln:results:/);
});

test("Android realtime broadcast subscriptions are shared per topic and skip unauthenticated private channels", () => {
  const client = read(realtimeClientPath);
  const master = read("app/src/main/java/com/lotterynet/pro/core/master/SupabaseMasterConfigRemoteStore.kt");
  const tickets = read("app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt");

  assert.match(client, /sharedBroadcastSubscriptions/);
  assert.match(client, /buildBroadcastSubscriptionKey/);
  assert.match(client, /releaseSharedBroadcastSubscription/);
  assert.match(client, /Skipping private realtime broadcast for \$topic because no auth token is available/);
  assert.match(client, /subscribeBroadcast\(/);
  assert.match(master, /authScopeKey\(bearerToken\)/);
  assert.match(master, /private fun authScopeKey\(bearerToken: String\?\): String/);
  assert.match(tickets, /authScopeKey\(bearerToken\)/);
  assert.match(tickets, /private fun authScopeKey\(bearerToken: String\?\): String/);
});

test("users-state realtime stays on the private broadcast path", () => {
  const result = spawnSync(
    "rg",
    ["-n", "subscribeUsersStateSignals\\(", "app/src/main/java/com/lotterynet/pro/ui"],
    {
      cwd: root,
      encoding: "utf8",
    },
  );

  assert.equal(result.status, 0);
  assert.match(result.stdout, /MasterDashboardActivity/);
  assert.match(result.stdout, /RecargasActivity/);
  assert.match(result.stdout, /SalesActivity/);
  const client = read("app/src/main/java/com/lotterynet/pro/core/realtime/LotterynetRealtimeClient.kt");
  const method = client.slice(client.indexOf("fun subscribeUsersStateSignals"), client.indexOf("fun shutdown()"));
  assert.match(method, /subscribeBroadcast\(/);
  assert.doesNotMatch(method, /postgresChangeFlow/);
});

test("main ticket and result screens use broadcast signals with fresh JWT", () => {
  const ticketSummary = read("app/src/main/java/com/lotterynet/pro/ui/tickets/TicketSummaryActivity.kt");
  const results = read("app/src/main/java/com/lotterynet/pro/ui/results/ResultsActivity.kt");
  const winners = read("app/src/main/java/com/lotterynet/pro/ui/admin/AdminWinnersActivity.kt");
  const sales = read("app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt");

  for (const source of [ticketSummary, winners, sales]) {
    assert.match(source, /subscribeTicketOwnerSignals/);
    assert.match(source, /freshAccessToken\(\)/);
  }
  assert.match(results, /subscribeResultsSignals/);
  assert.match(results, /freshAccessToken\(\)/);
  assert.match(sales, /subscribeResultsSignals/);
  assert.doesNotMatch(sales, /LotterynetRealtimeSubscription\.resultsCache/);
});

test("Upstash cache is optional and wired behind Supabase KV truth", () => {
  assert.equal(existsSync(upstashPath), true);
  const redis = read(upstashPath);
  const admin = read(adminPath);

  assert.match(redis, /UPSTASH_REDIS_REST_URL/);
  assert.match(redis, /UPSTASH_REDIS_REST_TOKEN/);
  assert.match(redis, /return null/);
  assert.match(admin, /redisGetJson/);
  assert.match(admin, /redisSetJson/);
  assert.match(admin, /upsertKvValue/);
});

test("Sentry edge helper exists and filters sensitive context", () => {
  assert.equal(existsSync(sentryPath), true);
  const sentry = read(sentryPath);

  assert.match(sentry, /SENTRY_DSN/);
  assert.match(sentry, /captureEdgeError/);
  assert.match(sentry, /password|token|secret|authorization|key/);
  assert.doesNotMatch(sentry, /payloadJson/);
});

test("runbooks document server-first money broadcast-as-signal and agent limits", () => {
  const runbook = read(runbookPath);
  const agent = read(agentPath);

  assert.match(runbook, /server-first/);
  assert.match(runbook, /Realtime Broadcast es una señal/);
  assert.match(runbook, /No registrar/);
  assert.match(agent, /Máximo 3 a 5 archivos/);
  assert.match(agent, /Dinero sigue server-first/);
});

test("omega execution map keeps agents scoped by money domain", () => {
  const map = read("docs/superpowers/plans/2026-06-05-omega-domain-execution-map.md");

  for (const domain of ["Venta", "Tickets", "Ganadores", "Resultados", "Finanzas", "Admin/Cajero", "Deportes"]) {
    assert.match(map, new RegExp(`Dominio: ${domain}`));
  }
  assert.match(map, /server-first/);
  assert.match(map, /Broadcast es señal/);
  assert.match(map, /Redis es cache auxiliar/);
  assert.match(map, /Si toca dinero: prueba Node o Kotlin obligatoria/);
});

test("FCM is wired only as a minimal catch-up wake signal", () => {
  const manifest = read("app/src/main/AndroidManifest.xml");
  const appGradle = read("app/build.gradle.kts");
  const rootGradle = read("build.gradle.kts");
  const fcm = read(fcmServicePath);
  const registrar = read(pushRegistrarPath);
  const config = read("supabase/config.toml");
  const registerFunction = read("supabase/functions/register-push-token/index.ts");
  const sendFunction = read("supabase/functions/send-operational-push/index.ts");

  assert.match(rootGradle, /com\.google\.gms\.google-services/);
  assert.match(appGradle, /google-services\.json/);
  assert.match(appGradle, /firebase-messaging/);
  assert.match(manifest, /FirebaseMessagingService/);
  assert.match(fcm, /onMessageReceived/);
  assert.match(fcm, /LotteryNetCatchUpScheduler\.enqueueImmediate/);
  assert.doesNotMatch(fcm, /amount|money|jugada|ticket_code|serial/i);
  assert.match(registrar, /register-push-token/);
  assert.match(registrar, /ownerKeyHash/);
  assert.match(config, /\[functions\.register-push-token\][\s\S]*?verify_jwt = true/);
  assert.match(registerFunction, /authenticatedActor/);
  assert.match(registerFunction, /lotterynet_push_tokens/);
  assert.match(sendFunction, /requireSharedSecret/);
  assert.match(sendFunction, /firebase\.messaging/);
});

test("SyncCenter has module reasons and single-flight worker inputs", () => {
  const scheduler = read(syncSchedulerPath);
  const worker = read(syncWorkerPath);
  const coordinator = read(syncCoordinatorPath);
  const uiState = read("app/src/main/java/com/lotterynet/pro/core/sync/OperationalUiState.kt");

  for (const reason of ["APP_START", "FOREGROUND", "BROADCAST", "FCM", "MANUAL_REFRESH", "PERIODIC"]) {
    assert.match(read("app/src/main/java/com/lotterynet/pro/core/sync/SyncReason.kt"), new RegExp(reason));
  }
  for (const key of ["KEY_FORCE_TICKETS", "KEY_FORCE_RESULTS", "KEY_FORCE_FINANCE", "KEY_FORCE_CONFIG", "KEY_FORCE_SPORTS"]) {
    assert.match(worker, new RegExp(key));
  }
  assert.match(scheduler, /ExistingWorkPolicy\.KEEP/);
  assert.match(coordinator, /AtomicBoolean/);
  assert.match(coordinator, /SyncModuleResult/);
  assert.match(coordinator, /name = "finance"/);
  assert.match(coordinator, /measureSyncModule\("sports"/);
  for (const state of ["LoadingLocal", "ReadyLocal", "CatchingUp", "Fresh", "ErrorRecoverable"]) {
    assert.match(uiState, new RegExp(state));
  }
});
