export type AppConfig = {
  /** Absolute API origin, or "" meaning same-origin (requests go to a relative /v1). */
  apiBaseUrl: string;
};

const EMPTY: AppConfig = { apiBaseUrl: "" };

/**
 * Reads /config.json if it is there.
 *
 * Every failure resolves to the empty config rather than throwing. The bundled
 * (realty-web-dist) deployment ships no such file at all, and a static deployment
 * behind a reverse proxy does not need one, so a 404 is a normal case for one build
 * that has to serve both.
 *
 * Deliberately the only thing configured here. Anything the game server already knows
 * -- the resource pack and its credits, for instance -- is answered by the API rather
 * than restated in a second file on what may be a second host.
 */
export async function loadConfig(): Promise<AppConfig> {
  try {
    const response = await fetch("/config.json", { cache: "no-store" });
    if (!response.ok) return EMPTY;
    const parsed = (await response.json()) as Partial<AppConfig>;
    const base = typeof parsed.apiBaseUrl === "string" ? parsed.apiBaseUrl.trim() : "";
    // A trailing slash would double up against the leading slash of every path.
    return { apiBaseUrl: base.replace(/\/+$/, "") };
  } catch {
    return EMPTY;
  }
}
