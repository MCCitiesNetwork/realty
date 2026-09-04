import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

vi.mock("../viewer/SchematicViewer", () => ({
  SchematicViewer: () => <div data-testid="viewer" />,
}));

import { AppRoutes } from "../router";
import type { ApiClient } from "../api/client";

const region = {
  worldGuardRegionId: "plot_a",
  world: { id: "8f4d1c2e-0000-0000-0000-000000000099", name: "world" },
  state: "FOR_SALE",
  tags: [],
};

const client = {
  GET: vi.fn(async (path: string) => ({
    data: path === "/v1/region" ? region : { results: [], totalCount: 0, attribution: [] },
    error: undefined,
    response: { status: 200 },
  })),
} as unknown as ApiClient;

const disclaimer = /not an official minecraft product/i;

describe("SiteFooter", () => {
  it("carries the Mojang disclaimer on the browse screen", async () => {
    render(<MemoryRouter initialEntries={["/"]}><AppRoutes client={client} /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText(disclaimer)).toBeInTheDocument());
  });

  it("carries it on a region screen too", async () => {
    // Every page, not just the landing one: the brand guidelines ask for it wherever the
    // content appears, and a region page is where most visitors actually land.
    render(
      <MemoryRouter initialEntries={["/region/world/plot_a"]}>
        <AppRoutes client={client} />
      </MemoryRouter>,
    );
    await waitFor(() => expect(screen.getByText(disclaimer)).toBeInTheDocument());
  });

  it("names Mojang and Microsoft, and disclaims both approval and association", async () => {
    // The wording is fixed by the guidelines rather than by taste, so it is asserted
    // rather than left to whoever next edits the footer's styling.
    render(<MemoryRouter><AppRoutes client={client} /></MemoryRouter>);
    const text = (await screen.findByText(disclaimer)).textContent ?? "";
    expect(text).toMatch(/not approved by or associated with/i);
    expect(text).toMatch(/mojang/i);
    expect(text).toMatch(/microsoft/i);
  });
});
