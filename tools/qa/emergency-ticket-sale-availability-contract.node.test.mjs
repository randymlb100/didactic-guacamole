import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const exposurePath = "supabase/functions/get-sale-limit-exposure/index.ts";
const ticketListPath = "supabase/functions/get-ticket-list/index.ts";
const createTicketPath = "supabase/functions/create-ticket-v2/index.ts";

function read(path) {
  return readFileSync(path, "utf8");
}

test("sale-limit precheck degrades immediately without scanning ticket tables", () => {
  const source = read(exposurePath);
  const handler = source.slice(source.indexOf("Deno.serve"));

  assert.match(handler, /degraded:\s*true/);
  assert.match(handler, /authoritativeValidation:\s*"create-ticket-v2"/);
  assert.doesNotMatch(handler, /\.from\("tickets"\)/);
  assert.doesNotMatch(handler, /\.from\("ticket_items"\)/);
  assert.doesNotMatch(handler, /adminLimitKeys\(/);
});

test("unbounded snapshot fetch fails closed before authentication or database reads", () => {
  const source = read(ticketListPath);
  const handler = source.slice(source.indexOf("Deno.serve"));
  const unboundedFetchGate = handler.indexOf('message: "Historial remoto completo pausado temporalmente.');
  const authentication = handler.indexOf("authenticatedActor(req)");
  const snapshotRead = handler.indexOf('.from("lotterynet_tickets_by_owner")');

  assert.ok(unboundedFetchGate >= 0, "unbounded fetch must expose a temporary unavailable response");
  assert.ok(authentication > unboundedFetchGate, "fetch gate must run before authentication work");
  assert.ok(snapshotRead > unboundedFetchGate, "fetch gate must run before snapshot database reads");
  assert.match(
    handler.slice(0, authentication),
    /action === "fetch" && \(!boundedFetchRange \|\| boundedFetchLimit <= 0\)[\s\S]*status:\s*503[\s\S]*"Retry-After":\s*"120"/,
  );
});

test("authoritative sale-limit rejection is a conflict instead of a server error", () => {
  const source = read(createTicketPath);

  assert.match(source, /function createTicketErrorStatus\(error: unknown\): number/);
  assert.match(source, /message\.includes\("numero lleno"\)/);
  assert.match(source, /"message" in error/);
  assert.match(source, /return 409/);
  assert.match(source, /createTicketErrorStatus\(payload\.message\)/);
  assert.match(source, /createTicketErrorStatus\(error\)/);
});
