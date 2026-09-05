import { useCallback, useEffect, useRef, useState } from "react";
import type { DragEvent } from "react";
import { SchematicRenderer } from "schematic-renderer";
import { fetchResourcePacks, fetchSchematic, type ApiClient } from "../api/client";
import { forgetRememberedPacksUnless } from "./rememberedPacks";

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
  cameraManager?: {
    controls?: Map<string, OrbitControls>;
    snapToDirection?: (direction: [number, number, number], refocus?: boolean) => unknown;
  };
};

/**
 * Where the camera sits relative to the plot: above its south-east corner, looking
 * north-west.
 *
 * <p>Blocks enter the scene at the coordinates the schematic gives them, and Minecraft
 * shares Three.js's handedness, so this vector is a compass bearing -- x east, y up, z
 * south. The library's own named angles do not agree with that compass, since the one it
 * calls north-east puts the camera to the south-east, so the bearing is given as a vector
 * rather than by name.</p>
 *
 * <p>South-east is the quadrant the renderer already favours, so this pins a bearing it
 * was already picking near rather than sending plots somewhere new. The y term is
 * shallower than the 1 a true isometric bearing would take, putting the camera 26 degrees
 * above the plot rather than 35, which shows more of its walls and less of its roofs.</p>
 */
const CAMERA_BEARING: [number, number, number] = [1, 0.7, 1];

/**
 * Turns the camera to that fixed bearing, so every plot opens the same way round.
 *
 * <p>Left alone the renderer derives an opening angle from each plot's own bounding box:
 * yaw across roughly 30 to 60 degrees by how deep the footprint is against its width,
 * pitch across 30 to 55 degrees by height against footprint. Every plot therefore opens facing
 * a little differently from its neighbour, and none of them matches the server map, whose
 * flat view is north-up. Nothing recoverable says which way a plot itself faces -- a
 * captured schematic carries its world offset but no rotation -- so one bearing for all
 * of them is as close to the map as this can get.</p>
 *
 * <p>Called once the schematic has rendered, which is after the renderer's own framing:
 * that runs on the schematic-added event, so the bearing set here is the one that lasts.</p>
 */
export function faceCameraSouthEast(renderer: Renderer | undefined): void {
  renderer?.cameraManager?.snapToDirection?.(CAMERA_BEARING);
}

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

    void fetchResourcePacks(client).then(async (packs) => {
      // The renderer restores its stored packs ahead of the ones it is handed; they
      // are dropped first whenever the server's list is not the one they came from.
      await forgetRememberedPacksUnless(packs.map((pack) => pack.url));
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
            // Bearing first, limits second: the snap reframes the plot, and the limits
            // are meant to hold against wherever it leaves the camera.
            onSchematicRendered: () => {
              faceCameraSouthEast(rendererRef.current);
              keepCameraOutside(rendererRef.current);
            },
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
