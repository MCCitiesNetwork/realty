export type AppConfig = {
  /** Absolute API origin, or "" meaning same-origin (requests go to a relative /v1). */
  apiBaseUrl: string;
  /**
   * The worlds this site shows, by name; empty means every world the register knows.
   *
   * A whitelist, and a deployment setting rather than an API one: the same API can
   * serve a public site that hides the staff and event worlds and a staff site that
   * shows everything. It narrows what this site *shows* -- the world filter, the
   * listings, region pages, the activity feed, auctions, a player's holdings -- and
   * nothing about what the API answers. Server-wide figures the API computes without
   * a world in the question (the totals, tag counts, the owners leaderboard, a player's
   * counts) stay server-wide.
   */
  visibleWorlds: string[];
};

const EMPTY: AppConfig = { apiBaseUrl: "", visibleWorlds: [] };

/** Bounds on operator input, so a malformed setting cannot produce an unusable page. */
const MAX_VISIBLE_WORLDS = 64;

/**
 * Reads /config.json if it is there.
 *
 * Every failure resolves to the empty config rather than throwing. The bundled
 * (realty-web-dist) deployment ships no such file at all, and a static deployment
 * behind a reverse proxy does not need one, so a 404 is a normal case for one build
 * that has to serve both.
 *
 * Deliberately narrow. Anything the game server already knows -- the resource pack and
 * its credits, for instance -- is answered by the API rather than restated in a second
 * file on what may be a second host.
 */
export async function loadConfig(): Promise<AppConfig> {
  try {
    const response = await fetch("/config.json", { cache: "no-store" });
    if (!response.ok) return EMPTY;
    const parsed = (await response.json()) as Record<string, unknown>;
    const base = typeof parsed.apiBaseUrl === "string" ? parsed.apiBaseUrl.trim() : "";
    return {
      // A trailing slash would double up against the leading slash of every path.
      apiBaseUrl: base.replace(/\/+$/, ""),
      visibleWorlds: readVisibleWorlds(parsed.visibleWorlds),
    };
  } catch {
    return EMPTY;
  }
}

/**
 * World names only, trimmed, each once. A name that is not a string is dropped rather
 * than failing the whole config: a broken entry should hide one world, not the site.
 */
function readVisibleWorlds(raw: unknown): string[] {
  if (!Array.isArray(raw)) return [];
  const names: string[] = [];
  for (const entry of raw.slice(0, MAX_VISIBLE_WORLDS)) {
    if (typeof entry !== "string") continue;
    const trimmed = entry.trim();
    if (trimmed && !names.includes(trimmed)) names.push(trimmed);
  }
  return names;
}
