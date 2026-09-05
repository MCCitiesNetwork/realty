import { describe, expect, it } from "vitest";
import { listingsPath, playerPath, regionPath, worldLabel } from "./paths";

describe("regionPath", () => {
  it("uses the world's name, and encodes what a folder name may carry", () => {
    expect(regionPath({ id: "x", name: "My World" }, "plot a/b")).toBe("/region/My%20World/plot%20a%2Fb");
  });

  it("falls back to the world's id when it has no name", () => {
    // A null name is a world the RealtyWorld table has never seen; the id is still true.
    expect(regionPath({ id: "8f4d1c2e-0000-0000-0000-000000000099", name: null }, "plot_a"))
      .toBe("/region/8f4d1c2e-0000-0000-0000-000000000099/plot_a");
    expect(worldLabel({ id: "abc" })).toBe("abc");
  });
});

describe("playerPath", () => {
  it("accepts a player or a bare id", () => {
    expect(playerPath({ id: "abc", name: "Alice" })).toBe("/players/abc");
    expect(playerPath("abc")).toBe("/players/abc");
  });
});

describe("listingsPath", () => {
  it("repeats a tag per value, and leaves out what is unset", () => {
    expect(listingsPath({ type: "rent", tag: ["shop", "cbd"], world: undefined, sort: "" }))
      .toBe("/listings?type=rent&tag=shop&tag=cbd");
  });

  it("is the bare listings route when nothing is filtered", () => {
    expect(listingsPath({})).toBe("/listings");
    expect(listingsPath({ tag: [] })).toBe("/listings");
  });
});
