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

describe("loadConfig, the visible worlds", () => {
  it("shows every world when nothing is listed", async () => {
    mockFetch(async () => new Response(JSON.stringify({ apiBaseUrl: "" }), { status: 200 }));
    expect((await loadConfig()).visibleWorlds).toEqual([]);
  });

  it("reads the names, trimmed, each once, and drops anything that is not a name", async () => {
    // A broken entry hides one world, not the site.
    mockFetch(async () => new Response(JSON.stringify({
      visibleWorlds: [" Reveille ", "Hamilton", "Reveille", 7, "", null],
    }), { status: 200 }));
    expect((await loadConfig()).visibleWorlds).toEqual(["Reveille", "Hamilton"]);
  });

  it("survives a list that is not a list", async () => {
    mockFetch(async () => new Response(JSON.stringify({ visibleWorlds: "Reveille" }), { status: 200 }));
    expect((await loadConfig()).visibleWorlds).toEqual([]);
  });
});
