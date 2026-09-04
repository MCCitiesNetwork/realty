import createClient, { type Client } from "openapi-fetch";
import type { paths } from "./schema";

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

/**
 * Fetches the server's resource pack for the renderer, or null when there is nothing
 * usable to fetch.
 *
 * Three ways this legitimately yields null, none of them an error worth showing:
 * the server configures no pack (the default), the query-service module is not
 * reachable (a 502 here), or the pack is hosted somewhere that does not send CORS
 * headers -- which is common, since those hosts only ever expected the game client,
 * which is not a browser and does not enforce CORS.
 *
 * In every case the caller falls back to no pack. Note that this is a real loss of
 * fidelity, not a cosmetic one: without a pack the renderer draws almost nothing --
 * only block entities such as chests survive -- so a plot looks like a failed capture.
 * The viewer can still drop their own pack onto the canvas.
 */
export async function fetchResourcePack(client: ApiClient): Promise<Blob | null> {
  const { data, error } = await client.GET("/v1/resource-pack", {});
  if (error || !data?.url) return null;

  try {
    const response = await fetch(data.url);
    if (!response.ok) return null;
    return await response.blob();
  } catch {
    // Almost always CORS. Not worth surfacing: the page still renders.
    return null;
  }
}
