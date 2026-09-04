import { Suspense, lazy, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import type { ApiClient } from "../../api/client";
import type { components } from "../../api/schema";

type Region = components["schemas"]["RegionResponse"];

// Lazy, so the browse screen never downloads Three.js or the WASM mesh pipeline.
const SchematicViewer = lazy(() =>
  import("../../viewer/SchematicViewer").then((module) => ({ default: module.SchematicViewer })),
);

type State =
  | { status: "loading" }
  | { status: "missing" }
  | { status: "error"; message: string }
  | { status: "ready"; region: Region };

type Props = {
  client: ApiClient;
  world: string;
  region: string;
  /** Whether a schematic was captured. Absent is the common case, not a failure. */
  hasSchematic?: boolean;
};

export function RegionScreen({ client, world, region, hasSchematic = false }: Props) {
  const [state, setState] = useState<State>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    setState({ status: "loading" });

    client
      .GET("/v1/region", { params: { query: { world, region } } })
      .then(({ data, error, response }) => {
        if (cancelled) return;
        if (response?.status === 404) {
          setState({ status: "missing" });
          return;
        }
        if (error || !data) {
          setState({ status: "error", message: "Could not load this region." });
          return;
        }
        setState({ status: "ready", region: data });
      });

    return () => {
      cancelled = true;
    };
  }, [client, world, region]);

  if (state.status === "loading") return <main><p>Loading {region}…</p></main>;

  if (state.status === "missing") {
    return (
      <main>
        <p>No region named {region} in {world}.</p>
        <Link to="/">Back to all regions</Link>
      </main>
    );
  }

  if (state.status === "error") {
    return (
      <main>
        <p role="alert">{state.message}</p>
        <Link to="/">Back to all regions</Link>
      </main>
    );
  }

  const { region: found } = state;

  return (
    <main>
      <Link to="/">Back to all regions</Link>
      <h1>{found.worldGuardRegionId}</h1>
      <p>{found.world.name ?? found.world.id}</p>
      {found.state && <p>{found.state}</p>}

      {found.freehold && <p>Price {found.freehold.price}</p>}
      {found.leasehold && <p>Rent {found.leasehold.price}</p>}

      {found.tags.length > 0 && (
        <ul>
          {found.tags.map((tag) => <li key={tag}>{tag}</li>)}
        </ul>
      )}

      <section>
        <h2>Preview</h2>
        {hasSchematic ? (
          <Suspense fallback={<p>Loading preview…</p>}>
            <SchematicViewer client={client} world={world} region={region} />
          </Suspense>
        ) : (
          // Capture is on demand, so most regions have none. This is expected, and
          // must never read as an error.
          <p>No preview captured for this region yet.</p>
        )}
      </section>
    </main>
  );
}
