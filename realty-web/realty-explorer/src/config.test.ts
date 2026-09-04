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

describe("loadConfig resourcePackAttribution", () => {
  const withConfig = (body: unknown) =>
    mockFetch(async () => new Response(JSON.stringify(body), { status: 200 }));

  it("keeps text and an http(s) link", async () => {
    withConfig({ resourcePackAttribution: [{ text: "Faithful 64x", href: "https://faithfulpack.net/" }] });
    expect((await loadConfig()).resourcePackAttribution)
      .toEqual([{ text: "Faithful 64x", href: "https://faithfulpack.net/" }]);
  });

  it("keeps an entry with no link", async () => {
    withConfig({ resourcePackAttribution: [{ text: "Example Server" }] });
    expect((await loadConfig()).resourcePackAttribution).toEqual([{ text: "Example Server" }]);
  });

  it("drops a javascript: link but keeps the text", async () => {
    // The value is operator-supplied and rendered into the page, so the scheme is
    // checked rather than merely the presence of a string.
    withConfig({ resourcePackAttribution: [{ text: "Click", href: "javascript:alert(1)" }] });
    expect((await loadConfig()).resourcePackAttribution).toEqual([{ text: "Click" }]);
  });

  it("drops a relative link, which would resolve against this page", async () => {
    withConfig({ resourcePackAttribution: [{ text: "Credits", href: "/credits" }] });
    expect((await loadConfig()).resourcePackAttribution).toEqual([{ text: "Credits" }]);
  });

  it("ignores malformed entries rather than failing the whole config", async () => {
    withConfig({ resourcePackAttribution: [{ text: "" }, { text: 42 }, "nope", null, { text: "Kept" }] });
    expect((await loadConfig()).resourcePackAttribution).toEqual([{ text: "Kept" }]);
  });

  it("is empty when attribution is absent or not a list", async () => {
    withConfig({ apiBaseUrl: "" });
    expect((await loadConfig()).resourcePackAttribution).toEqual([]);
    withConfig({ resourcePackAttribution: "Example Server" });
    expect((await loadConfig()).resourcePackAttribution).toEqual([]);
  });

  it("bounds the number and length of entries", async () => {
    withConfig({
      resourcePackAttribution: Array.from({ length: 20 }, (_, i) => ({ text: `entry ${i}`.padEnd(300, "!") })),
    });
    const entries = (await loadConfig()).resourcePackAttribution;
    expect(entries).toHaveLength(8);
    expect(entries[0].text.length).toBeLessThanOrEqual(120);
  });
});
