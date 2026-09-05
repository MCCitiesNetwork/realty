import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { App } from "antd";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { PlayerFinder } from "./PlayerFinder";
import { alice, bob, emptyPage, pageOf } from "../test-support/fixtures";
import { failure, queriesTo, stubClient, type Routes as Stubs } from "../test-support/stubClient";

function Probe() {
  return <div data-testid="location">{useLocation().pathname}</div>;
}

const owners = pageOf("owners", [
  { rank: 1, player: alice, plotCount: 94 },
  { rank: 2, player: bob, plotCount: 3 },
]);

const renderFinder = (stubs: Stubs) => {
  const { client, get } = stubClient({ "/v1/leaderboard/owners": emptyPage("owners"), ...stubs });
  render(
    <App>
      <MemoryRouter initialEntries={["/"]}>
        <Routes>
          <Route path="/" element={<PlayerFinder client={client} />} />
          <Route path="/players/:id" element={<Probe />} />
        </Routes>
      </MemoryRouter>
    </App>,
  );
  return get;
};

const box = () => screen.getByRole("combobox");

const type = (name: string) => {
  fireEvent.focus(box());
  fireEvent.change(box(), { target: { value: name } });
};

const submit = () => fireEvent.keyDown(box(), { key: "Enter", code: "Enter" });

/** The dropdown entry naming the player; antd also echoes the name for screen readers. */
const suggestion = (name: string) =>
  [...document.querySelectorAll(".ant-select-item-option")].find((el) => el.textContent?.includes(name));

afterEach(() => vi.unstubAllGlobals());

describe("PlayerFinder", () => {
  it("suggests the title holders the register knows, with their plots", async () => {
    renderFinder({ "/v1/leaderboard/owners": owners });
    type("ali");
    await waitFor(() => expect(suggestion("Alice")).toBeDefined());
    expect(suggestion("Alice")?.textContent).toContain("94 plots");
    expect(suggestion("Bob")).toBeUndefined();
  });

  it("goes straight to a suggested player without asking the module", async () => {
    const get = renderFinder({ "/v1/leaderboard/owners": owners });
    type("ali");
    await waitFor(() => expect(suggestion("Alice")).toBeDefined());
    fireEvent.click(suggestion("Alice")!);
    await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent(`/players/${alice.id}`));
    expect(queriesTo(get, "/v1/players/lookup")).toHaveLength(0);
  });

  it("resolves a name the register does not know through the module", async () => {
    renderFinder({ "/v1/players/lookup": alice });
    type("Alice");
    submit();
    await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent(`/players/${alice.id}`));
  });

  it("says when no player has that name", async () => {
    renderFinder({ "/v1/players/lookup": failure(404, "PLAYER_NOT_FOUND") });
    type("Nobody");
    submit();
    await waitFor(() => expect(screen.getByText(/no player named nobody/i)).toBeInTheDocument());
  });

  it("falls back to playerdb.co when the module is unreachable", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(
      JSON.stringify({ data: { player: { id: bob.id } } }), { status: 200 })));
    renderFinder({ "/v1/players/lookup": failure(502, "NAME_LOOKUP_UNAVAILABLE") });
    type("Bob");
    submit();
    await waitFor(() => expect(screen.getByTestId("location")).toHaveTextContent(`/players/${bob.id}`));
  });

  it("says the lookups are down rather than that the player does not exist", async () => {
    // Neither resolver could be asked. "No such player" would be untrue.
    vi.stubGlobal("fetch", vi.fn(async () => { throw new TypeError("Failed to fetch"); }));
    renderFinder({ "/v1/players/lookup": failure(502, "NAME_LOOKUP_UNAVAILABLE") });
    type("Bob");
    submit();
    await waitFor(() => expect(screen.getByText(/query-service module/i)).toBeInTheDocument());
  });
});
