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

describe("loadConfig, the server map", () => {
  it("reads the map's address and strips a trailing slash", async () => {
    mockFetch(async () => new Response(JSON.stringify({
      map: { baseUrl: "https://map.example.com/" },
    }), { status: 200 }));
    expect((await loadConfig()).map.baseUrl).toBe("https://map.example.com");
  });

  it("configures no map when the section is absent", async () => {
    mockFetch(async () => new Response(JSON.stringify({ apiBaseUrl: "" }), { status: 200 }));
    expect((await loadConfig()).map).toEqual({ baseUrl: "", ids: {} });
  });

  it("refuses an address that is not absolute http", async () => {
    // A tile address is concatenated and handed to an image. A relative base would
    // point every tile back at this site, and a javascript: one is refused for the
    // reason every operator-written link here is.
    for (const baseUrl of ["/maps", "javascript:alert(1)", "not a url"]) {
      mockFetch(async () => new Response(JSON.stringify({ map: { baseUrl } }), { status: 200 }));
      expect((await loadConfig()).map.baseUrl).toBe("");
    }
  });

  it("reads the worlds whose BlueMap name differs, and ignores anything else", async () => {
    mockFetch(async () => new Response(JSON.stringify({
      map: { baseUrl: "https://m", ids: { Hamilton: " hamilton ", Broken: 7, Blank: "  " } },
    }), { status: 200 }));
    expect((await loadConfig()).map.ids).toEqual({ Hamilton: "hamilton" });
  });

  it("survives a map section that is not an object", async () => {
    mockFetch(async () => new Response(JSON.stringify({ map: "https://m" }), { status: 200 }));
    expect((await loadConfig()).map).toEqual({ baseUrl: "", ids: {} });
  });
});

describe("loadConfig, the logo", () => {
  it("reads an absolute http(s) address, trimmed", async () => {
    mockFetch(async () => new Response(JSON.stringify({
      logoUrl: " https://example.net/emblem.png ",
    }), { status: 200 }));
    expect((await loadConfig()).logoUrl).toBe("https://example.net/emblem.png");
  });

  it("configures none when the key is absent", async () => {
    mockFetch(async () => new Response(JSON.stringify({ apiBaseUrl: "" }), { status: 200 }));
    expect((await loadConfig()).logoUrl).toBe("");
  });

  it("refuses anything but absolute http(s)", async () => {
    // The value lands in an <img src> and a <link rel="icon">: no relative paths
    // pointing back at this site, no data: or javascript: schemes.
    for (const logoUrl of ["/emblem.png", "data:image/png;base64,AAAA", "javascript:alert(1)", 7]) {
      mockFetch(async () => new Response(JSON.stringify({ logoUrl }), { status: 200 }));
      expect((await loadConfig()).logoUrl).toBe("");
    }
  });
});

describe("loadConfig, the currency", () => {
  it("reads the symbol, trimmed", async () => {
    mockFetch(async () => new Response(JSON.stringify({ currency: " $ " }), { status: 200 }));
    expect((await loadConfig()).currency).toBe("$");
  });

  it("configures none when the key is absent or not text", async () => {
    for (const currency of [undefined, 7, null]) {
      mockFetch(async () => new Response(JSON.stringify({ currency }), { status: 200 }));
      expect((await loadConfig()).currency).toBe("");
    }
  });

  it("keeps it to a symbol's length, since it goes in front of every figure", async () => {
    mockFetch(async () => new Response(JSON.stringify({ currency: "a very long currency name" }), { status: 200 }));
    expect((await loadConfig()).currency).toBe("a very l");
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
