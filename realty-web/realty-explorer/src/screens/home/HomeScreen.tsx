import { SearchOutlined, TagOutlined } from "@ant-design/icons";
import { Button, Card, Col, Flex, Progress, Row, Segmented, Skeleton, Statistic, Typography, theme } from "antd";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import type { ApiClient } from "../../api/client";
import { listingsPath, regionPath, worldLabel } from "../../api/paths";
import { TTL, remembered } from "../../api/remembered";
import { useQuery } from "../../api/useQuery";
import { EventParties, EventTerms } from "../../ui/events";
import { eventLabel, formatCount, formatDuration, formatRelative } from "../../ui/format";
import { ListingGrid } from "../../ui/ListingGrid";
import { Page } from "../../ui/Page";
import { PlayerLink } from "../../ui/PlayerLink";
import { Price } from "../../ui/Price";
import { Rows } from "../../ui/Rows";
import { TagSelect } from "../../ui/TagSelect";
import { WorldSelect } from "../../ui/WorldSelect";
import { activityVisible, auctionsVisible, searchVisible } from "../../api/scoped";
import { useVisibility, visibleWorlds } from "../../visibility";

const { Title, Text } = Typography;

type Intent = "sale" | "rent" | "all";

const INTENTS: ReadonlyArray<{ value: Intent; label: string }> = [
  { value: "all", label: "All" },
  { value: "sale", label: "Buy" },
  { value: "rent", label: "Rent" },
];

const FEATURED_SPAN = { xs: 24, sm: 12, lg: 8, xl: 6 };

/**
 * The front door: a search, the market in numbers, and what is on it right now.
 *
 * Every figure and every listing below is the API's answer at load time. There is no
 * "featured" curation -- the search orders by asking price, so that is the order shown,
 * and the headings say so.
 */
