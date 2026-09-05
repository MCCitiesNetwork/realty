import { describe, expect, it } from "vitest";
import { activityVisible, searchVisible } from "./scoped";
import { listing, otherWorld, pageOf, rental, rentEvent } from "../test-support/fixtures";
import { failure, queriesTo, stubClient, type Query } from "../test-support/stubClient";
import { visibilityOf } from "../visibility";

describe("searchVisible", () => {
  it("asks once, unscoped, when every world shows", async () => {
    const { client, get } = stubClient({ "/v1/regions/search": pageOf("results", [listing]) });
    const answer = await searchVisible(client, visibilityOf([]), { type: "sale" }, 8);
    expect(answer.data?.results).toEqual([listing]);
    expect(queriesTo(get, "/v1/regions/search")).toEqual([{ type: "sale", pageSize: 8 }]);
  });

  it("asks once per listed world and merges in the search's own order", async () => {
    const dearer = { ...listing, worldGuardRegionId: "dear", world: otherWorld, price: 9000 };
    const { client, get } = stubClient({
      "/v1/regions/search": (query: Query) => query.world === "My World"
        ? pageOf("results", [dearer], 1)
        : pageOf("results", [listing, rental], 30),
    });
    const answer = await searchVisible(client, visibilityOf(["world", "My World"]), { type: "sale" }, 2);

    expect(queriesTo(get, "/v1/regions/search").map((q) => q.world)).toEqual(["world", "My World"]);
    // Highest asking price first across both worlds, cut to the sample size.
    expect(answer.data?.results.map((r) => r.worldGuardRegionId)).toEqual(["dear", "plot_a"]);
    expect(answer.data?.totalCount).toBe(31);
  });

  it("lets a world the API does not know contribute nothing rather than fail the page", async () => {
    // A typo in the whitelist hides one world, not the front page.
    const { client } = stubClient({
      "/v1/regions/search": (query: Query) => query.world === "Typo" ? failure(404, "WORLD_NOT_FOUND") : pageOf("results", [listing]),
    });
    const answer = await searchVisible(client, visibilityOf(["world", "Typo"]), {}, 8);
    expect(answer.data?.results).toEqual([listing]);
  });

  it("reports a failure of any listed world as the page's failure", async () => {
    const { client } = stubClient({
      "/v1/regions/search": (query: Query) => query.world === "world" ? failure(500, "INTERNAL_ERROR") : pageOf("results", [listing]),
    });
    const answer = await searchVisible(client, visibilityOf(["world", "My World"]), {}, 8);
    expect(answer.error).toBeDefined();
  });
});

describe("activityVisible", () => {
  it("merges newest first across the listed worlds", async () => {
    const older = { ...rentEvent, worldGuardRegionId: "older", eventTime: "2026-09-01T00:00:00Z" };
    const { client } = stubClient({
      "/v1/activity": (query: Query) => pageOf("events", query.world === "world" ? [older] : [rentEvent]),
    });
    const answer = await activityVisible(client, visibilityOf(["world", "My World"]), 6);
    expect(answer.data?.events.map((e) => e.worldGuardRegionId)).toEqual(["m646-2", "older"]);
  });
});
