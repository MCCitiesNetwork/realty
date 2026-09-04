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
    expect(screen.getByText("FOR_SALE")).toBeInTheDocument();
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

  it("reports an unknown region as missing rather than as a failure", async () => {
    renderScreen(clientReturning(null, 404));
    await waitFor(() => expect(screen.getByText(/no region named/i)).toBeInTheDocument());
    expect(screen.queryByRole("alert")).toBeNull();
  });
});
