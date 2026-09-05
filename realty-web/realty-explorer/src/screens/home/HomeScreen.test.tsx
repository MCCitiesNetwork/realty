import { describe, expect, it } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { HomeScreen } from "./HomeScreen";
import { auction, emptyPage, listing, otherWorld, pageOf, rental, rentEvent, stats, tags, world } from "../../test-support/fixtures";
import { queriesTo, stubClient, type Query, type Routes as Stubs } from "../../test-support/stubClient";
import { VisibilityProvider, visibilityOf } from "../../visibility";

const homeRoutes = (overrides: Stubs = {}): Stubs => ({
  "/v1/stats": stats,
  "/v1/tags": tags,
  "/v1/worlds": [world, otherWorld],
  "/v1/regions/search": (query: Query) => query.type === "rent" ? pageOf("results", [rental]) : pageOf("results", [listing]),
  "/v1/activity": pageOf("events", [rentEvent]),
  "/v1/auctions": emptyPage("auctions"),
  ...overrides,
});

/** Where the search sent the visitor. */
function Probe() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname}{location.search}</div>;
}

const renderHome = (stubs: Stubs, visibleWorlds: string[] = []) => {
  const { client, get } = stubClient(stubs);
  render(
    <VisibilityProvider value={visibilityOf(visibleWorlds)}>
      <MemoryRouter initialEntries={["/"]}>
        <Routes>
          <Route path="/" element={<HomeScreen client={client} />} />
          <Route path="/listings" element={<Probe />} />
        </Routes>
      </MemoryRouter>
    </VisibilityProvider>,
  );
  return get;
};

describe("HomeScreen", () => {
  it("states the market in the API's own figures", async () => {
    renderHome(homeRoutes());
    await waitFor(() =>
      expect(screen.getByText("7,782 registered regions across 2 worlds.")).toBeInTheDocument());
    expect(screen.getByText("1,649")).toBeInTheDocument();
    expect(screen.getByText("1,532 with a title holder")).toBeInTheDocument();
    // 1,532 of 1,649 -- the ring says how much of the market is taken.
    expect(screen.getByLabelText("93% with a title holder")).toBeInTheDocument();
  });

  it("offers exactly the tags in use, with their counts, and invents none", async () => {
    renderHome(homeRoutes());
    const chips = await screen.findAllByRole("link", { name: /apartment|shop/ });
    expect(chips.map((chip) => chip.getAttribute("href")))
      .toEqual(["/listings?tag=apartment", "/listings?tag=shop"]);
    // Once in the grid and once on the search bar's chip; nowhere is a count invented.
    expect(screen.getAllByText("475")).toHaveLength(2);
  });

  it("shows what is on the market: vacant rentals, and every priced freehold", async () => {
    const get = renderHome(homeRoutes());
    await waitFor(() => expect(screen.getByText("flat_9")).toBeInTheDocument());
    expect(screen.getByText("plot_a")).toBeInTheDocument();
    // A rent is per term: "200" alone is a number without a unit.
    expect(screen.getByText(/\/ 30 days/)).toBeInTheDocument();

    // A leased plot cannot be rented, so "to rent" asks for vacant ones only. A freehold
    // with an asking price is for sale whoever holds the title, so "for sale" does not.
    const queries = queriesTo(get, "/v1/regions/search");
    expect(queries.find((query) => query.type === "rent")).toMatchObject({ occupancy: "unoccupied" });
    expect(queries.find((query) => query.type === "sale")).not.toHaveProperty("occupancy");
  });

  it("says there are no auctions rather than hiding the section or padding it", async () => {
    renderHome(homeRoutes());
    await waitFor(() =>
      expect(screen.getByText("No auctions are taking bids right now.")).toBeInTheDocument());
  });

  it("lists an auction when one is running", async () => {
    renderHome(homeRoutes({ "/v1/auctions": pageOf("auctions", [auction]) }));
    await waitFor(() => expect(screen.getByRole("link", { name: "tower_1" })).toBeInTheDocument());
    expect(screen.getByText("5k")).toBeInTheDocument();
  });

  it("shows recent activity as events on named regions", async () => {
    renderHome(homeRoutes());
    await waitFor(() => expect(screen.getByText("Rented")).toBeInTheDocument());
    expect(screen.getByRole("link", { name: "m646-2" })).toHaveAttribute("href", "/region/world/m646-2");
  });

  it("starts from every listing, and sends the visitor on with the filters they chose", async () => {
    renderHome(homeRoutes());
    await screen.findByText("flat_9");
    expect(screen.getByRole("radio", { name: "All" })).toBeChecked();

    fireEvent.click(screen.getByRole("radio", { name: "Buy" }));
    fireEvent.click(screen.getByRole("button", { name: /search/i }));

    await waitFor(() =>
      expect(screen.getByTestId("location")).toHaveTextContent("/listings?type=sale"));
  });

  it("asks for the tag list once, though the grid and the search bar both want it", async () => {
    const get = renderHome(homeRoutes());
    await screen.findByText("flat_9");
    await waitFor(() => expect(screen.getAllByText("475").length).toBeGreaterThan(0));
    expect(queriesTo(get, "/v1/tags")).toHaveLength(1);
    expect(queriesTo(get, "/v1/worlds")).toHaveLength(1);
  });

  it("keeps to the whitelisted worlds, counting and asking for those alone", async () => {
    // config.json names "world" only; "My World" exists but is not this site's business.
    const elsewhere = { ...listing, worldGuardRegionId: "elsewhere", world: otherWorld };
    const get = renderHome(homeRoutes({
      "/v1/regions/search": (query: Query) => query.world !== "world"
        ? pageOf("results", [elsewhere])
        : pageOf("results", [query.type === "rent" ? rental : listing]),
    }), ["world"]);
    await waitFor(() => expect(screen.getByText("7,782 registered regions across 1 world.")).toBeInTheDocument());
    await waitFor(() => expect(screen.getByText("flat_9")).toBeInTheDocument());
    expect(screen.getByText("plot_a")).toBeInTheDocument();

    for (const query of queriesTo(get, "/v1/regions/search")) expect(query).toMatchObject({ world: "world" });
    for (const query of queriesTo(get, "/v1/activity")) expect(query).toMatchObject({ world: "world" });
    expect(screen.queryByText("elsewhere")).toBeNull();
  });

  it("does not fail the page when a secondary feed fails", async () => {
    // The tag list is chrome. A surprising answer there must not take the page down.
    renderHome(homeRoutes({ "/v1/tags": { error: "INTERNAL_ERROR" } }));
    await waitFor(() => expect(screen.getByText("flat_9")).toBeInTheDocument());
  });
});
