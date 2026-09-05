import { describe, expect, it, vi } from "vitest";
import {
  createApiClient,
  fetchResourcePacks,
  fetchResourcePackAttribution,
  fetchSchematic,
  type ApiClient,
} from "./client";

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

describe("fetchResourcePacks", () => {
  const clientReturning = (body: unknown, ok = true): ApiClient => {
    const stub = {
      GET: vi.fn(async () =>
        ok ? { data: body, error: undefined } : { data: undefined, error: body }),
    };
    return stub as unknown as ApiClient;
  };

  it("returns the pack when the server configures one", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("zip", { status: 200 })));
    const packs = await fetchResourcePacks(
      clientReturning({ packs: [{ url: "https://cdn.example.com/p.zip", attribution: [] }], hash: "abc", required: true }));
    // Not toBeInstanceOf(Blob): jsdom and undici each define their own Blob class,
    // so a perfectly good blob fails an identity check across those realms.
    expect(packs).toHaveLength(1);
    expect(await packs[0].blob.text()).toBe("zip");
    vi.unstubAllGlobals();
  });

  it("returns null when the server configures no pack", async () => {
    // The default in server.properties. Untextured geometry, not an error.
    const packs = await fetchResourcePacks(clientReturning({ packs: [], hash: null, required: false }));
    expect(packs).toEqual([]);
  });

  it("returns null when the module is unreachable", async () => {
    const packs = await fetchResourcePacks(clientReturning({ error: "RESOURCE_PACK_UNAVAILABLE" }, false));
    expect(packs).toEqual([]);
  });

  it("returns null when the pack host blocks the request", async () => {
    // The likely case in practice: pack hosts serve the game client, which is not a
    // browser and does not enforce CORS, so they rarely send the headers we need.
    vi.stubGlobal("fetch", vi.fn(async () => {
      throw new TypeError("Failed to fetch");
    }));
    const packs = await fetchResourcePacks(
      clientReturning({ packs: [{ url: "https://cdn.example.com/p.zip", attribution: [] }], hash: null, required: false }));
    expect(packs).toEqual([]);
    vi.unstubAllGlobals();
  });

  it("returns null when the pack URL 404s", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("", { status: 404 })));
    const packs = await fetchResourcePacks(
      clientReturning({ packs: [{ url: "https://cdn.example.com/gone.zip", attribution: [] }], hash: null, required: false }));
    expect(packs).toEqual([]);
    vi.unstubAllGlobals();
  });
});

describe("resource pack caching", () => {
  const packUrl = "https://cdn.example.com/p.zip";

  const countingClient = () => {
    const get = vi.fn(async () => ({
      data: { packs: [{ url: packUrl, attribution: [{ text: "Example Pack 32x", url: null }] }], hash: null, required: false },
      error: undefined,
    }));
    return { client: { GET: get } as unknown as ApiClient, get };
  };

  it("asks the API about the pack once, however many callers want it", async () => {
    // The credit line and the renderer both want this, and each region page asked
    // again on mount. It describes a server setting that cannot change while the page
    // is open.
    vi.stubGlobal("fetch", vi.fn(async () => new Response("zip", { status: 200 })));
    const { client, get } = countingClient();

    await Promise.all([
      fetchResourcePackAttribution(client),
      fetchResourcePacks(client),
      fetchResourcePackAttribution(client),
    ]);
    await fetchResourcePacks(client);

    expect(get).toHaveBeenCalledTimes(1);
  });

  it("downloads the pack itself only once", async () => {
    // A 64x pack is around 18 MB. The HTTP cache spares the network on a return visit
    // but not the decode, and that cost landed on every region page.
    const download = vi.fn(async (_url: string) => new Response("zip", { status: 200 }));
    vi.stubGlobal("fetch", download);
    const { client } = countingClient();

    const first = await fetchResourcePacks(client);
    const second = await fetchResourcePacks(client);

    expect(download.mock.calls.filter((call) => call[0] === packUrl)).toHaveLength(1);
    // The same array of the same Blobs, not merely equal ones -- that is what makes it free.
    expect(second).toBe(first);
  });

  it("caches per client, so a different client is not served a stale pack", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("zip", { status: 200 })));
    const a = countingClient();
    const b = countingClient();

    await fetchResourcePacks(a.client);
    await fetchResourcePacks(b.client);

    expect(a.get).toHaveBeenCalledTimes(1);
    expect(b.get).toHaveBeenCalledTimes(1);
  });
});
