import { Route, Routes, useParams } from "react-router-dom";
import type { ApiClient } from "./api/client";
import { BrowseScreen } from "./screens/browse/BrowseScreen";
import { RegionScreen } from "./screens/region/RegionScreen";

function RegionRoute({ client }: { client: ApiClient }) {
  const params = useParams<{ world: string; region: string }>();
  return (
    <RegionScreen
      client={client}
      world={params.world ?? ""}
      region={params.region ?? ""}
      hasSchematic
    />
  );
}

export function AppRoutes({ client }: { client: ApiClient }) {
  return (
    <Routes>
      <Route path="/" element={<BrowseScreen client={client} />} />
      <Route path="/region/:world/:region" element={<RegionRoute client={client} />} />
    </Routes>
  );
}
