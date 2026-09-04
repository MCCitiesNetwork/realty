import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

vi.mock("../../viewer/SchematicViewer", () => ({
  SchematicViewer: () => <div data-testid="viewer" />,
}));

import { RegionScreen } from "./RegionScreen";
import type { ApiClient } from "../../api/client";

const region = {
  worldGuardRegionId: "plot_a",
  world: { id: "8f4d1c2e-0000-0000-0000-000000000099", name: "world" },
  state: "FOR_SALE",
  freehold: { price: 1500 },
  tags: ["shop"],
};

const clientReturning = (body: unknown, status = 200) =>
  ({
    GET: vi.fn(async () => ({
      data: status === 200 ? body : undefined,
      error: status === 200 ? undefined : { error: "NOT_FOUND" },
      response: { status },
    })),
  }) as unknown as ApiClient;

const renderScreen = (client: ApiClient, hasSchematic = false) =>
  render(
    <MemoryRouter>
      <RegionScreen client={client} world="world" region="plot_a" hasSchematic={hasSchematic} />
    </MemoryRouter>,
  );

describe("RegionScreen", () => {
  it("renders the region's details", async () => {
    renderScreen(clientReturning(region));
    await waitFor(() => expect(screen.getByRole("heading", { name: "plot_a" })).toBeInTheDocument());
    // The badge humanises the enum: FOR_SALE reads "FOR SALE".
    expect(screen.getByText("FOR SALE")).toHaveClass("badge-for_sale");
    expect(screen.getByText("shop")).toBeInTheDocument();
  });

  it("shows a plain panel, not an error, when no schematic was captured", async () => {
    // Capture is on demand, so most regions have none. This is expected traffic.
    renderScreen(clientReturning(region));
    await waitFor(() => expect(screen.getByText(/no preview captured/i)).toBeInTheDocument());
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.queryByTestId("viewer")).toBeNull();
  });

  it("renders the viewer when a schematic exists", async () => {
    renderScreen(clientReturning(region), true);
    await waitFor(() => expect(screen.getByTestId("viewer")).toBeInTheDocument());
  });

  it("probes for a preview when not told, and shows the panel when there is none", async () => {
    // The router passes no hasSchematic: whether a capture exists is the API's to
    // answer. Hardcoding it mounted the viewer for every region, downloading ~12 MB
    // and failing to initialise on the many that have none.
    const client = ({
      GET: vi.fn(async (path: string) =>
        path === "/v1/region/schematic"
          ? { data: undefined, error: { error: "SCHEMATIC_NOT_FOUND" }, response: { status: 404 } }
          : { data: region, error: undefined, response: { status: 200 } }),
    }) as unknown as ApiClient;

    render(
      <MemoryRouter>
        <RegionScreen client={client} world="world" region="plot_a" />
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByText(/no preview captured/i)).toBeInTheDocument());
    expect(screen.queryByTestId("viewer")).toBeNull();
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("reports an unknown region as missing rather than as a failure", async () => {
    renderScreen(clientReturning(null, 404));
    await waitFor(() => expect(screen.getByText(/no region named/i)).toBeInTheDocument());
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("credits the resource pack only where a schematic actually renders", async () => {
    const credit = [{ text: "Example Pack 32x", href: "https://packs.example.com/" }];

    const withPreview = render(
      <MemoryRouter>
        <RegionScreen client={clientReturning(region)} world="world" region="plot_a"
                      hasSchematic resourcePackAttribution={credit} />
      </MemoryRouter>,
    );
    await waitFor(() =>
      expect(withPreview.getByRole("link", { name: "Example Pack 32x" }))
        .toHaveAttribute("href", "https://packs.example.com/"));
    withPreview.unmount();

    // No schematic means no pack was used, so there is nothing to credit.
    const withoutPreview = render(
      <MemoryRouter>
        <RegionScreen client={clientReturning(region)} world="world" region="plot_a"
                      hasSchematic={false} resourcePackAttribution={credit} />
      </MemoryRouter>,
    );
    await waitFor(() => expect(withoutPreview.getByText(/no preview captured/i)).toBeInTheDocument());
    expect(withoutPreview.queryByText("Example Pack 32x")).toBeNull();
  });

  it("takes the credit from the API, since the pack is the game server's setting", async () => {
    // The operator picks the pack in query-service's config.yml and states its credit
    // in the same file. Nothing about it is configured on the web host.
    const client = {
      GET: vi.fn(async (path: string) => ({
        data: path === "/v1/resource-pack"
          ? { url: null, attribution: [{ text: "Example Pack 32x", url: "https://packs.example.com/" }] }
          : region,
        error: undefined,
        response: { status: 200 },
      })),
    } as unknown as ApiClient;

    render(
      <MemoryRouter>
        <RegionScreen client={client} world="world" region="plot_a" hasSchematic />
      </MemoryRouter>,
    );

    await waitFor(() =>
      expect(screen.getByRole("link", { name: "Example Pack 32x" }))
        .toHaveAttribute("href", "https://packs.example.com/"));
  });

  it("shows the region's own page while the details are still loading", async () => {
    // Following a card used to blank the page until /v1/region answered, which read as a
    // slow navigation even though the name and the way back were known from the URL.
    const client = ({
      GET: vi.fn(() => new Promise(() => {})),
    }) as unknown as ApiClient;

    render(
      <MemoryRouter>
        <RegionScreen client={client} world="world" region="plot_a" hasSchematic={false} />
      </MemoryRouter>,
    );

    expect(screen.getByRole("heading", { name: "plot_a" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /all regions/i })).toBeInTheDocument();
  });

  it("hands the probed bytes to the viewer instead of fetching them twice", async () => {
    // The probe downloads the schematic to learn whether there is one. Discarding it
    // meant the viewer fetched the same megabytes again before drawing anything.
    const bytes = new ArrayBuffer(8);
    const get = vi.fn(async (path: string) => ({
      data: path === "/v1/region/schematic" ? bytes : region,
      error: undefined,
      response: { status: 200 },
    }));

    render(
      <MemoryRouter>
        <RegionScreen client={({ GET: get }) as unknown as ApiClient} world="world" region="plot_a" />
      </MemoryRouter>,
    );

    await waitFor(() => expect(screen.getByTestId("viewer")).toBeInTheDocument());
    expect(get.mock.calls.filter((call) => call[0] === "/v1/region/schematic")).toHaveLength(1);
  });

  it("renders nothing extra when no credit is configured", async () => {
    renderScreen(clientReturning(region), true);
    await waitFor(() => expect(screen.getByTestId("viewer")).toBeInTheDocument());
    expect(document.querySelector(".viewer-credit")).toBeNull();
  });
});
