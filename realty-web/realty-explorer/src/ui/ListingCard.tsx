import { EnvironmentOutlined } from "@ant-design/icons";
import { Badge, Card, Flex, Typography } from "antd";
import { Link } from "react-router-dom";
import { regionPath, worldLabel } from "../api/paths";
import type { components } from "../api/schema";
import { prefetchViewer } from "../viewer/lazyViewer";
import { formatDuration } from "./format";
import { Price } from "./Price";
import { marketState, stateStyle } from "./StateTag";

const { Text } = Typography;

type SearchResult = components["schemas"]["SearchResponse_Result"];

/**
 * One listing in a grid, in the order a listing site reads: what it costs, what it is,
 * where it is. The state rides on a ribbon, as "new" or "sold" does on a property card.
 *
 * There is no photograph. A captured preview exists for few regions and is a 3D scene
 * rather than an image, and a stock picture would show nothing true about the plot --
 * so the price takes the top line instead. The whole card is the link.
 */
export function ListingCard({ listing }: { listing: SearchResult }) {
  const leasehold = listing.contractType === "leasehold";
  const state = stateStyle(marketState(listing.state, leasehold ? null : listing.price));
  const caption = leasehold ? "Rent" : listing.price === null ? "Freehold" : "Asking price";
  // The term is what the rent buys; without it "200" is a number without a unit.
  const term = leasehold && listing.durationSeconds !== null && listing.durationSeconds !== undefined
    ? formatDuration(listing.durationSeconds)
    : undefined;

  return (
    <Link
      className="listing-link"
      to={regionPath(listing.world, listing.worldGuardRegionId)}
      // The viewer chunk is ~12 MB and is what makes a region page feel slow on a cold
      // cache. Pointing at a card is signal enough to start fetching it.
      onMouseEnter={prefetchViewer}
      onFocus={prefetchViewer}
    >
      <Badge.Ribbon text={state.label} color={state.color} rootClassName="listing-ribbon">
        <Card hoverable size="small" style={{ height: "100%" }}>
          <Flex vertical gap={2}>
            <Text type="secondary" style={{ fontSize: 11, textTransform: "uppercase", letterSpacing: "0.06em" }}>
              {caption}
            </Text>
            <Price value={listing.price} size="card" per={term} />
            <Text strong style={{ fontSize: 16, marginTop: 6, overflowWrap: "anywhere" }}>
              {listing.worldGuardRegionId}
            </Text>
            <Text type="secondary" style={{ fontSize: 13 }}>
              <EnvironmentOutlined /> {worldLabel(listing.world)}
              <span aria-hidden="true"> · </span>
              {leasehold ? "Leasehold" : "Freehold"}
            </Text>
          </Flex>
        </Card>
      </Badge.Ribbon>
    </Link>
  );
}
