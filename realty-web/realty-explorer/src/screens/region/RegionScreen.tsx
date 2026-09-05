import { Alert, Breadcrumb, Card, Col, Descriptions, Empty, Flex, Result, Row, Skeleton, Space, Tag, Typography } from "antd";
import { Suspense, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  fetchResourcePackAttribution,
  fetchSchematic,
  type ApiClient,
  type Attribution,
} from "../../api/client";
import { listingsPath, worldLabel } from "../../api/paths";
import type { components } from "../../api/schema";
import { useQuery } from "../../api/useQuery";
import { formatCount } from "../../ui/format";
import { Page } from "../../ui/Page";
import { PlayerLink } from "../../ui/PlayerLink";
import { ResourcePackCredit } from "../../ui/ResourcePackCredit";
import { StateTag, marketState } from "../../ui/StateTag";
// Lazy, so the browse screens never download Three.js or the WASM mesh pipeline.
import { SchematicViewer } from "../../viewer/lazyViewer";
import { allowsWorld, useVisibility } from "../../visibility";
import { PricePanel } from "./PricePanel";
import { RegionAccess } from "./RegionAccess";
import { RegionHistory } from "./RegionHistory";

const { Title, Text } = Typography;

type Region = components["schemas"]["RegionResponse"];

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

/**
 * One region, laid out as a listing: the preview where a photograph would be, the
 * price and terms beside it, then the history and the access lists side by side --
 * both visible at once, since a visitor asking who holds a plot usually also wants
 * to know who can walk into it.
 */
export function RegionScreen({ client, world, region, hasSchematic, resourcePackAttribution }: Props) {
  const visibility = useVisibility();
  // A region in a hidden world is not asked for at all: the API would answer, and this
  // site has undertaken not to show it. It reads exactly as an unknown region.
  const hidden = !allowsWorld(visibility, world);
  const found = useQuery(
    () => hidden
      ? Promise.resolve({ data: undefined, error: { error: "REGION_NOT_FOUND", message: "hidden" }, response: { status: 404 } })
      : client.GET("/v1/region", { params: { query: { world, region } } }),
    [client, world, region, hidden],
  );
  const [credits, setCredits] = useState<Attribution[]>(resourcePackAttribution ?? []);
  // Kept, not discarded: this is the schematic itself, and handing it to the viewer is
  // what stops the same megabytes being fetched twice.
  const [schematic, setSchematic] = useState<ArrayBuffer | undefined>(undefined);
  const [preview, setPreview] = useState<Preview>(
    hasSchematic === undefined ? "probing" : hasSchematic ? "present" : "absent",
  );

  useEffect(() => {
    if (hasSchematic !== undefined || hidden) return;
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
  }, [client, world, region, hasSchematic, hidden]);

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

  const crumbs = (
    <Breadcrumb
      style={{ marginBottom: 12 }}
      items={[
        { title: <Link to="/">Realty</Link> },
        { title: <Link to={listingsPath({})}>Listings</Link> },
        { title: <Link to={listingsPath({ world })}>{world}</Link> },
        { title: region },
      ]}
    />
  );

  if (hidden || (found.status === "error" && found.error.httpStatus === 404)) {
    return (
      <Page>
        {crumbs}
        <Result
          status="404"
          title={<>No region named <Text code>{region}</Text> in {world}</>}
          subTitle="Realty has no record of it. It may have been renamed, or never registered."
          extra={<Link to={listingsPath({})}>Browse the listings</Link>}
        />
      </Page>
    );
  }

  if (found.status === "error") {
    return (
      <Page>
        {crumbs}
        <Alert type="error" showIcon message="Could not load this region." />
      </Page>
    );
  }

  // The frame is drawn from what the URL already says, so following a card lands on
  // the region's own page immediately and the facts fill in. Blanking the whole page
  // until /v1/region answered made every click feel like a wait, even though the name
  // and the way back were known before the request was sent.
  const data = found.status === "ready" ? found.data : undefined;

  return (
    <Page>
      {crumbs}

      <Flex vertical gap={4} style={{ marginBottom: 20 }}>
        <Space size={8} wrap>
          <Title level={1} style={{ margin: 0, overflowWrap: "anywhere" }}>{data?.worldGuardRegionId ?? region}</Title>
          {data && <StateTag state={marketState(data.state, data.freehold?.price)} />}
        </Space>
        <Space size={4} wrap>
          <Text type="secondary">{data ? worldLabel(data.world) : world}</Text>
          {data && data.tags.length > 0 && (
            <>
              <Text type="secondary">·</Text>
              {data.tags.map((tag) => (
                <Link key={tag} to={listingsPath({ tag: [tag] })}>
                  <Tag style={{ cursor: "pointer", margin: 0 }}>{tag}</Tag>
                </Link>
              ))}
            </>
          )}
        </Space>
      </Flex>

      <Row gutter={[24, 24]} style={{ marginBottom: 24 }}>
        <Col xs={24} lg={15}>
          <Card
            title="Preview"
            size="small"
            extra={preview === "present" && <Text type="secondary" style={{ fontSize: 12 }}>Drag to orbit · scroll to zoom</Text>}
            styles={{ body: { padding: 0 } }}
          >
            {preview === "probing" && (
              <div style={{ padding: 24 }}>
                <Skeleton active paragraph={{ rows: 4 }} title={false} />
              </div>
            )}
            {preview === "present" && (
              <>
                <div className="viewer-body">
                  <Suspense fallback={<Text type="secondary">Loading preview…</Text>}>
                    <SchematicViewer client={client} world={world} region={region} schematic={schematic} />
                  </Suspense>
                </div>
                {/* Only here: the credit is owed for the pack, and this is the only
                    place the pack is used. */}
                <ResourcePackCredit attribution={credits} />
              </>
            )}
            {preview === "absent" && (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                style={{ padding: "32px 16px" }}
                description={
                  <Flex vertical gap={4} align="center">
                    {/* Capture is on demand, so most regions have none. Expected, not an error. */}
                    <span>No preview captured yet</span>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      Run <Text code>/realty schematic capture {data?.worldGuardRegionId ?? region}</Text> in game.
                    </Text>
                  </Flex>
                }
              />
            )}
          </Card>
        </Col>

        <Col xs={24} lg={9}>
          <Flex vertical gap={16}>
            {data ? <PricePanel region={data} /> : <Card size="small"><Skeleton active paragraph={{ rows: 2 }} /></Card>}
            <Card title="Details" size="small" styles={{ body: { padding: 0 } }}>
              {data ? <Facts region={data} /> : <div style={{ padding: 12 }}><Skeleton active paragraph={{ rows: 5 }} title={false} /></div>}
            </Card>
          </Flex>
        </Col>
      </Row>

      <Row gutter={[24, 24]}>
        <Col xs={24} lg={15}>
          <Card title="History" size="small">
            {/* Keyed so a different region starts its own history, not a continuation. */}
            <RegionHistory key={`${world}/${region}`} client={client} world={world} region={region} />
          </Card>
        </Col>
        <Col xs={24} lg={9}>
          <Card title="Who can build here" size="small">
            <RegionAccess client={client} world={world} region={region} />
          </Card>
        </Col>
      </Row>
    </Page>
  );
}

