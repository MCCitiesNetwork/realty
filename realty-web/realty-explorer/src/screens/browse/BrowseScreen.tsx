import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import type { ApiClient } from "../../api/client";
import type { components, paths } from "../../api/schema";
import { prefetchViewer } from "../../viewer/lazyViewer";
import { StateBadge } from "../../ui/StateBadge";
import { formatPrice } from "../../ui/format";

type SearchResult = components["schemas"]["SearchResponse_Result"];
type WorldRef = components["schemas"]["WorldRef"];

/** The API's own `type` values, so the filter cannot offer one the search rejects. */
type ContractFilter = NonNullable<
  NonNullable<paths["/v1/regions/search"]["get"]["parameters"]["query"]>["type"]
>;

type Filters = {
  type: ContractFilter;
  world: string;
};

/**
 * Every value the search accepts, labelled as the API describes it.
 *
 * `sale`/`rent` are the market view -- what is actually listed -- while `freehold` and
 * `leasehold` widen to every contract of that kind, including sold or never-listed
 * regions, which come back with a null price. `leasehold` covers the same rows as
 * `rent`, since a lease always carries a price; it is offered anyway because the API
 * accepts it and a filter that silently omits an option is one nobody can find.
 */
const CONTRACT_FILTERS: ReadonlyArray<{ value: ContractFilter; label: string }> = [
  { value: "all", label: "All listings" },
  { value: "sale", label: "For sale" },
  { value: "rent", label: "For rent" },
  { value: "freehold", label: "All freeholds" },
  { value: "leasehold", label: "All leaseholds" },
];

type State =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "ready"; results: SearchResult[]; totalCount: number };

const PAGE_SIZE = 24;

export function BrowseScreen({ client }: { client: ApiClient }) {
  const [filters, setFilters] = useState<Filters>({ type: "all", world: "" });
  const [page, setPage] = useState(1);
  const [state, setState] = useState<State>({ status: "loading" });
  const [worlds, setWorlds] = useState<WorldRef[]>([]);

  useEffect(() => {
    // The world list comes from the database rather than from typing: a world name is a
    // folder name, so a filter that had to be spelled exactly was a filter that mostly
    // returned nothing. Fetched once -- worlds are registered when a region in them is,
    // which does not happen while someone is looking at this page.
    let cancelled = false;

    client.GET("/v1/worlds", {}).then(({ data, error }) => {
      // Array-checked rather than trusted: the filter is chrome, and a surprising body
      // here must not take the page it sits on down with it.
      if (cancelled || error || !Array.isArray(data)) return;
      setWorlds(data);
    });

    return () => {
      cancelled = true;
    };
  }, [client]);

  useEffect(() => {
    let cancelled = false;
    setState({ status: "loading" });

    client
      .GET("/v1/regions/search", {
        params: {
          query: {
            page,
            pageSize: PAGE_SIZE,
            ...(filters.type === "all" ? {} : { type: filters.type }),
            ...(filters.world ? { world: filters.world } : {}),
          },
        },
      })
      .then(({ data, error }) => {
        // Filters change faster than requests complete, so a late response must not
        // overwrite a newer one.
        if (cancelled) return;
        if (error || !data) {
          setState({ status: "error", message: "Could not load regions." });
          return;
        }
        setState({ status: "ready", results: data.results, totalCount: data.totalCount });
      });

    return () => {
      cancelled = true;
    };
  }, [client, filters, page]);

  const totalPages = state.status === "ready"
    ? Math.max(1, Math.ceil(state.totalCount / PAGE_SIZE))
    : 1;

  return (
    <div className="page">
      <header className="page-head">
        <h1>Regions</h1>
        <p className="sub">
          {state.status === "ready"
            ? `${state.totalCount} ${state.totalCount === 1 ? "region" : "regions"} on the market`
            : "Browse plots for sale and for rent"}
        </p>
      </header>

      <form className="filters" onSubmit={(event) => event.preventDefault()}>
        <label className="field">
          <span>Type</span>
          <select
            value={filters.type}
            onChange={(event) => {
              setPage(1);
              setFilters({ ...filters, type: event.target.value as ContractFilter });
            }}
          >
            {CONTRACT_FILTERS.map((filter) => (
              <option key={filter.value} value={filter.value}>{filter.label}</option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>World</span>
          <select
            value={filters.world}
            onChange={(event) => {
              setPage(1);
              setFilters({ ...filters, world: event.target.value });
            }}
          >
            <option value="">Any world</option>
            {/* No option is invented: an empty or unreachable list leaves just the one
                above, which filters nothing and is the honest state. */}
            {worlds.map((world) => (
              <option key={world.id} value={world.name ?? world.id}>
                {world.name ?? world.id}
              </option>
            ))}
          </select>
        </label>
      </form>

      {state.status === "loading" && (
        <ul className="grid">
          {Array.from({ length: 6 }, (_, index) => (
            <li key={index} className="skeleton skeleton-card" aria-hidden="true" />
          ))}
        </ul>
      )}

      {state.status === "error" && <p className="alert" role="alert">{state.message}</p>}

      {state.status === "ready" && state.results.length === 0 && (
        <p className="notice">No regions match these filters.</p>
      )}

      {state.status === "ready" && state.results.length > 0 && (
        <>
          <ul className="grid">
            {state.results.map((result) => {
              const world = result.world.name ?? result.world.id;
              return (
                <li key={`${result.world.id}/${result.worldGuardRegionId}`} className="card">
                  <StateBadge state={result.state} />
                  <Link
                    className="card-name"
                    // The viewer chunk is ~12 MB and is what makes a region page feel
                    // slow on a cold cache. Pointing at a card is a good enough signal
                    // to start fetching it, and by the click it is usually there.
                    onMouseEnter={prefetchViewer}
                    onFocus={prefetchViewer}
                    to={`/region/${encodeURIComponent(world)}/${encodeURIComponent(result.worldGuardRegionId)}`}
                  >
                    {result.worldGuardRegionId}
                  </Link>
                  <p className="card-world">{world}</p>
                  <div className="card-foot">
                    {result.price === null
                      ? <span className="price-none">No price set</span>
                      : <span className="price">{formatPrice(result.price)}</span>}
                    <span className="card-world">{result.contractType}</span>
                  </div>
                </li>
              );
            })}
          </ul>

          <nav className="pager">
            <button type="button" disabled={page <= 1} onClick={() => setPage(page - 1)}>
              Previous
            </button>
            <span>Page {page} of {totalPages}</span>
            <button
              type="button"
              disabled={page * PAGE_SIZE >= state.totalCount}
              onClick={() => setPage(page + 1)}
            >
              Next
            </button>
          </nav>
        </>
      )}
    </div>
  );
}
