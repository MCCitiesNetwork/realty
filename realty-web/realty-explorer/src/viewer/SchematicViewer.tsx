import { useCallback, useEffect, useRef, useState } from "react";
import type { DragEvent } from "react";
import { SchematicRenderer } from "schematic-renderer";
import { fetchResourcePack, fetchSchematic, type ApiClient } from "../api/client";

type Props = {
  client: ApiClient;
  world: string;
  region: string;
  /**
   * The schematic's bytes, when the caller already has them.
   *
   * <p>The detail screen downloads the schematic to find out whether there is one to
   * show, so without this the same megabytes were fetched a second time the moment the
   * viewer mounted -- the preview appeared at roughly half the speed the network
   * allowed. Omitted, the viewer fetches for itself and stands alone.</p>
   */
  schematic?: ArrayBuffer;
};

/** The parts of the renderer instance this component actually uses. */
type Renderer = {
  dispose?: () => void;
  addResourcePack?: (file: File) => Promise<unknown>;
};

/**
 * Renders the region's captured schematic in 3D.
 *
 * <p>Imported lazily by the detail screen: this pulls in Three.js and a WASM mesh
 * pipeline, which the browse screen must not pay for.</p>
 *
 * <p>The resource pack is whatever the game server points its own clients at, fetched
 * from where the operator already hosts it. Realty stores and serves no texture assets
 * of its own.</p>
 *
 * <p><strong>Without a pack the preview is close to useless.</strong> Blocks are not
 * drawn untextured -- most are not drawn at all, because there is no model or texture
 * to build a mesh from. Chests still appear, being block entities with their own
 * handling, so a plot renders as a couple of chests floating in space and reads as a
 * failed capture rather than a missing texture set. That is why a viewer can drop
 * their own pack, and why the server pack is worth exposing.</p>
 *
 * <p>A viewer may drop their own resource pack onto the canvas. Resource packs only --
 * see the drop handler.</p>
 */
export function SchematicViewer({ client, world, region, schematic }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const rendererRef = useRef<Renderer | undefined>(undefined);
  const [packNote, setPackNote] = useState<string | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    // The pack is resolved before construction because SchematicRenderer builds its
    // texture atlas during initialisation; adding one afterwards means a rebuild.
    let disposed = false;

    void fetchResourcePack(client).then((pack) => {
      if (disposed) return;
      rendererRef.current = new SchematicRenderer(
        canvas,
        { [region]: schematic ? async () => schematic : fetchSchematic(client, world, region) },
        pack ? { server: async () => pack } : {},
        {
          showGrid: true,
          // Defaults to false, which leaves the camera fixed -- and makes the
          // "drag to orbit" hint beside the canvas untrue.
          enableInteraction: true,
          // Deliberately NOT enabled. The library's own drop handler branches on the
          // dropped file's type and accepts schematics as well as resource packs,
          // which would let any visitor load arbitrary geometry into what is meant to
          // be this region's preview. The handler below takes packs only.
          enableDragAndDrop: false,
        },
      ) as unknown as Renderer;
    });

    return () => {
      disposed = true;
      // Without this, moving between regions leaks a WebGL context per visit and the
      // browser eventually refuses to create more.
      rendererRef.current?.dispose?.();
      rendererRef.current = undefined;
    };
  }, [client, world, region, schematic]);

  /**
   * Accepts a dropped resource pack, and nothing else.
   *
   * <p>A dropped schematic is refused on purpose: this canvas shows one region's
   * captured preview, and letting a visitor swap in their own file would make it show
   * something that is not the region. The extension check only sorts packs from
   * schematics; the renderer validates the pack's contents itself, so a wrongly named
   * {@code .zip} still fails safely.</p>
   */
  const handleDrop = useCallback((event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    const file = event.dataTransfer.files?.[0];
    if (!file) return;

    if (!file.name.toLowerCase().endsWith(".zip")) {
      setPackNote("Only resource packs (.zip) can be dropped here.");
      return;
    }

    const renderer = rendererRef.current;
    if (!renderer?.addResourcePack) return;

    setPackNote(`Loading ${file.name}…`);
    void renderer
      .addResourcePack(file)
      .then(() => setPackNote(`Textured with ${file.name}.`))
      .catch(() => setPackNote(`${file.name} is not a valid resource pack.`));
  }, []);

  return (
    <div
      className="viewer-drop"
      onDrop={handleDrop}
      onDragOver={(event) => event.preventDefault()}
    >
      <canvas ref={canvasRef} aria-label={`3D preview of ${region}`} />
      {packNote && <p className="viewer-drop-note" role="status">{packNote}</p>}
    </div>
  );
}
