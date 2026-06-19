# Canonical Ticket Owner Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop ticket lists from shrinking when Nicolás or one of his cashiers is addressed through an ID, username, UUID, or stale `"null"` snapshot.

**Architecture:** Resolve every session to one canonical administrator/cashier identity, query authoritative normalized tickets through bounded authenticated requests, and merge remote results monotonically into Android state. Keep snapshot upserts and unbounded reads disabled; quarantine invalid snapshot owners only after a read-only reconciliation proves every ticket recoverable.

**Tech Stack:** Kotlin/Android, JUnit, Node.js contract tests, Supabase Edge Functions (Deno/TypeScript), PostgreSQL 15+, Supabase MCP/CLI.

## Global Constraints

- Never delete or rewrite authoritative `public.tickets` or `public.ticket_items` during this repair.
- Reject blank, `"null"`, and `"undefined"` owner keys before network or database access.
- Preserve terminal server states and tombstones as authoritative.
- A partial, stale, unauthenticated, timed-out, or degraded response must never reduce visible local tickets.
- Keep full-history JSONB upserts and unbounded fetches disabled.
- Every production mutation requires a pre-change backup, rollback SQL, and count-regression gate.
- All ticket reads require a date range and an explicit limit no greater than 1,000.
- Do not add an index until `EXPLAIN (ANALYZE, BUFFERS)` demonstrates that an existing index is insufficient.

---

### Task 1: Canonical Android Owner Identity

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/core/sync/CanonicalOwnerIdentity.kt`
- Create: `app/src/test/java/com/lotterynet/pro/core/sync/CanonicalOwnerIdentityTest.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalSyncCoordinator.kt`

**Interfaces:**
- Consumes: `ActiveSession`
- Produces:
  - `data class CanonicalOwnerIdentity(val canonicalOwnerKey: String, val aliases: List<String>)`
  - `fun resolveCanonicalOwnerIdentity(session: ActiveSession?): CanonicalOwnerIdentity?`
  - `fun normalizeOperationalOwnerKey(value: String?): String?`

- [ ] **Step 1: Write the failing normalization tests**

```kotlin
class CanonicalOwnerIdentityTest {
    @Test fun `rejects placeholder owner values`() {
        assertNull(normalizeOperationalOwnerKey(null))
        assertNull(normalizeOperationalOwnerKey(""))
        assertNull(normalizeOperationalOwnerKey(" null "))
        assertNull(normalizeOperationalOwnerKey("undefined"))
    }

