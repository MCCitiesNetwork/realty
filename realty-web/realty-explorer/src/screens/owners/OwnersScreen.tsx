import { Alert, Avatar, Card, Flex, Pagination, Progress, Typography, theme } from "antd";
import { useState } from "react";
import type { ApiClient } from "../../api/client";
import { useQuery } from "../../api/useQuery";
import { formatCount } from "../../ui/format";
import { Page } from "../../ui/Page";
import { PlayerLink } from "../../ui/PlayerLink";
import { Rows } from "../../ui/Rows";

const { Title, Text } = Typography;

const PAGE_SIZE = 25;

/** The medals: a colour for the first three places, the theme's own for the rest. */
const MEDALS: Readonly<Record<number, string>> = { 1: "#d4a017", 2: "#9aa0a6", 3: "#b87333" };

/**
 * Title holders ranked by how many plots they hold -- the one ranking the API offers.
 * Each bar is the holder's share of the leader's count, so the shape of the market
 * shows at a glance: one or two large estates, then a long tail.
 */
export function OwnersScreen({ client }: { client: ApiClient }) {
  const { token } = theme.useToken();
  const [page, setPage] = useState(1);
  const owners = useQuery(
    () => client.GET("/v1/leaderboard/owners", { params: { query: { page, pageSize: PAGE_SIZE } } }),
    [client, page],
  );

  const rows = owners.status === "ready" ? owners.data.owners : [];
  // The bars are relative to the largest holder on this page. On the first page that is
  // the leader outright; on later pages it is the first row, and the caption says so.
  const top = rows[0]?.plotCount ?? 0;

  return (
    <Page width={800}>
      <div style={{ marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>Largest title holders</Title>
        <Text type="secondary">
          {owners.status === "ready"
            ? `${formatCount(owners.data.totalCount)} players hold at least one plot`
            : "Players ranked by the plots they hold"}
        </Text>
      </div>

      {owners.status === "error"
        ? <Alert type="error" showIcon message="Could not load the leaderboard." />
        : (
          <Card size="small" styles={{ body: { padding: "0 16px" } }}>
            <Rows
              loading={owners.status === "loading"}
              items={rows}
              itemKey={(owner) => owner.player.id}
              emptyText="Nobody holds a plot yet."
              renderItem={(owner) => (
                <Flex align="center" gap={16}>
                  <Avatar
                    size={36}
                    style={{
                      background: MEDALS[owner.rank] ?? token.colorFillSecondary,
                      color: MEDALS[owner.rank] ? "#fff" : token.colorText,
                      fontWeight: 600,
                      flexShrink: 0,
                    }}
                  >
                    {owner.rank}
                  </Avatar>
                  <Flex vertical style={{ flex: 1, minWidth: 0 }} gap={2}>
                    <Flex justify="space-between" gap={12} align="baseline">
                      <span style={{ fontSize: 15 }}><PlayerLink player={owner.player} /></span>
                      <Text style={{ fontVariantNumeric: "tabular-nums", whiteSpace: "nowrap" }}>
                        <Text strong>{formatCount(owner.plotCount)}</Text>
                        <Text type="secondary"> {owner.plotCount === 1 ? "plot" : "plots"}</Text>
                      </Text>
                    </Flex>
                    <Progress
                      percent={top > 0 ? Math.round((owner.plotCount / top) * 100) : 0}
                      showInfo={false}
                      size={["100%", 6]}
                      strokeColor={MEDALS[owner.rank] ?? token.colorPrimary}
                    />
                  </Flex>
                </Flex>
              )}
            />
          </Card>
        )}

      {owners.status === "ready" && (
        <Flex justify="space-between" align="center" wrap gap={12} style={{ marginTop: 16 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {page > 1 && rows.length > 0
              ? `Bars are relative to rank ${rows[0].rank}, the largest holder on this page.`
              : "Bars are relative to the largest holder."}
          </Text>
          {owners.data.totalCount > PAGE_SIZE && (
            <Pagination
              current={page}
              pageSize={PAGE_SIZE}
              total={owners.data.totalCount}
              showSizeChanger={false}
              onChange={setPage}
            />
          )}
        </Flex>
      )}
    </Page>
  );
}
