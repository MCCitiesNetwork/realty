import type { ApiClient } from "./client";

type Entry = { at: number; value: Promise<unknown> };

/**
 * Answers already fetched, per client, keyed by what was asked for.
 *
 * Per client so a test's stub never sees another's answers, and so a page that builds
 * a new client -- the app builds exactly one -- starts fresh.
 */
const memory = new WeakMap<ApiClient, Map<string, Entry>>();

/** How long the slow-changing lists are trusted before being asked for again. */
export const TTL = {
  /** Worlds are registered when a region in them is; not while anyone is browsing. */
  worlds: 5 * 60_000,
  /** Tags and totals move as players trade. A minute is as stale as the API allows too. */
  tags: 60_000,
  stats: 60_000,
  /**
   * A map's footprints and prices. The same minute the API's own header allows, which is
   * what makes leaving the map for a plot and coming back cost nothing.
   */
  geometry: 60_000,
} as const;

/**
 * Runs `fetch` once and hands every caller within `ttlMs` the same answer.
 *
 * Every filter on the site wants the world list, the front page wants the tag list
 * twice, and going back to the front page asked for all of it again. Nothing here is
 * invalidated by hand: the lists are cheap to re-ask after the interval, and a stale
 * answer for that long is what the API's own cache headers already allow.
 *
 * A failed answer is not remembered. openapi-fetch reports failure as a resolved
 * `{ error }`, not a rejection, so both shapes are checked.
 */
export function remembered<T>(
  client: ApiClient,
  key: string,
  ttlMs: number,
  fetch: () => Promise<T>,
  now: () => number = Date.now,
): Promise<T> {
  let entries = memory.get(client);
  if (!entries) {
    entries = new Map();
    memory.set(client, entries);
  }
  const at = now();
  const held = entries.get(key);
  if (held && at - held.at < ttlMs) {
    return held.value as Promise<T>;
  }

  const value = fetch().then(
    (answer) => {
      if (isFailure(answer)) entries.delete(key);
      return answer;
    },
    (thrown: unknown) => {
      entries.delete(key);
      throw thrown;
    },
  );
  entries.set(key, { at, value });
  return value;
}

function isFailure(answer: unknown): boolean {
  return typeof answer === "object" && answer !== null && "error" in answer
    && (answer as { error?: unknown }).error !== undefined;
}
