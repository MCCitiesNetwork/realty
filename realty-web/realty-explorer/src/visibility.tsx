import { createContext, useContext, type ReactNode } from "react";
import type { components } from "./api/schema";

type WorldRef = components["schemas"]["WorldRef"];

/** Which worlds this site shows: every one the register knows, or exactly the named ones. */
export type Visibility = {
  readonly all: boolean;
  /** The whitelisted names, in the operator's order; empty when `all`. */
  readonly names: ReadonlyArray<string>;
};

export const EVERY_WORLD: Visibility = { all: true, names: [] };

export function visibilityOf(names: ReadonlyArray<string>): Visibility {
  return names.length === 0 ? EVERY_WORLD : { all: false, names: [...names] };
}

/**
 * Whether a world may be shown.
 *
 * Matched by name, because a name is what an operator writes in `config.json`. A world
 * the register cannot name -- a null name, or a bare UUID in a link -- cannot be on a
 * list of names, so under a whitelist it is hidden: the safe reading of a whitelist is
 * that what it does not name stays out of sight.
 */
export function allowsWorld(visibility: Visibility, world: WorldRef | string): boolean {
  if (visibility.all) return true;
  const name = typeof world === "string" ? world : world.name;
  return name !== null && name !== undefined && visibility.names.includes(name);
}

export function visibleWorlds(visibility: Visibility, worlds: ReadonlyArray<WorldRef>): WorldRef[] {
  return worlds.filter((world) => allowsWorld(visibility, world));
}

/**
 * The world a whitelisted site opens a world-scoped page on when none was asked for.
 *
 * The API filters by one world at a time, so a page that pages through listings cannot
 * span several without paging wrongly; under a whitelist such a page always has a world,
 * and this is the one it starts with. Undefined when every world shows: "any world" is
 * a real choice there.
 */
export function defaultWorld(visibility: Visibility): string | undefined {
  return visibility.all ? undefined : visibility.names[0];
}

/**
 * The world a page should use: the one asked for if it may be shown, else the default.
 * A hidden world named in a link is not honoured -- the API would answer it -- and is
 * not distinguished from an unknown one either.
 */
export function worldFor(visibility: Visibility, requested: string | null | undefined): string | undefined {
  if (requested && allowsWorld(visibility, requested)) return requested;
  return defaultWorld(visibility);
}

const VisibilityContext = createContext<Visibility>(EVERY_WORLD);

export function VisibilityProvider({ value, children }: { value: Visibility; children: ReactNode }) {
  return <VisibilityContext.Provider value={value}>{children}</VisibilityContext.Provider>;
}

/** The deployment's whitelist; every world where no provider has said otherwise, as in tests. */
export function useVisibility(): Visibility {
  return useContext(VisibilityContext);
}
