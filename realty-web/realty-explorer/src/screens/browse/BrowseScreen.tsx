import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import type { ApiClient } from "../../api/client";
import type { components } from "../../api/schema";
import { StateBadge } from "../../ui/StateBadge";
import { formatPrice } from "../../ui/format";

type SearchResult = components["schemas"]["SearchResponse_Result"];

type Filters = {
  type: "all" | "sale" | "rent";
  world: string;
};

type State =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "ready"; results: SearchResult[]; totalCount: number };

const PAGE_SIZE = 24;

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
              setFilters({ ...filters, type: event.target.value as Filters["type"] });
            }}
          >
            <option value="all">All</option>
            <option value="sale">For sale</option>
            <option value="rent">For rent</option>
          </select>
        </label>
        <label className="field">
          <span>World</span>
          <input
            value={filters.world}
            placeholder="Any world"
            onChange={(event) => {
              setPage(1);
              setFilters({ ...filters, world: event.target.value });
            }}
          />
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
