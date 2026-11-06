import { describe, expect, it } from "vitest";
import { handle, monthOf, wavSeconds, type Env, type Usage } from "../src/index";

const device = "a".repeat(64);
const env = { OPENROUTER_KEY: "test-key", FREE_SECONDS_PER_MONTH: "1800" } as Env;
const now = new Date("2026-11-06T12:00:00Z");

function memoryUsage(start = 0): Usage & { seconds: number } {
  return {
    seconds: start,
    async get() { return this.seconds; },
    async add(_d, _m, s) { this.seconds += s; },
  };
}

/** A base64 WAV body of [seconds] of audio, as the app sends it. */
function audio(seconds: number): string {
  return Buffer.alloc(44 + seconds * 32000).toString("base64");
}

function transcribe(seconds: number, model = "microsoft/mai-transcribe-2", headers: Record<string, string> = { "X-Device-Id": device }) {
  return new Request("https://example.com/v1/transcriptions", {
    method: "POST", headers,
    body: JSON.stringify({ model, input_audio: { data: audio(seconds), format: "wav" } }),
  });
}

const ok = (async () => Response.json({ text: "hello" })) as unknown as typeof fetch;

describe("transcription server", () => {
  it("measures audio length from the WAV", () => {
    expect(wavSeconds(audio(10))).toBeCloseTo(10, 5);
  });

  it("names months in UTC", () => {
    expect(monthOf(now)).toBe("2026-11");
  });

  it("passes audio to OpenRouter with the server's key and counts the seconds", async () => {
    const usage = memoryUsage();
    let sentKey = "";
    const fetcher = (async (_u: string, init: RequestInit) => {
      sentKey = (init.headers as Record<string, string>)["Authorization"];
      return Response.json({ text: "hello" });
    }) as unknown as typeof fetch;
    const res = await handle(transcribe(12), env, usage, now, fetcher);
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ text: "hello" });
    expect(sentKey).toBe("Bearer test-key");
    expect(usage.seconds).toBeCloseTo(12, 5);
  });

  it("stops at the free minutes", async () => {
    const res = await handle(transcribe(5), env, memoryUsage(1800), now, ok);
    expect(res.status).toBe(402);
    expect((await res.json() as { error: { code: string } }).error.code).toBe("free_minutes_used");
  });

  it("doesn't count audio OpenRouter failed on", async () => {
    const usage = memoryUsage();
    const failing = (async () => Response.json({ error: { message: "busy" } }, { status: 503 })) as unknown as typeof fetch;
    const res = await handle(transcribe(5), env, usage, now, failing);
    expect(res.status).toBe(503);
    expect(usage.seconds).toBe(0);
  });

  it("refuses models outside the app's list", async () => {
    const res = await handle(transcribe(5, "openai/gpt-5"), env, memoryUsage(), now, ok);
    expect(res.status).toBe(400);
  });

  it("needs a device ID", async () => {
    const res = await handle(transcribe(5, undefined, {}), env, memoryUsage(), now, ok);
    expect(res.status).toBe(400);
  });

  it("reports usage", async () => {
    const res = await handle(new Request("https://example.com/v1/usage", { headers: { "X-Device-Id": device } }),
      env, memoryUsage(90), now, ok);
    expect(await res.json()).toEqual({ used_seconds: 90, free_seconds: 1800, month: "2026-11" });
  });
});
