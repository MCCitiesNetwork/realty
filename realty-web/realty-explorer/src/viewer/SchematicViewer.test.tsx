import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";

const constructorSpy = vi.fn();
const disposeSpy = vi.fn();
const addPackSpy = vi.fn(async (_file: File) => undefined);

// WebGL and WASM do not run under jsdom. The worthwhile assertion is that the
// renderer is constructed correctly and torn down, not that Three.js draws.
vi.mock("schematic-renderer", () => ({
  SchematicRenderer: class {
    constructor(...args: unknown[]) {
      constructorSpy(...args);
    }

    dispose() {
      disposeSpy();
    }

    addResourcePack(file: File) {
      return addPackSpy(file);
    }
  },
}));

import { SchematicViewer } from "./SchematicViewer";
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

  it("disposes the renderer on unmount so WebGL contexts are not leaked", async () => {
    const view = render(<SchematicViewer client={client} world="world" region="plot_a" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalled());

    view.unmount();

    expect(disposeSpy).toHaveBeenCalled();
  });

  it("loads a dropped resource pack", async () => {
    const view = render(<SchematicViewer client={client} world="world" region="plot_a" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalled());

    const pack = new File(["zip"], "faithful.zip", { type: "application/zip" });
    fireEvent.drop(view.container.querySelector(".viewer-drop")!, {
      dataTransfer: { files: [pack] },
    });

    await waitFor(() => expect(addPackSpy).toHaveBeenCalledWith(pack));
    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent(/faithful\.zip/));
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
