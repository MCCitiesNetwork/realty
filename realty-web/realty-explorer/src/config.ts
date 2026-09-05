export type AppConfig = {
  /** Absolute API origin, or "" meaning same-origin (requests go to a relative /v1). */
  apiBaseUrl: string;
  /** Where the server's BlueMap lives, and what it calls each world. */
  map: MapConfig;
  /**
   * The server's emblem, as an absolute http(s) URL, shown beside the site name and as
   * the favicon. "" when the operator configures none, and a generic house stands in.
   * Whose site this is lives here for the reason the map address does: the register
   * does not know.
   */
  logoUrl: string;
  /**
   * What goes in front of every price: "$", say. "" when the operator configures none,
   * and prices are bare figures. Here rather than from the API because the API reports
   * a number and nothing about the economy behind it; only the operator knows what the
   * server's money is called.
   */
  currency: string;
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

/**
 * The server's BlueMap, which is a separate site this one only reads tiles from.
 *
 * Stated here rather than asked of the API because Realty's game server does not know
 * the answer: BlueMap is another plugin, published to a host the operator chose, and
 * nothing in the register records the address. That is the line this file draws --
 * what the game server knows, the API answers; where this deployment's neighbours
 * live, the operator says here.
 */
export type MapConfig = {
  /** Absolute http(s) origin, no trailing slash. "" when the operator configures none. */
  baseUrl: string;
  /**
   * Realty world name to BlueMap map id, for the worlds where the two differ.
   *
   * A world name is a folder on disk; a map id is whatever the operator wrote in
   * BlueMap's own config, and lower-casing is the usual difference -- the "Hamilton"
   * folder is the "hamilton" map. So the lower-cased world name is the guess for a
   * world not listed here. It cannot be checked at runtime: BlueMap sends no CORS
   * headers, so a page on another host may display its tiles but never read its
   * settings.
   */
  ids: Record<string, string>;
};

const EMPTY: AppConfig = { apiBaseUrl: "", map: { baseUrl: "", ids: {} }, logoUrl: "", currency: "", visibleWorlds: [] };

/** Bounds on operator input, so a malformed setting cannot produce an unusable page. */
const MAX_WORLD_IDS = 64;
const MAX_VISIBLE_WORLDS = 64;
/** A symbol or a short code, not a sentence: it is repeated in front of every figure. */
const MAX_CURRENCY_LENGTH = 8;

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
      map: readMapConfig(parsed.map),
      logoUrl: absoluteHttpUrl(parsed.logoUrl),
      currency: readCurrency(parsed.currency),
      visibleWorlds: readVisibleWorlds(parsed.visibleWorlds),
    };
  } catch {
    return EMPTY;
  }
}

function readMapConfig(raw: unknown): MapConfig {
  const map = isRecord(raw) ? raw : {};
  return { baseUrl: absoluteOrigin(map.baseUrl), ids: readWorldIds(map.ids) };
}

/**
 * An absolute http(s) origin, or "" for anything else.
 *
 * A tile address is built by concatenation and handed straight to an image, so a
 * relative base would quietly point every tile back at this site, and a `javascript:`
 * one is refused here for the same reason a credit link is.
 */
function absoluteOrigin(value: unknown): string {
  return absoluteHttpUrl(value).replace(/\/+$/, "");
}

/**
 * An absolute http(s) URL as written, or "" for anything else.
 *
 * The same refusal as for an origin: the value ends up in an image's `src` and a
 * `<link rel="icon">`, and neither should be pointed back at this site by a relative
 * path, nor at a `javascript:` or `data:` scheme the operator did not mean.
 */
function absoluteHttpUrl(value: unknown): string {
  if (typeof value !== "string" || !value.trim()) return "";
  try {
    const parsed = new URL(value.trim());
    return parsed.protocol === "http:" || parsed.protocol === "https:" ? parsed.href : "";
  } catch {
    return "";
  }
}

/** Named worlds only, and a bounded number of them: a map id is one path segment. */
function readWorldIds(raw: unknown): Record<string, string> {
  if (!isRecord(raw)) return {};
  const ids: Record<string, string> = {};
  for (const [world, id] of Object.entries(raw).slice(0, MAX_WORLD_IDS)) {
    if (typeof id !== "string") continue;
    const trimmed = id.trim();
    if (trimmed) ids[world] = trimmed;
  }
  return ids;
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

/** Trimmed, and cut to a length that still reads as a symbol; "" for anything else. */
function readCurrency(raw: unknown): string {
  return typeof raw === "string" ? raw.trim().slice(0, MAX_CURRENCY_LENGTH) : "";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
