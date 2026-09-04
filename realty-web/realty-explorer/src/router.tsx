import { Link, Route, Routes, useParams } from "react-router-dom";
import type { ApiClient } from "./api/client";
import type { Attribution } from "./config";
import { BrowseScreen } from "./screens/browse/BrowseScreen";
import { RegionScreen } from "./screens/region/RegionScreen";

function RegionRoute({ client, resourcePackAttribution }: Props) {
  const params = useParams<{ world: string; region: string }>();
  return (
    // hasSchematic is deliberately not passed: the screen probes for one. Passing a
    // hardcoded true here mounted the viewer for every region, including those with
    // no capture at all.
    <RegionScreen
      client={client}
      world={params.world ?? ""}
      region={params.region ?? ""}
      resourcePackAttribution={resourcePackAttribution}
    />
  );
}

type Props = { client: ApiClient; resourcePackAttribution: Attribution[] };

export function AppRoutes({ client, resourcePackAttribution }: Props) {
  return (
    <div className="app">
      <header className="masthead">
        <div className="masthead-inner">
          <Link className="wordmark" to="/">
            <span className="wordmark-mark" aria-hidden="true">R</span>
            Realty Explorer
          </Link>
        </div>
      </header>

      <Routes>
        <Route path="/" element={<BrowseScreen client={client} />} />
        <Route path="/region/:world/:region" element={<RegionRoute client={client} resourcePackAttribution={resourcePackAttribution} />} />
      </Routes>
    </div>
  );
}
