# Canonical Ticket Owner Reconciliation Design

**Date:** 2026-06-18  
**Status:** Approved design; implementation not started  
**Production project:** `unhoulkujbtsypccpirc`

## Objective

Stop tickets from appearing and disappearing by ensuring that an administrator, their username, their profile UUID, and their cashier aliases are treated as one operational identity without losing, deleting, or reassigning any ticket.

The initial affected identity is:

- Canonical administrator key: `ADM-163C38`
- Administrator alias: `nicola01`
- Profile UUID: `5e9553d2-72b2-484e-8b85-095fbce6f2a4`
- Known cashier example: `CAJ-E1A630` / `bancay05`
- Invalid owner value requiring quarantine: literal string `"null"`

## Confirmed Production Evidence

At diagnosis time:

- The authoritative `tickets` table contained 73 tickets for `ADM-163C38` for the active Dominican day.
- The `ADM-163C38` and `nicola01` snapshots each exposed only 36 tickets for that day.
- The invalid `"null"` snapshot exposed 35 tickets for that day.
- All 35 active-day ticket identifiers in the `"null"` snapshot matched authoritative rows through either the UUID or `legacy_ticket_id`.
- ID/alias mappings in `profiles` correctly linked Nicolás and his cashiers to `ADM-163C38`.
- PostgreSQL had no blocked transactional query at diagnosis time.
- Historical snapshot upserts and unbounded fetches were intentionally paused in `get-ticket-list` to protect production from the previous JSONB timeout incident.
- The application still has general synchronization paths that call `replaceScopedImportedTickets`, while bounded administrative hydration uses merge semantics.

This proves the disappearing-ticket symptom is a read/reconciliation problem, not evidence that authoritative ticket rows are being deleted.

## Safety Invariants

These requirements are non-negotiable:

1. The authoritative `public.tickets` and `public.ticket_items` rows are never deleted or rewritten during identity repair.
2. The `"null"` snapshot is quarantined before any cleanup and remains recoverable until reconciliation is verified.
3. A partial, stale, unauthenticated, timed-out, or degraded response must never reduce the visible local ticket set.
4. Terminal server states such as paid, winner, voided, invalid, or deleted remain authoritative.
5. Ticket identity comparison accepts both the canonical UUID and `legacy_ticket_id`; display aliases never become ticket primary keys.
6. No full-history JSONB rewrite is re-enabled as part of this repair.
7. Every production mutation must have a captured pre-change snapshot and an explicit rollback statement.
8. Deployment stops immediately if authoritative counts decrease unexpectedly for any administrator or cashier.

## Alternatives Considered

### A. Delete the `"null"` snapshot immediately

Rejected. Although its active-day tickets currently match authoritative rows, immediate deletion would remove forensic evidence and could hide older rows that have not yet been compared.

### B. Keep every alias as an independent owner and merge them only in the UI

Rejected. Independent snapshots continue to drift, generate duplicate work, and allow a late partial response to overwrite a complete local view.

### C. Canonical owner resolution with alias-aware reads and merge-only hydration

Selected. One stable owner key drives reads and subscriptions; aliases remain lookup inputs. The authoritative normalized ticket tables provide current data, and snapshots remain compatibility caches rather than sources allowed to shrink state.

## Architecture

### 1. Canonical identity resolver

Introduce one shared resolver used by Android synchronization and server ticket reads.

It produces:

```text
CanonicalOwnerIdentity
  canonicalOwnerKey = ADM-163C38
  administratorAliases = [nicola01, profile UUID]
  cashierIdentityGroups = [
    { canonical = CAJ-E1A630, aliases = [bancay05, cashier profile UUID] },
    ...
  ]
```

Normalization rules:

- Trim surrounding whitespace.
- Compare keys case-insensitively.
- Reject empty strings and literal `"null"` or `"undefined"`.
- Prefer a valid `ADM-*` administrator key as the canonical administrator identity.
- Prefer a valid `CAJ-*` key as the canonical cashier identity.
- Use profile UUIDs and usernames only as aliases.
- Never infer two users are identical solely because they share a display name.

### 2. Server read model

Current-day and bounded historical views read authoritative ticket rows by the complete validated owner-key set:

- Administrator requests match canonical administrator key and validated administrator aliases.
- Cashier requests match the canonical cashier key and validated cashier aliases while retaining the administrator scope.
- Results are deduplicated by authoritative ticket UUID, with `legacy_ticket_id` as a compatibility lookup key.
- Snapshot tickets may enrich compatibility fields but may not override authoritative ownership, status, amount, or deletion state.

Reads remain bounded by:

- Required date range.
- Explicit limit of at most 1,000 rows.
- Indexed `admin_key`, `cashier_key`, date, status, and update columns.

The existing fail-closed behavior for unbounded fetch and historical upsert remains active.

### 3. Android reconciliation

All operational ticket hydration follows one policy:

1. Resolve the session to one canonical owner identity.
2. Fetch a bounded authenticated server result for the requested day.
3. Merge returned tickets by ticket identity.
4. Apply authoritative terminal statuses and tombstones.
5. Preserve local tickets not addressed by the bounded response.
6. Publish the new UI state atomically after reconciliation completes.

