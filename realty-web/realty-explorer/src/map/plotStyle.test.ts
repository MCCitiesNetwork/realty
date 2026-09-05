import { describe, expect, it } from "vitest";
import { LEGEND, hiddenByDefault, plotStyle } from "./plotStyle";
import type { Listing } from "./worldMap";

const listing = (over: Partial<Listing>): Listing => ({
  worldGuardRegionId: "plot_a",
  world: { id: "w", name: "world" },
  contractType: "freehold",
  price: 1500,
  state: "FOR_SALE",
  durationSeconds: null,
  ...over,
});

describe("plotStyle", () => {
  it("colours a plot for sale and one for rent apart", () => {
    expect(plotStyle(listing({})).label).toBe("For sale");
    expect(plotStyle(listing({ contractType: "leasehold", state: "FOR_LEASE" })).label).toBe("For rent");
    expect(plotStyle(listing({})).colour).not.toBe(
      plotStyle(listing({ contractType: "leasehold", state: "FOR_LEASE" })).colour);
  });

  it("calls a sold freehold with an asking price for sale, as the listing card does", () => {
    expect(plotStyle(listing({ state: "SOLD", price: 900 })).label).toBe("For sale");
  });

  it("keeps a let plot distinct from one going spare", () => {
    const leased = plotStyle(listing({ contractType: "leasehold", state: "LEASED", price: 200 }));
    expect(leased.label).toBe("Leased");
    expect(leased.colour).not.toBe(plotStyle(listing({ contractType: "leasehold", state: "FOR_LEASE" })).colour);
  });
});

describe("LEGEND", () => {
  it("has an entry for every label a plot can be drawn with", () => {
    // The legend doubles as the switch that hides a kind of plot, so a kind it did
    // not name could never be hidden.
    const labels = new Set(LEGEND.map((entry) => entry.label));
    for (const state of ["FOR_SALE", "FOR_LEASE", "SOLD", "LEASED"] as const) {
      const priced = state === "LEASED" || state === "FOR_LEASE";
      expect(labels.has(plotStyle(listing({ state, contractType: priced ? "leasehold" : "freehold", price: state === "SOLD" ? null : 100 })).label)).toBe(true);
    }
  });

  it("names one colour per state a plot can be drawn in", () => {
    expect(LEGEND.map((entry) => entry.label))
      .toEqual(["For sale", "For rent", "Leased", "Sold"]);
    expect(new Set(LEGEND.map((entry) => entry.colour)).size).toBe(LEGEND.length);
  });

  it("opens on what a visitor could have, with what is taken a press away", () => {
    expect([...hiddenByDefault()]).toEqual(["Leased", "Sold"]);
  });
});
