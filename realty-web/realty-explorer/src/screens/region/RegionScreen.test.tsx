import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

vi.mock("../../viewer/SchematicViewer", () => ({
  SchematicViewer: () => <div data-testid="viewer" />,
}));

import { RegionScreen } from "./RegionScreen";
import { alice, bob, freeholdRegion, pageOf, emptyPage } from "../../test-support/fixtures";
import { failure, stubClient, type Query, type Routes } from "../../test-support/stubClient";

/** Everything the region page asks for, answered as it would be for a plain region. */
const regionRoutes = (overrides: Routes = {}): Routes => ({
  "/v1/region": freeholdRegion,
  "/v1/region/history": emptyPage("entries"),
  "/v1/region/members": failure(502, "MEMBERS_UNAVAILABLE"),
  "/v1/region/schematic": failure(404, "SCHEMATIC_NOT_FOUND"),
  ...overrides,
});

const renderScreen = (routes: Routes, props: Partial<React.ComponentProps<typeof RegionScreen>> = {}) => {
  const { client, get } = stubClient(routes);
  render(
    <MemoryRouter>
      <RegionScreen client={client} world="world" region="plot_a" {...props} />
    </MemoryRouter>,
  );
  return get;
};

describe("RegionScreen", () => {
  it("renders the region's details", async () => {
    renderScreen(regionRoutes(), { hasSchematic: false });
    await waitFor(() => expect(screen.getByRole("heading", { level: 1, name: "plot_a" })).toBeInTheDocument());
    expect(screen.getByText("For sale")).toBeInTheDocument();
    expect(screen.getByText("shop")).toBeInTheDocument();
    // The authority is a person, linked to their own page rather than printed.
    expect(screen.getByRole("link", { name: "Alice" })).toHaveAttribute("href", `/players/${alice.id}`);
  });

  it("calls a titled freehold with an asking price for sale, not sold", async () => {
    renderScreen(regionRoutes({
      "/v1/region": { ...freeholdRegion, state: "SOLD", freehold: { ...freeholdRegion.freehold, titleHolder: bob, price: 2500 } },
    }), { hasSchematic: false });
    await waitFor(() => expect(screen.getByRole("heading", { level: 1, name: "plot_a" })).toBeInTheDocument());
    expect(screen.getByText("For sale")).toBeInTheDocument();
    expect(screen.queryByText("Sold")).toBeNull();
  });

  it("shows a plain panel, not an error, when no schematic was captured", async () => {
    // Capture is on demand, so most regions have none. This is expected traffic.
    renderScreen(regionRoutes(), { hasSchematic: false });
    await waitFor(() => expect(screen.getByText(/no preview captured/i)).toBeInTheDocument());
    expect(screen.queryByText(/could not load/i)).toBeNull();
    expect(screen.queryByTestId("viewer")).toBeNull();
  });

  it("renders the viewer when a schematic exists", async () => {
    renderScreen(regionRoutes(), { hasSchematic: true });
    await waitFor(() => expect(screen.getByTestId("viewer")).toBeInTheDocument());
  });

  it("probes for a preview when not told, and shows the panel when there is none", async () => {
    // The router passes no hasSchematic: whether a capture exists is the API's to
    // answer. Hardcoding it mounted the viewer for every region, downloading ~12 MB
    // and failing to initialise on the many that have none.
    renderScreen(regionRoutes());
    await waitFor(() => expect(screen.getByText(/no preview captured/i)).toBeInTheDocument());
    expect(screen.queryByTestId("viewer")).toBeNull();
    expect(screen.queryByText(/could not load/i)).toBeNull();
  });

  it("hands the probed bytes to the viewer instead of fetching them twice", async () => {
    // The probe downloads the schematic to learn whether there is one. Discarding it
    // meant the viewer fetched the same megabytes again before drawing anything.
    const get = renderScreen(regionRoutes({ "/v1/region/schematic": new ArrayBuffer(8) }));
    await waitFor(() => expect(screen.getByTestId("viewer")).toBeInTheDocument());
    expect(get.mock.calls.filter((call) => call[0] === "/v1/region/schematic")).toHaveLength(1);
  });

  it("reports an unknown region as missing rather than as a failure", async () => {
    renderScreen(regionRoutes({ "/v1/region": failure(404, "REGION_NOT_FOUND") }), { hasSchematic: false });
    await waitFor(() => expect(screen.getByText(/no region named/i)).toBeInTheDocument());
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("credits the resource pack only where a schematic actually renders", async () => {
    const credit = [{ text: "Example Pack 32x", href: "https://packs.example.com/" }];

    const withPreview = render(
      <MemoryRouter>
        <RegionScreen client={stubClient(regionRoutes()).client} world="world" region="plot_a"
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
        <RegionScreen client={stubClient(regionRoutes()).client} world="world" region="plot_a"
                      hasSchematic={false} resourcePackAttribution={credit} />
      </MemoryRouter>,
    );
    await waitFor(() => expect(withoutPreview.getByText(/no preview captured/i)).toBeInTheDocument());
    expect(withoutPreview.queryByText("Example Pack 32x")).toBeNull();
  });

  it("takes the credit from the API, since the pack is the game server's setting", async () => {
    // The operator picks the pack in query-service's config.yml and states its credit
    // in the same file. Nothing about it is configured on the web host.
    renderScreen(regionRoutes({
      "/v1/resource-pack": {
        packs: [{ url: "https://cdn.example.com/p.zip", attribution: [{ text: "Example Pack 32x", url: "https://packs.example.com/" }] }],
        hash: null,
        required: false,
      },
    }), { hasSchematic: true });

    await waitFor(() =>
      expect(screen.getByRole("link", { name: "Example Pack 32x" }))
        .toHaveAttribute("href", "https://packs.example.com/"));
  });

  it("renders nothing extra when no credit is configured", async () => {
    renderScreen(regionRoutes(), { hasSchematic: true });
    await waitFor(() => expect(screen.getByTestId("viewer")).toBeInTheDocument());
    expect(document.querySelector(".viewer-credit")).toBeNull();
  });

  it("shows the region's own page while the details are still loading", () => {
    // Following a card used to blank the page until /v1/region answered, which read as a
    // slow navigation even though the name and the way back were known from the URL.
    const client = ({ GET: vi.fn(() => new Promise(() => {})) }) as unknown as Parameters<typeof RegionScreen>[0]["client"];
    render(
      <MemoryRouter>
        <RegionScreen client={client} world="world" region="plot_a" hasSchematic={false} />
      </MemoryRouter>,
    );
    expect(screen.getByRole("heading", { level: 1, name: "plot_a" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Listings" })).toBeInTheDocument();
  });

  it("lays out the register's history beneath the facts", async () => {
    renderScreen(regionRoutes({
      "/v1/region/history": pageOf("entries", [{
        kind: "freehold", eventType: "BUY", eventTime: "2026-06-26T06:34:50Z",
        buyer: bob, authority: alice, price: 1500,
      }]),
    }), { hasSchematic: false });

    await waitFor(() => expect(screen.getByText("Bought")).toBeInTheDocument());
    expect(screen.getByRole("link", { name: "Bob" })).toBeInTheDocument();
    expect(screen.getByText("1 event recorded")).toBeInTheDocument();
  });

  it("appends older events beneath the ones shown rather than replacing them", async () => {
    const entry = (n: number) => ({
      kind: "freehold", eventType: "BUY", eventTime: `2026-06-${String(n).padStart(2, "0")}T00:00:00Z`,
      buyer: bob, authority: alice, price: n,
    });
    renderScreen(regionRoutes({
      "/v1/region/history": (query: Query) => query.page === 2
        ? { ...pageOf("entries", [entry(2)], 21), page: 2 }
        : pageOf("entries", Array.from({ length: 20 }, (_, i) => entry(i + 3)), 21),
    }), { hasSchematic: false });

    const more = await screen.findByRole("button", { name: /show older events \(1 more\)/i });
    fireEvent.click(more);

    await waitFor(() => expect(screen.getAllByText("Bought")).toHaveLength(21));
    expect(screen.queryByRole("button", { name: /show older/i })).toBeNull();
  });

  it("says plainly when nothing has ever been recorded", async () => {
    // A region nobody has traded is a 200 with no entries, not a failure.
    renderScreen(regionRoutes(), { hasSchematic: false });
    await waitFor(() => expect(screen.getByText(/nothing has been recorded/i)).toBeInTheDocument());
  });

  it("explains an unreachable module beside the history instead of showing an empty access list", async () => {
    renderScreen(regionRoutes(), { hasSchematic: false });

    // No tab to switch to: who can build here sits beside what happened here.
    await waitFor(() => expect(screen.getByText(/query-service module/i)).toBeInTheDocument());
    // An empty owner list would be a claim about the region; the truth is that nobody
    // could be asked, so no list is drawn at all.
    expect(screen.queryByText("Owners")).toBeNull();
    expect(screen.queryByText("Members")).toBeNull();
  });
});
