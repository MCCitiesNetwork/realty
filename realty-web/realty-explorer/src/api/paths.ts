import type { components } from "./schema";

type WorldRef = components["schemas"]["WorldRef"];
type PlayerRef = components["schemas"]["PlayerRef"];

/** A world's name where the API knows one; its id otherwise. Never anything invented. */
export function worldLabel(world: WorldRef): string {
  return world.name ?? world.id;
}

/**
 * A world name is a folder name on disk and may carry spaces, so it is encoded here and
 * decoded by the router, never rebuilt by hand.
 */
export function regionPath(world: WorldRef, regionId: string): string {
  return `/region/${encodeURIComponent(worldLabel(world))}/${encodeURIComponent(regionId)}`;
}

export function playerPath(player: PlayerRef | string): string {
  const id = typeof player === "string" ? player : player.id;
  return `/players/${encodeURIComponent(id)}`;
}

export type ListingsQuery = {
  type?: string;
  world?: string;
  tag?: string[];
  minPrice?: string;
  maxPrice?: string;
  occupancy?: string;
  sort?: string;
  page?: string;
};

/** The listings screen reads its filters from the URL, so a link is a saved search. */
export function listingsPath(query: ListingsQuery): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === "") continue;
    if (Array.isArray(value)) {
      for (const entry of value) params.append(key, entry);
    } else {
      params.set(key, value);
    }
  }
  const search = params.toString();
  return search ? `/listings?${search}` : "/listings";
}
