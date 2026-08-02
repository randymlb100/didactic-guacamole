import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const handlerPath = path.join(root, "supabase", "functions", "get-ticket-delta", "index.ts");

test("ticket delta orders by the cursor column instead of sorting by creation timestamps", () => {
  const source = fs.readFileSync(handlerPath, "utf8");

  assert.match(source, /\.order\("updated_at",\s*\{\s*ascending:\s*false/);
  assert.match(source, /\.order\("id",\s*\{\s*ascending:\s*false/);
  assert.match(source, /query = query\.gt\("updated_at",\s*sinceCursor\)/);
  assert.doesNotMatch(
    source,
    /\.order\("server_created_at"[\s\S]*?\.order\("created_at"/,
  );
});

test("ticket delta keeps the bounded query and item hydration contracts", () => {
  const source = fs.readFileSync(handlerPath, "utf8");

  assert.match(source, /\.limit\(limit\)/);
  assert.match(source, /includeItems \? await fetchTicketItems\(ids\) : \[\]/);
  assert.match(source, /\.in\("ticket_id", idChunk\)/);
});
