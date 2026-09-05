import { Alert, Card, Col, Empty, Row, Skeleton } from "antd";
import type { components } from "../api/schema";
import type { QueryState } from "../api/useQuery";
import { ListingCard } from "./ListingCard";

type SearchResponse = components["schemas"]["SearchResponse"];

type Span = { xs: number; sm: number; lg: number; xl?: number };

type Props = {
  query: QueryState<SearchResponse>;
  /** What "nothing" means here: no matches, nothing vacant -- the caller knows. */
  emptyText: string;
  span?: Span;
  skeletons?: number;
};

const DEFAULT_SPAN: Span = { xs: 24, sm: 12, lg: 8 };

/** The three states every listing grid has, so each screen does not redraw them. */
export function ListingGrid({ query, emptyText, span = DEFAULT_SPAN, skeletons = 8 }: Props) {
  if (query.status === "loading") {
    return (
      <Row gutter={[16, 16]} aria-busy="true">
        {Array.from({ length: skeletons }, (_, index) => (
          <Col key={index} {...span}>
            <Card size="small"><Skeleton active paragraph={{ rows: 2 }} /></Card>
          </Col>
        ))}
      </Row>
    );
  }
  if (query.status === "error") {
    return <Alert type="error" showIcon message="Could not load listings." />;
  }
  if (query.data.results.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />;
  }
  return (
    <Row gutter={[16, 16]}>
      {query.data.results.map((listing) => (
        <Col key={`${listing.world.id}/${listing.worldGuardRegionId}`} {...span}>
          <ListingCard listing={listing} />
        </Col>
      ))}
    </Row>
  );
}
