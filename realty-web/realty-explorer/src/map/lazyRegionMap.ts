import { lazy } from "react";

/**
 * Loads the map chunk.
 *
 * <p>Leaflet and its stylesheet are only ever wanted on one route, and every other
 * screen would otherwise pay for them on first load. Split out, they arrive when
 * someone opens the map -- which is the same bargain the 3D viewer makes, for a
 * hundredth of the bytes.</p>
 */
export const importRegionMap = () => import("./RegionMap");

/** Warms the chunk without rendering it. Idempotent: the import cache does the work. */
export function prefetchRegionMap(): void {
  void importRegionMap().catch(() => {
    // A prefetch that fails costs nothing: the real import will retry and report.
  });
}

export const RegionMap = lazy(() =>
  importRegionMap().then((module) => ({ default: module.RegionMap })),
);
