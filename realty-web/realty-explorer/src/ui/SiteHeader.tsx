import { HomeFilled } from "@ant-design/icons";
import { Flex, Layout, Menu, Typography } from "antd";
import { Link, useLocation } from "react-router-dom";
import type { ApiClient } from "../api/client";
import { listingsPath } from "../api/paths";
import { PlayerFinder } from "./PlayerFinder";

const NAV = [
  { key: "buy", to: listingsPath({ type: "sale" }), label: "Buy" },
  { key: "rent", to: listingsPath({ type: "rent", occupancy: "unoccupied" }), label: "Rent" },
  { key: "auctions", to: "/auctions", label: "Auctions" },
  { key: "activity", to: "/activity", label: "Activity" },
  { key: "owners", to: "/owners", label: "Owners" },
];

/** Which entry the current URL belongs to, so the menu can say where you are. */
function activeKey(pathname: string, search: string): string | undefined {
  if (pathname === "/listings") {
    const type = new URLSearchParams(search).get("type");
    return type === "sale" ? "buy" : type === "rent" ? "rent" : undefined;
  }
  return NAV.find((entry) => entry.to === pathname)?.key;
}

export function SiteHeader({ client }: { client: ApiClient }) {
  const location = useLocation();
  const active = activeKey(location.pathname, location.search);

  return (
    <Layout.Header style={{ position: "sticky", top: 0, zIndex: 10, boxShadow: "0 1px 0 rgba(0,0,0,0.06)" }}>
      <Flex align="center" gap={24} style={{ height: "100%", maxWidth: 1200, margin: "0 auto" }}>
        <Link to="/" style={{ display: "inline-flex", alignItems: "center", gap: 8, whiteSpace: "nowrap" }}>
          <HomeFilled style={{ fontSize: 18 }} />
          <Typography.Text strong style={{ fontSize: 18, letterSpacing: "-0.01em" }}>Realty</Typography.Text>
        </Link>
        <Menu
          mode="horizontal"
          selectedKeys={active ? [active] : []}
          items={NAV.map((entry) => ({ key: entry.key, label: <Link to={entry.to}>{entry.label}</Link> }))}
          style={{ flex: 1, minWidth: 0, background: "transparent", borderBottom: "none" }}
        />
        <PlayerFinder client={client} />
      </Flex>
    </Layout.Header>
  );
}
