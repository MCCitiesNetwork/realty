import createClient, { type Client } from "openapi-fetch";
import type { components, paths } from "./schema";

export type ApiClient = Client<paths>;

/**
 * @param baseUrl absolute API origin, or "" for same-origin
 * @param fetchImpl injected by tests; production uses the global fetch
 */
export function createApiClient(baseUrl: string, fetchImpl?: typeof fetch): ApiClient {
  return createClient<paths>({
    baseUrl: resolveBaseUrl(baseUrl),
    ...(fetchImpl ? { fetch: fetchImpl } : {}),
  });
}

/**
 * Turns the same-origin case into an explicit origin rather than leaving a bare "/".
 *
 * A "/" base makes openapi-fetch emit a relative URL. Browsers resolve those against
 * the document, but nothing else does -- so it is not merely untestable outside a
 * browser, it is implicit where it could be stated. Reading the origin makes the
 * bundled deployment's "same origin" concrete at the one point it is decided.
 */
function resolveBaseUrl(baseUrl: string): string {
  if (baseUrl) return baseUrl;
  return typeof window === "undefined" ? "/" : window.location.origin;
}

/**
 * Returns a loader for the region's schematic bytes.
 *
 * The {@code parseAs} below is load-bearing and cannot be type-checked: openapi-fetch
 * leaves it unconditionally optional, so omitting it on this octet-stream endpoint
 * fails at runtime rather than at compile time. This is the only call site, so the
 * mistake is available exactly once.
 *
 * The returned closure is already the shape SchematicRenderer expects.
 */
export function fetchSchematic(
  client: ApiClient,
  world: string,
  region: string,
): () => Promise<ArrayBuffer> {
  return async () => {
    const { data, error } = await client.GET("/v1/region/schematic", {
      params: { query: { world, region } },
      parseAs: "arrayBuffer",
    });
    if (error || !data) {
      throw new Error(`No schematic for ${region} in ${world}`);
    }
    return data as ArrayBuffer;
  };
}

/** One credit line for the resource pack, with a link that has been vetted. */
export type Attribution = {
  text: string;
  /** Absolute http(s) target, or absent for plain text. */
  href?: string;
};

/** Bounds on operator input, so a malformed setting cannot produce an unusable page. */
const MAX_CREDITS = 8;
const MAX_CREDIT_TEXT = 120;

type PackResponse = components["schemas"]["ResourcePackResponse"];
type PackDescription = { data?: PackResponse; error?: unknown };

/**
 * Asks the API about the pack, once per client.
 *
 * Both the credit line and the renderer want this, and every region page asked again as
 * it mounted. It describes a server setting that cannot change while the page is open,
 * so the first request's promise is the answer for the life of the client.
 */
const packDescriptions = new WeakMap<ApiClient, Promise<PackDescription>>();

function describeResourcePack(client: ApiClient): Promise<PackDescription> {
  let described = packDescriptions.get(client);
  if (!described) {
    described = client.GET("/v1/resource-pack", {}) as Promise<PackDescription>;
    packDescriptions.set(client, described);
  }
  return described;
}

/**
 * Fetches the credits owed for the configured resource pack, or an empty list.
 *
 * The credits are configured beside the pack URL, on the game server, since the operator
 * who picks a pack is the one who knows what its licence asks for. They arrive here as
 * operator-written strings destined for the page, so they are re-checked rather than
 * trusted: text is bounded and rendered as text, and a link is dropped unless it is an
 * absolute http(s) URL -- which is what excludes `javascript:`. The module already
 * rejects those at startup; this is the second check, not the only one.
 *
 * A failure yields an empty list. There is nothing useful to show a visitor about an
 * unreachable module, and the credit's absence is already visible to the operator.
 */
export async function fetchResourcePackAttribution(client: ApiClient): Promise<Attribution[]> {
  const { data, error } = await describeResourcePack(client);
  if (error || !data?.attribution) return [];

  return data.attribution
    .slice(0, MAX_CREDITS)
    .map((credit): Attribution | null => {
      const text = typeof credit.text === "string" ? credit.text.trim() : "";
      if (!text) return null;
      return { text: text.slice(0, MAX_CREDIT_TEXT), ...safeHref(credit.url) };
    })
    .filter((credit): credit is Attribution => credit !== null);
}

function safeHref(url: unknown): { href?: string } {
  if (typeof url !== "string" || !url.trim()) return {};
  try {
    const parsed = new URL(url.trim());
    return parsed.protocol === "http:" || parsed.protocol === "https:" ? { href: parsed.href } : {};
  } catch {
    // Not an absolute URL. A relative one would resolve against this page, which is
    // never what a credit link means.
    return {};
  }
}

/**
 * The pack itself, fetched and decoded once per client, or null when there is nothing
 * usable to fetch.
 *
 * A pack is tens of megabytes -- 18 MB is an ordinary size for a 64x one. The HTTP cache
 * spares the network on a return visit but not the decode, and every region page was
 * paying for both again, which is most of why the preview was quick on one visit and
 * slow on the next. Holding the Blob makes the first region page the only slow one.
 *
 * Three ways this legitimately yields null, none of them an error worth showing: the
 * server configures no pack (the default), the query-service module is not reachable
 * (a 502 here), or the pack is hosted somewhere that does not send CORS headers --
 * which is common, since those hosts only ever expected the game client, which is not
 * a browser and does not enforce CORS.
 *
 * In every case the caller falls back to no pack. That is a real loss of fidelity, not
 * a cosmetic one: without a pack the renderer draws almost nothing -- only block
 * entities such as chests survive -- so a plot looks like a failed capture. The viewer
 * can still drop their own pack onto the canvas.
 */
const packBlobs = new WeakMap<ApiClient, Promise<Blob | null>>();

export function fetchResourcePack(client: ApiClient): Promise<Blob | null> {
  let pack = packBlobs.get(client);
  if (!pack) {
    pack = loadResourcePack(client);
    packBlobs.set(client, pack);
  }
  return pack;
}

async function loadResourcePack(client: ApiClient): Promise<Blob | null> {
  const { data, error } = await describeResourcePack(client);
  if (error || !data?.url) return null;

  try {
    const response = await fetch(data.url);
    if (!response.ok) return null;
    return await response.blob();
  } catch {
    return null;
  }
}
