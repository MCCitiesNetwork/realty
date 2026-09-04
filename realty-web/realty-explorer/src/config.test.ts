import { afterEach, describe, expect, it, vi } from "vitest";
import { loadConfig } from "./config";

const mockFetch = (impl: () => Promise<Response>) => {
  vi.stubGlobal("fetch", vi.fn(impl));
};

afterEach(() => vi.unstubAllGlobals());

describe("loadConfig", () => {
  it("uses apiBaseUrl from config.json when present", async () => {
    mockFetch(async () =>
      new Response(JSON.stringify({ apiBaseUrl: "https://api.example.com" }), { status: 200 }));
    expect((await loadConfig()).apiBaseUrl).toBe("https://api.example.com");
  });

  it("falls back to same-origin when config.json is missing", async () => {
    // The dist deployment ships no config.json at all: a 404 here is expected
    // traffic, not an error, and must not surface as one.
    mockFetch(async () => new Response("", { status: 404 }));
    expect((await loadConfig()).apiBaseUrl).toBe("");
  });

  it("falls back to same-origin when apiBaseUrl is empty", async () => {
    mockFetch(async () => new Response(JSON.stringify({ apiBaseUrl: "" }), { status: 200 }));
    expect((await loadConfig()).apiBaseUrl).toBe("");
  });

  it("falls back to same-origin when config.json is not valid JSON", async () => {
    // A misconfigured static host answering the SPA index for every path would
    // otherwise crash the app before it renders anything.
    mockFetch(async () => new Response("<html>404</html>", { status: 200 }));
    expect((await loadConfig()).apiBaseUrl).toBe("");
  });

  it("falls back to same-origin when the request throws", async () => {
    mockFetch(async () => {
      throw new Error("network down");
    });
    expect((await loadConfig()).apiBaseUrl).toBe("");
  });

  it("strips a trailing slash so paths concatenate cleanly", async () => {
    mockFetch(async () =>
      new Response(JSON.stringify({ apiBaseUrl: "https://api.example.com/" }), { status: 200 }));
    expect((await loadConfig()).apiBaseUrl).toBe("https://api.example.com");
  });
});
