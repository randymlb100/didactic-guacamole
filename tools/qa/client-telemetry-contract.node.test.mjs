import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const androidSource = readFileSync(
  "app/src/main/java/com/lotterynet/pro/core/remote/SupabaseEdgeClient.kt",
  "utf8",
);
const webSource = readFileSync(
  "proyecto web nuevo/src/utils/supabaseClient.ts",
  "utf8",
);

test("Android Edge calls identify the client build without changing payloads", () => {
  assert.match(androidSource, /LotteryNetAndroid\/\$\{BuildConfig\.VERSION_NAME\}/);
  assert.match(androidSource, /X-Lotterynet-Client/);
  assert.match(androidSource, /X-Lotterynet-Client-Version/);
  assert.match(androidSource, /X-Lotterynet-Build-Variant/);
  assert.match(androidSource, /BuildConfig\.DEBUG/);
});

test("web dashboard calls identify the client without changing payloads", () => {
  assert.match(webSource, /X-Lotterynet-Client/);
  assert.match(webSource, /web-dashboard/);
  assert.match(webSource, /X-Lotterynet-Client-Version/);
});
