import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { BrowseScreen } from "./BrowseScreen";
import type { ApiClient } from "../../api/client";

const clientReturning = (body: unknown) =>
  ({ GET: vi.fn(async () => ({ data: body, error: undefined })) }) as unknown as ApiClient;

const renderScreen = (client: ApiClient) =>
  render(
    <MemoryRouter>
      <BrowseScreen client={client} />
    </MemoryRouter>,
  );

const result = {
  worldGuardRegionId: "plot_a",
  world: { id: "8f4d1c2e-0000-0000-0000-000000000099", name: "world" },
  contractType: "freehold",
  price: 1500,
  state: "FOR_SALE",
};

describe("BrowseScreen", () => {
  it("lists the regions it is given", async () => {
    const client = clientReturning({ results: [result], totalCount: 1, page: 1, pageSize: 25, totalPages: 1 });
    renderScreen(client);
    await waitFor(() => expect(screen.getByText("plot_a")).toBeInTheDocument());
  });

  it("links each region to its detail route", async () => {
    const client = clientReturning({ results: [result], totalCount: 1, page: 1, pageSize: 25, totalPages: 1 });
    renderScreen(client);
    await waitFor(() =>
      expect(screen.getByRole("link", { name: "plot_a" })).toHaveAttribute(
        "href",
        "/region/world/plot_a",
      ));
  });

  it("shows an empty state rather than a blank page when nothing matches", async () => {
    const client = clientReturning({ results: [], totalCount: 0, page: 1, pageSize: 25, totalPages: 0 });
    renderScreen(client);
    await waitFor(() => expect(screen.getByText(/no regions match/i)).toBeInTheDocument());
  });

  it("surfaces an error when the request fails", async () => {
    const client = ({
      GET: vi.fn(async () => ({ data: undefined, error: { error: "boom" } })),
    }) as unknown as ApiClient;
    renderScreen(client);
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
  });

  it("asks for the first page at the configured page size", async () => {
    type SearchOptions = { params: { query: Record<string, unknown> } };
    const get = vi.fn(async (_path: string, _options: SearchOptions) => ({
      data: { results: [], totalCount: 0, page: 1, pageSize: 24, totalPages: 0 },
      error: undefined,
    }));
    renderScreen(({ GET: get }) as unknown as ApiClient);

    await waitFor(() => expect(get).toHaveBeenCalled());
    expect(get.mock.calls[0]?.[1].params.query).toMatchObject({ page: 1, pageSize: 24 });
  });
});
