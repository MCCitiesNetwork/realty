import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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

  it("offers the worlds the database knows, and invents none", async () => {
    // A world name is a folder name on disk, so a typed filter mostly returned nothing.
    const client = ({
      GET: vi.fn(async (path: string) => ({
        data: path === "/v1/worlds"
          ? [{ id: "8f4d1c2e-0000-0000-0000-000000000099", name: "world" },
             { id: "8f4d1c2e-0000-0000-0000-000000000100", name: "My World" }]
          : { results: [], totalCount: 0, page: 1, pageSize: 24, totalPages: 0 },
        error: undefined,
      })),
    }) as unknown as ApiClient;
    renderScreen(client);

    const select = await screen.findByLabelText("World");
    await waitFor(() =>
      expect(screen.getByRole("option", { name: "My World" })).toBeInTheDocument());
    // Any world, plus exactly the two the API reported. Nothing is made up.
    expect(select.querySelectorAll("option")).toHaveLength(3);
  });

  it("falls back to Any world alone when no world is registered", async () => {
    const client = ({
      GET: vi.fn(async (path: string) => ({
        data: path === "/v1/worlds"
          ? []
          : { results: [], totalCount: 0, page: 1, pageSize: 24, totalPages: 0 },
        error: undefined,
      })),
    }) as unknown as ApiClient;
    renderScreen(client);

    const select = await screen.findByLabelText("World");
    expect(select.querySelectorAll("option")).toHaveLength(1);
  });

  it("sends the chosen world to the search", async () => {
    type SearchOptions = { params: { query: Record<string, unknown> } };
    const get = vi.fn(async (path: string, _options?: SearchOptions) => ({
      data: path === "/v1/worlds"
        ? [{ id: "8f4d1c2e-0000-0000-0000-000000000099", name: "world" }]
        : { results: [], totalCount: 0, page: 1, pageSize: 24, totalPages: 0 },
      error: undefined,
    }));
    renderScreen(({ GET: get }) as unknown as ApiClient);

    await waitFor(() => expect(screen.getByRole("option", { name: "world" })).toBeInTheDocument());
    fireEvent.change(screen.getByLabelText("World"), { target: { value: "world" } });

    await waitFor(() => {
      const searches = get.mock.calls.filter((call) => call[0] === "/v1/regions/search");
      expect(searches.at(-1)?.[1]?.params.query).toMatchObject({ world: "world" });
    });
  });

  it("offers every contract type the search accepts", async () => {
    // The filter is generated from the API's own enum, so an option added there cannot
    // go missing here -- and one offered here cannot be rejected there.
    const client = clientReturning({ results: [], totalCount: 0, page: 1, pageSize: 24, totalPages: 0 });
    renderScreen(client);

    const select = await screen.findByLabelText("Type");
    expect([...select.querySelectorAll("option")].map((option) => option.getAttribute("value")))
      .toEqual(["all", "sale", "rent", "freehold", "leasehold"]);
  });

  it("sends the chosen contract type, and omits it for all", async () => {
    type SearchOptions = { params: { query: Record<string, unknown> } };
    const get = vi.fn(async (_path: string, _options?: SearchOptions) => ({
      data: { results: [], totalCount: 0, page: 1, pageSize: 24, totalPages: 0 },
      error: undefined,
    }));
    renderScreen(({ GET: get }) as unknown as ApiClient);

    // The screen also fetches the world list, so the search is picked out by path
    // rather than by call order.
    const searchQuery = () => get.mock.calls
      .filter((call) => call[0] === "/v1/regions/search").at(-1)?.[1]?.params.query;

    await waitFor(() => expect(searchQuery()).toBeDefined());
    // "all" is the API's own default, so sending it would only restate it.
    expect(searchQuery()).not.toHaveProperty("type");

    fireEvent.change(await screen.findByLabelText("Type"), { target: { value: "freehold" } });
    await waitFor(() => expect(searchQuery()).toMatchObject({ type: "freehold" }));
  });

  it("asks for the first page at the configured page size", async () => {
    type SearchOptions = { params: { query: Record<string, unknown> } };
    const get = vi.fn(async (_path: string, _options: SearchOptions) => ({
      data: { results: [], totalCount: 0, page: 1, pageSize: 24, totalPages: 0 },
      error: undefined,
    }));
    renderScreen(({ GET: get }) as unknown as ApiClient);

    await waitFor(() => expect(get).toHaveBeenCalled());
    const search = get.mock.calls.find((call) => call[0] === "/v1/regions/search");
    expect(search?.[1].params.query).toMatchObject({ page: 1, pageSize: 24 });
  });
});
