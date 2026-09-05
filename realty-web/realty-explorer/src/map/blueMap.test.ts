import { describe, expect, it } from "vitest";
import { blocksPerTile, lodForZoom, mapIdFor, tileUrl } from "./blueMap";

describe("tileUrl", () => {
  it("addresses a tile by level and coordinates", () => {
    expect(tileUrl("https://map.example.com", "hamilton", 1, 0, 0))
      .toBe("https://map.example.com/maps/hamilton/tiles/1/x0/z0.png");
  });

  it("splits a multi-digit coordinate into one directory per digit", () => {
    // This is the layout BlueMap writes to disk, and so the only one a plain static
    // host can serve. Its own web server also answers the undivided form, which is how
    // a map works in testing and breaks once it moves behind nginx.
    expect(tileUrl("https://m", "h", 1, 12, 345)).toBe("https://m/maps/h/tiles/1/x1/2/z3/4/5.png");
  });

  it("keeps a negative sign with the first digit", () => {
    expect(tileUrl("https://m", "h", 2, -12, -3)).toBe("https://m/maps/h/tiles/2/x-1/2/z-3.png");
  });

  it("encodes the map id, which is a path segment and not a promise about characters", () => {
    expect(tileUrl("https://m", "my map", 1, 0, 0)).toContain("/maps/my%20map/");
  });
});

describe("lodForZoom", () => {
  it("draws the coarsest level at zoom 0 and the finest at the last", () => {
    expect(lodForZoom(0)).toBe(3);
    expect(lodForZoom(1)).toBe(2);
    expect(lodForZoom(2)).toBe(1);
  });

  it("clamps past either end, since zoom carries on where the levels stop", () => {
    // Zooming past the finest level stretches its tiles; there is no level 0 to ask
    // for, and asking would fetch 3D geometry rather than an image.
    expect(lodForZoom(5)).toBe(1);
    expect(lodForZoom(-2)).toBe(3);
  });
});

describe("blocksPerTile", () => {
  it("covers five times more ground at each coarser level", () => {
    expect(blocksPerTile(1)).toBe(500);
    expect(blocksPerTile(2)).toBe(2500);
    expect(blocksPerTile(3)).toBe(12500);
  });
});

describe("mapIdFor", () => {
  it("uses the configured id for a world whose names differ", () => {
    const config = { baseUrl: "https://m", ids: { Hamilton: "hamilton-overworld" } };
    expect(mapIdFor(config, "Hamilton")).toBe("hamilton-overworld");
  });

  it("guesses the lower-cased world name, which is what BlueMap generates", () => {
    expect(mapIdFor({ baseUrl: "https://m", ids: {} }, "Reveille")).toBe("reveille");
  });
});
