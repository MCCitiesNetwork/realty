import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

vi.mock("../viewer/SchematicViewer", () => ({
  SchematicViewer: () => <div data-testid="viewer" />,
}));

import { AppRoutes } from "../router";
import type { AppConfig } from "../config";
import { emptyPage, freeholdRegion, stats, tags, world } from "../test-support/fixtures";
import { failure, stubClient } from "../test-support/stubClient";

const { client } = stubClient({
  "/v1/stats": stats,
  "/v1/tags": tags,
  "/v1/worlds": [world],
  "/v1/regions/search": emptyPage("results"),
  "/v1/activity": emptyPage("events"),
  "/v1/auctions": emptyPage("auctions"),
  "/v1/region": freeholdRegion,
  "/v1/region/history": emptyPage("entries"),
  "/v1/region/schematic": failure(404, "SCHEMATIC_NOT_FOUND"),
});

const disclaimer = /not an official minecraft product/i;

const config: AppConfig = { apiBaseUrl: "", map: { baseUrl: "", ids: {} }, logoUrl: "", currency: "", visibleWorlds: [] };

describe("SiteFooter", () => {
  it("carries the Mojang disclaimer on the home screen", async () => {
    render(<MemoryRouter initialEntries={["/"]}><AppRoutes client={client} config={config} /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText(disclaimer)).toBeInTheDocument());
  });

  it("carries it on a region screen too", async () => {
    // Every page, not just the landing one: the brand guidelines ask for it wherever the
    // content appears, and a region page is where most visitors actually land.
    render(
      <MemoryRouter initialEntries={["/region/world/plot_a"]}>
        <AppRoutes client={client} config={config} />
      </MemoryRouter>,
    );
    await waitFor(() => expect(screen.getByText(disclaimer)).toBeInTheDocument());
  });

  it("carries it on a page that does not exist", async () => {
    render(<MemoryRouter initialEntries={["/nowhere"]}><AppRoutes client={client} config={config} /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText(disclaimer)).toBeInTheDocument());
  });

  it("names Mojang and Microsoft, and disclaims both approval and association", async () => {
    // The wording is fixed by the guidelines rather than by taste, so it is asserted
    // rather than left to whoever next edits the footer's styling.
    render(<MemoryRouter><AppRoutes client={client} config={config} /></MemoryRouter>);
    const text = (await screen.findByText(disclaimer)).textContent ?? "";
    expect(text).toMatch(/not approved by or associated with/i);
    expect(text).toMatch(/mojang/i);
    expect(text).toMatch(/microsoft/i);
  });
});
