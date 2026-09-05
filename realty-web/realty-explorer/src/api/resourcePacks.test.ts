import { describe, expect, it, vi } from "vitest";
import { type ApiClient, fetchResourcePacks } from "./client";

/** Fetching every configured pack, highest priority first. */
describe("fetchResourcePacks", () => {
  const clientReturning = (body: unknown, ok = true): ApiClient => {
    const stub = {
      GET: vi.fn(async () =>
        ok ? { data: body, error: undefined } : { data: undefined, error: body }),
    };
    return stub as unknown as ApiClient;
  };

  const twoPacks = {
    packs: [
      { url: "https://cdn.example.com/override.zip", attribution: [] },
      { url: "https://cdn.example.com/base.zip", attribution: [] },
    ],
    hash: null,
    required: false,
  };

  it("fetches every pack and keeps the server's order", async () => {
    vi.stubGlobal("fetch", vi.fn(async (url: string) =>
      new Response(url.includes("override") ? "over" : "base", { status: 200 })));

    const packs = await fetchResourcePacks(clientReturning(twoPacks));

    expect(packs).toHaveLength(2);
    expect(await packs[0].blob.text()).toBe("over");
    expect(await packs[1].blob.text()).toBe("base");
    vi.unstubAllGlobals();
  });

  it("keeps each pack's url so a caller can tell them apart", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("zip", { status: 200 })));
    const packs = await fetchResourcePacks(clientReturning(twoPacks));
    expect(packs.map((pack) => pack.url)).toEqual([
      "https://cdn.example.com/override.zip",
      "https://cdn.example.com/base.zip",
    ]);
    vi.unstubAllGlobals();
  });

  it("drops only the pack that fails, keeping the rest", async () => {
    // A pack host that sends no CORS headers is common. Losing one pack degrades the
    // preview; failing all of them would leave it drawing nothing at all.
    vi.stubGlobal("fetch", vi.fn(async (url: string) =>
      url.includes("override") ? Promise.reject(new Error("CORS")) : new Response("base", { status: 200 })));

    const packs = await fetchResourcePacks(clientReturning(twoPacks));

    expect(packs).toHaveLength(1);
    expect(packs[0].url).toBe("https://cdn.example.com/base.zip");
    vi.unstubAllGlobals();
  });

  it("returns nothing when the server configures no pack", async () => {
    const packs = await fetchResourcePacks(
      clientReturning({ packs: [], hash: null, required: false }));
    expect(packs).toEqual([]);
  });

  it("returns nothing when the module is unreachable", async () => {
    const packs = await fetchResourcePacks(
      clientReturning({ error: "RESOURCE_PACK_UNAVAILABLE" }, false));
    expect(packs).toEqual([]);
  });

  it("fetches once per client, not once per region page", async () => {
    const fetchSpy = vi.fn(async () => new Response("zip", { status: 200 }));
    vi.stubGlobal("fetch", fetchSpy);
    const client = clientReturning(twoPacks);

    await fetchResourcePacks(client);
    await fetchResourcePacks(client);

    expect(fetchSpy).toHaveBeenCalledTimes(2);
    vi.unstubAllGlobals();
  });
});
