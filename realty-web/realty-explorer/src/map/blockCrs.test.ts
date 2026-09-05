import L from "leaflet";
import { describe, expect, it } from "vitest";
import { atBlock, BLOCK_CRS, parseBlockCoordinate } from "./blockCrs";
import { BLOCKS_PER_TILE, LOD_COUNT, blocksPerTile, lodForZoom } from "./blueMap";

/** The zoom at which BlueMap's finest level is drawn without stretching. */
const FINEST_ZOOM = LOD_COUNT - 1;

const pixelsAt = (x: number, z: number, zoom: number) =>
  BLOCK_CRS.latLngToPoint(L.latLng(atBlock(x, z)), zoom);

describe("parseBlockCoordinate", () => {
  it("reads a whole number, with or without the thousands separators the readout uses", () => {
    expect(parseBlockCoordinate("1204")).toBe(1204);
    expect(parseBlockCoordinate(" -1,204 ")).toBe(-1204);
    expect(parseBlockCoordinate("0")).toBe(0);
  });

  it("puts a fractional position in the block it is in, as the game does", () => {
    expect(parseBlockCoordinate("12.9")).toBe(12);
    expect(parseBlockCoordinate("-0.5")).toBe(-1);
  });

  it("refuses what is not a coordinate", () => {
    for (const text of ["", "abc", "1e5", "12 34", "--1", "30000001"]) {
      expect(parseBlockCoordinate(text)).toBeUndefined();
    }
  });
});

describe("BLOCK_CRS", () => {
  it("draws a block as a pixel at the finest level", () => {
    expect(BLOCK_CRS.scale(FINEST_ZOOM)).toBe(1);
  });

  it("puts north up and east to the right, as the server's own map does", () => {
    const origin = pixelsAt(0, 0, FINEST_ZOOM);
    const northEast = pixelsAt(100, -100, FINEST_ZOOM);
    expect(northEast.x - origin.x).toBe(100);
    expect(northEast.y - origin.y).toBe(-100);
  });

  it("lands every BlueMap level on a zoom of its own", () => {
    // This is the whole point of the five-fold ladder: at each zoom, one tile's worth
    // of ground has to be one tile image wide, or Leaflet asks for a grid of tiles
    // BlueMap never stored.
    for (let zoom = 0; zoom <= FINEST_ZOOM; zoom++) {
      const tile = blocksPerTile(lodForZoom(zoom));
      const corner = pixelsAt(tile, tile, zoom);
      expect(corner.x).toBeCloseTo(BLOCKS_PER_TILE, 6);
      expect(corner.y).toBeCloseTo(BLOCKS_PER_TILE, 6);
    }
  });

  it("round-trips a part-way zoom, which is what smooth zooming asks of it", () => {
    // Zoom levels five apart are too coarse to step through, so the map zooms
    // continuously and Leaflet converts back and forth on every frame.
    expect(BLOCK_CRS.zoom(BLOCK_CRS.scale(1.4))).toBeCloseTo(1.4, 10);
  });
});
