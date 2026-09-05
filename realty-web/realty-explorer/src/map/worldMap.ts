import { useEffect, useState } from "react";
import type { ApiClient } from "../api/client";
import type { components } from "../api/schema";
import { TTL, remembered } from "../api/remembered";
import { describeApiError, type ApiError } from "../api/useQuery";
import { outlineOf, type Footprint } from "./footprints";

/** One region as the market search reports it. */
export type Listing = components["schemas"]["SearchResponse_Result"];

/** One world's map, as far as it has been read. */
export type WorldMap = {
  footprints: Footprint[];
  /** Every contract, by region id. Fills in behind the outlines. */
  market: Map<string, Listing>;
  /**
   * Registered regions the game server could not place, and so could not draw.
   *
   * <p>Worth reporting rather than hiding: when the query-service module is down this
   * is every region in the world, and a map that simply came up empty would look like
   * a world with nothing in it.</p>
   */
  unplaced: number;
  /** Registered regions read so far, against how many the world holds. */
  read: number;
  total: number;
  /** True once there is nothing further to read, whether or not everything arrived. */
  done: boolean;
  /** Set when the regions themselves could not be read, which leaves no map to draw. */
  error?: ApiError;
};

export const NOTHING_DRAWN: WorldMap = {
  footprints: [], market: new Map(), unplaced: 0, read: 0, total: 0, done: false,
};

/** The largest page the API will serve, so the fewest requests cover a world. */
const PAGE_SIZE = 100;

/** Pages in flight at once: enough to fill a world quickly, few enough to stay polite. */
const CONCURRENT_PAGES = 5;

/**
 * A ceiling on how many pages one map will ask for.
 *
 * <p>Not a page size anyone should reach -- twenty thousand regions in a single world
 * is past what these routes are shaped for -- but every page costs the game server a
 * main-thread hop, so there is a number at which the map stops asking rather than one
 * at which it asks forever.</p>
 */
const MAX_PAGES = 200;

/** The shape every openapi-fetch call resolves to. */
type Outcome<T> = { data?: T; error?: unknown; response?: { status: number } };

type Page<T> = { rows: T[]; totalPages: number; totalCount: number };

/**
 * Follows one world's map as it is read, reporting after every page.
 *
 * <p>Reported rather than returned because a built-up world is thousands of regions and
 * a page is a hop on the game server's main thread: waiting for all of them is the
 * better part of a minute staring at nothing. Drawn page by page, the map is useful
 * about a second in and simply gets fuller.</p>
 *
 * <p>The two halves are read at once and fail differently. Without footprints there is
 * no map at all, so a failed geometry request is reported as the map's error. The
 * contract search decides what each plot is, so a failure there leaves plots undrawn
 * rather than costing the visitor the map's error page -- the same trade the API itself
 * makes when it cannot read a region's shape.</p>
 */
export async function readWorldMap(
  client: ApiClient,
  world: string,
  report: (map: WorldMap) => void,
  cancelled: () => boolean,
): Promise<void> {
  const footprints: Footprint[] = [];
  const market = new Map<string, Listing>();
  let unplaced = 0;
  let read = 0;
  let total = 0;
  let error: ApiError | undefined;

  const publish = (done: boolean) => {
    if (cancelled()) return;
    report({ footprints: [...footprints], market: new Map(market), unplaced, read, total, done, error });
  };

  const geometry = readPages(
    // Remembered per page: leaving the map for a plot's page and coming back is the
    // commonest thing a visitor does here, and the footprints have not moved in between.
    (page) => remembered(client, `geometry:${world}:${page}`, TTL.geometry,
      () => client.GET("/v1/worlds/geometry", {
        params: { query: { world, page, pageSize: PAGE_SIZE } },
      })).then((outcome) => asPage(outcome, (data) => data.regions)),
    (rows, count) => {
      total = count;
      read += rows.length;
      for (const row of rows) {
        const footprint = outlineOf(row);
        if (footprint) footprints.push(footprint);
        else unplaced += 1;
      }
      publish(false);
    },
    cancelled,
  ).then((failure) => {
    error = failure;
  });

  // Every contract, not the market view: `all` is what is for sale or rent, which
  // leaves a sold freehold with no asking price out, and a map that drew one as
  // having no contract would be wrong about it. `freehold` and `leasehold` between
  // them are the whole register.
  const contracts = (["freehold", "leasehold"] as const).map((type) => readPages(
    (page) => remembered(client, `market:${world}:${type}:${page}`, TTL.geometry,
      () => client.GET("/v1/regions/search", {
        params: { query: { world, type, page, pageSize: PAGE_SIZE } },
      })).then((outcome) => asPage(outcome, (data) => data.results)),
    (rows) => {
      for (const listing of rows) market.set(listing.worldGuardRegionId, listing);
      publish(false);
    },
    cancelled,
    // A market that cannot be read costs colour, not the map, so its failure is
    // swallowed here rather than reported.
  ).catch(() => undefined));

  await Promise.all([geometry, ...contracts]);
  publish(true);
}

/** Narrows a paged response to the rows and the counts every one of them carries. */
function asPage<T, D extends { totalPages: number; totalCount: number }>(
  outcome: Outcome<D>,
  rowsOf: (data: D) => T[],
): Outcome<Page<T>> {
  if (!outcome.data) return { error: outcome.error, response: outcome.response };
  return {
    data: {
      rows: rowsOf(outcome.data),
      totalPages: outcome.data.totalPages,
      totalCount: outcome.data.totalCount,
    },
  };
}

/**
 * Reads every page there is, handing each to {@code onPage} as it lands.
 *
 * <p>The first page is what says how many there are, so it is read alone and the rest
 * in batches. A failed page stops the reading and is returned: half a world's regions
 * drawn without a word would read as a world where the missing half does not exist.</p>
 */
async function readPages<T>(
  loadPage: (page: number) => Promise<Outcome<Page<T>>>,
  onPage: (rows: T[], totalCount: number) => void,
  cancelled: () => boolean,
): Promise<ApiError | undefined> {
  const first = await loadPage(1);
  if (cancelled()) return undefined;
  if (!first.data) return describeApiError(first.error, first.response);
  onPage(first.data.rows, first.data.totalCount);

  const lastPage = Math.min(first.data.totalPages, MAX_PAGES);
  for (let page = 2; page <= lastPage; page += CONCURRENT_PAGES) {
    if (cancelled()) return undefined;
    const batch: Promise<Outcome<Page<T>>>[] = [];
    for (let next = page; next < page + CONCURRENT_PAGES && next <= lastPage; next++) {
      batch.push(loadPage(next));
    }
    for (const outcome of await Promise.all(batch)) {
      if (cancelled()) return undefined;
      if (!outcome.data) return describeApiError(outcome.error, outcome.response);
      onPage(outcome.data.rows, outcome.data.totalCount);
    }
  }
  return undefined;
}

/** One world's map, redrawn as each page of it arrives. */
export function useWorldMap(client: ApiClient, world: string | undefined): WorldMap {
  const [map, setMap] = useState<WorldMap>(NOTHING_DRAWN);

  useEffect(() => {
    setMap(NOTHING_DRAWN);
    if (!world) return;

    // Reads in flight when the world changes would otherwise draw the old world's
    // plots onto the new one's ground.
    let stopped = false;
    void readWorldMap(client, world, setMap, () => stopped);
    return () => {
      stopped = true;
    };
  }, [client, world]);

  return map;
}
