import { Alert, Card, Empty, Flex, Pagination, Select, Skeleton, Timeline, Typography } from "antd";
import { useState } from "react";
import { Link } from "react-router-dom";
import type { ApiClient } from "../../api/client";
import { regionPath, worldLabel } from "../../api/paths";
import type { components } from "../../api/schema";
import { useQuery } from "../../api/useQuery";
import { EventParties, EventTerms, eventColor } from "../../ui/events";
import { EVENT_TYPES, eventLabel, formatCount, formatDay, formatTime } from "../../ui/format";
import { Page } from "../../ui/Page";
import { WorldSelect } from "../../ui/WorldSelect";
import { defaultWorld, useVisibility } from "../../visibility";

const { Title, Text } = Typography;

type Event = components["schemas"]["ActivityResponse_Event"];

const PAGE_SIZE = 25;

/** The page's events under the day each fell on, in the order they arrived. */
function byDay(events: ReadonlyArray<Event>): Array<{ day: string; events: Event[] }> {
  const groups: Array<{ day: string; events: Event[] }> = [];
  for (const event of events) {
    const day = formatDay(event.eventTime);
    const last = groups[groups.length - 1];
    if (last && last.day === day) last.events.push(event);
    else groups.push({ day, events: [event] });
  }
  return groups;
}

/**
 * The server-wide feed, as a diary: a heading per day, a timeline beneath it. Left
 * alone, the API applies its own ticker set -- sales and lettings -- and this screen
 * sends nothing, so the default is the API's and not a copy of it that could drift.
 */
export function ActivityScreen({ client }: { client: ApiClient }) {
  const visibility = useVisibility();
  // Under a whitelist this page is always of one visible world.
  const [world, setWorld] = useState<string | undefined>(defaultWorld(visibility));
  const [types, setTypes] = useState<string[]>([]);
  const [page, setPage] = useState(1);

  const activity = useQuery(
    () => client.GET("/v1/activity", {
      params: {
        query: {
          page,
          pageSize: PAGE_SIZE,
          ...(world ? { world } : {}),
          ...(types.length > 0 ? { type: types } : {}),
        },
      },
    }),
    [client, page, world, types.join(" ")],
  );

  return (
    <Page width={960}>
      <div style={{ marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>Market activity</Title>
        <Text type="secondary">
          {activity.status === "ready"
            ? `${formatCount(activity.data.totalCount)} events, newest first`
            : "Every sale and letting across the server"}
        </Text>
      </div>

      <Card size="small" style={{ marginBottom: 16 }}>
        <Flex gap={12} wrap align="center">
          <Text type="secondary" style={{ fontSize: 12 }}>Show</Text>
          <Select
            aria-label="Event types"
            mode="multiple"
            allowClear
            placeholder="Sales and lettings"
            value={types}
            onChange={(next) => { setTypes(next); setPage(1); }}
            options={EVENT_TYPES.map((type) => ({ value: type, label: eventLabel(type) }))}
            maxTagCount="responsive"
            style={{ flex: 1, minWidth: 240 }}
          />
          <Text type="secondary" style={{ fontSize: 12 }}>in</Text>
          <WorldSelect
            client={client}
            value={world}
            onChange={(next) => { setWorld(next); setPage(1); }}
            style={{ width: 200 }}
          />
        </Flex>
      </Card>

      {activity.status === "loading" && <Skeleton active paragraph={{ rows: 10 }} title={false} />}
      {activity.status === "error" && <Alert type="error" showIcon message="Could not load the activity feed." />}
      {activity.status === "ready" && activity.data.events.length === 0 && (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No events match." />
      )}
      {activity.status === "ready" && activity.data.events.length > 0 && (
        <Flex vertical gap={8}>
          {byDay(activity.data.events).map((group) => (
            <section key={group.day}>
              <Title level={5} style={{ marginBottom: 12 }}>{group.day}</Title>
              <Timeline
                items={group.events.map((event, index) => ({
                  key: `${event.eventTime}-${index}`,
                  color: eventColor(event.eventType),
                  content: (
                    <Flex vertical gap={2}>
                      <span>
                        <Text type="secondary" style={{ fontSize: 12, fontVariantNumeric: "tabular-nums" }}>
                          {formatTime(event.eventTime)}
                        </Text>
                        {"  "}
                        <Text strong>{eventLabel(event.eventType)}</Text>
                        {" "}
                        <Link to={regionPath(event.world, event.worldGuardRegionId)}>{event.worldGuardRegionId}</Link>
                        <Text type="secondary"> in {worldLabel(event.world)}</Text>
                      </span>
                      <Flex gap={12} wrap>
                        <EventTerms event={event} />
                        <EventParties event={event} />
                      </Flex>
                    </Flex>
                  ),
                }))}
              />
            </section>
          ))}
          {activity.data.totalCount > PAGE_SIZE && (
            <Pagination
              current={page}
              pageSize={PAGE_SIZE}
              total={activity.data.totalCount}
              showSizeChanger={false}
              onChange={setPage}
            />
          )}
        </Flex>
      )}
    </Page>
  );
}