export function HomeScreen({ client }: { client: ApiClient }) {
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const visibility = useVisibility();
  const [intent, setIntent] = useState<Intent>("all");
  const [world, setWorld] = useState<string | undefined>(undefined);
  const [tags, setTags] = useState<string[]>([]);

  // Shared with the search bar's selects and remembered across visits to this page:
  // the tag list was asked for twice per visit, and everything again on the way back.
  const stats = useQuery(() => remembered(client, "stats", TTL.stats, () => client.GET("/v1/stats", {})), [client]);
  const tagList = useQuery(() => remembered(client, "tags", TTL.tags, () => client.GET("/v1/tags", {})), [client]);
  const worlds = useQuery(() => remembered(client, "worlds", TTL.worlds, () => client.GET("/v1/worlds", {})), [client]);
  // Every priced freehold, occupied or not: a title holder with an asking price is
  // selling. Vacancy only matters for renting, where a tenant in place means no room.
  // Each sample spans the visible worlds -- one question, or one per listed world.
  const forSale = useQuery(() => searchVisible(client, visibility, { type: "sale" }, 8), [client, visibility]);
  const toRent = useQuery(() => searchVisible(client, visibility, { type: "rent", occupancy: "unoccupied" }, 8), [client, visibility]);
  const activity = useQuery(() => activityVisible(client, visibility, 6), [client, visibility]);
  const auctions = useQuery(() => auctionsVisible(client, visibility, 4), [client, visibility]);
  const worldCount = worlds.status === "ready" ? visibleWorlds(visibility, worlds.data).length : 0;

  const search = () => navigate(listingsPath({
    type: intent === "all" ? undefined : intent,
    ...(intent === "rent" ? { occupancy: "unoccupied" } : {}),
    world,
    tag: tags,
  }));

  return (
    <Page>
      <Card style={{ marginBottom: 24 }}>
        <Flex vertical gap={16}>
          <div>
            <Title level={1} style={{ marginBottom: 4 }}>Find a plot to buy or rent</Title>
            <Text type="secondary">
              {stats.status === "ready" && worlds.status === "ready"
                ? `${formatCount(stats.data.regions)} registered regions across ${formatCount(worldCount)} ${worldCount === 1 ? "world" : "worlds"}.`
                : "Plots for sale and for rent, straight from the server's records."}
            </Text>
          </div>
          {/* One search bar: intent, where, what, go. The selects share the slack and
              the whole row folds onto several lines only when the screen is narrow. */}
          <Flex wrap gap={12} align="center">
            <Segmented
              aria-label="Looking to"
              size="large"
              options={INTENTS.map((entry) => ({ value: entry.value, label: entry.label }))}
              value={intent}
              onChange={(value) => setIntent(value as Intent)}
            />
            <WorldSelect client={client} value={world} onChange={setWorld} size="large"
                         style={{ flex: "1 1 200px", width: "auto" }} />
            <TagSelect client={client} value={tags} onChange={setTags} size="large"
                       style={{ flex: "2 1 260px", width: "auto" }} />
            <Button type="primary" size="large" icon={<SearchOutlined />} onClick={search}>
              Search
            </Button>
          </Flex>
        </Flex>
      </Card>

      <Row gutter={[16, 16]} style={{ marginBottom: 32 }}>
        <Col xs={24} sm={12} lg={6}>
          <OccupancyTile
            title="Freehold contracts"
            total={stats.status === "ready" ? stats.data.freehold.contracts : undefined}
            occupied={stats.status === "ready" ? stats.data.freehold.occupied : undefined}
            occupiedLabel="with a title holder"
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <OccupancyTile
            title="Leasehold contracts"
            total={stats.status === "ready" ? stats.data.leasehold.contracts : undefined}
            occupied={stats.status === "ready" ? stats.data.leasehold.occupied : undefined}
            occupiedLabel="let"
            note={stats.status === "ready" ? `mean term ${formatDuration(stats.data.leasehold.averageDurationSeconds)}` : undefined}
          />
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small" style={{ height: "100%" }}>
            <Statistic
              title="Open offers"
              value={stats.status === "ready" ? stats.data.activeOffers : undefined}
              loading={stats.status === "loading"}
            />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small" style={{ height: "100%" }}>
            <Statistic
              title="Live auctions"
              value={stats.status === "ready" ? stats.data.activeAuctions : undefined}
              loading={stats.status === "loading"}
            />
          </Card>
        </Col>
      </Row>

      <section style={{ marginBottom: 32 }}>
        <Title level={3}>Browse by tag</Title>
        <Text type="secondary" style={{ display: "block", marginBottom: 12 }}>
          Every tag in use, most common first. Each bar is the tag's share of the most-used one.
        </Text>
        {tagList.status === "loading" && <Skeleton active paragraph={{ rows: 2 }} title={false} />}
        {tagList.status === "ready" && tagList.data.length === 0 && (
          <Text type="secondary">No region carries a tag yet.</Text>
        )}
        {tagList.status === "ready" && tagList.data.length > 0 && (
          <Row gutter={[12, 12]}>
            {[...tagList.data]
              .sort((a, b) => b.regionCount - a.regionCount)
              .map((tag, _, sorted) => (
                <Col key={tag.id} xs={12} sm={8} md={6} lg={4}>
                  <Link className="listing-link" to={listingsPath({ tag: [tag.id] })}>
                    <Card hoverable size="small" style={{ height: "100%" }}>
                      <Flex vertical gap={4}>
                        <Flex align="center" gap={6}>
                          <TagOutlined style={{ color: token.colorPrimary }} />
                          <Text strong style={{ overflowWrap: "anywhere" }}>{tag.id}</Text>
                        </Flex>
                        <span>
                          <Text strong>{formatCount(tag.regionCount)}</Text>
                          <Text type="secondary"> {tag.regionCount === 1 ? "region" : "regions"}</Text>
                        </span>
                        <Progress
                          percent={Math.round((tag.regionCount / sorted[0].regionCount) * 100)}
                          showInfo={false}
                          size={["100%", 4]}
                          // A full bar is the largest tag, not a job completed, so no success green.
                          status="normal"
                          strokeColor={token.colorPrimary}
                        />
                      </Flex>
                    </Card>
                  </Link>
                </Col>
              ))}
          </Row>
        )}
      </section>

      <section style={{ marginBottom: 32 }}>
        <Flex justify="space-between" align="baseline">
          <Title level={3}>Available to rent</Title>
          <Link to={listingsPath({ type: "rent", occupancy: "unoccupied" })}>See all</Link>
        </Flex>
        <Text type="secondary" style={{ display: "block", marginBottom: 12 }}>
          Vacant leaseholds, highest asking rent first.
        </Text>
        <ListingGrid query={toRent} emptyText="Nothing is vacant to rent right now." span={FEATURED_SPAN} />
      </section>

      <section style={{ marginBottom: 32 }}>
        <Flex justify="space-between" align="baseline">
          <Title level={3}>For sale</Title>
          <Link to={listingsPath({ type: "sale" })}>See all</Link>
        </Flex>
        <Text type="secondary" style={{ display: "block", marginBottom: 12 }}>
          Freeholds with an asking price, highest first.
        </Text>
        <ListingGrid query={forSale} emptyText="Nothing is on the market to buy right now." span={FEATURED_SPAN} />
      </section>

      <Row gutter={[24, 24]}>
        <Col xs={24} lg={14}>
          <Flex justify="space-between" align="baseline">
            <Title level={3}>Recent activity</Title>
            <Link to="/activity">Full feed</Link>
          </Flex>
          <Card size="small" styles={{ body: { padding: "0 12px" } }}>
            <Rows
              loading={activity.status === "loading"}
              items={activity.status === "ready" ? activity.data.events : []}
              itemKey={(_, index) => index}
              emptyText={activity.status === "error"
                ? "Could not load the activity feed."
                : "Nothing has been bought or rented yet."}
              renderItem={(event) => (
                <Flex vertical gap={2}>
                  <Flex justify="space-between" gap={8} wrap>
                    <span>
                      <Text strong>{eventLabel(event.eventType)}</Text>
                      {" "}
                      <Link to={regionPath(event.world, event.worldGuardRegionId)}>{event.worldGuardRegionId}</Link>
                      <Text type="secondary"> in {worldLabel(event.world)}</Text>
                    </span>
                    <Text type="secondary" title={event.eventTime} style={{ fontSize: 12 }}>
                      {formatRelative(event.eventTime)}
                    </Text>
                  </Flex>
                  <Flex gap={12} wrap>
                    <EventTerms event={event} />
                    <EventParties event={event} />
                  </Flex>
                </Flex>
              )}
            />
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Flex justify="space-between" align="baseline">
            <Title level={3}>Auctions</Title>
            <Link to="/auctions">All auctions</Link>
          </Flex>
          <Card size="small" styles={{ body: { padding: "0 12px" } }}>
            <Rows
              loading={auctions.status === "loading"}
              items={auctions.status === "ready" ? auctions.data.auctions : []}
              itemKey={(auction) => `${auction.world.id}/${auction.worldGuardRegionId}`}
              emptyText={auctions.status === "error"
                ? "Could not load auctions."
                : "No auctions are taking bids right now."}
              renderItem={(auction) => (
                <Flex vertical gap={2}>
                  <Flex justify="space-between" gap={8} wrap>
                    <Link to={regionPath(auction.world, auction.worldGuardRegionId)}>
                      {auction.worldGuardRegionId}
                    </Link>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      closes {formatRelative(auction.endDate)}
                    </Text>
                  </Flex>
                  <Flex gap={12} wrap>
                    {auction.highestBid
                      ? <span><Text type="secondary">Bid </Text><Price value={auction.highestBid.amount} /></span>
                      : <span><Text type="secondary">From </Text><Price value={auction.minBid} /></span>}
                    <span><Text type="secondary">Auctioneer </Text><PlayerLink player={auction.auctioneer} /></span>
                  </Flex>
                </Flex>
              )}
            />
          </Card>
        </Col>
      </Row>
    </Page>
  );
}

type TileProps = {
  title: string;
  total?: number;
  occupied?: number;
  occupiedLabel: string;
  note?: string;
};

/** A contract count with the share of it that is taken, as a ring: the market's pulse. */
function OccupancyTile({ title, total, occupied, occupiedLabel, note }: TileProps) {
  const loading = total === undefined || occupied === undefined;
  const percent = !loading && total > 0 ? Math.round((occupied / total) * 100) : 0;
  return (
    <Card size="small" style={{ height: "100%" }}>
      <Flex justify="space-between" align="center" gap={12}>
        <div>
          <Statistic title={title} value={total} loading={loading} />
          {!loading && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {formatCount(occupied)} {occupiedLabel}{note ? `, ${note}` : ""}
            </Text>
          )}
        </div>
        {!loading && (
          <Progress
            type="circle"
            size={56}
            percent={percent}
            format={(value) => <span style={{ fontSize: 12 }}>{value}%</span>}
            aria-label={`${percent}% ${occupiedLabel}`}
          />
        )}
      </Flex>
    </Card>
  );
}
