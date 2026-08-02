import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const source = readFileSync("supabase/functions/create-ticket-v2/index.ts", "utf8");

test("placeholder identity values are rejected", () => {
  assert.match(
    source,
    /function validIdentity[\s\S]*"null"[\s\S]*"undefined"/,
  );
});

test("authenticated account supplies canonical admin identity before controls and RPC", () => {
  const handler = source.slice(source.indexOf("Deno.serve"));
  const canonicalIdentity = handler.indexOf("canonicalAdminKey");
  const controls = handler.indexOf("validateAdministrativeControls");
  const rpc = handler.indexOf("supabase.rpc");

  assert.match(source, /account\?:\s*Record<string,\s*unknown>/);
  assert.ok(canonicalIdentity >= 0, "handler must derive a canonical admin identity");
  assert.ok(controls > canonicalIdentity, "controls must use the canonical identity");
  assert.ok(rpc > canonicalIdentity, "RPC must receive the canonical identity");
  assert.match(
    handler,
    /canonicalBody[\s\S]*adminKey:\s*canonicalAdminKey[\s\S]*adminId:\s*canonicalAdminKey[\s\S]*sanitizeCreateTicketBody\(canonicalBody\)/,
  );
});
