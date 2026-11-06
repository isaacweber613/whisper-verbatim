/**
 * Word for Word's transcription server (a Cloudflare Worker).
 *
 * The Play Store build sends recordings here instead of to OpenRouter, so people can dictate
 * without their own OpenRouter key. This server holds the OpenRouter key, gives each phone
 * FREE_SECONDS_PER_MONTH of audio a month, and passes the audio on to OpenRouter.
 *
 * The OpenRouter key has its own monthly spending limit set on openrouter.ai, which caps the
 * total cost even if someone fakes many phones (Isaac, 2026-11-06: $10 a month).
 *
 * Endpoints:
 *   POST /v1/transcriptions  same JSON body as OpenRouter's /api/v1/audio/transcriptions,
 *                            with header X-Device-Id. Answers like OpenRouter does.
 *   GET  /v1/usage           header X-Device-Id. {"used_seconds", "free_seconds", "month"}
 */

export interface Env {
  DB: D1Database;
  OPENROUTER_KEY: string;
  FREE_SECONDS_PER_MONTH: string;
}

/** The only models the server pays for: the app's built-in list. */
export const MODELS = new Set([
  "microsoft/mai-transcribe-2",
  "openai/gpt-transcribe",
  "mistralai/voxtral-mini-transcribe-3-0",
  "elevenlabs/scribe-v2",
]);

const OPENROUTER_URL = "https://openrouter.ai/api/v1/audio/transcriptions";

/** Storage for each phone's seconds used per month. */
export interface Usage {
  get(device: string, month: string): Promise<number>;
  add(device: string, month: string, seconds: number): Promise<void>;
}

export function d1Usage(db: D1Database): Usage {
  return {
    async get(device, month) {
      const row = await db.prepare("SELECT seconds FROM usage WHERE device = ? AND month = ?")
        .bind(device, month).first<{ seconds: number }>();
      return row?.seconds ?? 0;
    },
    async add(device, month, seconds) {
      await db.prepare(
        "INSERT INTO usage (device, month, seconds) VALUES (?, ?, ?) " +
        "ON CONFLICT (device, month) DO UPDATE SET seconds = seconds + excluded.seconds",
      ).bind(device, month, seconds).run();
    },
  };
}

/** "2026-11" for the current month, in UTC. */
export function monthOf(date: Date): string {
  return date.toISOString().slice(0, 7);
}

/**
 * Seconds of audio in a base64 WAV the app sent: 16 kHz, 16-bit mono is 32,000 bytes a
 * second, after a 44-byte header.
 */
export function wavSeconds(base64: string): number {
  const padding = base64.endsWith("==") ? 2 : base64.endsWith("=") ? 1 : 0;
  const bytes = (base64.length * 3) / 4 - padding;
  return Math.max(0, bytes - 44) / 32000;
}

function error(status: number, message: string, code?: string): Response {
  return Response.json({ error: { message, ...(code ? { code } : {}) } }, { status });
}

/** A device ID is the app's hashed Android ID: 64 hex characters. */
function deviceOf(request: Request): string | null {
  const id = request.headers.get("X-Device-Id") ?? "";
  return /^[0-9a-f]{64}$/.test(id) ? id : null;
}

export async function handle(request: Request, env: Env, usage: Usage, now = new Date(),
                             fetcher: typeof fetch = fetch): Promise<Response> {
  const url = new URL(request.url);
  const device = deviceOf(request);
  const free = Number(env.FREE_SECONDS_PER_MONTH);
  const month = monthOf(now);

  if (url.pathname === "/v1/usage" && request.method === "GET") {
    if (!device) return error(400, "Missing device ID.");
    return Response.json({ used_seconds: await usage.get(device, month), free_seconds: free, month });
  }

  if (url.pathname !== "/v1/transcriptions" || request.method !== "POST") return error(404, "Not found.");
  if (!device) return error(400, "Missing device ID.");

  let body: { model?: unknown; input_audio?: { data?: unknown } };
  try {
    body = await request.json();
  } catch {
    return error(400, "The request isn't valid JSON.");
  }
  if (typeof body.model !== "string" || !MODELS.has(body.model)) {
    return error(400, "Model \"" + String(body.model) + "\" does not exist on Word for Word's server.");
  }
  const audio = body.input_audio?.data;
  if (typeof audio !== "string" || audio.length === 0) return error(400, "No audio in the request.");

  const seconds = wavSeconds(audio);
  const used = await usage.get(device, month);
  if (used >= free) {
    return error(402, "You've used your " + Math.round(free / 60) + " free minutes this month.", "free_minutes_used");
  }

  const upstream = await fetcher(OPENROUTER_URL, {
    method: "POST",
    headers: {
      "Authorization": "Bearer " + env.OPENROUTER_KEY,
      "Content-Type": "application/json",
      "X-Title": "Word for Word",
    },
    body: JSON.stringify(body),
  });
  const text = await upstream.text();
  // Only audio that came back as text counts against the free minutes.
  if (upstream.ok) await usage.add(device, month, seconds);
  return new Response(text, { status: upstream.status, headers: { "Content-Type": "application/json" } });
}

export default {
  fetch(request: Request, env: Env): Promise<Response> {
    return handle(request, env, d1Usage(env.DB));
  },
} satisfies ExportedHandler<Env>;
