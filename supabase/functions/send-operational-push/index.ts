import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { clean, corsHeaders, json, requireSharedSecret, supabaseAdmin } from "../_shared/lotterynet-admin.ts";

function base64Url(input: ArrayBuffer | string): string {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : new Uint8Array(input);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const normalized = pem.replace(/\\n/g, "\n");
  const body = normalized.replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const binary = atob(body);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}

async function serviceAccountAccessToken(): Promise<string | null> {
  const clientEmail = Deno.env.get("FIREBASE_CLIENT_EMAIL") ?? "";
  const privateKey = Deno.env.get("FIREBASE_PRIVATE_KEY") ?? "";
  if (!clientEmail || !privateKey) return null;

  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = base64Url(JSON.stringify({
    iss: clientEmail,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const unsigned = `${header}.${payload}`;
  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(privateKey),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", cryptoKey, new TextEncoder().encode(unsigned));
  const jwt = `${unsigned}.${base64Url(signature)}`;
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!response.ok) throw new Error(`Firebase token failed: ${response.status}`);
  const jsonBody = await response.json();
  return clean(jsonBody.access_token);
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, message: "Metodo no permitido." }, 405);
  const secretError = requireSharedSecret(req);
  if (secretError) return secretError;

  try {
    const projectId = Deno.env.get("FIREBASE_PROJECT_ID") ?? "";
    const accessToken = await serviceAccountAccessToken();
    if (!projectId || !accessToken) return json({ ok: true, configured: false, sent: 0 });

    const body = await req.json().catch(() => ({}));
    const ownerKeyHash = clean(body.ownerKeyHash);
    const type = clean(body.type);
    const dayKey = clean(body.dayKey);
    const reason = clean(body.reason) || "server_changed";
    if (!ownerKeyHash || !type) return json({ ok: false, message: "ownerKeyHash y type requeridos." }, 400);

    const { data, error } = await supabaseAdmin()
      .from("lotterynet_push_tokens")
      .select("token")
      .eq("owner_key_hash", ownerKeyHash)
      .eq("enabled", true)
      .limit(100);
    if (error) throw error;

    let sent = 0;
    for (const row of data ?? []) {
      const token = clean(row.token);
      if (!token) continue;
      const response = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token,
            data: { type, dayKey, ownerKeyHash, reason },
            android: { priority: "high" },
          },
        }),
      });
      if (response.ok) sent += 1;
    }
    return json({ ok: true, configured: true, sent });
  } catch (error) {
    return json({ ok: false, message: error instanceof Error ? error.message : "No se pudo enviar push." }, 500);
  }
});
