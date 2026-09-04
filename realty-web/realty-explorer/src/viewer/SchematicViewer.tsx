import { useEffect, useRef } from "react";
import { SchematicRenderer } from "schematic-renderer";
import { fetchResourcePack, fetchSchematic, type ApiClient } from "../api/client";

type Props = { client: ApiClient; world: string; region: string };

/**
 * Renders the region's captured schematic in 3D.
 *
 * <p>Imported lazily by the detail screen: this pulls in Three.js and a WASM mesh
 * pipeline, which the browse screen must not pay for.</p>
 *
 * <p>The resource pack is whatever the game server points its own clients at, fetched
 * from where the operator already hosts it. Realty stores and serves no texture assets
 * of its own. When there is no pack -- or it cannot be fetched -- geometry renders
 * untextured, which is a downgrade rather than a failure.</p>
 */
export function SchematicViewer({ client, world, region }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    // The pack is resolved before construction because SchematicRenderer builds its
    // texture atlas during initialisation; adding one afterwards would mean a rebuild.
    let renderer: { dispose?: () => void } | undefined;
    let disposed = false;

    void fetchResourcePack(client).then((pack) => {
      if (disposed) return;
      renderer = new SchematicRenderer(
        canvas,
        { [region]: fetchSchematic(client, world, region) },
        pack ? { server: async () => pack } : {},
        {
          showGrid: true,
          // Both default to false in schematic-renderer. Without the first the
          // camera cannot be moved at all -- the preview is a fixed still, and the
          // "drag to orbit" hint beside it is untrue. Without the second, dropping
          // a resource pack onto the canvas silently does nothing, even though the
          // library's own console message suggests it.
          enableInteraction: true,
          enableDragAndDrop: true,
        },
      ) as unknown as { dispose?: () => void };
    });

    return () => {
      disposed = true;
      // Without this, moving between regions leaks a WebGL context per visit and
      // the browser eventually refuses to create more.
      renderer?.dispose?.();
    };
  }, [client, world, region]);

  return <canvas ref={canvasRef} aria-label={`3D preview of ${region}`} />;
}
