import type { ApiClient } from "./client";
import type { components } from "./schema";
import { type Visibility } from "../visibility";

type Outcome<T> = { data?: T; error?: unknown; response?: { status: number } };
type SearchResponse = components["schemas"]["SearchResponse"];
type ActivityResponse = components["schemas"]["ActivityResponse"];
type AuctionsResponse = components["schemas"]["AuctionsResponse"];

/**
 * A "top N across the visible worlds" query, for the front page's samples.
 *
 * The API filters by one world at a time. Under a whitelist of several, the page asks
 * once per world in parallel and merges the answers in the order the API itself would
 * have used; with every world visible it asks once, unscoped. A world the API does not
 * know -- a typo in the whitelist -- answers 404 and simply contributes nothing, so a
 * slip hides one world rather than the page. Only for short samples: a merge of pages
 * cannot page, which is why the listings screen keeps to one world instead.
 */
async function acrossVisibleWorlds<T, I>(
  visibility: Visibility,
  ask: (world: string | undefined) => Promise<Outcome<T>>,
  itemsOf: (answer: T) => I[],
  order: (a: I, b: I) => number,
  rebuild: (items: I[], totalCount: number, template: T) => T,
  take: number,
): Promise<Outcome<T>> {
  if (visibility.all) return ask(undefined);

  const answers = await Promise.all(visibility.names.map((world) => ask(world)));
  const items: I[] = [];
  let totalCount = 0;
  let template: T | undefined;
  for (const answer of answers) {
    if (answer.response?.status === 404) continue;
    if (answer.error !== undefined || answer.data === undefined) return answer;
    template ??= answer.data;
    items.push(...itemsOf(answer.data));
    totalCount += (answer.data as { totalCount?: number }).totalCount ?? 0;
  }
  if (template === undefined) {
    // Every listed world was unknown to the API. An empty answer, not a failure.
    return { data: undefined, error: { error: "WORLD_NOT_FOUND", message: "No listed world is known" }, response: { status: 404 } };
  }
  items.sort(order);
  return { data: rebuild(items.slice(0, take), totalCount, template), error: undefined, response: { status: 200 } };
}

/** Highest asking price first, an unpriced freehold last -- the search's own order. */
const byPriceDesc = (a: { price: number | null }, b: { price: number | null }) =>
  (b.price ?? -Infinity) - (a.price ?? -Infinity);

export function searchVisible(
  client: ApiClient,
  visibility: Visibility,
  query: { type?: "sale" | "rent"; occupancy?: "unoccupied" | "occupied" },
  take: number,
): Promise<Outcome<SearchResponse>> {
  return acrossVisibleWorlds<SearchResponse, SearchResponse["results"][number]>(
    visibility,
    (world) => client.GET("/v1/regions/search", {
      params: { query: { ...query, pageSize: take, ...(world ? { world } : {}) } },
    }),
    (answer) => answer.results,
    byPriceDesc,
    (results, totalCount, template) => ({ ...template, page: 1, totalCount, totalPages: Math.ceil(totalCount / take), results }),
    take,
  );
}

export function activityVisible(client: ApiClient, visibility: Visibility, take: number): Promise<Outcome<ActivityResponse>> {
  return acrossVisibleWorlds<ActivityResponse, ActivityResponse["events"][number]>(
    visibility,
    (world) => client.GET("/v1/activity", { params: { query: { pageSize: take, ...(world ? { world } : {}) } } }),
    (answer) => answer.events,
    (a, b) => b.eventTime.localeCompare(a.eventTime),
    (events, totalCount, template) => ({ ...template, page: 1, totalCount, totalPages: Math.ceil(totalCount / take), events }),
    take,
  );
}

export function auctionsVisible(client: ApiClient, visibility: Visibility, take: number): Promise<Outcome<AuctionsResponse>> {
  return acrossVisibleWorlds<AuctionsResponse, AuctionsResponse["auctions"][number]>(
    visibility,
    (world) => client.GET("/v1/auctions", { params: { query: { pageSize: take, ...(world ? { world } : {}) } } }),
    (answer) => answer.auctions,
    (a, b) => a.endDate.localeCompare(b.endDate),
    (auctions, totalCount, template) => ({ ...template, page: 1, totalCount, totalPages: Math.ceil(totalCount / take), auctions }),
    take,
  );
}
