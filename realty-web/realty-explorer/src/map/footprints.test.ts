import { describe, expect, it } from "vitest";
import { extentOf, outlineOf, spanOf } from "./footprints";

const entry = (shape: "CUBOID" | "POLYGONAL", points: Array<{ x: number; z: number }>,
               priority = 0) => ({
  worldGuardRegionId: "plot_a",
  dimensions: { shape, minY: 0, maxY: 255, priority, points },
});

describe("outlineOf", () => {
  it("closes a cuboid on the block past its far corner", () => {
    // WorldGuard's corners are inclusive: a region from x=0 to x=9 is ten blocks wide,
    // so an outline drawn through x=9 leaves the last row of blocks outside it.
    expect(outlineOf(entry("CUBOID", [{ x: 0, z: 0 }, { x: 9, z: 0 }, { x: 9, z: 4 }, { x: 0, z: 4 }])))
      .toEqual({
        regionId: "plot_a",
        priority: 0,
        outline: [{ x: 0, z: 0 }, { x: 10, z: 0 }, { x: 10, z: 5 }, { x: 0, z: 5 }],
      });
  });

  it("draws a one-block region, which arrives as a single corner", () => {
    // The API sends distinct corners only, so a one-by-one region -- a marker, a
    // signpost -- is one point. Drawn as a polygon that is nothing at all.
    expect(outlineOf(entry("CUBOID", [{ x: 4, z: 7 }]))?.outline)
      .toEqual([{ x: 4, z: 7 }, { x: 5, z: 7 }, { x: 5, z: 8 }, { x: 4, z: 8 }]);
  });

  it("draws a one-block-wide strip, which arrives as a line", () => {
    expect(outlineOf(entry("CUBOID", [{ x: 4, z: 0 }, { x: 4, z: 9 }]))?.outline)
      .toEqual([{ x: 4, z: 0 }, { x: 5, z: 0 }, { x: 5, z: 10 }, { x: 4, z: 10 }]);
  });

  it("keeps a polygon's vertices as they were sent", () => {
    const points = [{ x: 0, z: 0 }, { x: 10, z: 2 }, { x: 6, z: 9 }];
    expect(outlineOf(entry("POLYGONAL", points))?.outline).toEqual(points);
  });

  it("falls back to the bounding box for a polygon too degenerate to draw", () => {
    expect(outlineOf(entry("POLYGONAL", [{ x: 1, z: 1 }, { x: 3, z: 5 }]))?.outline)
      .toEqual([{ x: 1, z: 1 }, { x: 4, z: 1 }, { x: 4, z: 6 }, { x: 1, z: 6 }]);
  });

  it("places nothing for a region the server could not locate", () => {
    // Null dimensions mean the query module is unreachable, or WorldGuard no longer
    // holds a region the register does. Neither is a shape.
    expect(outlineOf({ worldGuardRegionId: "plot_a", dimensions: null })).toBeNull();
    expect(outlineOf(entry("CUBOID", []))).toBeNull();
  });
});

describe("extentOf", () => {
  it("covers every corner of every outline", () => {
    const footprints = [
      outlineOf(entry("CUBOID", [{ x: 0, z: 0 }, { x: 9, z: 9 }]))!,
      outlineOf(entry("CUBOID", [{ x: -40, z: 20 }, { x: -30, z: 25 }]))!,
    ];
    expect(extentOf(footprints)).toEqual({ minX: -40, minZ: 0, maxX: 10, maxZ: 26 });
  });

  it("has no extent when there is nothing to draw", () => {
    expect(extentOf([])).toBeNull();
  });
});

describe("priority", () => {
  it("carries WorldGuard's priority, which is what settles an overlap", () => {
    // Two regions covering one block are settled by priority in game, and a map has to draw
    // them the same way round or it shows the one that does not apply.
    expect(outlineOf(entry("CUBOID", [{ x: 0, z: 0 }, { x: 9, z: 9 }], 7))?.priority).toBe(7);
  });

  it("reads an unprioritised region as zero, which is WorldGuard's own default", () => {
    expect(outlineOf(entry("CUBOID", [{ x: 0, z: 0 }, { x: 9, z: 9 }]))?.priority).toBe(0);
  });
});

describe("spanOf", () => {
  it("measures the ground a footprint's box covers, which breaks a tie between equals", () => {
    // Equal priorities are settled by size, so the smaller of two is drawn on top and stays
    // reachable rather than disappearing under the one that contains it.
    const district = outlineOf(entry("CUBOID", [{ x: 0, z: 0 }, { x: 99, z: 99 }]))!;
    const plot = outlineOf(entry("CUBOID", [{ x: 10, z: 10 }, { x: 19, z: 19 }]))!;

    expect(spanOf(district)).toBe(100 * 100);
    expect(spanOf(plot)).toBe(10 * 10);
    expect(spanOf(district)).toBeGreaterThan(spanOf(plot));
  });
});
