# Canonical Ticket Owner Production Canary

Date: 2026-06-19
Project: `unhoulkujbtsypccpirc`

## Deployment

- Database migration applied: `quarantine_invalid_ticket_owner_snapshots`
- Edge Function deployed: `get-ticket-list`
- Previous Edge version: `40`
- Previous Edge SHA-256: `ed2746740f573df918fcec29fe2e1367a394d0e8e65b6ff7c33c18428c3fd000`
- Current Edge version: `41`
- Current Edge SHA-256: `4e78a890f936fd81ebad37ead396e84702d457900bd263d7c181122ece097282`
- `verify_jwt`: preserved as `false` for anonymous `updated-at` compatibility; every bounded official read still validates a Supabase user JWT in the handler.

## Database gates

Before and after migration/deployment:

- Active official tickets: `1461`
- Official total: `RD$812,718.90`
- Official checksum: `743488b08064f569188f4891c4c8c42b5cf6b4ae9e895f61a7235a4f8a65d7c3`

Quarantine:

- Rows: `1`
- Owner: `null`
- Snapshot tickets: `433`
- Matched official tickets: `433`
- Unmatched: `0`
- Payload checksum: `4ffe77aa4528ee911582626470dd1932ef5abd67ea80b1ef8380a5606094a08a`
- Recomputed checksum matches: yes
- Invalid-owner trigger enabled: yes
- Test insert for `undefined`: rejected; zero rows created

## HTTP canary

- Anonymous `updated-at` for `ADM-163C38`: HTTP 200, not degraded
- Invalid owner `null`: HTTP 400 `Owner requerido`
- Bounded official read without user JWT: HTTP 401
- Edge logs confirm version 41 active
- No HTTP 500 observed after deployment
- No PostgreSQL statement timeout observed after deployment

Legacy APKs still issue some unbounded reads. Version 41 rejects them quickly with intentional HTTP 503 responses in roughly 158–194 ms, preserving local cache and preventing expensive PostgreSQL/JSONB work.

## Remaining release item

The backend protection is live. The Android v1.0.15 debug APK containing bounded authenticated reads compiled successfully, but it was not distributed or installed as part of this backend canary.
