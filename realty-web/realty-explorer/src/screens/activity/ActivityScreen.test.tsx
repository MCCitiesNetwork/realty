import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { ActivityScreen } from "./ActivityScreen";
import { emptyPage, pageOf, rentEvent, world } from "../../test-support/fixtures";
import { queriesTo, stubClient, type Routes } from "../../test-support/stubClient";

const renderScreen = (routes: Routes) => {
  const { client, get } = stubClient({ "/v1/worlds": [world], ...routes });
  render(<MemoryRouter><ActivityScreen client={client} /></MemoryRouter>);
  return get;
};

describe("ActivityScreen", () => {
  it("shows each event with its region, terms and parties", async () => {
    renderScreen({ "/v1/activity": pageOf("events", [rentEvent], 26_016) });
    await waitFor(() => expect(screen.getByText("Rented")).toBeInTheDocument());
    expect(screen.getByRole("link", { name: "m646-2" })).toHaveAttribute("href", "/region/world/m646-2");
    expect(screen.getByText("1k")).toBeInTheDocument();
    expect(screen.getByText("30 days")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Bob" })).toBeInTheDocument();
    expect(screen.getByText("26,016 events, newest first")).toBeInTheDocument();
  });

  it("leaves the event types to the API's default rather than restating them", async () => {
    // The default ticker set is the API's. Sending a copy would let the two drift.
    const get = renderScreen({ "/v1/activity": emptyPage("events") });
    await waitFor(() => expect(queriesTo(get, "/v1/activity")).toHaveLength(1));
    expect(queriesTo(get, "/v1/activity")[0]).not.toHaveProperty("type");
  });
});
