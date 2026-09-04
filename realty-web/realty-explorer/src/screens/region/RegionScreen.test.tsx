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

  it("renders nothing extra when no credit is configured", async () => {
    renderScreen(clientReturning(region), true);
    await waitFor(() => expect(screen.getByTestId("viewer")).toBeInTheDocument());
    expect(document.querySelector(".viewer-credit")).toBeNull();
  });
});
