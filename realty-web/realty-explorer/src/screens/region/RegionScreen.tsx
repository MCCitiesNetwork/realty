import { Suspense, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  fetchResourcePackAttribution,
  fetchSchematic,
  type ApiClient,
  type Attribution,
} from "../../api/client";
import type { components } from "../../api/schema";
import { ResourcePackCredit } from "../../ui/ResourcePackCredit";
import { StateBadge } from "../../ui/StateBadge";
import { formatPrice } from "../../ui/format";

type Region = components["schemas"]["RegionResponse"];

// Lazy, so the browse screen never downloads Three.js or the WASM mesh pipeline.
import { SchematicViewer } from "../../viewer/lazyViewer";

type State =
  | { status: "loading" }
  | { status: "missing" }
  | { status: "error"; message: string }
  | { status: "ready"; region: Region };

type Props = {
  client: ApiClient;
  world: string;
  region: string;
  /**
   * Forces the preview state instead of probing for one. Tests set it; the app does
   * not, because whether a schematic exists is something only the API knows.
   */
  hasSchematic?: boolean;
  /**
   * Overrides the credits instead of asking the API for them. Tests set it; the app does
   * not, because the pack -- and so what is owed for it -- is the game server's setting.
   */
  resourcePackAttribution?: Attribution[];
};

/** Absent is the common case -- capture is on demand -- so it is a state, not an error. */
type Preview = "probing" | "present" | "absent";

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="fact">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

