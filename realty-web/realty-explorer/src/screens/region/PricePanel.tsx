import { Alert, Card, Descriptions, Flex, Statistic, Tag, Typography } from "antd";
import type { components } from "../../api/schema";
import { formatDate, formatDuration, formatRelative, humanise } from "../../ui/format";
import { PlayerLink } from "../../ui/PlayerLink";
import { Price } from "../../ui/Price";

const { Text } = Typography;

type Region = components["schemas"]["RegionResponse"];

const yesNo = (value: boolean) => (value ? <Tag color="green">Yes</Tag> : <Tag>No</Tag>);

/**
 * The commercial terms, as a listing site puts them beside the photograph: the price,
 * whether the door is open, and when the lease turns over. One card per contract the
 * region carries -- a region can be both a freehold and a leasehold.
 */
export function PricePanel({ region }: { region: Region }) {
  const { freehold, leasehold, auction } = region;
  if (!freehold && !leasehold && !auction) {
    return (
      <Card size="small">
        <Text type="secondary">This region carries no contract.</Text>
      </Card>
    );
  }

  return (
    <Flex vertical gap={16}>
      {freehold && (
        <Card size="small" title="Freehold">
          <Flex vertical gap={8}>
            {freehold.price === null || freehold.price === undefined
              ? <Text type="secondary" style={{ fontSize: 16 }}>Not for sale</Text>
              : <Price value={freehold.price} size="hero" />}
            <Descriptions size="small" column={1} colon={false} items={[
              { key: "offers", label: "Accepting offers", children: yesNo(freehold.acceptingOffers) },
              ...(freehold.lastSoldPrice !== null && freehold.lastSoldPrice !== undefined
                ? [{ key: "last", label: "Last sold for", children: <Price value={freehold.lastSoldPrice} /> }]
                : []),
            ]} />
          </Flex>
        </Card>
      )}

      {leasehold && (
        <Card size="small" title="Leasehold">
          <Flex vertical gap={8}>
            <Price value={leasehold.price} size="hero" per={formatDuration(leasehold.durationSeconds)} />
            {leasehold.terminationEffectiveDate && (
              <Alert
                type="warning"
                showIcon
                message={`Notice given${leasehold.terminatedByRole ? ` by the ${humanise(leasehold.terminatedByRole).toLowerCase()}` : ""}`}
                description={`The lease ends ${formatDate(leasehold.terminationEffectiveDate)}.`}
              />
            )}
            <Descriptions size="small" column={1} colon={false} items={[
              { key: "tenants", label: "Accepting tenants", children: yesNo(leasehold.acceptingTenants) },
              ...(leasehold.startDate
                ? [{ key: "start", label: "Lease began", children: formatDate(leasehold.startDate) }]
                : []),
              ...(leasehold.endDate
                ? [{ key: "end", label: "Lease ends", children: <>{formatDate(leasehold.endDate)} <Text type="secondary">({formatRelative(leasehold.endDate)})</Text></> }]
                : []),
              {
                key: "extensions",
                label: "Extensions",
                children: leasehold.maxExtensions === null || leasehold.maxExtensions === undefined
                  ? `${leasehold.extensionsUsed ?? 0} used, unlimited`
                  : `${leasehold.extensionsUsed ?? 0} of ${leasehold.maxExtensions} used`,
              },
            ]} />
          </Flex>
        </Card>
      )}

      {auction && (
        <Card size="small" title="Under the hammer">
          <Flex vertical gap={8}>
            {auction.highestBid
              ? (
                <Statistic
                  title={<>Highest bid, from <PlayerLink player={auction.highestBid.bidder} /></>}
                  valueRender={() => <Price value={auction.highestBid!.amount} size="hero" />}
                />
              )
              : (
                <Statistic title="No bids yet · opens at" valueRender={() => <Price value={auction.minBid} size="hero" />} />
              )}
            {auction.endDate && (
              <Statistic.Timer
                type="countdown"
                title={`Bidding closes ${formatDate(auction.endDate)}`}
                value={new Date(auction.endDate).getTime()}
                format="D[d] HH:mm:ss"
              />
            )}
            <Descriptions size="small" column={1} colon={false} items={[
              { key: "auctioneer", label: "Auctioneer", children: <PlayerLink player={auction.auctioneer} /> },
              { key: "min", label: "Minimum bid", children: <Price value={auction.minBid} /> },
              { key: "step", label: "Minimum raise", children: <Price value={auction.minStep} /> },
              { key: "window", label: "Bidding window", children: `${formatDuration(auction.biddingDurationSeconds)} after the last bid` },
              { key: "pay", label: "Time to pay", children: formatDuration(auction.paymentDurationSeconds) },
              { key: "started", label: "Started", children: formatDate(auction.startDate) },
            ]} />
          </Flex>
        </Card>
      )}
    </Flex>
  );
}
