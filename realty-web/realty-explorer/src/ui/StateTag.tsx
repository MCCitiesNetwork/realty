import { Tag } from "antd";
import { humanise } from "./format";

export type StateStyle = { color?: string; label: string };

/**
 * Availability as a colour, so it reads at a glance in a grid. The set is the API's
 * `state` enum; a value outside it still renders, uncoloured, rather than vanishing.
 */
const KNOWN: Readonly<Record<string, StateStyle>> = {
  FOR_SALE: { color: "green", label: "For sale" },
  FOR_LEASE: { color: "blue", label: "For rent" },
  SOLD: { color: "gold", label: "Sold" },
  LEASED: { color: "purple", label: "Leased" },
};

/**
 * The state a buyer cares about. The register says SOLD whenever a freehold has a title
 * holder, but a title holder who has set an asking price is selling: to a visitor that
 * plot is for sale, and calling it sold would send them away from a listing.
 */
export function marketState(
  state: string | null | undefined,
  freeholdPrice: number | null | undefined,
): string | null | undefined {
  if (state === "SOLD" && freeholdPrice !== null && freeholdPrice !== undefined) return "FOR_SALE";
  return state;
}

/** The label and colour for a state, shared by the tag and the listing-card ribbon. */
export function stateStyle(state?: string | null): StateStyle {
  // A registered region can carry no contract at all; that is a fact, not an unknown.
  if (!state) return { label: "No contract" };
  return KNOWN[state] ?? { label: humanise(state) };
}

export function StateTag({ state }: { state?: string | null }) {
  const style = stateStyle(state);
  return <Tag color={style.color}>{style.label}</Tag>;
}
