import { App as AntApp, ConfigProvider, Layout } from "antd";
import { useEffect } from "react";
import { Route, Routes, useParams } from "react-router-dom";
import type { ApiClient } from "./api/client";
import type { AppConfig } from "./config";
import { ActivityScreen } from "./screens/activity/ActivityScreen";
import { AuctionsScreen } from "./screens/auctions/AuctionsScreen";
import { HomeScreen } from "./screens/home/HomeScreen";
import { ListingsScreen } from "./screens/listings/ListingsScreen";
import { NotFoundScreen } from "./screens/NotFoundScreen";
import { OwnersScreen } from "./screens/owners/OwnersScreen";
import { PlayerScreen } from "./screens/players/PlayerScreen";
import { RegionScreen } from "./screens/region/RegionScreen";
import { themeFor, useColorScheme } from "./theme";
import { SiteFooter } from "./ui/SiteFooter";
import { SiteHeader } from "./ui/SiteHeader";
import { VisibilityProvider, visibilityOf } from "./visibility";

type Props = { client: ApiClient };

/** The whole app, including the deployment settings: the worlds this site shows. */
type AppProps = Props & { config: AppConfig };

function RegionRoute({ client }: Props) {
  const params = useParams<{ world: string; region: string }>();
  return (
    // hasSchematic is deliberately not passed: the screen probes for one. Passing a
    // hardcoded true here mounted the viewer for every region, including those with
    // no capture at all.
    <RegionScreen client={client} world={params.world ?? ""} region={params.region ?? ""} />
  );
}

function PlayerRoute({ client }: Props) {
  const params = useParams<{ id: string }>();
  return <PlayerScreen client={client} id={params.id ?? ""} />;
}

export function AppRoutes({ client, config }: AppProps) {
  const scheme = useColorScheme();

  // Native controls and scrollbars follow the same scheme as the components.
  useEffect(() => {
    document.documentElement.style.colorScheme = scheme;
  }, [scheme]);

  return (
    <VisibilityProvider value={visibilityOf(config.visibleWorlds)}>
    <ConfigProvider theme={themeFor(scheme)}>
      <AntApp>
        <Layout style={{ minHeight: "100vh" }}>
          <SiteHeader client={client} />
          <Layout.Content style={{ display: "flex", flexDirection: "column" }}>
            <Routes>
              <Route path="/" element={<HomeScreen client={client} />} />
              <Route path="/listings" element={<ListingsScreen client={client} />} />
              <Route path="/region/:world/:region" element={<RegionRoute client={client} />} />
              <Route path="/auctions" element={<AuctionsScreen client={client} />} />
              <Route path="/activity" element={<ActivityScreen client={client} />} />
              <Route path="/owners" element={<OwnersScreen client={client} />} />
              <Route path="/players/:id" element={<PlayerRoute client={client} />} />
              <Route path="*" element={<NotFoundScreen />} />
            </Routes>
          </Layout.Content>
          <SiteFooter />
        </Layout>
      </AntApp>
    </ConfigProvider>
    </VisibilityProvider>
  );
}
