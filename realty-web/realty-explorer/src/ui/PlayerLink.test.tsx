import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { PlayerLink, playerLabel } from "./PlayerLink";

const uuid = "8f4d1c2e-0000-0000-0000-000000000001";

function renderLink(party: Parameters<typeof PlayerLink>[0]["player"]) {
  return render(
    <MemoryRouter>
      <PlayerLink player={party} />
    </MemoryRouter>,
  );
}

describe("PlayerLink", () => {
  it("links a player", () => {
    renderLink({ kind: "personal", id: uuid, name: "Steve" });
    expect(screen.getByRole("link", { name: "Steve" }).getAttribute("href")).toBe(`/players/${uuid}`);
  });

  it("shows an account as text with its kind", () => {
    const { container } = renderLink({ kind: "government", id: "42", name: "GovSecurity" });
    expect(screen.getByText("GovSecurity (government)")).toBeTruthy();
    expect(container.querySelector("a")).toBeNull();
  });

  it("shows an account without a name by id", () => {
    const { container } = renderLink({ kind: "government", id: "42", name: null });
    expect(screen.getByText("#42 (government)")).toBeTruthy();
    expect(container.querySelector("a")).toBeNull();
  });

  it("shows a group as text", () => {
    const { container } = renderLink({ kind: "group", id: "police", name: "police" });
    expect(screen.getByText("police (group)")).toBeTruthy();
    expect(container.querySelector("a")).toBeNull();
  });

  it("shows a name that looks like markup as text", () => {
    const { container } = renderLink({ kind: "business", id: "7", name: "<b>Acme</b>" });
    expect(screen.getByText("<b>Acme</b> (business)")).toBeTruthy();
    expect(container.querySelector("b")).toBeNull();
  });

  it("labels a party the same way as plain text", () => {
    expect(playerLabel({ kind: "personal", id: uuid, name: null })).toBe("8f4d1c2e");
    expect(playerLabel({ kind: "group", id: "police", name: "police" })).toBe("police (group)");
    expect(playerLabel({ kind: "system", id: "3", name: null })).toBe("#3 (system)");
  });
});
