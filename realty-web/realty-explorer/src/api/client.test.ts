import { describe, expect, it, vi } from "vitest";
import { createApiClient, fetchResourcePack, fetchSchematic, type ApiClient } from "./client";

/** Captures the URL openapi-fetch actually requested. */
const requestedUrl = (spy: ReturnType<typeof vi.fn>): string => {
  const first = spy.mock.calls[0][0];
  return typeof first === "string" ? first : String((first as Request).url);
};

describe("fetchSchematic", () => {
  it("reads the response as an ArrayBuffer", async () => {
    // openapi-fetch does not type-enforce parseAs, so this assertion is the only
    // thing standing between us and a runtime failure on the binary endpoint.
    const bytes = new Uint8Array([1, 2, 3]);
    const fetchSpy = vi.fn(async () =>
      new Response(bytes, {
        status: 200,
        headers: { "Content-Type": "application/octet-stream" },
      }));

    const client = createApiClient("", fetchSpy as unknown as typeof fetch);
    const result = await fetchSchematic(client, "world", "plot_a")();

    expect(result).toBeInstanceOf(ArrayBuffer);
    expect(new Uint8Array(result)).toEqual(bytes);
  });

  it("passes world and region as query parameters", async () => {
    const fetchSpy = vi.fn(async () => new Response(new ArrayBuffer(0), { status: 200 }));
    const client = createApiClient("", fetchSpy as unknown as typeof fetch);

    await fetchSchematic(client, "My World", "plot_a")();

    const url = requestedUrl(fetchSpy);
    expect(url).toContain("region=plot_a");
    expect(url).toMatch(/world=My(\+|%20)World/);
  });

  it("defaults to the page's own origin when no base URL is configured", async () => {
    // The bundled deployment ships no config.json, so this is its every request.
    const fetchSpy = vi.fn(async () => new Response(new ArrayBuffer(0), { status: 200 }));
    const client = createApiClient("", fetchSpy as unknown as typeof fetch);

    await fetchSchematic(client, "world", "plot_a")();

    expect(requestedUrl(fetchSpy)).toContain(`${window.location.origin}/v1/region/schematic`);
  });

  it("targets the configured base URL", async () => {
    const fetchSpy = vi.fn(async () => new Response(new ArrayBuffer(0), { status: 200 }));
    const client = createApiClient("https://api.example.com", fetchSpy as unknown as typeof fetch);

    await fetchSchematic(client, "world", "plot_a")();

    expect(requestedUrl(fetchSpy)).toContain("https://api.example.com/v1/region/schematic");
  });

  it("throws when the region has no schematic, so the caller can show a panel", async () => {
    const fetchSpy = vi.fn(async () =>
      new Response(JSON.stringify({ error: "SCHEMATIC_NOT_FOUND" }), {
        status: 404,
        headers: { "Content-Type": "application/json" },
      }));
    const client = createApiClient("", fetchSpy as unknown as typeof fetch);

    await expect(fetchSchematic(client, "world", "plot_a")()).rejects.toThrow(/no schematic/i);
  });
});

describe("fetchResourcePack", () => {
  const clientReturning = (body: unknown, ok = true): ApiClient => {
    const stub = {
      GET: vi.fn(async () =>
        ok ? { data: body, error: undefined } : { data: undefined, error: body }),
    };
    return stub as unknown as ApiClient;
  };

  it("returns the pack when the server configures one", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("zip", { status: 200 })));
    const pack = await fetchResourcePack(
      clientReturning({ url: "https://cdn.example.com/p.zip", hash: "abc", required: true }));
    // Not toBeInstanceOf(Blob): jsdom and undici each define their own Blob class,
    // so a perfectly good blob fails an identity check across those realms.
    expect(pack).not.toBeNull();
    expect(await pack!.text()).toBe("zip");
    vi.unstubAllGlobals();
  });

  it("returns null when the server configures no pack", async () => {
    // The default in server.properties. Untextured geometry, not an error.
    const pack = await fetchResourcePack(clientReturning({ url: null, hash: null, required: false }));
    expect(pack).toBeNull();
  });

  it("returns null when the module is unreachable", async () => {
    const pack = await fetchResourcePack(clientReturning({ error: "RESOURCE_PACK_UNAVAILABLE" }, false));
    expect(pack).toBeNull();
  });

  it("returns null when the pack host blocks the request", async () => {
    // The likely case in practice: pack hosts serve the game client, which is not a
    // browser and does not enforce CORS, so they rarely send the headers we need.
    vi.stubGlobal("fetch", vi.fn(async () => {
      throw new TypeError("Failed to fetch");
    }));
    const pack = await fetchResourcePack(
      clientReturning({ url: "https://cdn.example.com/p.zip", hash: null, required: false }));
    expect(pack).toBeNull();
    vi.unstubAllGlobals();
  });

  it("returns null when the pack URL 404s", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("", { status: 404 })));
    const pack = await fetchResourcePack(
      clientReturning({ url: "https://cdn.example.com/gone.zip", hash: null, required: false }));
    expect(pack).toBeNull();
    vi.unstubAllGlobals();
  });
});
