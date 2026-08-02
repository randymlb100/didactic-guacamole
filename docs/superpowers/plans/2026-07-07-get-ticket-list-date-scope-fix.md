# get-ticket-list date-scope fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `get-ticket-list` return the ticket in the owner/cashier list when the ticket is valid but its draw date differs from its server creation date, without widening the query or breaking the current delta flow.

**Architecture:** Keep the current delta path intact. Fix only the authoritative list path so it stops treating `server_created_at` as the only gate for visible history. The server should use the ticket’s draw-date scope for the visible list, while still preserving owner filtering, snapshot caching, and the existing realtime/delta behavior.

**Tech Stack:** Supabase Edge Functions, PostgREST filters, Node.js QA smoke tests, Postgres query tuning.

## Global Constraints

- Do not change the realtime/auth dedupe work that already passed.
- Do not introduce a schema migration unless the failing smoke requires it.
- Do not use Gradle for verification; use Node tests and the existing smoke only.
- Keep the fix narrow to `get-ticket-list` and the contract test coverage around it.
- Preserve owner/cajero authorization exactly as it works today.

---

### Task 1: Lock the failure down with a smaller Node reproduction

**Files:**
- Modify: `tools/qa/ticket-payload-integrity-smoke.mjs`
- Modify: `tools/qa/ticket-list-snapshot-cache-contract.node.test.mjs`

**Interfaces:**
- Consumes: `get-ticket-list`, `get-ticket-delta`, existing QA credentials file.
- Produces: a reproducible assertion that a valid ticket can still reach the owner list even when draw date and server creation date do not match.

- [ ] **Step 1: Write the failing assertion**

```js
const valid = await createTicket("valid-one", [validPlay()]);
const list = await getList(admin.id, adminSession.token);
const listTickets = list.json?.payload?.tickets ?? list.json?.tickets ?? [];
const listValid = listTickets.find((ticket) =>
  [ticket.id, ticket.clientRequestId, ticket.client_request_id].some((value) => clean(value) === valid.body.clientRequestId)
);
check(Boolean(listValid), "ticket valido cae en lista del owner admin", { code: listValid?.code ?? listValid?.ticketCode });
```

- [ ] **Step 2: Verify it still fails before the server change**

Run:
```powershell
node --test tools/qa/ticket-payload-integrity-smoke.mjs
```

Expected: the delta checks pass, but the owner list checks still fail with the current server logic.

- [ ] **Step 3: Add a tiny contract guard**

```js
assert.match(source, /const serverRange = expandedServerCreatedRange\(dateRange\);/);
assert.match(source, /\.gte\("server_created_at", since\)/);
assert.match(source, /ticketInDateRange\(ticket, dateRange\)/);
```

This keeps the reproduction pinned to the exact list gating logic that is causing the miss.

---

### Task 2: Narrow the server list query to the real visibility scope

**Files:**
- Modify: `supabase/functions/get-ticket-list/index.ts`
- Modify: `tools/qa/supabase-edge-auth-contract.node.test.mjs`

**Interfaces:**
- Consumes: `dateRangeFromBody`, `ticketInDateRange`, `officialTicketsForOwner`, `appTicketFromOfficial`.
- Produces: authoritative owner/cashier lists that include valid tickets even when `server_created_at` and draw date differ.

- [ ] **Step 1: Decide the gate to change**

Current behavior to remove from the visible-list gate:

```ts
const serverRange = expandedServerCreatedRange(dateRange);
const since = serverRange?.from ?? new Date(Date.now() - 14 * 24 * 60 * 60 * 1000).toISOString();
query = query.gte("server_created_at", since);
if (serverRange?.to) {
  query = query.lt("server_created_at", serverRange.to);
}
```

Replace this with a narrower ownership-aware read that uses the ticket’s draw-date scope for visibility, while keeping `server_created_at` only as a freshness/order helper if needed.

- [ ] **Step 2: Keep the owner filter and item hydration**

```ts
const tickets = await officialTicketsForOwner(
  admin,
  ownerKeys,
  includeItems,
  snapshotTickets,
  auth.actor,
  requestedLimit,
  bool(body.includeSnapshotOfficialLookup),
  dateRange,
);
```

Do not remove:

```ts
const payload = {
  ...basePayload,
  schemaVersion: Number(basePayload.schemaVersion ?? 2),
  tickets: limitTickets(filterTicketsForActor(
    mergeTickets(snapshotTickets, officialTickets, deletedIds)
      .filter((ticket) => ticketInDateRange(ticket, dateRange)),
    auth.actor,
  ), requestedLimit),
  deletedIds: Array.from(deletedIds),
};
```

That keeps the current merge behavior and only changes what rows are eligible to enter `officialTickets`.

- [ ] **Step 3: Add a contract that names the intended scope**

```js
assert.match(source, /ticketInDateRange\(ticket, dateRange\)/);
assert.match(source, /canonicalOwnerScope\(auth\.actor, ownerKey\)/);
assert.match(source, /officialTicketsForOwner\(/);
assert.doesNotMatch(source, /\.gte\("server_created_at", since\)/);
```

The contract should fail until the list query no longer depends on a hard `server_created_at` gate for the visible owner history.

- [ ] **Step 4: Run the minimal verification loop**

Run:
```powershell
node --test tools/qa/ticket-list-snapshot-cache-contract.node.test.mjs tools/qa/supabase-edge-auth-contract.node.test.mjs
```

Expected: the list contract still passes, and the new date-scope assertions now pass.

---

### Task 3: Re-run the end-to-end smoke and confirm the fix did not widen access

**Files:**
- Test: `tools/qa/ticket-payload-integrity-smoke.mjs`
- Test: `tools/qa/ticket-payload-integrity-summary-*.json`
- Test: `tools/qa/ticket-summary-bounded-refresh-contract.node.test.mjs`

**Interfaces:**
- Consumes: the same podero02/admin and cashier QA credentials used by the smoke.
- Produces: a passing smoke run and a saved summary showing the ticket reaches the admin list and cashier list again.

- [ ] **Step 1: Run the focused smoke**

Run:
```powershell
node --test tools/qa/ticket-payload-integrity-smoke.mjs
```

Expected:
- `ticket valido cae en delta del admin` passes
- `ticket valido cae en lista del owner admin` passes
- `ticket valido conserva vendedor cajero en lista` passes
- `lista cajero trae items del ticket valido` passes

- [ ] **Step 2: Confirm the fix did not affect the bounded refresh path**

Run:
```powershell
node --test tools/qa/ticket-summary-bounded-refresh-contract.node.test.mjs
```

Expected: pass.

- [ ] **Step 3: Save the result summary and stop**

Keep the generated smoke summary JSON for review and do not schedule any Gradle build from this task.

---

## Review Checklist

- [ ] The owner list now includes a valid ticket created today even if its draw date is different.
- [ ] The cashier view still shows the same ticket with its items.
- [ ] Delta behavior is unchanged.
- [ ] No Gradle build was used.
- [ ] No schema migration was introduced.