export function RegionScreen({
  client,
  world,
  region,
  hasSchematic,
  resourcePackAttribution,
}: Props) {
  const [credits, setCredits] = useState<Attribution[]>(resourcePackAttribution ?? []);
  // Kept, not discarded: this is the schematic itself, and handing it to the viewer is
  // what stops the same megabytes being fetched twice.
  const [schematic, setSchematic] = useState<ArrayBuffer | undefined>(undefined);
  const [state, setState] = useState<State>({ status: "loading" });
  const [preview, setPreview] = useState<Preview>(
    hasSchematic === undefined ? "probing" : hasSchematic ? "present" : "absent",
  );

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

  useEffect(() => {
    if (hasSchematic !== undefined) return;
    let cancelled = false;
    setPreview("probing");

    // Ask before mounting the viewer. Mounting it unconditionally downloads ~12 MB of
    // Three.js and WASM and then fails to initialise for the many regions that have no
    // capture -- which is the normal case, not an error.
    fetchSchematic(client, world, region)()
      .then((bytes) => {
        if (cancelled) return;
        setSchematic(bytes);
        setPreview("present");
      })
      .catch(() => {
        if (!cancelled) setPreview("absent");
      });

    return () => {
      cancelled = true;
    };
  }, [client, world, region, hasSchematic]);

  // A different region means the bytes in hand belong to the previous one.
  useEffect(() => setSchematic(undefined), [world, region]);

  useEffect(() => {
    // Only once a preview is actually rendering: the credit is owed for the pack, and a
    // page that draws nothing with it owes nothing.
    if (resourcePackAttribution !== undefined || preview !== "present") return;
    let cancelled = false;

    void fetchResourcePackAttribution(client).then((fetched) => {
      if (!cancelled) setCredits(fetched);
    });

    return () => {
      cancelled = true;
    };
  }, [client, preview, resourcePackAttribution]);

  if (state.status === "loading") {
    // The frame is drawn from what the URL already says, so following a card lands on
    // the region's own page immediately and the facts fill in. Blanking the whole page
    // until /v1/region answers made every click feel like a wait, even though the name
    // and the way back were known before the request was sent.
    return (
      <div className="page">
        <Link className="back" to="/">&larr; All regions</Link>

        <header className="page-head">
          <h1>{region}</h1>
          <p className="sub">{world}</p>
        </header>

        <div className="detail">
          <section className="panel">
            <h2>Details</h2>
            <div className="skeleton" style={{ height: "9rem" }} />
          </section>
          <section className="panel viewer-panel">
            <div className="viewer-head"><h2>Preview</h2></div>
            <div className="viewer-body is-empty">
              <div className="viewer-empty">Checking for a preview…</div>
            </div>
          </section>
        </div>
      </div>
    );
  }

  if (state.status === "missing") {
    return (
      <div className="page">
        <Link className="back" to="/">&larr; All regions</Link>
        <p className="notice">No region named <strong>{region}</strong> in {world}.</p>
      </div>
    );
  }

  if (state.status === "error") {
    return (
      <div className="page">
        <Link className="back" to="/">&larr; All regions</Link>
        <p className="alert" role="alert">{state.message}</p>
      </div>
    );
  }

  const found = state.region;
  const worldName = found.world.name ?? found.world.id;

  return (
    <div className="page">
      <Link className="back" to="/">&larr; All regions</Link>

      <header className="page-head">
        <StateBadge state={found.state} />
        <h1>{found.worldGuardRegionId}</h1>
        <p className="sub">{worldName}</p>
      </header>

      <div className="detail">
        <section className="panel">
          <h2>Details</h2>
          <dl className="facts">
            {found.freehold && (
              <>
                <Fact label="Price">
                  <span className="price">{formatPrice(found.freehold.price ?? 0)}</span>
                </Fact>
                <Fact label="Accepting offers">
                  {found.freehold.acceptingOffers ? "Yes" : "No"}
                </Fact>
              </>
            )}
            {found.leasehold && (
              <Fact label="Rent">
                <span className="price">{formatPrice(found.leasehold.price ?? 0)}</span>
              </Fact>
            )}
            {found.auction && <Fact label="Auction">Active</Fact>}
            <Fact label="World">{worldName}</Fact>
            {found.dimensions && (
              <>
                <Fact label="Shape">{found.dimensions.shape.toLowerCase()}</Fact>
                <Fact label="Height">
                  {found.dimensions.maxY - found.dimensions.minY + 1} blocks
                </Fact>
                {/* A cuboid's two points are opposite corners, so the footprint is
                    derivable; a polygon's is not, and reporting one would be a lie. */}
                {found.dimensions.shape === "CUBOID" && found.dimensions.points.length === 2 && (
                  <Fact label="Footprint">
                    {Math.abs(found.dimensions.points[1].x - found.dimensions.points[0].x) + 1}
                    {" × "}
                    {Math.abs(found.dimensions.points[1].z - found.dimensions.points[0].z) + 1}
                  </Fact>
                )}
              </>
            )}
          </dl>

          {found.tags.length > 0 && (
            <>
              <h2 style={{ marginTop: "1.1rem" }}>Tags</h2>
              <div className="tags">
                {found.tags.map((tag) => <span key={tag} className="tag">{tag}</span>)}
              </div>
            </>
          )}
        </section>

        <section className="panel viewer-panel">
          <div className="viewer-head">
            <h2>Preview</h2>
            {preview === "present" && (
              <span className="viewer-note">Drag to orbit &middot; scroll to zoom</span>
            )}
          </div>

          {preview === "probing" && (
            <div className="viewer-body is-empty">
              <div className="viewer-empty">Checking for a preview…</div>
            </div>
          )}

          {preview === "present" && (
            <>
              <div className="viewer-body">
                <Suspense fallback={<div className="viewer-empty">Loading preview…</div>}>
                  <SchematicViewer
                    client={client}
                    world={world}
                    region={region}
                    schematic={schematic}
                  />
                </Suspense>
              </div>
              {/* Only here: the credit is owed for the pack, and this is the only place
                  the pack is used. */}
              <ResourcePackCredit attribution={credits} />
            </>
          )}

          {preview === "absent" && (
            <div className="viewer-body is-empty">
              {/* Capture is on demand, so most regions have none. Expected, not an error. */}
              <div className="viewer-empty">
                <span>No preview captured yet</span>
                <span className="hint">
                  Run <code>/realty schematic capture {found.worldGuardRegionId}</code> in game.
                </span>
              </div>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
