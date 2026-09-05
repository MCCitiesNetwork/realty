import { marketState, stateStyle } from "../ui/StateTag";
import type { Listing } from "./worldMap";

/** How one plot is drawn, and what the tooltip and legend call it. */
export type PlotStyle = { colour: string; label: string };

/** One line of the key, which is also the switch for that kind of plot. */
export type LegendEntry = PlotStyle & {
  /** Whether the map opens with this kind drawn. */
  onByDefault: boolean;
};

/**
 * The tag palette, as hex.
 *
 * <p>Ant Design's tags take colour names, and a map draws on a canvas that takes none,
 * so the same five states are spelled out here in the library's own preset values. They
 * are the presets rather than invented colours precisely so a plot on the map and the
 * same plot's tag on its page are one colour, not two greens.</p>
 */
const COLOURS: Readonly<Record<string, string>> = {
  FOR_SALE: "#52c41a",
  FOR_LEASE: "#1677ff",
  SOLD: "#faad14",
  LEASED: "#722ed1",
};

/** A contract in a state the palette has no colour for, which the register does not produce. */
const UNKNOWN = "#8c8c8c";

/**
 * What colour a plot is, from its contract.
 *
 * <p>A plot is its contract here: a region the register holds no contract for is not
 * drawn at all, since there is nothing to say about it. Matches the listing card in
 * calling a freehold with an asking price for sale, whatever the register calls it.</p>
 */
export function plotStyle(listing: Listing): PlotStyle {
  const state = marketState(listing.state, listing.contractType === "leasehold" ? null : listing.price);
  return { colour: COLOURS[state ?? ""] ?? UNKNOWN, label: stateStyle(state).label };
}

/**
 * The key, in the order it is listed: every label {@link plotStyle} can answer, since
 * the key is also how a visitor hides a kind of plot, and a kind with no entry could
 * not be hidden. What is taken -- sold, leased -- starts hidden: the map opens on what
 * a visitor could have, and the rest is a press away.
 */
export const LEGEND: readonly LegendEntry[] = [
  { colour: COLOURS.FOR_SALE, label: "For sale", onByDefault: true },
  { colour: COLOURS.FOR_LEASE, label: "For rent", onByDefault: true },
  { colour: COLOURS.LEASED, label: "Leased", onByDefault: false },
  { colour: COLOURS.SOLD, label: "Sold", onByDefault: false },
];

/** The kinds the map opens without, as the screen's initial switches. */
export function hiddenByDefault(): Set<string> {
  return new Set(LEGEND.filter((entry) => !entry.onByDefault).map((entry) => entry.label));
}
