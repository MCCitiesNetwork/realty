import { Alert, Badge, Card, Col, Descriptions, Empty, Flex, Pagination, Row, Segmented, Skeleton, Statistic, Typography } from "antd";
import { useState } from "react";
import { Link } from "react-router-dom";
import type { ApiClient } from "../../api/client";
import { regionPath, worldLabel } from "../../api/paths";
import type { components, paths } from "../../api/schema";
import { useQuery } from "../../api/useQuery";
import { useCurrency } from "../../currency";
import { formatCount, formatDate, formatDuration, formatPrice } from "../../ui/format";
import { Page } from "../../ui/Page";
import { PlayerLink } from "../../ui/PlayerLink";
import { Price } from "../../ui/Price";
import { WorldSelect } from "../../ui/WorldSelect";
import { defaultWorld, useVisibility } from "../../visibility";

const { Title, Text } = Typography;

type Auction = components["schemas"]["AuctionsResponse_Entry"];
type Sort = NonNullable<NonNullable<paths["/v1/auctions"]["get"]["parameters"]["query"]>["sort"]>;

const SORTS: ReadonlyArray<{ value: Sort; label: string }> = [
  { value: "ending_soon", label: "Ending soon" },
  { value: "highest_bid", label: "Highest bid" },
];

const PAGE_SIZE = 12;

/**
 * Every auction taking bids, one card each, with the clock the API computed for it
 * counting down live. The ribbon carries the standing bid -- the one number a bidder
 * glances at -- and says so plainly when there is none yet.
 */
export function AuctionsScreen({ client }: { client: ApiClient }) {
  const [sort, setSort] = useState<Sort>("ending_soon");
  const visibility = useVisibility();
  // Under a whitelist this page is always of one visible world.
  const [world, setWorld] = useState<string | undefined>(defaultWorld(visibility));
  const [page, setPage] = useState(1);

  const auctions = useQuery(
    () => client.GET("/v1/auctions", {
      params: {
        query: {
          page,
          pageSize: PAGE_SIZE,
          ...(sort === "ending_soon" ? {} : { sort }),
          ...(world ? { world } : {}),
        },
      },
    }),
    [client, page, sort, world],
  );

  return (
    <Page>
      <div style={{ marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>Auctions</Title>
        <Text type="secondary">
          {auctions.status === "ready"
            ? `${formatCount(auctions.data.totalCount)} taking bids`
            : "Regions under the hammer"}
        </Text>
      </div>

      <Card size="small" style={{ marginBottom: 16 }}>
        <Flex gap={12} wrap align="center">
          <Segmented
            aria-label="Sort"
            value={sort}
            onChange={(value) => { setSort(value as Sort); setPage(1); }}
            options={SORTS.map((entry) => ({ value: entry.value, label: entry.label }))}
          />
          <WorldSelect
            client={client}
            value={world}
            onChange={(next) => { setWorld(next); setPage(1); }}
            style={{ width: 200 }}
          />
        </Flex>
      </Card>

      {auctions.status === "loading" && (
        <Row gutter={[16, 16]}>
          {Array.from({ length: 3 }, (_, index) => (
            <Col key={index} xs={24} md={12} xl={8}><Card size="small"><Skeleton active /></Card></Col>
          ))}
        </Row>
      )}
      {auctions.status === "error" && <Alert type="error" showIcon message="Could not load auctions." />}
      {auctions.status === "ready" && auctions.data.auctions.length === 0 && (
        <Card>
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              <Flex vertical gap={4} align="center">
                <span>No auctions are taking bids right now.</span>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  A title holder starts one in game with <Text code>/realty auction</Text>.
                </Text>
              </Flex>
            }
          />
        </Card>
      )}
      {auctions.status === "ready" && auctions.data.auctions.length > 0 && (
        <Flex vertical gap={16}>
          <Row gutter={[16, 16]}>
            {auctions.data.auctions.map((auction) => (
              <Col key={`${auction.world.id}/${auction.worldGuardRegionId}`} xs={24} md={12} xl={8}>
                <AuctionCard auction={auction} />
              </Col>
            ))}
          </Row>
          {auctions.data.totalCount > PAGE_SIZE && (
            <Flex justify="center">
              <Pagination
                current={page}
                pageSize={PAGE_SIZE}
                total={auctions.data.totalCount}
                showSizeChanger={false}
                onChange={setPage}
              />
            </Flex>
          )}
        </Flex>
      )}
    </Page>
  );
}

function AuctionCard({ auction }: { auction: Auction }) {
  const currency = useCurrency();
  const bid = auction.highestBid;
  return (
    <Badge.Ribbon
      text={bid ? `Leading bid ${formatPrice(bid.amount, currency)}` : "No bids yet"}
      color={bid ? "green" : undefined}
      rootClassName="listing-ribbon"
    >
      <Card
        size="small"
        style={{ height: "100%" }}
        title={
          <Flex vertical>
            <Link to={regionPath(auction.world, auction.worldGuardRegionId)}>{auction.worldGuardRegionId}</Link>
            <Text type="secondary" style={{ fontSize: 12, fontWeight: 400 }}>{worldLabel(auction.world)}</Text>
          </Flex>
        }
      >
        <Flex vertical gap={12}>
          <Statistic.Timer
            type="countdown"
            title="Bidding closes in"
            value={new Date(auction.endDate).getTime()}
            format="D[d] HH:mm:ss"
          />
          <Descriptions size="small" column={1} colon={false} items={[
            ...(bid
              ? [{
                key: "bid",
                label: "Standing bid",
                children: <><Price value={bid.amount} /> <Text type="secondary">by <PlayerLink player={bid.bidder} />, {formatDate(bid.bidTime)}</Text></>,
              }]
              : [{ key: "opens", label: "Opens at", children: <Price value={auction.minBid} /> }]),
            { key: "step", label: "Minimum raise", children: <Price value={auction.minStep} /> },
            {
              key: "bidders",
              label: "Bidders",
              children: `${formatCount(auction.bidderCount)} ${auction.bidderCount === 1 ? "bidder" : "bidders"}`,
            },
            { key: "auctioneer", label: "Auctioneer", children: <PlayerLink player={auction.auctioneer} /> },
            { key: "closes", label: "Closes", children: formatDate(auction.endDate) },
            { key: "window", label: "Each bid extends", children: `${formatDuration(auction.biddingDurationSeconds)} from the last bid` },
          ]} />
        </Flex>
      </Card>
    </Badge.Ribbon>
  );
}
