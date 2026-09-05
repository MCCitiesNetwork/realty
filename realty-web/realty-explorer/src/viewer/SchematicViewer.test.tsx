import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";

const constructorSpy = vi.fn();
const disposeSpy = vi.fn();
const addPackSpy = vi.fn(async (_file: File) => undefined);

// WebGL and WASM do not run under jsdom. The worthwhile assertion is that the
// renderer is constructed correctly and torn down, not that Three.js draws.
// The instance the component built, so a test can give it the managers a real renderer
// would have grown by the time it reports a schematic rendered.
let lastRenderer: Record<string, unknown>;

vi.mock("schematic-renderer", () => ({
  SchematicRenderer: class {
    constructor(...args: unknown[]) {
      constructorSpy(...args);
      lastRenderer = this as unknown as Record<string, unknown>;
    }

    dispose() {
      disposeSpy();
    }

    addResourcePack(file: File) {
      return addPackSpy(file);
    }
  },
}));

import { SchematicViewer, faceCameraSouthEast, keepCameraOutside } from "./SchematicViewer";
import type { ApiClient } from "../api/client";

const client = ({
  GET: vi.fn(async () => ({ data: new ArrayBuffer(8), error: undefined })),
}) as unknown as ApiClient;

describe("SchematicViewer", () => {
  // Module-scoped spies persist across tests, so a later assertion would otherwise
  // see an earlier test's calls.
  beforeEach(() => {
    constructorSpy.mockClear();
    disposeSpy.mockClear();
    addPackSpy.mockClear();
  });

  it("constructs the renderer with a canvas and a loader keyed by region", async () => {
    render(<SchematicViewer client={client} world="world" region="plot_a" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalled());

    const [canvas, schematics] = constructorSpy.mock.calls[0];
    expect(canvas).toBeInstanceOf(HTMLCanvasElement);
    expect(Object.keys(schematics as object)).toEqual(["plot_a"]);
    expect(typeof (schematics as Record<string, unknown>)["plot_a"]).toBe("function");
  });

  it("uses bytes it is handed rather than fetching the schematic again", async () => {
    // The detail screen downloads the schematic to learn whether one exists. Fetching it
    // a second time here halved the speed at which the preview could appear.
    const bytes = new ArrayBuffer(16);
    const get = vi.fn(async (_path: string) => ({ data: new ArrayBuffer(8), error: undefined }));

    render(<SchematicViewer client={({ GET: get }) as unknown as ApiClient}
                            world="world" region="plot_a" schematic={bytes} />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalled());

    const loaders = constructorSpy.mock.calls[0][1] as Record<string, () => Promise<ArrayBuffer>>;
    await expect(loaders["plot_a"]()).resolves.toBe(bytes);
    expect(get.mock.calls.filter((call) => call[0] === "/v1/region/schematic")).toHaveLength(0);
  });

  it("enables interaction, which defaults to off and leaves the camera fixed", async () => {
    render(<SchematicViewer client={client} world="world" region="plot_a" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalled());
    expect((constructorSpy.mock.calls[0][3] as Record<string, unknown>).enableInteraction)
      .toBe(true);
  });

  it("leaves the library's own drag-and-drop off, because it accepts schematics", async () => {
    // The library branches on the dropped file's type and will load a schematic,
    // which would let a visitor replace this region's preview with their own file.
    render(<SchematicViewer client={client} world="world" region="plot_a" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalled());
    expect((constructorSpy.mock.calls[0][3] as Record<string, unknown>).enableDragAndDrop)
      .toBe(false);
  });

  it("passes no resource packs, so geometry renders untextured", async () => {
    render(<SchematicViewer client={client} world="world" region="plot_a" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalled());
    expect(constructorSpy.mock.calls[0][2]).toEqual({});
  });

  it("keeps the camera outside the plot once it has rendered", async () => {
    // Orbit and zoom, but never through a wall: the nearest approach is the radius of
    // the sphere around the build, and panning -- which moves the orbited point, and
    // with it the camera -- is off.
    render(<SchematicViewer client={client} world="world" region="plot_a" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalled());
    const options = constructorSpy.mock.calls[0][3] as { callbacks?: { onSchematicRendered?: () => void } };
    expect(typeof options.callbacks?.onSchematicRendered).toBe("function");

    const orbit = { minDistance: 1, maxDistance: 1000, enablePan: true, target: { set: vi.fn() }, update: vi.fn() };
    keepCameraOutside({
      schematicManager: { getGlobalTightWorldBox: () => ({ min: { x: 0, y: 0, z: 0 }, max: { x: 2, y: 3, z: 6 } }) },
      cameraManager: { controls: new Map([["orbit", orbit]]) },
    });

    // The box's diagonal is 7, so its bounding sphere has radius 3.5: nothing that far
    // from the centre can be inside the box.
    expect(orbit.minDistance).toBeCloseTo(3.675, 5);
    expect(orbit.maxDistance).toBeCloseTo(28, 5);
    expect(orbit.enablePan).toBe(false);
    expect(orbit.target.set).toHaveBeenCalledWith(1, 1.5, 3);
    expect(orbit.update).toHaveBeenCalled();
  });

  it("opens every plot from one compass bearing, rather than one the plot decides", async () => {
    // The renderer's own framing derives an angle from the plot's bounding box, so two
    // neighbouring plots open facing differently and neither matches the north-up map.
    // The vector is a compass bearing -- x east, y up, z south -- so this is the camera
    // above the plot's south-east corner, looking north-west.
    const snapToDirection = vi.fn();
    faceCameraSouthEast({ cameraManager: { snapToDirection } });

    expect(snapToDirection).toHaveBeenCalledWith([1, 0.7, 1]);
  });

  it("turns the camera before constraining it, so the limits hold where the snap lands", async () => {
    render(<SchematicViewer client={client} world="world" region="plot_a" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalled());
    const options = constructorSpy.mock.calls[0][3] as { callbacks?: { onSchematicRendered?: () => void } };

    const order: string[] = [];
    const orbit = {
      minDistance: 1,
      maxDistance: 1000,
      enablePan: true,
      target: { set: vi.fn() },
      update: () => order.push("limits"),
    };
    lastRenderer.schematicManager = {
      getGlobalTightWorldBox: () => ({ min: { x: 0, y: 0, z: 0 }, max: { x: 2, y: 3, z: 6 } }),
    };
    lastRenderer.cameraManager = {
      controls: new Map([["orbit", orbit]]),
      snapToDirection: () => order.push("snap"),
    };

    options.callbacks?.onSchematicRendered?.();

    expect(order).toEqual(["snap", "limits"]);
  });

  it("leaves the camera alone when the renderer has no bearing to set", () => {
    // A renderer whose managers never arrived: no bearing is better than a throw that
    // costs the visitor the preview.
    expect(() => faceCameraSouthEast(undefined)).not.toThrow();
    expect(() => faceCameraSouthEast({ cameraManager: {} })).not.toThrow();
  });

  it("leaves the camera alone when the renderer has no bounds to give", () => {
    // A schematic that failed to load, or a renderer without the managers: no
    // constraint is better than a wrong one.
    expect(() => keepCameraOutside(undefined)).not.toThrow();
    expect(() => keepCameraOutside({ cameraManager: { controls: new Map() } })).not.toThrow();
  });

  /** jsdom offers no localStorage here; a Map stands in for the fingerprint note. */
  const fakeStorage = () => {
    const store = new Map<string, string>();
    vi.stubGlobal("localStorage", {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => void store.set(key, value),
      removeItem: (key: string) => void store.delete(key),
    });
  };

  it("forgets the packs the renderer remembered when the server's pack list has changed", async () => {
    // The renderer restores packs from IndexedDB ahead of the one it is handed, and drops
    // a handed pack whose bytes it has seen. A browser that had ever loaded a pack was
    // therefore stuck with it whenever the server named a new one.
    const deleted: string[] = [];
    vi.stubGlobal("indexedDB", {
      deleteDatabase: (name: string) => {
        deleted.push(name);
        const request = {} as { onsuccess?: () => void };
        queueMicrotask(() => request.onsuccess?.());
        return request;
      },
    });
    fakeStorage();

    render(<SchematicViewer client={client} world="world" region="plot_a" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalled());

    expect(deleted).toEqual(["ResourcePacksDB", "cubane-resource-packs", "cubane-cache"]);
    vi.unstubAllGlobals();
  });

  it("keeps the remembered packs while the server's list is unchanged", async () => {
    // A wipe on every visit threw away a parsed 30 MB pack each time. The list is the
    // fingerprint: unchanged, what the stores hold is what would be loaded again.
    const deleted: string[] = [];
    vi.stubGlobal("indexedDB", {
      deleteDatabase: (name: string) => {
        deleted.push(name);
        const request = {} as { onsuccess?: () => void };
        queueMicrotask(() => request.onsuccess?.());
        return request;
      },
    });
    fakeStorage();

    const first = render(<SchematicViewer client={client} world="world" region="plot_a" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalledTimes(1));
    first.unmount();
    expect(deleted).toHaveLength(3);

    render(<SchematicViewer client={client} world="world" region="plot_b" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalledTimes(2));
    expect(deleted).toHaveLength(3);
    vi.unstubAllGlobals();
  });

  it("disposes the renderer on unmount so WebGL contexts are not leaked", async () => {
    const view = render(<SchematicViewer client={client} world="world" region="plot_a" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalled());

    view.unmount();

    expect(disposeSpy).toHaveBeenCalled();
  });

  it("loads a dropped resource pack", async () => {
    const view = render(<SchematicViewer client={client} world="world" region="plot_a" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalled());

    const pack = new File(["zip"], "pack.zip", { type: "application/zip" });
    fireEvent.drop(view.container.querySelector(".viewer-drop")!, {
      dataTransfer: { files: [pack] },
    });

    await waitFor(() => expect(addPackSpy).toHaveBeenCalledWith(pack));
    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent(/pack\.zip/));
  });

  it("refuses a dropped schematic rather than replacing the region's preview", async () => {
    // This canvas shows one region's capture. Loading a visitor's own file would make
    // it show something that is not the region.
    const view = render(<SchematicViewer client={client} world="world" region="plot_a" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalled());

    fireEvent.drop(view.container.querySelector(".viewer-drop")!, {
      dataTransfer: { files: [new File(["nbt"], "castle.schem")] },
    });

    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent(/only resource packs/i));
    expect(addPackSpy).not.toHaveBeenCalled();
  });
});
