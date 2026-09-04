import { describe, expect, it, vi } from "vitest";
import { render, waitFor } from "@testing-library/react";

const constructorSpy = vi.fn();
const disposeSpy = vi.fn();

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
  },
}));

import { SchematicViewer } from "./SchematicViewer";
import type { ApiClient } from "../api/client";

const client = ({
  GET: vi.fn(async () => ({ data: new ArrayBuffer(8), error: undefined })),
}) as unknown as ApiClient;

describe("SchematicViewer", () => {
  it("constructs the renderer with a canvas and a loader keyed by region", async () => {
    render(<SchematicViewer client={client} world="world" region="plot_a" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalled());

    const [canvas, schematics] = constructorSpy.mock.calls[0];
    expect(canvas).toBeInstanceOf(HTMLCanvasElement);
    expect(Object.keys(schematics as object)).toEqual(["plot_a"]);
    expect(typeof (schematics as Record<string, unknown>)["plot_a"]).toBe("function");
  });

  it("enables interaction and drag-and-drop, which both default to off", async () => {
    // enableInteraction false means the camera cannot move; enableDragAndDrop false
    // means dropping a resource pack does nothing. Neither failure is visible except
    // by trying it, so they are pinned here.
    render(<SchematicViewer client={client} world="world" region="plot_a" />);
    await waitFor(() => expect(constructorSpy).toHaveBeenCalled());

    const options = constructorSpy.mock.calls[0][3] as Record<string, unknown>;
    expect(options.enableInteraction).toBe(true);
    expect(options.enableDragAndDrop).toBe(true);
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
});