    @Test fun `admin legacy id wins over alias and uuid`() {
        val session = ActiveSession(
            userId = "5e9553d2-72b2-484e-8b85-095fbce6f2a4",
            username = "nicola01",
            role = UserRole.ADMIN,
            adminId = "ADM-163C38",
            adminUser = "nicola01",
        )
        assertEquals(
            CanonicalOwnerIdentity(
                canonicalOwnerKey = "ADM-163C38",
                aliases = listOf("nicola01", "5e9553d2-72b2-484e-8b85-095fbce6f2a4"),
            ),
            resolveCanonicalOwnerIdentity(session),
        )
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.core.sync.CanonicalOwnerIdentityTest"
```

Expected: compilation failure because `CanonicalOwnerIdentity` and resolver functions do not exist.

- [ ] **Step 3: Implement the minimal canonical resolver**

```kotlin
data class CanonicalOwnerIdentity(
    val canonicalOwnerKey: String,
    val aliases: List<String>,
)

fun normalizeOperationalOwnerKey(value: String?): String? =
    value?.trim()?.takeIf {
        it.isNotBlank() &&
            !it.equals("null", ignoreCase = true) &&
            !it.equals("undefined", ignoreCase = true)
    }

fun resolveCanonicalOwnerIdentity(session: ActiveSession?): CanonicalOwnerIdentity? {
    session ?: return null
    val valid = listOf(
        session.adminId,
        session.adminUser,
        session.userId,
        session.username,
        session.authUserId,
    ).mapNotNull(::normalizeOperationalOwnerKey)
        .distinctBy(String::lowercase)
    val canonical = valid.firstOrNull { it.startsWith("ADM-", ignoreCase = true) }
        ?: valid.firstOrNull()
        ?: return null
    return CanonicalOwnerIdentity(
        canonicalOwnerKey = canonical,
        aliases = valid.filterNot { it.equals(canonical, ignoreCase = true) },
    )
}
```

Update `resolveOperationalOwnerKey`, `resolveOperationalOwnerKeys`, and `resolveOperationalHydrationOwnerKeys` to delegate to this resolver and return one canonical hydration key.

- [ ] **Step 4: Run focused and existing owner-resolution tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.core.sync.CanonicalOwnerIdentityTest" --tests "com.lotterynet.pro.core.sync.NativeOperationalSyncContractsTest"
```

Expected: PASS.

- [ ] **Step 5: Commit only Task 1 files**

```powershell
git add app/src/main/java/com/lotterynet/pro/core/sync/CanonicalOwnerIdentity.kt app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalSyncCoordinator.kt app/src/test/java/com/lotterynet/pro/core/sync/CanonicalOwnerIdentityTest.kt
git commit -m "fix(android): canonicalize ticket owner identities"
```

---

### Task 2: Monotonic Ticket Reconciliation

**Files:**
- Create: `app/src/main/java/com/lotterynet/pro/core/sync/MonotonicTicketReconciler.kt`
- Create: `app/src/test/java/com/lotterynet/pro/core/sync/MonotonicTicketReconcilerTest.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinator.kt`
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalHydration.kt`

**Interfaces:**
- Consumes: existing local tickets, bounded remote tickets, deleted IDs, `completeScope`
- Produces:

```kotlin
fun reconcileMonotonicTickets(
    existing: List<TicketRecord>,
    remote: List<TicketRecord>,
    deletedIds: Set<String>,
    completeScope: Boolean,
): List<TicketRecord>
```

- [ ] **Step 1: Write the failing partial-response regression test**

```kotlin
@Test fun `partial response cannot shrink existing ticket set`() {
    val existing = (1..73).map(::ticket)
    val remote = existing.take(36)

    val result = reconcileMonotonicTickets(existing, remote, emptySet(), completeScope = false)

    assertEquals(73, result.size)
}

@Test fun `explicit tombstone removes matching ticket`() {
    val existing = listOf(ticket(1), ticket(2))
    val result = reconcileMonotonicTickets(existing, emptyList(), setOf(ticket(2).id), false)
    assertEquals(listOf(ticket(1).id), result.map(TicketRecord::id))
}
```

- [ ] **Step 2: Run test and verify RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.core.sync.MonotonicTicketReconcilerTest"
```

Expected: compilation failure because the reconciler does not exist.

- [ ] **Step 3: Implement merge-first reconciliation**

```kotlin
fun reconcileMonotonicTickets(
    existing: List<TicketRecord>,
    remote: List<TicketRecord>,
    deletedIds: Set<String>,
    completeScope: Boolean,
): List<TicketRecord> {
    val baseline = if (completeScope) emptyList() else existing
    return filterServerVisibleTickets(
        mergeTicketsPreferImported(baseline, remote),
        deletedIds,
    )
}
```

Replace remote hydration calls to `replaceScopedImportedTickets` with `mergeImportedTickets` or the new reconciler. Keep replacement available only for a response carrying an explicit `completeScope=true` contract.

- [ ] **Step 4: Run sync regression tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.core.sync.MonotonicTicketReconcilerTest" --tests "com.lotterynet.pro.core.sync.NativeTicketSyncContractsTest" --tests "com.lotterynet.pro.core.sync.NativeOperationalSyncContractsTest"
```

Expected: PASS and no remote hydration path matching `replaceScopedImportedTickets`.

- [ ] **Step 5: Run source contract**

```powershell
node --test tools/qa/admin-breakdown-bounded-hydration-contract.node.test.mjs tools/qa/ticket-summary-bounded-refresh-contract.node.test.mjs
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

```powershell
git add app/src/main/java/com/lotterynet/pro/core/sync/MonotonicTicketReconciler.kt app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketCloudSyncCoordinator.kt app/src/main/java/com/lotterynet/pro/core/sync/NativeOperationalHydration.kt app/src/test/java/com/lotterynet/pro/core/sync/MonotonicTicketReconcilerTest.kt
git commit -m "fix(android): preserve tickets across partial hydration"
```

---

### Task 3: Authenticated Bounded Read Contract

**Files:**
- Modify: `app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt`
- Modify: `app/src/test/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStoreTest.kt`
- Create: `tools/qa/canonical-ticket-read-contract.node.test.mjs`

**Interfaces:**
- Produces:

```kotlin
data class NativeTicketRemoteSnapshot(
    val tickets: List<TicketRecord>,
    val deletedIds: Set<String>,
    val completeScope: Boolean,
    val source: String?,
)
```

- [ ] **Step 1: Write failing request-contract tests**

Test that bounded UI reads:

```kotlin
assertEquals("fetch", request.getString("action"))
assertEquals("2026-06-18", request.getString("fromDate"))
assertEquals("2026-06-18", request.getString("toDate"))
assertEquals(1000, request.getInt("limit"))
assertFalse(request.optBoolean("preferSnapshot"))
```

Also assert that missing authentication throws a typed pending-sync error instead of requesting snapshot-only data.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.core.sync.NativeTicketRemoteStoreTest"
```

Expected: FAIL because unauthenticated requests currently set `preferSnapshot=true` and response completeness is not represented.

- [ ] **Step 3: Implement authenticated bounded request metadata**

For bounded operational reads:

```kotlin
JSONObject()
    .put("action", "fetch")
    .put("ownerKey", key)
    .put("fromDate", fromDate)
    .put("toDate", toDate)
    .put("limit", limit)
    .put("preferSnapshot", false)
    .put("includeOfficialStamp", true)
```

Parse server fields `source` and `completeScope`. Do not fall back to snapshot-only data when the bearer token is unavailable.

- [ ] **Step 4: Add Node source contract**

Assert:

```js
assert.match(remoteStore, /put\\("preferSnapshot", false\\)/);
assert.match(remoteStore, /completeScope/);
assert.doesNotMatch(boundedPath, /preferSnapshot.*authToken == null/);
```

- [ ] **Step 5: Run tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.lotterynet.pro.core.sync.NativeTicketRemoteStoreTest"
node --test tools/qa/canonical-ticket-read-contract.node.test.mjs
```

Expected: PASS.

- [ ] **Step 6: Commit Task 3**

```powershell
git add app/src/main/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStore.kt app/src/test/java/com/lotterynet/pro/core/sync/NativeTicketRemoteStoreTest.kt tools/qa/canonical-ticket-read-contract.node.test.mjs
git commit -m "fix(sync): require bounded authoritative ticket reads"
```

---

### Task 4: Edge Function Canonical Owner and Completeness

**Files:**
- Modify: `supabase/functions/_shared/lotterynet-admin.ts`
- Modify: `supabase/functions/get-ticket-list/index.ts`
- Create: `supabase/functions/get-ticket-list/index.test.ts`
- Modify: `tools/qa/canonical-ticket-read-contract.node.test.mjs`

**Interfaces:**
- Produces:

```ts
export type CanonicalOwnerScope = {
  canonicalOwnerKey: string;
  ownerKeys: string[];
  cashierKeys: string[];
};

export function validIdentityKey(value: unknown): string;
export function canonicalOwnerScope(actor: AuthenticatedActor, requestedOwner: string): CanonicalOwnerScope;
```

- [ ] **Step 1: Write failing Deno tests**

```ts
Deno.test("invalid owner placeholders are rejected", () => {
  assertEquals(validIdentityKey("null"), "");
  assertEquals(validIdentityKey(" undefined "), "");
});

Deno.test("nicola aliases resolve to ADM-163C38", () => {
  const scope = canonicalOwnerScope(actorFixture, "nicola01");
  assertEquals(scope.canonicalOwnerKey, "ADM-163C38");
  assert(scope.ownerKeys.includes("nicola01"));
});
```

- [ ] **Step 2: Verify RED**

```powershell
deno test supabase/functions/get-ticket-list/index.test.ts --allow-env
```

Expected: FAIL because exported canonical helpers do not exist.

- [ ] **Step 3: Implement canonical scope**

Filter all identity arrays through:

```ts
export function validIdentityKey(value: unknown): string {
  const key = clean(value);
  return key && !["null", "undefined"].includes(key.toLowerCase()) ? key : "";
}
```

Return `400` before database access for an invalid requested owner. Query official tickets with validated canonical and alias keys only.

- [ ] **Step 4: Mark response completeness explicitly**

For successful bounded authoritative reads return:

```ts
return json(200, {
  ok: true,
  ownerKey: scope.canonicalOwnerKey,
  payload,
  source: "authoritative",
  completeScope: true,
  updatedAt,
});
```

Snapshot-only compatibility responses must return `completeScope: false`; degraded/503 responses return no payload.

- [ ] **Step 5: Keep query bounds and fail-closed guards**

Retain:

```ts
if (action === "upsert") return deferred503();
if (action === "fetch" && (!dateRange || requestedLimit <= 0)) return deferred503();
```

The official query must continue using `.is("deleted_at", null)`, date bounds, ordered indexed columns, and `.limit(rowLimit)`.

- [ ] **Step 6: Run Deno and Node tests**

```powershell
deno test supabase/functions/get-ticket-list/index.test.ts --allow-env
node --test tools/qa/canonical-ticket-read-contract.node.test.mjs tools/qa/ticket-hydrate-readonly-contract.node.test.mjs tools/qa/emergency-ticket-sale-availability-contract.node.test.mjs
```

Expected: PASS.

- [ ] **Step 7: Commit Task 4**

```powershell
git add supabase/functions/_shared/lotterynet-admin.ts supabase/functions/get-ticket-list/index.ts supabase/functions/get-ticket-list/index.test.ts tools/qa/canonical-ticket-read-contract.node.test.mjs
git commit -m "fix(edge): resolve canonical ticket owner scope"
```

---

### Task 5: Reversible `"null"` Snapshot Quarantine

**Files:**
- Create: `supabase/migrations/20260619HHMMSS_quarantine_invalid_ticket_owner_snapshots.sql`
- Create: `supabase/migrations/rollback/20260619HHMMSS_quarantine_invalid_ticket_owner_snapshots.rollback.sql`
- Create: `supabase/tests/canonical_ticket_owner_reconciliation.sql`
- Create: `tools/qa/canonical-owner-quarantine-contract.node.test.mjs`

**Interfaces:**
- Produces private table `private.lotterynet_ticket_owner_snapshot_quarantine`
- Produces function `private.lotterynet_invalid_owner_snapshot_report(text)`

- [ ] **Step 1: Create the migration filename using the CLI**

```powershell
supabase migration new quarantine_invalid_ticket_owner_snapshots
```

Expected: one timestamped migration path; use that exact timestamp for rollback and tests.

- [ ] **Step 2: Write failing pgTAP/SQL assertions**

The SQL test must assert:

```sql
select is(
  (select count(*) from public.lotterynet_tickets_by_owner where lower(trim(owner_key)) = 'null'),
  1::bigint,
  'fixture begins with invalid owner snapshot'
);

select is(
  (select count(*) from private.lotterynet_ticket_owner_snapshot_quarantine where lower(trim(owner_key)) = 'null'),
  1::bigint,
  'invalid snapshot is backed up exactly once'
);
```

Also assert that `public.tickets` row count and checksums are unchanged.

- [ ] **Step 3: Verify RED locally or on an isolated Supabase branch**

```powershell
supabase test db supabase/tests/canonical_ticket_owner_reconciliation.sql
```

Expected: FAIL because quarantine objects do not exist.

- [ ] **Step 4: Implement backup and guard without deleting `"null"`**

```sql
create table if not exists private.lotterynet_ticket_owner_snapshot_quarantine (
  owner_key text primary key,
  payload jsonb not null,
  updated_at timestamptz,
  payload_sha256 text not null,
  quarantined_at timestamptz not null default now(),
  reconciliation jsonb not null
);

insert into private.lotterynet_ticket_owner_snapshot_quarantine (...)
select owner_key, payload, updated_at,
       encode(extensions.digest(payload::text, 'sha256'), 'hex'),
       private.lotterynet_invalid_owner_snapshot_report(owner_key)
from public.lotterynet_tickets_by_owner
where lower(trim(owner_key)) in ('null', 'undefined')
on conflict (owner_key) do nothing;
```

Do not delete or rename the public row in this migration. Add a guard in the bounded snapshot write RPC/function rejecting invalid owner keys.

- [ ] **Step 5: Write exact rollback**

Rollback restores the public snapshot only if missing and verifies the SHA-256 checksum:

```sql
insert into public.lotterynet_tickets_by_owner(owner_key, payload, updated_at)
select owner_key, payload, updated_at
from private.lotterynet_ticket_owner_snapshot_quarantine
on conflict (owner_key) do update
set payload = excluded.payload, updated_at = excluded.updated_at;
```

- [ ] **Step 6: Run tests and contract**

```powershell
supabase test db supabase/tests/canonical_ticket_owner_reconciliation.sql
node --test tools/qa/canonical-owner-quarantine-contract.node.test.mjs
```

Expected: PASS; authoritative ticket checksum unchanged.

- [ ] **Step 7: Commit Task 5**

```powershell
git add supabase/migrations supabase/tests/canonical_ticket_owner_reconciliation.sql tools/qa/canonical-owner-quarantine-contract.node.test.mjs
git commit -m "fix(db): quarantine invalid ticket owner snapshots"
```

---

### Task 6: Query-Plan and Concurrency Gate

**Files:**
- Create: `tools/qa/canonical-owner-query-plan.sql`
- Modify: `tools/qa/production-readiness-timing-suite.mjs`
- Create: `docs/superpowers/audits/2026-06-19-canonical-owner-readiness.md`

**Interfaces:**
- Consumes canonical bounded query from Task 4
- Produces repeatable query-plan and load evidence

- [ ] **Step 1: Capture baseline plans**

Run read-only:

```sql
explain (analyze, buffers, format json)
select id, client_request_id, admin_key, cashier_key, server_created_at, updated_at
from public.tickets
where deleted_at is null
  and admin_key = 'ADM-163C38'
  and server_created_at >= timestamptz '2026-06-18 04:00:00+00'
  and server_created_at < timestamptz '2026-06-19 04:00:00+00'
order by server_created_at desc
limit 1000;
```

Repeat for `cashier_key='CAJ-E1A630'`.

Expected: bounded index/bitmap plan, no long sequential scan, execution below 250 ms on warm production data. If the existing plan meets the target, add no index.

- [ ] **Step 2: Add readiness assertions**

The timing suite must reject:

```js
assert.ok(result.p95Ms < 1000);
assert.equal(result.http5xx, 0);
assert.equal(result.unboundedFetches, 0);
assert.equal(result.snapshotUpserts, 0);
assert.equal(result.countRegressions, 0);
```

- [ ] **Step 3: Run 10-client test**

```powershell
node tools/qa/production-readiness-timing-suite.mjs --clients 10 --duration-seconds 120 --read-only
```

Expected: no count regression or 5xx.

- [ ] **Step 4: Run 50-client test**

```powershell
node tools/qa/production-readiness-timing-suite.mjs --clients 50 --duration-seconds 180 --read-only
```

Expected: p95 under 1,000 ms and zero snapshot writes.

- [ ] **Step 5: Run 150-client test in the approved maintenance window**

```powershell
node tools/qa/production-readiness-timing-suite.mjs --clients 150 --duration-seconds 300 --read-only
```

Expected: no server unhealthy event, no statement timeout, no count regression.

- [ ] **Step 6: Record evidence and commit**

```powershell
git add tools/qa/canonical-owner-query-plan.sql tools/qa/production-readiness-timing-suite.mjs docs/superpowers/audits/2026-06-19-canonical-owner-readiness.md
git commit -m "test: gate canonical ticket reconciliation at scale"
```

---

### Task 7: Staged Release and Production Verification

**Files:**
- Create: `docs/superpowers/audits/2026-06-19-canonical-owner-production-canary.md`
- Modify only after all previous gates pass:
  - `supabase/functions/get-ticket-list/index.ts`
  - timestamped Task 5 migration
  - Android release artifact/version files already used by the project

**Interfaces:**
- Produces production canary evidence and rollback decision

- [ ] **Step 1: Capture production backup**

Using read-only SQL, export:

- `"null"` snapshot payload and checksum;
- `ADM-163C38` and `nicola01` snapshot payloads and checksums;
- authoritative ticket IDs/counts grouped by cashier for the active Dominican day;
- current `get-ticket-list` function version and hash.

- [ ] **Step 2: Apply database migration**

Use Supabase migration deployment only after branch/local tests pass. Verify quarantine checksum and confirm `public.tickets` checksum unchanged.

- [ ] **Step 3: Deploy Edge Function**

Deploy `get-ticket-list` with its existing JWT setting preserved. Immediately run one bounded administrator request and one bounded cashier request.

- [ ] **Step 4: Canary Android sessions**

Validate in order:

1. Nicolás administrator session.
2. `bancay05` / `CAJ-E1A630`.
3. Five mixed cashier sessions.

For each, record authoritative count, visible count, owner key, alias resolution, and two refresh cycles.

- [ ] **Step 5: Observe for 30–60 minutes**

Stop and rollback if:

- visible count is below authoritative count;
- `"null"` receives a new `updated_at`;
- any ticket changes administrator/cashier unexpectedly;
- 5xx or statement timeout appears;
- PostgreSQL lock waits or latency rise materially.

- [ ] **Step 6: Roll back if any gate fails**

Rollback order:

1. Revert Android release/feature flag.
2. Redeploy captured previous Edge Function.
3. Execute Task 5 rollback SQL.
4. Confirm authoritative ticket checksums still match pre-release evidence.

- [ ] **Step 7: Final verification**

```powershell
.\gradlew.bat :app:assembleDebug
node --test tools/qa/canonical-ticket-read-contract.node.test.mjs tools/qa/canonical-owner-quarantine-contract.node.test.mjs tools/qa/admin-breakdown-bounded-hydration-contract.node.test.mjs
```

Expected: all PASS; production counts stable across repeated refreshes.

- [ ] **Step 8: Commit canary evidence**

```powershell
git add docs/superpowers/audits/2026-06-19-canonical-owner-production-canary.md
git commit -m "docs: record canonical ticket owner canary"
```
