import { describe, expect, it } from "vitest";
import { failure, queriesTo, stubClient, type Query } from "../test-support/stubClient";
import { readWorldMap, type WorldMap } from "./worldMap";

const cuboid = (id: string, x = 0, z = 0) => ({
  worldGuardRegionId: id,
  dimensions: { shape: "CUBOID", minY: 0, maxY: 255, points: [{ x, z }, { x: x + 9, z: z + 9 }] },
});

const geometryPage = (regions: unknown[], totalPages: number, totalCount = regions.length) => ({
  page: 1, pageSize: 100, totalCount, totalPages, world: { id: "w", name: "world" }, regions,
});

const searchPage = (results: unknown[], totalPages = 1) => ({
  page: 1, pageSize: 100, totalCount: results.length, totalPages, results,
});

const listing = {
  worldGuardRegionId: "plot_a",
  world: { id: "w", name: "world" },
  contractType: "freehold",
  price: 1500,
  state: "FOR_SALE",
  durationSeconds: null,
};

/** Every report one read produced, oldest first. */
async function readAll(client: Parameters<typeof readWorldMap>[0], world = "world"): Promise<WorldMap[]> {
  const reports: WorldMap[] = [];
  await readWorldMap(client, world, (map) => reports.push(map), () => false);
  return reports;
}

describe("readWorldMap", () => {
  it("reads every registered region, across as many pages as there are", async () => {
    // One page per request is one main-thread hop on the game server, so the map asks
    // for the largest page the API serves and then asks for the rest of them.
    const { client, get } = stubClient({
      "/v1/worlds/geometry": (query: Query) =>
        geometryPage([cuboid(`page_${query.page}`)], 3, 3),
      "/v1/regions/search": searchPage([]),
    });

    const reports = await readAll(client);

    expect(reports.at(-1)!.footprints.map((footprint) => footprint.regionId))
      .toEqual(["page_1", "page_2", "page_3"]);
    expect(queriesTo(get, "/v1/worlds/geometry").map((query) => query.page)).toEqual([1, 2, 3]);
    expect(queriesTo(get, "/v1/worlds/geometry")[0].pageSize).toBe(100);
  });

  it("reports each page as it lands rather than only the finished world", async () => {
    // A built-up world is the better part of a minute's reading. Drawn page by page it
    // is useful a second in; drawn at the end it is a minute of blank screen.
    const { client } = stubClient({
      "/v1/worlds/geometry": (query: Query) => geometryPage([cuboid(`page_${query.page}`)], 3, 3),
      "/v1/regions/search": searchPage([]),
    });

    const reports = await readAll(client);

    expect(reports.length).toBeGreaterThan(1);
    expect(reports[0].footprints).toHaveLength(1);
    expect(reports[0].done).toBe(false);
    expect(reports.at(-1)!.done).toBe(true);
  });

  it("says how far through the world it is", async () => {
    const { client } = stubClient({
      "/v1/worlds/geometry": (query: Query) => geometryPage([cuboid(`page_${query.page}`)], 4, 4),
      "/v1/regions/search": searchPage([]),
    });

    const reports = await readAll(client);

    expect(reports[0].total).toBe(4);
    expect(reports[0].read).toBe(1);
    expect(reports.at(-1)!.read).toBe(4);
  });

  it("counts the regions the server could not place rather than dropping them", async () => {
    // With the query module down this is every region, and a map that just came up
    // empty would read as a world nobody has built in.
    const { client } = stubClient({
      "/v1/worlds/geometry": geometryPage(
        [cuboid("a"), { worldGuardRegionId: "b", dimensions: null }], 1),
      "/v1/regions/search": searchPage([]),
    });

    const final = (await readAll(client)).at(-1)!;

    expect(final.footprints).toHaveLength(1);
    expect(final.unplaced).toBe(1);
  });

  it("keys the market by region, so a plot knows its own price", async () => {
    const { client } = stubClient({
      "/v1/worlds/geometry": geometryPage([cuboid("plot_a")], 1),
      "/v1/regions/search": (query: Query) => searchPage(query.type === "freehold" ? [listing] : []),
    });

    expect((await readAll(client)).at(-1)!.market.get("plot_a")?.price).toBe(1500);
  });

  it("reads every contract, not only what is on the market", async () => {
    // `all` is the market view, which leaves out a sold freehold with no asking price;
    // a map that drew one as having no contract would be wrong about it.
    const sold = { ...listing, worldGuardRegionId: "plot_b", price: null, state: "SOLD" as const };
    const let_ = { ...listing, worldGuardRegionId: "plot_c", contractType: "leasehold" as const, state: "LEASED" as const };
    const { client, get } = stubClient({
      "/v1/worlds/geometry": geometryPage([cuboid("plot_b"), cuboid("plot_c")], 1),
      "/v1/regions/search": (query: Query) => searchPage(query.type === "freehold" ? [sold] : [let_]),
    });

    const final = (await readAll(client)).at(-1)!;

    expect(queriesTo(get, "/v1/regions/search").map((q) => q.type).sort()).toEqual(["freehold", "leasehold"]);
    expect(final.market.get("plot_b")?.state).toBe("SOLD");
    expect(final.market.get("plot_c")?.state).toBe("LEASED");
  });

  it("still draws the world when the market cannot be read", async () => {
    // Colour is enrichment. Losing it costs a legend, and losing the map costs the
    // screen -- the same trade the API makes when it cannot read a region's shape.
    const { client } = stubClient({
      "/v1/worlds/geometry": geometryPage([cuboid("a")], 1),
      "/v1/regions/search": failure(502, "MODULE_UNAVAILABLE"),
    });

    const final = (await readAll(client)).at(-1)!;

    expect(final.footprints).toHaveLength(1);
    expect(final.market.size).toBe(0);
    expect(final.error).toBeUndefined();
  });

  it("reports an error when the regions themselves cannot be read", async () => {
    const { client } = stubClient({
      "/v1/worlds/geometry": failure(404, "WORLD_NOT_FOUND"),
      "/v1/regions/search": searchPage([]),
    });

    const final = (await readAll(client, "unknown")).at(-1)!;

    expect(final.error?.httpStatus).toBe(404);
    expect(final.error?.code).toBe("WORLD_NOT_FOUND");
  });

  it("stops at a ceiling rather than asking the game server forever", async () => {
    const { client, get } = stubClient({
      "/v1/worlds/geometry": (query: Query) => geometryPage([cuboid(`r${query.page}`)], 250, 25000),
      "/v1/regions/search": searchPage([]),
    });

    const final = (await readAll(client)).at(-1)!;

    expect(queriesTo(get, "/v1/worlds/geometry")).toHaveLength(200);
    expect(final.read).toBeLessThan(final.total);
  });

  it("stops reading, and stops reporting, once the world has changed", async () => {
    // A read still running when the visitor picks another world would otherwise draw
    // the old world's plots onto the new one's ground.
    const { client, get } = stubClient({
      "/v1/worlds/geometry": (query: Query) => geometryPage([cuboid(`page_${query.page}`)], 20, 20),
      "/v1/regions/search": searchPage([]),
    });

    const reports: WorldMap[] = [];
    let stopped = false;
    await readWorldMap(client, "world", (map) => {
      reports.push(map);
      stopped = true;
    }, () => stopped);

    expect(reports).toHaveLength(1);
    expect(queriesTo(get, "/v1/worlds/geometry").length).toBeLessThan(20);
  });
});
