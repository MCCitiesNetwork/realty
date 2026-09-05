import { describe, expect, it } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { PlayerScreen } from "./PlayerScreen";
import { alice, unnamed, world } from "../../test-support/fixtures";
import { failure, queriesTo, stubClient, type Query, type Routes } from "../../test-support/stubClient";
import { VisibilityProvider, visibilityOf } from "../../visibility";

const summary = (player: typeof alice) => ({
  player, titleHeld: 93, landlordOf: 14, occupiedLandlordOf: 2, renting: 2, authorityOver: 0,
});

const holdings = (player: typeof alice) => ({
  player,
  page: 1, pageSize: 20, totalCount: 3, totalPages: 1,
  owned: [{ worldGuardRegionId: "or-c059", world }],
  landlord: [{ worldGuardRegionId: "av-gas", world }],
  rented: [{ worldGuardRegionId: "flat_9", world, endDate: "2026-10-01T00:00:00Z", secondsRemaining: 86_400 }],
});

const renderPlayer = (id: string, routes: Routes) => {
  const { client, get } = stubClient(routes);
  render(<MemoryRouter><PlayerScreen client={client} id={id} /></MemoryRouter>);
  return get;
};

describe("PlayerScreen", () => {
  it("shows the player's name and counts", async () => {
    renderPlayer(alice.id, { "/v1/players/summary": summary(alice), "/v1/players/regions": holdings(alice) });
    await waitFor(() => expect(screen.getByRole("heading", { level: 1, name: "Alice" })).toBeInTheDocument());
    expect(screen.getByText("93")).toBeInTheDocument();
    expect(screen.getByText("2 let, 12 vacant")).toBeInTheDocument();
  });

  it("lists every holding on the page, in the API's own groups", async () => {
    renderPlayer(alice.id, { "/v1/players/summary": summary(alice), "/v1/players/regions": holdings(alice) });
    await waitFor(() => expect(screen.getByRole("link", { name: "or-c059" })).toBeInTheDocument());
    expect(screen.getByRole("link", { name: "av-gas" })).toHaveAttribute("href", "/region/world/av-gas");
    expect(screen.getByText(/1 day left/)).toBeInTheDocument();
  });

  it("falls back to the id when the module could not name the player", async () => {
    renderPlayer(unnamed.id, { "/v1/players/summary": summary(unnamed), "/v1/players/regions": holdings(unnamed) });
    await waitFor(() => expect(screen.getByRole("heading", { level: 1, name: "c3c3c3c3" })).toBeInTheDocument());
  });

  it("asks for everything by default, and one category when chosen", async () => {
    const get = renderPlayer(alice.id, {
      "/v1/players/summary": summary(alice),
      "/v1/players/regions": (query: Query) => query.category === "owned"
        ? { player: alice, page: 1, pageSize: 20, totalCount: 1, totalPages: 1, regions: [{ worldGuardRegionId: "or-c059", world }] }
        : holdings(alice),
    });
    await screen.findByRole("link", { name: "av-gas" });
    expect(queriesTo(get, "/v1/players/regions")[0]).not.toHaveProperty("category");

    fireEvent.click(screen.getByRole("radio", { name: "Owned" }));

    await waitFor(() => expect(queriesTo(get, "/v1/players/regions").at(-1)).toMatchObject({ category: "owned" }));
    await waitFor(() => expect(screen.queryByRole("link", { name: "av-gas" })).toBeNull());
  });

  it("lists no holding in a hidden world, while the counts stay the API's", async () => {
    const { client } = stubClient({ "/v1/players/summary": summary(alice), "/v1/players/regions": holdings(alice) });
    render(
      <VisibilityProvider value={visibilityOf(["Elsewhere"])}>
        <MemoryRouter><PlayerScreen client={client} id={alice.id} /></MemoryRouter>
      </VisibilityProvider>,
    );
    await waitFor(() => expect(screen.getByText("93")).toBeInTheDocument());
    await waitFor(() => expect(screen.queryByText("Holdings")).toBeInTheDocument());
    expect(screen.queryByRole("link", { name: "or-c059" })).toBeNull();
    expect(screen.queryByRole("link", { name: "av-gas" })).toBeNull();
  });

  it("says when the id is not a player id at all", async () => {
    renderPlayer("not-a-uuid", {
      "/v1/players/summary": failure(400, "MALFORMED_UUID"),
      "/v1/players/regions": failure(400, "MALFORMED_UUID"),
    });
    await waitFor(() => expect(screen.getByText(/not a player id/i)).toBeInTheDocument());
  });
});
