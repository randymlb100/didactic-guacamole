import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const blueprint = readFileSync(new URL("../../render.yaml", import.meta.url), "utf8");

test("Render Blueprint names only the active web service and results cron", () => {
  const names = [...blueprint.matchAll(/^\s+name:\s*(\S+)\s*$/gm)].map((match) => match[1]);

  assert.deepEqual(names, ["didactic-guacamole", "didactic-guacamole-results-cron"]);
  assert.match(blueprint, /type:\s*cron[\s\S]*?schedule:\s*["']\*\/5 \* \* \* \*["']/);
  assert.match(blueprint, /startCommand:\s*python scraper\/scrape_and_save\.py/);
  assert.doesNotMatch(blueprint, /lotterynet-(?:results|scraper-cron|sports-odds-sync|sports-results-sync)/);
});

test("Blueprint service commands and plans match the active Render services", () => {
  assert.match(blueprint, /name:\s*didactic-guacamole\r?\n\s+runtime:\s*python\r?\n\s+region:\s*oregon\r?\n\s+plan:\s*free/);
  assert.match(blueprint, /name:\s*didactic-guacamole-results-cron\r?\n\s+runtime:\s*python\r?\n\s+region:\s*oregon\r?\n\s+plan:\s*starter/);
  assert.match(blueprint, /buildCommand:\s*pip install -r requirements\.txt gevent==26\.4\.0/);
  assert.match(blueprint, /startCommand:\s*gunicorn app:app/);
});
