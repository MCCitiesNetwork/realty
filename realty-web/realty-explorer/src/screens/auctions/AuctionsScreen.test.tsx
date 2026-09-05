import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { AuctionsScreen } from "./AuctionsScreen";
import { auction, bob, emptyPage, pageOf, world } from "../../test-support/fixtures";
import { queriesTo, stubClient, type Routes } from "../../test-support/stubClient";

const renderScreen = (routes: Routes) => {
  const { client, get } = stubClient({ "/v1/worlds": [world], ...routes });
  render(<MemoryRouter><AuctionsScreen client={client} /></MemoryRouter>);
  return get;
};

describe("AuctionsScreen", () => {
  it("says nothing is under the hammer rather than showing an empty grid", async () => {
    renderScreen({ "/v1/auctions": emptyPage("auctions") });
    await waitFor(() =>
      expect(screen.getByText("No auctions are taking bids right now.")).toBeInTheDocument());
  });

  it("lists an auction with no bids as opening at its minimum", async () => {
    renderScreen({ "/v1/auctions": pageOf("auctions", [auction]) });
    await waitFor(() => expect(screen.getByRole("link", { name: "tower_1" })).toBeInTheDocument());
    expect(screen.getByText("No bids yet")).toBeInTheDocument();
    expect(screen.getByText("5k")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Alice" })).toBeInTheDocument();
  });

  it("names the standing bid and who placed it", async () => {
    renderScreen({
      "/v1/auctions": pageOf("auctions", [{
        ...auction,
        highestBid: { bidder: bob, amount: 7500, bidTime: "2026-09-02T10:00:00Z" },
        bidderCount: 3,
      }]),
    });
    await waitFor(() => expect(screen.getByText("7.5k")).toBeInTheDocument());
    expect(screen.getByRole("link", { name: "Bob" })).toBeInTheDocument();
    expect(screen.getByText("3 bidders")).toBeInTheDocument();
  });

  it("asks for the soonest-closing auctions first, which is the API's own default", async () => {
    const get = renderScreen({ "/v1/auctions": emptyPage("auctions") });
    await waitFor(() => expect(queriesTo(get, "/v1/auctions")).toHaveLength(1));
    expect(queriesTo(get, "/v1/auctions")[0]).not.toHaveProperty("sort");
  });
});
