import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import type { ApiClient } from "../../api/client";
import type { components } from "../../api/schema";

type SearchResult = components["schemas"]["SearchResponse_Result"];

type Filters = {
  type: "all" | "sale" | "rent";
  world: string;
};

type State =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "ready"; results: SearchResult[]; totalCount: number };

const PAGE_SIZE = 25;

export function BrowseScreen({ client }: { client: ApiClient }) {
  const [filters, setFilters] = useState<Filters>({ type: "all", world: "" });
  const [page, setPage] = useState(1);
  const [state, setState] = useState<State>({ status: "loading" });

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
        // An unmounted component setting state is a warning at best and a stale
        // render at worst, and filters change faster than requests complete.
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

  return (
    <main>
      <h1>Regions</h1>

      <form onSubmit={(event) => event.preventDefault()}>
        <label>
          Type
          <select
            value={filters.type}
            onChange={(event) => {
              setPage(1);
              setFilters({ ...filters, type: event.target.value as Filters["type"] });
            }}
          >
            <option value="all">All</option>
            <option value="sale">For sale</option>
            <option value="rent">For rent</option>
          </select>
        </label>
        <label>
          World
          <input
            value={filters.world}
            placeholder="any world"
            onChange={(event) => {
              setPage(1);
              setFilters({ ...filters, world: event.target.value });
            }}
          />
        </label>
      </form>

      {state.status === "loading" && <p>Loading regions…</p>}

      {state.status === "error" && <p role="alert">{state.message}</p>}

      {state.status === "ready" && state.results.length === 0 && (
        <p>No regions match these filters.</p>
      )}

      {state.status === "ready" && state.results.length > 0 && (
        <>
          <ul>
            {state.results.map((result) => (
              <li key={`${result.world.id}/${result.worldGuardRegionId}`}>
                <Link
                  to={`/region/${encodeURIComponent(result.world.name ?? result.world.id)}/${encodeURIComponent(result.worldGuardRegionId)}`}
                >
                  {result.worldGuardRegionId}
                </Link>
                <span>{result.state}</span>
                {result.price !== null && <span>{result.price}</span>}
              </li>
            ))}
          </ul>
          <nav>
            <button type="button" disabled={page <= 1} onClick={() => setPage(page - 1)}>
              Previous
            </button>
            <span>
              Page {page} of {Math.max(1, Math.ceil(state.totalCount / PAGE_SIZE))}
            </span>
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
    </main>
  );
}
