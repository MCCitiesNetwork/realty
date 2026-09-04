import { useEffect, useRef } from "react";
import { SchematicRenderer } from "schematic-renderer";
import { fetchSchematic, type ApiClient } from "../api/client";

type Props = { client: ApiClient; world: string; region: string };

/**
 * Renders the region's captured schematic in 3D.
 *
 * <p>Imported lazily by the detail screen: this pulls in Three.js and a WASM mesh
 * pipeline, which the browse screen must not pay for.</p>
 *
 * <p>No resource pack is supplied, so geometry renders untextured. Shipping one
 * means shipping copyrighted texture assets, which is a licensing decision rather
 * than an engineering one.</p>
 */
export function SchematicViewer({ client, world, region }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const renderer = new SchematicRenderer(
      canvas,
      { [region]: fetchSchematic(client, world, region) },
      {},
      { showGrid: true },
    );

    return () => {
      // Without this, moving between regions leaks a WebGL context per visit and
      // the browser eventually refuses to create more.
      (renderer as unknown as { dispose?: () => void }).dispose?.();
    };
  }, [client, world, region]);

  return <canvas ref={canvasRef} aria-label={`3D preview of ${region}`} />;
}