function Facts({ region }: { region: Region }) {
  const items: { key: string; label: string; children: React.ReactNode }[] = [];
  const add = (label: string, children: React.ReactNode) => items.push({ key: label, label, children });

  add("World", worldLabel(region.world));
  add("Contract", region.freehold && region.leasehold
    ? "Freehold and leasehold"
    : region.freehold ? "Freehold" : region.leasehold ? "Leasehold" : "None");

  if (region.freehold) {
    add("Title holder", region.freehold.titleHolder
      ? <PlayerLink player={region.freehold.titleHolder} />
      : <Text type="secondary">Nobody</Text>);
    add("Authority", <PlayerLink player={region.freehold.authority} />);
  }
  if (region.leasehold) {
    add("Landlord", <PlayerLink player={region.leasehold.landlord} />);
    add("Tenant", region.leasehold.tenant
      ? <PlayerLink player={region.leasehold.tenant} />
      : <Text type="secondary">Nobody</Text>);
  }

  if (region.dimensions) {
    const { shape, minY, maxY, points } = region.dimensions;
    add("Shape", shape === "CUBOID" ? "Cuboid" : `Polygon, ${points.length} corners`);
    add("Height", `${formatCount(maxY - minY + 1)} blocks (y ${minY} to ${maxY})`);
    // A cuboid's two points are opposite corners, so the footprint is derivable; a
    // polygon's is not, and reporting one would be a lie.
    if (shape === "CUBOID" && points.length === 2) {
      add("Footprint", `${Math.abs(points[1].x - points[0].x) + 1} × ${Math.abs(points[1].z - points[0].z) + 1}`);
    }
  }

  return (
    <Descriptions
      bordered
      size="small"
      column={1}
      items={items}
      styles={{ label: { width: "38%" } }}
    />
  );
}
