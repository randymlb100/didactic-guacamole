type EdgeErrorContext = Record<string, unknown> & {
  functionName: string;
  operation?: string;
};

function clean(value: unknown): string {
  return String(value ?? "").trim();
}

function safeContext(context: EdgeErrorContext): Record<string, unknown> {
  const blocked = /authorization|token|secret|key|password|payload|body|ticket|items|jugadas/i;
  const result: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(context)) {
    if (blocked.test(key)) continue;
    if (value === null || value === undefined) continue;
    const text = typeof value === "string" ? value : JSON.stringify(value);
    result[key] = text.length > 180 ? `${text.slice(0, 180)}...` : value;
  }
  return result;
}

function dsnParts(): { endpoint: string; publicKey: string } | null {
  const raw = clean(Deno.env.get("SENTRY_DSN"));
  if (!raw) return null;
  try {
    const url = new URL(raw);
    const publicKey = url.username;
    const projectId = url.pathname.split("/").filter(Boolean).at(-1);
    if (!publicKey || !projectId) return null;
    const basePath = url.pathname.split("/").filter(Boolean).slice(0, -1).join("/");
    const prefix = basePath ? `/${basePath}` : "";
    return {
      endpoint: `${url.protocol}//${url.host}${prefix}/api/${projectId}/store/`,
      publicKey,
    };
  } catch {
    return null;
  }
}

export async function captureEdgeError(error: unknown, context: EdgeErrorContext): Promise<void> {
  const parts = dsnParts();
  if (!parts) return;

  const exception = error instanceof Error
    ? { type: error.name, value: error.message, stacktrace: error.stack }
    : { type: "Error", value: clean(error) || "Unknown edge function error" };

  const event = {
    event_id: crypto.randomUUID().replaceAll("-", ""),
    timestamp: new Date().toISOString(),
    platform: "javascript",
    level: "error",
    environment: clean(Deno.env.get("SENTRY_ENVIRONMENT")) || "production",
    logger: "lotterynet-edge",
    transaction: context.functionName,
    tags: {
      function: context.functionName,
      operation: clean(context.operation),
    },
    extra: safeContext(context),
    exception: {
      values: [exception],
    },
  };

  try {
    await fetch(parts.endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Sentry-Auth": [
          "Sentry sentry_version=7",
          `sentry_client=lotterynet-edge/1.0`,
          `sentry_key=${parts.publicKey}`,
        ].join(", "),
      },
      body: JSON.stringify(event),
    });
  } catch {
    // Error reporting must never break production paths.
  }
}
