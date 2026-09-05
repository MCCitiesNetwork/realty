import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { OwnersScreen } from "./OwnersScreen";
import { alice, pageOf, unnamed } from "../../test-support/fixtures";
import { stubClient } from "../../test-support/stubClient";

describe("OwnersScreen", () => {
  it("ranks title holders and links each to their page", async () => {
    const { client } = stubClient({
      "/v1/leaderboard/owners": pageOf("owners", [
        { rank: 1, player: alice, plotCount: 94 },
        { rank: 2, player: unnamed, plotCount: 86 },
      ], 367),
    });
    render(<MemoryRouter><OwnersScreen client={client} /></MemoryRouter>);

    await waitFor(() => expect(screen.getByRole("link", { name: "Alice" })).toBeInTheDocument());
    expect(screen.getByRole("link", { name: "Alice" })).toHaveAttribute("href", `/players/${alice.id}`);
    expect(screen.getByText("94")).toBeInTheDocument();
    expect(screen.getByText("367 players hold at least one plot")).toBeInTheDocument();
  });

  it("shows an unnamed player by the start of their id, never by an invented name", async () => {
    // A null name is the module being unreachable. The UUID is the only true label.
    const { client } = stubClient({
      "/v1/leaderboard/owners": pageOf("owners", [{ rank: 1, player: unnamed, plotCount: 5 }]),
    });
    render(<MemoryRouter><OwnersScreen client={client} /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText("c3c3c3c3")).toBeInTheDocument());
    expect(screen.getByRole("link", { name: "c3c3c3c3" })).toHaveAttribute("href", `/players/${unnamed.id}`);
  });
});
