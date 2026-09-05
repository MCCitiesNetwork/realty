import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

const drawn = vi.fn();

// Leaflet measures a container jsdom gives no size, so the map itself draws nothing
// here. What this screen is responsible for is which world it asks about, what it
// hands the map, and what it says when part of the answer is missing.
vi.mock("../../map/RegionMap", () => ({
  RegionMap: (props: Record<string, unknown>) => {
    drawn(props);
    return null;
  },
}));

import { MapScreen } from "./MapScreen";
import { stubClient, queriesTo, failure, type Query } from "../../test-support/stubClient";
import { world } from "../../test-support/fixtures";

const NO_MAP = { baseUrl: "", ids: {} };

const cuboid = (id: string) => ({
  worldGuardRegionId: id,
  dimensions: { shape: "CUBOID", minY: 0, maxY: 255, points: [{ x: 0, z: 0 }, { x: 9, z: 9 }] },
});

const routes = (regions: unknown[] = [cuboid("plot_a")], results: unknown[] = []) => ({
  "/v1/worlds": [world],
  "/v1/worlds/geometry": {
    page: 1, pageSize: 100, totalCount: regions.length, totalPages: 1, world, regions,
  },
  "/v1/regions/search": { page: 1, pageSize: 100, totalCount: results.length, totalPages: 1, results },
});

const show = (map: { baseUrl: string; ids: Record<string, string> }, stub: ReturnType<typeof stubClient>,
              at = "/map") =>
  render(<MemoryRouter initialEntries={[at]}><MapScreen client={stub.client} map={map} /></MemoryRouter>);

