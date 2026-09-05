import { Alert, Card, Col, Empty, Flex, Pagination, Result, Row, Segmented, Skeleton, Statistic, Typography } from "antd";
import { useState } from "react";
import { Link } from "react-router-dom";
import type { ApiClient } from "../../api/client";
import { regionPath, worldLabel } from "../../api/paths";
import type { components, paths } from "../../api/schema";
import { useQuery } from "../../api/useQuery";
import { formatCount, formatDate, formatDuration } from "../../ui/format";
import { Page } from "../../ui/Page";
import { playerLabel } from "../../ui/PlayerLink";
import { Rows } from "../../ui/Rows";
import { allowsWorld, useVisibility } from "../../visibility";

const { Title, Text } = Typography;

type RegionRef = components["schemas"]["PlayerRegionsResponse_RegionRef"];
type RentedRef = components["schemas"]["PlayerRegionsResponse_RentedRef"];
type Category = NonNullable<NonNullable<paths["/v1/players/regions"]["get"]["parameters"]["query"]>["category"]>;

const CATEGORIES: ReadonlyArray<{ value: Category; label: string }> = [
  { value: "all", label: "Everything" },
  { value: "owned", label: "Owned" },
  { value: "rented", label: "Renting" },
];

const PAGE_SIZE = 20;

type Props = { client: ApiClient; id: string };

/**
 * A player's holdings -- the counts behind `/realty list`, then the list itself.
 *
 * The id in the URL is a UUID, which the database answers alone, so this page keeps
 * working while the module that names players is down; the name is simply absent.
 */
export function PlayerScreen({ client, id }: Props) {
  const [category, setCategory] = useState<Category>("all");
  const [page, setPage] = useState(1);

  const summary = useQuery(
    () => client.GET("/v1/players/summary", { params: { query: { player: id } } }),
    [client, id],
  );
  const holdings = useQuery(
    () => client.GET("/v1/players/regions", {
      params: {
        query: {
          player: id,
          page,
          pageSize: PAGE_SIZE,
          ...(category === "all" ? {} : { category }),
        },
      },
    }),
    [client, id, page, category],
  );

  if (summary.status === "error" && summary.error.httpStatus === 400) {
    return (
      <Page>
        <Result status="warning" title="That is not a player id" subTitle={<Text code>{id}</Text>} />
      </Page>
    );
  }
  if (summary.status === "error") {
    return <Page><Alert type="error" showIcon message="Could not load this player." /></Page>;
  }

  const player = summary.status === "ready" ? summary.data.player : undefined;

  return (
    <Page width={960}>
      <Flex vertical gap={2} style={{ marginBottom: 20 }}>
        <Title level={1} style={{ margin: 0 }}>
          {player ? playerLabel(player) : <Skeleton.Input active size="large" />}
        </Title>
        <Text type="secondary" copyable={{ text: id }} style={{ fontFamily: "monospace", fontSize: 12 }}>
          {id}
        </Text>
      </Flex>

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic
              title="Titles held"
              loading={summary.status === "loading"}
              value={summary.status === "ready" ? summary.data.titleHeld : undefined}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic
              title="Landlord of"
              loading={summary.status === "loading"}
              value={summary.status === "ready" ? summary.data.landlordOf : undefined}
            />
            {summary.status === "ready" && summary.data.landlordOf > 0 && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {formatCount(summary.data.occupiedLandlordOf)} let,{" "}
                {formatCount(summary.data.landlordOf - summary.data.occupiedLandlordOf)} vacant
              </Text>
            )}
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic
              title="Renting"
              loading={summary.status === "loading"}
              value={summary.status === "ready" ? summary.data.renting : undefined}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic
              title="Authority over"
              loading={summary.status === "loading"}
              value={summary.status === "ready" ? summary.data.authorityOver : undefined}
            />
          </Card>
        </Col>
      </Row>

      <Flex justify="space-between" align="center" wrap gap={12} style={{ marginBottom: 12 }}>
        <Title level={3} style={{ margin: 0 }}>Holdings</Title>
        <Segmented
          aria-label="Holdings"
          value={category}
          onChange={(value) => { setCategory(value as Category); setPage(1); }}
          options={CATEGORIES.map((entry) => ({ value: entry.value, label: entry.label }))}
        />
      </Flex>

      {holdings.status === "loading" && <Skeleton active paragraph={{ rows: 6 }} title={false} />}
      {holdings.status === "error" && (
        <Alert type="error" showIcon message="Could not load this player's holdings." />
      )}
      {holdings.status === "ready" && (
        <Flex vertical gap={16}>
          {holdings.data.totalCount === 0 && (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Nothing in this category." />
          )}
          {category === "all" && (
            // The three lists share one page offset, exactly as `/realty list` pages
            // them in game: a page boundary can fall inside a category, so a page shows
            // whichever groups land on it rather than three lists paged separately.
            <>
              <Group title="Owned" regions={holdings.data.owned ?? []} />
              <Group title="Landlord of" regions={holdings.data.landlord ?? []} />
              <Group title="Renting" regions={holdings.data.rented ?? []} />
            </>
          )}
          {category === "owned" && (
            <Group title="Owned" regions={(holdings.data.regions ?? []) as RegionRef[]} />
          )}
          {category === "rented" && (
            <Group title="Renting" regions={(holdings.data.regions ?? []) as RentedRef[]} />
          )}
          {holdings.data.totalCount > PAGE_SIZE && (
            <Pagination
              current={page}
              pageSize={PAGE_SIZE}
              total={holdings.data.totalCount}
              showSizeChanger={false}
              onChange={setPage}
            />
          )}
        </Flex>
      )}
    </Page>
  );
}

function Group({ title, regions: all }: { title: string; regions: ReadonlyArray<RegionRef | RentedRef> }) {
  const visibility = useVisibility();
  // The counts above are the API's and stay server-wide; the list is this site's.
  const regions = all.filter((entry) => allowsWorld(visibility, entry.world));
  if (regions.length === 0) return null;
  return (
    <Card size="small" title={title} styles={{ body: { padding: "0 12px" } }}>
      <Rows
        items={regions}
        itemKey={(entry) => `${entry.world.id}/${entry.worldGuardRegionId}`}
        emptyText="Nothing here."
        renderItem={(entry) => (
          <Flex justify="space-between" gap={12} wrap>
            <span>
              <Link to={regionPath(entry.world, entry.worldGuardRegionId)}>{entry.worldGuardRegionId}</Link>
              <Text type="secondary"> in {worldLabel(entry.world)}</Text>
            </span>
            {"endDate" in entry && entry.endDate && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                ends {formatDate(entry.endDate)}
                {entry.secondsRemaining !== null && entry.secondsRemaining !== undefined
                  && `, ${formatDuration(entry.secondsRemaining)} left`}
              </Text>
            )}
          </Flex>
        )}
      />
    </Card>
  );
}
