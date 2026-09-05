import { describe, expect, it } from "vitest";
import { allowsWorld, defaultWorld, visibilityOf, visibleWorlds, worldFor } from "./visibility";

const reveille = { id: "b04fccfd-0000-0000-0000-000000000001", name: "Reveille" };
const staff = { id: "b04fccfd-0000-0000-0000-000000000002", name: "StaffWorld" };
const unnamed = { id: "b04fccfd-0000-0000-0000-000000000003", name: null };

describe("visibility", () => {
  it("shows everything when no world is named", () => {
    const every = visibilityOf([]);
    expect(every.all).toBe(true);
    expect(allowsWorld(every, staff)).toBe(true);
    expect(allowsWorld(every, unnamed)).toBe(true);
    expect(defaultWorld(every)).toBeUndefined();
    expect(worldFor(every, null)).toBeUndefined();
    expect(worldFor(every, "StaffWorld")).toBe("StaffWorld");
  });

  it("shows exactly the named worlds, by name", () => {
    const listed = visibilityOf(["Reveille"]);
    expect(allowsWorld(listed, reveille)).toBe(true);
    expect(allowsWorld(listed, "Reveille")).toBe(true);
    expect(allowsWorld(listed, staff)).toBe(false);
    expect(visibleWorlds(listed, [reveille, staff, unnamed])).toEqual([reveille]);
  });

  it("hides a world it cannot name", () => {
    // A null name, or a bare id in a link, cannot be on a list of names.
    const listed = visibilityOf(["Reveille"]);
    expect(allowsWorld(listed, unnamed)).toBe(false);
    expect(allowsWorld(listed, reveille.id)).toBe(false);
  });

  it("opens world-scoped pages on the first listed world, and never on a hidden one", () => {
    const listed = visibilityOf(["Reveille", "Hamilton"]);
    expect(defaultWorld(listed)).toBe("Reveille");
    expect(worldFor(listed, null)).toBe("Reveille");
    expect(worldFor(listed, "Hamilton")).toBe("Hamilton");
    // A link naming a hidden world is not honoured: the API would answer it.
    expect(worldFor(listed, "StaffWorld")).toBe("Reveille");
  });
});
