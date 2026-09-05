import type { components } from "../api/schema";

type GeometryEntry = components["schemas"]["WorldGeometryResponse_Entry"];

/** One corner of an outline, in block coordinates. */
export type Corner = { x: number; z: number };

export type Footprint = {
  regionId: string;
  /**
   * WorldGuard's priority, which settles what covers what.
   *
   * <p>Where two regions cover one block, the higher priority is the one whose rules apply
   * there. A map has the same question to answer and must answer it the same way: a plot
   * inside a district is drawn over the district, or it cannot be seen or pointed at.</p>
   */
  priority: number;
  /** The outline in map order, at least three corners, left open rather than closed. */
  outline: Corner[];
};

/** The block rectangle a set of outlines covers. */
export type Extent = { minX: number; minZ: number; maxX: number; maxZ: number };

/**
 * One region's outline, or null when the server could not say where the region is.
 *
 * <p>A null arrives for a region the register holds but WorldGuard no longer does, and
 * for every region when the game server's query module is unreachable. Neither is an
 * error the map can act on, so both are simply not drawn.</p>
 *
 * <p>A cuboid arrives as WorldGuard's corner blocks, which are inclusive and therefore
 * not an outline: a region from x=4 to x=4 is one block wide rather than none. The API
 * also sends distinct corners only, so that region arrives as a single point and a
 * one-block strip as a line -- both undrawable as polygons. Taking the bounding box and
 * adding a block past the far corner fixes all three at once, and lands on the block
 * boundary where the region really ends.</p>
 *
 * <p>A polygon's vertices are kept as sent. Its edges then run through the middle of
 * the boundary blocks rather than around them, half a block short on each side, which
 * is invisible at a scale where a block is a pixel.</p>
 */
export function outlineOf(entry: GeometryEntry): Footprint | null {
  const points = entry.dimensions?.points;
  if (!points || points.length === 0) return null;

  const priority = entry.dimensions?.priority ?? 0;

  if (entry.dimensions?.shape === "POLYGONAL" && points.length >= 3) {
    return {
      regionId: entry.worldGuardRegionId,
      priority,
      outline: points.map((point) => ({ x: point.x, z: point.z })),
    };
  }

  const xs = points.map((point) => point.x);
  const zs = points.map((point) => point.z);
  const minX = Math.min(...xs);
  const minZ = Math.min(...zs);
  const maxX = Math.max(...xs) + 1;
  const maxZ = Math.max(...zs) + 1;
  return {
    regionId: entry.worldGuardRegionId,
    priority,
    outline: [
      { x: minX, z: minZ },
      { x: maxX, z: minZ },
      { x: maxX, z: maxZ },
      { x: minX, z: maxZ },
    ],
  };
}

/** The rectangle every outline fits inside, or null when there are none to fit. */
export function extentOf(footprints: readonly Footprint[]): Extent | null {
  let extent: Extent | null = null;
  for (const footprint of footprints) {
    for (const corner of footprint.outline) {
      if (!extent) {
        extent = { minX: corner.x, minZ: corner.z, maxX: corner.x, maxZ: corner.z };
        continue;
      }
      extent.minX = Math.min(extent.minX, corner.x);
      extent.minZ = Math.min(extent.minZ, corner.z);
      extent.maxX = Math.max(extent.maxX, corner.x);
      extent.maxZ = Math.max(extent.maxZ, corner.z);
    }
  }
  return extent;
}

/**
 * How much ground a footprint's bounding box covers.
 *
 * <p>The tie-breaker between two regions of equal priority: the smaller is drawn last, so a
 * plot sitting inside an equally-prioritised one is still visible and can still be pointed
 * at. WorldGuard settles an equal-priority overlap by parentage, which is not reported here,
 * and size is the reading of it that a map can act on.</p>
 */
export function spanOf(footprint: Footprint): number {
  const extent = extentOf([footprint]);
  if (!extent) return 0;
  return (extent.maxX - extent.minX) * (extent.maxZ - extent.minZ);
}
