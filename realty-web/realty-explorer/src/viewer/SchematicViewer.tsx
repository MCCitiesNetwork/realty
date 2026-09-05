import { useCallback, useEffect, useRef, useState } from "react";
import type { DragEvent } from "react";
import { SchematicRenderer } from "schematic-renderer";
import { fetchResourcePacks, fetchSchematic, type ApiClient } from "../api/client";

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

/**
 * The IndexedDB databases in which the renderer remembers resource packs between visits.
 *
 * <p>It keeps them in two layers, and both put stored packs back on start-up before
 * looking at the pack they are handed: the outer manager keeps a pack under the name it
 * was given -- always "server" here -- and skips fetching a default pack whose name it
 * already holds, while the inner one drops a handed pack whose bytes it has seen. So
 * once a browser had loaded any pack, a change of pack on the server never reached it:
 * the old pack came back from the store under the same name, and the preview built its
 * atlas from whatever that pack had, or from nothing. The server's answer is the only
 * pack this page means -- and it is cached in memory for the session -- so every stored
 * copy is dropped before each renderer starts. The atlas cache is a separate store and
 * is left alone.</p>
 */
const REMEMBERED_PACK_STORES = ["ResourcePacksDB", "cubane-resource-packs", "cubane-cache"];

async function forgetRememberedPacks(): Promise<void> {
  if (typeof indexedDB === "undefined") return;
  await Promise.all(REMEMBERED_PACK_STORES.map((name) => new Promise<void>((resolve) => {
    const request = indexedDB.deleteDatabase(name);
    // Best effort: a blocked delete finishes once the previous renderer lets go, and a
    // failure to delete is no reason to show no preview at all.
    request.onsuccess = () => resolve();
    request.onerror = () => resolve();
    request.onblocked = () => resolve();
  })));
}

type Point = { x: number; y: number; z: number };

/** The orbit controls, as far as this component adjusts them. */
type OrbitControls = {
  minDistance?: number;
  maxDistance?: number;
  enablePan?: boolean;
  target?: { set: (x: number, y: number, z: number) => unknown };
  update?: () => unknown;
};

/** The parts of the renderer instance this component actually uses. */
type Renderer = {
  dispose?: () => void;
  addResourcePack?: (file: File) => Promise<unknown>;
  schematicManager?: { getGlobalTightWorldBox?: () => { min: Point; max: Point } };
  cameraManager?: { controls?: Map<string, OrbitControls> };
};

/**
 * Keeps the camera outside the plot: a visitor can circle it and zoom, but not pass
 * through a wall into the rooms.
 *
 * <p>The nearest the camera may come is the radius of the sphere around the build, so
 * no angle of approach reaches the inside; the farthest is a few times that, so the
 * plot cannot be zoomed away to a speck. Panning is off because it moves the point
 * being orbited, and a point moved into the building takes the camera with it.</p>
 *
 * <p>Called once the schematic has rendered, since only then are its bounds known.</p>
 */
export function keepCameraOutside(renderer: Renderer | undefined): void {
  const box = renderer?.schematicManager?.getGlobalTightWorldBox?.();
  const orbit = renderer?.cameraManager?.controls?.get("orbit");
  if (!box || !orbit) return;
  const width = box.max.x - box.min.x;
  const height = box.max.y - box.min.y;
  const depth = box.max.z - box.min.z;
  const radius = Math.sqrt(width * width + height * height + depth * depth) / 2;
  if (!Number.isFinite(radius) || radius <= 0) return;
  orbit.minDistance = radius * 1.05;
  orbit.maxDistance = radius * 8;
  orbit.enablePan = false;
  orbit.target?.set((box.min.x + box.max.x) / 2, (box.min.y + box.max.y) / 2, (box.min.z + box.max.z) / 2);
  orbit.update?.();
}

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

    void Promise.all([fetchResourcePacks(client), forgetRememberedPacks()]).then(([packs]) => {
      if (disposed) return;
      rendererRef.current = new SchematicRenderer(
        canvas,
        { [region]: schematic ? async () => schematic : fetchSchematic(client, world, region) },
        // Keyed by index rather than by name so the record's insertion order is the
        // server's priority order, and two packs that happen to share a name cannot
        // collapse into one. The renderer resolves a contested texture in favour of the
        // pack it loaded first, which is the one the operator listed first.
        Object.fromEntries(packs.map((pack, index) => [`pack-${index}`, async () => pack.blob])),
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
          callbacks: {
            onSchematicRendered: () => keepCameraOutside(rendererRef.current),
          },
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