`replaceScopedImportedTickets` must not be called from a remote hydration path unless the response explicitly proves it is a complete authoritative scope. Degraded responses preserve the last known good local state and expose a synchronization warning instead of an empty or smaller list.

Realtime subscriptions use the canonical owner key. An alias event may trigger a refresh, but it does not establish a second competing local owner scope.

### 4. Snapshot quarantine and repair

The `"null"` row is handled in phases:

1. Capture its full payload, checksum, byte size, ticket count, deleted IDs, and `updated_at`.
2. Compare every ticket against `tickets.id`, `tickets.legacy_ticket_id`, administrator key, cashier key, day, amount, and status.
3. Produce a reconciliation report with:
   - safely represented by authoritative rows;
   - ownership conflicts;
   - amount/status conflicts;
   - unmatched legacy tickets;
   - malformed records.
4. Copy unresolved records into a dedicated backup artifact or quarantine table before changing the compatibility row.
5. Prevent all new writes whose normalized owner key is invalid.
6. Only after zero unresolved active records remain, rename or remove the invalid compatibility snapshot in a reversible migration.

No ticket is reassigned merely because it exists in the `"null"` snapshot. Ownership comes from authoritative columns and validated profile relationships.

## Data Flow

```text
Session identity
    ↓
Canonical owner resolver
    ↓
Validated canonical + alias keys
    ↓
Bounded authenticated authoritative query
    ↓
Ticket UUID / legacy ID reconciliation
    ↓
Merge-only local repository update
    ↓
Single atomic UI state
```

The snapshot compatibility cache is a side input, not the authority:

```text
Compatibility snapshot ──→ enrich missing legacy presentation fields
Authoritative tickets ───→ ownership, existence, amount, status, dates
```

## Error Handling

- Missing authentication: retain local state and show pending synchronization.
- Query timeout or HTTP 5xx: retain local state; do not call replacement APIs.
- Empty bounded response: treat as valid only when the server explicitly marks the requested scope complete.
- Invalid owner key: reject before network or database access and log a privacy-safe diagnostic.
- Alias ambiguity: stop reconciliation for that identity, retain data, and require an operator-reviewed mapping.
- Count regression: cancel rollout and execute rollback.

Logs must include canonical owner hash, requested day, source type, response completeness, pulled count, merged count, and retained count. Logs must not contain full ticket payloads, credentials, or customer-sensitive information.

## Testing Strategy

### Contract and unit tests

- `"null"`, `"undefined"`, and blank owners are rejected.
- `ADM-163C38`, `nicola01`, and the profile UUID resolve to one canonical administrator.
- `CAJ-E1A630`, `bancay05`, and its UUID resolve to one cashier identity.
- A 36-ticket partial response cannot reduce a local 73-ticket set.
- Authenticated official tickets replace matching stale records without duplicating legacy IDs.
- Terminal server states remain authoritative.
- Alias events do not create a competing local owner scope.
- Empty or timed-out responses preserve the previous list.

### Database tests

- Every quarantined `"null"` ticket is classified.
- No authoritative ticket row changes during dry-run reconciliation.
- Canonical and alias queries return identical authoritative ticket sets.
- `EXPLAIN (ANALYZE, BUFFERS)` confirms bounded indexed queries.
- Invalid owner writes fail before touching JSONB.

### Staging/load tests

- 10, 50, and 150 concurrent clients refresh bounded current-day data.
- No full snapshot upsert occurs.
- Visible ticket counts remain monotonic except for confirmed terminal deletion/void operations.
- PostgreSQL connection use, p95 latency, lock waits, and statement timeouts stay inside the release thresholds defined in the implementation plan.

### Production canary

1. Read-only reconciliation report.
2. One administrator session.
3. One cashier session under that administrator.
4. Five mixed sessions.
5. General release only after 30–60 minutes with no count regression, timeout spike, or owner ambiguity.

## Rollback

Rollback must be possible independently at each layer:

- Android: feature flag returns to the previous read path while preserving the last local cache.
- Edge Function: deploy the previously captured function version.
- Database: restore the quarantined compatibility snapshot from its exact JSONB backup.
- Identity mapping: remove only the new canonical mapping records; authoritative ticket rows remain untouched.

Rollback is triggered by any of:

- a visible count lower than the authoritative bounded query;
- a ticket assigned to the wrong administrator or cashier;
- new `"null"` owner activity;
- elevated 5xx/timeout rate;
- sustained PostgreSQL lock or latency regression.

## Success Criteria

- Tickets no longer disappear and return during refresh or realtime events.
- Nicolás sees the same authoritative current-day ticket count through `ADM-163C38`, `nicola01`, and his valid session identity.
- Each cashier sees only their own tickets, while the administrator sees the complete network.
- No new `"null"` snapshot writes occur.
- The existing server-protection controls for unbounded history and heavy JSONB writes remain enabled.
- All production reconciliation is auditable and reversible.

## Out of Scope

- Re-enabling full historical snapshot upserts.
- Deleting authoritative tickets.
- Redesigning reports or accounting formulas.
- General user-account cleanup unrelated to owner identity.
- Removing old snapshots for other administrators before their own reconciliation reports exist.