describe("MapScreen", () => {
  // The spy is module-scoped, so without this a test reads the previous test's map.
  beforeEach(() => drawn.mockClear());

  it("opens on the first world it knows, so the screen is a map rather than a prompt", async () => {
    const stub = stubClient(routes());
    show(NO_MAP, stub);

    await waitFor(() => expect(queriesTo(stub.get, "/v1/worlds/geometry")).toHaveLength(1));
    expect(queriesTo(stub.get, "/v1/worlds/geometry")[0].world).toBe("world");
  });

  it("takes the world from the URL, so a link to a district is one that can be sent", async () => {
    const stub = stubClient({ ...routes(), "/v1/worlds": [world, { id: "x", name: "My World" }] });
    show(NO_MAP, stub, "/map?world=My%20World");

    await waitFor(() => expect(queriesTo(stub.get, "/v1/worlds/geometry")).toHaveLength(1));
    expect(queriesTo(stub.get, "/v1/worlds/geometry")[0].world).toBe("My World");
  });

  it("hands the map every plot it found", async () => {
    const stub = stubClient(routes([cuboid("plot_a"), cuboid("plot_b")]));
    show(NO_MAP, stub);

    await waitFor(() => {
      const props = drawn.mock.calls.at(-1)![0] as { footprints: Array<{ regionId: string }> };
      expect(props.footprints.map((footprint) => footprint.regionId)).toEqual(["plot_a", "plot_b"]);
    });
  });

  it("names the BlueMap map for the world being shown", async () => {
    const stub = stubClient(routes());
    show({ baseUrl: "https://map.example.com", ids: {} }, stub);

    await waitFor(() => expect(drawn).toHaveBeenCalled());
    const props = drawn.mock.calls.at(-1)![0] as { tiles: { baseUrl: string; mapId: string } | null };
    expect(props.tiles).toEqual({ baseUrl: "https://map.example.com", mapId: "world" });
  });

  it("draws the plots on nothing when no server map is configured", async () => {
    const stub = stubClient(routes());
    show(NO_MAP, stub);

    await waitFor(() => expect(drawn).toHaveBeenCalled());
    expect((drawn.mock.calls.at(-1)![0] as { tiles: unknown }).tiles).toBeNull();
  });

  it("keeps how the register was read off the page", async () => {
    // A missing server map, a plot the game server could not place, a progress count:
    // all the operator's business, and none of it a visitor's.
    const stub = stubClient(routes([cuboid("plot_a"), { worldGuardRegionId: "b", dimensions: null }]));
    show(NO_MAP, stub);

    await waitFor(() => expect(drawn).toHaveBeenCalled());
    expect(screen.queryByText(/could not be placed/)).toBeNull();
    expect(screen.queryByText(/No server map/)).toBeNull();
    expect(screen.queryByText(/plots? drawn/)).toBeNull();
    expect(screen.queryByText(/where it stands/)).toBeNull();
  });

  it("says so when the world's plots cannot be read at all", async () => {
    const stub = stubClient({ ...routes(), "/v1/worlds/geometry": failure(404, "WORLD_NOT_FOUND") });
    show(NO_MAP, stub);

    expect(await screen.findByText("This world's plots could not be read")).toBeInTheDocument();
  });

  it("hides a kind of plot when its key is pressed, and shows it again on the next press", async () => {
    const stub = stubClient(routes());
    show(NO_MAP, stub);
    await waitFor(() => expect(drawn).toHaveBeenCalled());
    const hiddenNow = () => [...(drawn.mock.calls.at(-1)![0] as { hidden: Set<string> }).hidden];

    // What is taken starts off; what a visitor could have starts on.
    expect(hiddenNow()).toEqual(["Leased", "Sold"]);
    expect(screen.getByRole("button", { name: "Sold" })).toHaveAttribute("aria-pressed", "false");

    const key = screen.getByRole("button", { name: "For sale" });
    fireEvent.click(key);
    expect(hiddenNow()).toEqual(["Leased", "Sold", "For sale"]);
    expect(key).toHaveAttribute("aria-pressed", "false");

    fireEvent.click(screen.getByRole("button", { name: "Leased" }));
    expect(hiddenNow()).toEqual(["Sold", "For sale"]);

    fireEvent.click(key);
    expect(hiddenNow()).toEqual(["Sold"]);
    expect(key).toHaveAttribute("aria-pressed", "true");
  });

  it("shows a hairline of progress while the world is read, and lets it go once it is", async () => {
    const stub = stubClient(routes());
    show(NO_MAP, stub);

    // The world is read in the background; the bar is there from the first render.
    expect(screen.getByRole("progressbar", { name: /reading/i })).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("progressbar")).toBeNull(), { timeout: 2000 });
  });

  it("finds a plot by name and hands it to the map to frame, switching its kind on", async () => {
    const stub = stubClient(routes([cuboid("plot_a"), cuboid("plot_b"), cuboid("other")], [
      { worldGuardRegionId: "plot_b", world, contractType: "freehold", price: null, state: "SOLD", durationSeconds: null },
    ]));
    show(NO_MAP, stub);
    await waitFor(() => expect((drawn.mock.calls.at(-1)![0] as { footprints: unknown[] }).footprints).toHaveLength(3));

    const box = screen.getByRole("combobox", { name: "Find a plot" });
    fireEvent.change(box, { target: { value: "plot" } });
    await waitFor(() => expect(document.querySelectorAll(".ant-select-item-option")).toHaveLength(2));
    fireEvent.click([...document.querySelectorAll(".ant-select-item-option")].find((o) => o.textContent === "plot_b")!);

    await waitFor(() => {
      const props = drawn.mock.calls.at(-1)![0] as { focus: { regionId: string } | null; hidden: Set<string> };
      expect(props.focus?.regionId).toBe("plot_b");
      // Sold plots start switched off; the one asked for is switched back on.
      expect(props.hidden.has("Sold")).toBe(false);
    });
  });

  it("shows a legend, since a colour on its own says nothing", async () => {
    const stub = stubClient(routes());
    show(NO_MAP, stub);

    await waitFor(() => expect(drawn).toHaveBeenCalled());
    for (const label of ["For sale", "For rent", "Leased", "Sold"]) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
  });

  it("asks about one world only, since two worlds share one set of coordinates", async () => {
    const stub = stubClient(routes());
    show(NO_MAP, stub);

    await waitFor(() => expect(drawn).toHaveBeenCalled());
    const asked = queriesTo(stub.get, "/v1/worlds/geometry").map((query: Query) => query.world);
    expect(new Set(asked).size).toBe(1);
  });
});
