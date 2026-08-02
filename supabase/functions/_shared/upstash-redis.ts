type RedisJson = {
  result?: unknown;
  error?: string;
};

function redisUrl(): string {
  return (Deno.env.get("UPSTASH_REDIS_REST_URL") ?? "").trim().replace(/\/+$/, "");
}

function redisToken(): string {
  return (Deno.env.get("UPSTASH_REDIS_REST_TOKEN") ?? "").trim();
}

function defaultTtlSeconds(): number {
  const parsed = Number(Deno.env.get("UPSTASH_REDIS_DEFAULT_TTL_SECONDS") ?? "120");
  return Number.isFinite(parsed) && parsed > 0 ? Math.trunc(parsed) : 120;
}

export function upstashRedisEnabled(): boolean {
  return redisUrl().length > 0 && redisToken().length > 0;
}

async function command(args: unknown[]): Promise<RedisJson | null> {
  if (!upstashRedisEnabled()) return null;
  try {
    const response = await fetch(redisUrl(), {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${redisToken()}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(args),
    });
    if (!response.ok) return null;
    const parsed = await response.json().catch(() => null) as RedisJson | null;
    if (!parsed || parsed.error) return null;
    return parsed;
  } catch {
    return null;
  }
}

export async function redisGetJson<T = unknown>(key: string): Promise<T | null> {
  const result = await command(["GET", key]);
  const value = result?.result;
  if (value === null || value === undefined) return null;
  if (typeof value !== "string") return value as T;
  try {
    return JSON.parse(value) as T;
  } catch {
    return value as T;
  }
}

export async function redisSetJson(key: string, value: unknown, ttlSeconds = defaultTtlSeconds()): Promise<void> {
  const payload = typeof value === "string" ? value : JSON.stringify(value);
  if (ttlSeconds > 0) {
    await command(["SET", key, payload, "EX", ttlSeconds]);
  } else {
    await command(["SET", key, payload]);
  }
}

export async function redisDelete(key: string): Promise<void> {
  await command(["DEL", key]);
}

/** Soft counter for non-financial protections. Null means Redis is unavailable. */
export async function redisIncrement(key: string, ttlSeconds: number): Promise<number | null> {
  const result = await command(["INCR", key]);
  const count = Number(result?.result);
  if (!Number.isFinite(count)) return null;
  if (count === 1 && ttlSeconds > 0) await command(["EXPIRE", key, Math.trunc(ttlSeconds)]);
  return count;
}
