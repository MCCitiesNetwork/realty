/**
 * A region's availability, as a shape rather than only a word.
 *
 * Semantic colour, deliberately separate from the accent: whether a plot can be
 * bought is information, not branding, and it should read at a glance in a grid.
 */
export function StateBadge({ state }: { state?: string | null }) {
  const label = state ?? "Unknown";
  const key = (state ?? "unknown").toLowerCase();
  return <span className={`badge badge-${key}`}>{label.replace(/_/g, " ")}</span>;
}
