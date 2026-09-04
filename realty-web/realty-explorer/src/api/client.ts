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
