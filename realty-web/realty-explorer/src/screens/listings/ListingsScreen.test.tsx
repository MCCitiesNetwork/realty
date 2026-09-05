import { describe, expect, it } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { ListingsScreen, PAGE_SIZE } from "./ListingsScreen";
import { emptyPage, listing, otherWorld, pageOf, tags, world } from "../../test-support/fixtures";
import { failure, queriesTo, stubClient, type Routes as Stubs } from "../../test-support/stubClient";
import { VisibilityProvider, visibilityOf } from "../../visibility";

const listingRoutes = (overrides: Stubs = {}): Stubs => ({
  "/v1/worlds": [world, otherWorld],
  "/v1/tags": tags,
  "/v1/regions/search": pageOf("results", [listing]),
  ...overrides,
});

function Probe() {
  const location = useLocation();
  return <div data-testid="location">{location.search}</div>;
}

const renderAt = (url: string, stubs: Stubs = listingRoutes(), visibleWorlds: string[] = []) => {
  const { client, get } = stubClient(stubs);
  render(
    <VisibilityProvider value={visibilityOf(visibleWorlds)}>
      <MemoryRouter initialEntries={[url]}>
        <Routes>
          <Route path="/listings" element={<><ListingsScreen client={client} /><Probe /></>} />
        </Routes>
      </MemoryRouter>
    </VisibilityProvider>,
  );
  return get;
};

const lastSearch = (get: ReturnType<typeof stubClient>["get"]) => queriesTo(get, "/v1/regions/search").at(-1);

describe("ListingsScreen", () => {
  it("lists the regions it is given", async () => {
    renderAt("/listings");
    await waitFor(() => expect(screen.getByText("plot_a")).toBeInTheDocument());
    expect(screen.getByText("1 region matches")).toBeInTheDocument();
  });

  it("makes the whole card the link to the region", async () => {
    renderAt("/listings");
    const link = await screen.findByRole("link", { name: /plot_a/ });
    expect(link).toHaveAttribute("href", "/region/world/plot_a");
    // The price is inside the link, so a click on it opens the region too.
    expect(link).toHaveTextContent("1.5k");
  });

  it("calls a freehold with an asking price for sale, whoever holds the title", async () => {
    // The register says SOLD for any freehold with a title holder. A holder who has set
    // a price is selling, and "Sold" on a listing would turn a buyer away.
    renderAt("/listings", listingRoutes({
      "/v1/regions/search": pageOf("results", [{ ...listing, state: "SOLD", price: 2500 }]),
    }));
    await screen.findByText("plot_a");
    expect(screen.getByText("For sale")).toBeInTheDocument();
    expect(screen.queryByText("Sold")).toBeNull();
  });

  it("still calls an unpriced freehold sold", async () => {
    renderAt("/listings", listingRoutes({
      "/v1/regions/search": pageOf("results", [{ ...listing, state: "SOLD", price: null }]),
    }));
    await screen.findByText("plot_a");
    expect(screen.getByText("Sold")).toBeInTheDocument();
  });

  it("asks for the first page at the configured page size, restating no default", async () => {
    const get = renderAt("/listings");
    await waitFor(() => expect(lastSearch(get)).toBeDefined());
    expect(lastSearch(get)).toEqual({ page: 1, pageSize: PAGE_SIZE });
  });

  it("turns every filter in the URL into the matching search parameter", async () => {
    const get = renderAt(
      "/listings?type=freehold&world=My%20World&tag=shop&tag=cbd&minPrice=100&maxPrice=5000&occupancy=unoccupied&sort=price_asc&page=3",
    );
    await waitFor(() => expect(lastSearch(get)).toMatchObject({ page: 3 }));
    expect(lastSearch(get)).toEqual({
      page: 3,
      pageSize: PAGE_SIZE,
      type: "freehold",
      world: "My World",
      tag: ["shop", "cbd"],
      minPrice: 100,
      maxPrice: 5000,
      occupancy: "unoccupied",
      sort: "price_asc",
    });
  });

  it("ignores a filter value the API would reject rather than sending it", async () => {
    const get = renderAt("/listings?type=mansion&sort=newest&minPrice=-4&page=0");
    await waitFor(() => expect(lastSearch(get)).toBeDefined());
    expect(lastSearch(get)).toEqual({ page: 1, pageSize: PAGE_SIZE });
  });

  it("searches the first whitelisted world when a whitelisted site is asked for no world", async () => {
    // The API filters by one world at a time, so under a whitelist there is no "any".
    const get = renderAt("/listings", listingRoutes(), ["My World", "world"]);
    await waitFor(() => expect(lastSearch(get)).toBeDefined());
    expect(lastSearch(get)).toMatchObject({ world: "My World" });
  });

  it("does not honour a link into a hidden world", async () => {
    const get = renderAt("/listings?world=world", listingRoutes(), ["My World"]);
    await waitFor(() => expect(lastSearch(get)).toBeDefined());
    expect(lastSearch(get)).toMatchObject({ world: "My World" });
  });

  it("offers only the whitelisted worlds, and no way to clear the choice", async () => {
    renderAt("/listings", listingRoutes(), ["My World"]);
    await screen.findByText("plot_a");
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "World" }));
    await waitFor(() => expect(document.querySelectorAll(".ant-select-item-option")).toHaveLength(1));
    expect(document.querySelector(".ant-select-item-option")?.textContent).toBe("My World");
  });

  it("shows an empty state rather than a blank page when nothing matches", async () => {
    renderAt("/listings", listingRoutes({ "/v1/regions/search": emptyPage("results") }));
    await waitFor(() => expect(screen.getByText(/no regions match/i)).toBeInTheDocument());
  });

  it("surfaces an error when the search fails", async () => {
    renderAt("/listings", listingRoutes({ "/v1/regions/search": failure(500, "INTERNAL_ERROR") }));
    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());
  });

  it("writes a changed sort into the URL and drops back to the first page", async () => {
    renderAt("/listings?page=2");
    await screen.findByText("plot_a");

    fireEvent.click(screen.getByRole("radio", { name: "Lowest first" }));

    await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent("?sort=price_asc"));
  });

  it("spells Sold as titled freeholds, since the API has no state filter", async () => {
    renderAt("/listings");
    await screen.findByText("plot_a");

    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Show" }));
    fireEvent.click(await screen.findByText("Sold"));

    await waitFor(() =>
      expect(screen.getByTestId("location")).toHaveTextContent("?type=freehold&occupancy=occupied"));
  });

  it("reads the Show control back from the URL's two filters", async () => {
    renderAt("/listings?type=leasehold&occupancy=occupied");
    await screen.findByText("plot_a");
    // antd's Select shows its choice as text beside the input, not as the input's value.
    await waitFor(() => expect(screen.getByText("Leased")).toBeInTheDocument());
  });

  it("writes a changed occupancy into the URL", async () => {
    renderAt("/listings");
    await screen.findByText("plot_a");

    fireEvent.click(screen.getByRole("radio", { name: "Vacant" }));

    await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent("?occupancy=unoccupied"));
  });

  it("pages only when there is more than one page", async () => {
    renderAt("/listings", listingRoutes({ "/v1/regions/search": pageOf("results", [listing], PAGE_SIZE + 1) }));
    await screen.findByText("plot_a");
    expect(screen.getByRole("listitem", { name: "2" })).toBeInTheDocument();
  });

  it("does not offer paging for a single page of results", async () => {
    renderAt("/listings");
    await screen.findByText("plot_a");
    expect(screen.queryByRole("listitem", { name: "2" })).toBeNull();
  });
});
