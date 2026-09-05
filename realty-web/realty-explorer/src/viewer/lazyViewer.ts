import { lazy } from "react";

/**
 * Loads the 3D viewer chunk.
 *
 * <p>It is far and away the largest thing this app can pull -- around 12 MB of renderer
 * plus a 10 MB WASM mesher -- which is why it is split out of the main bundle and why
 * the browse screen must never pay for it.</p>
 */
export const importViewer = () => import("./SchematicViewer");

/** Warms the chunk without rendering it. Idempotent: the import cache does the work. */
export function prefetchViewer(): void {
  void importViewer().catch(() => {
    // A prefetch that fails costs nothing: the real import will retry and report.
  });
}

export const SchematicViewer = lazy(() =>
  importViewer().then((module) => ({ default: module.SchematicViewer })),
);
