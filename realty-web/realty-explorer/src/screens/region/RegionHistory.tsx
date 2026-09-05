import { Alert, Button, Empty, Flex, Skeleton, Timeline, Typography } from "antd";
import { useEffect, useState } from "react";
import type { ApiClient } from "../../api/client";
import type { components } from "../../api/schema";
import { EventParties, EventTerms, eventColor } from "../../ui/events";
import { eventLabel, formatCount, formatDate, formatRelative } from "../../ui/format";

const { Text } = Typography;

type Entry = components["schemas"]["HistoryResponse_Entry"];

const PAGE_SIZE = 20;

/** How tall the timeline may grow before it scrolls, so a busy plot does not push the page out. */
const MAX_HEIGHT = 480;

type Props = { client: ApiClient; world: string; region: string };

type Loaded = { entries: Entry[]; totalCount: number };

/**
 * Everything the register recorded about this region, newest first -- the same
 * `/realty history` any player can run, laid out as a timeline.
 *
 * Pages are appended rather than swapped: a history is read downwards, and a visitor
 * who has scrolled to the oldest event on screen wants the ones before it beneath.
 * The parent keys this component by region, so a new region starts from nothing.
 */
export function RegionHistory({ client, world, region }: Props) {
  const [page, setPage] = useState(1);
  const [loaded, setLoaded] = useState<Loaded>({ entries: [], totalCount: 0 });
  const [status, setStatus] = useState<"loading" | "error" | "ready">("loading");

  useEffect(() => {
    let cancelled = false;
    setStatus("loading");

    client.GET("/v1/region/history", {
      params: { query: { world, region, page, pageSize: PAGE_SIZE } },
    }).then(({ data, error }) => {
      if (cancelled) return;
      if (error || !data) {
        setStatus("error");
        return;
      }
      setLoaded((previous) => ({
        entries: page === 1 ? data.entries : [...previous.entries, ...data.entries],
        totalCount: data.totalCount,
      }));
      setStatus("ready");
    });

    return () => {
      cancelled = true;
    };
  }, [client, world, region, page]);

  if (status === "loading" && loaded.entries.length === 0) {
    return <Skeleton active paragraph={{ rows: 6 }} title={false} />;
  }
  if (status === "error" && loaded.entries.length === 0) {
    return <Alert type="error" showIcon message="Could not load this region's history." />;
  }
  if (loaded.entries.length === 0) {
    // A region nobody has traded is a 200 with no entries. That is a fact about the plot.
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Nothing has been recorded for this region." />;
  }

  const remaining = loaded.totalCount - loaded.entries.length;

  return (
    <Flex vertical gap={12}>
      <Text type="secondary" style={{ fontSize: 12 }}>
        {formatCount(loaded.totalCount)} {loaded.totalCount === 1 ? "event" : "events"} recorded
      </Text>
      <div style={{ maxHeight: MAX_HEIGHT, overflowY: "auto", paddingTop: 4 }}>
        <Timeline
          items={loaded.entries.map((entry, index) => ({
            key: `${entry.eventTime}-${index}`,
            color: eventColor(entry.eventType),
            content: (
              <Flex vertical gap={2}>
                <span>
                  <Text strong>{eventLabel(entry.eventType)}</Text>
                  <Text type="secondary" title={entry.eventTime}>
                    {" · "}{formatDate(entry.eventTime)} ({formatRelative(entry.eventTime)})
                  </Text>
                </span>
                <Flex gap={12} wrap>
                  <EventTerms event={entry} />
                  <EventParties event={entry} />
                </Flex>
              </Flex>
            ),
          }))}
        />
      </div>
      {status === "error" && <Alert type="error" showIcon message="Could not load older events." />}
      {remaining > 0 && (
        <Button onClick={() => setPage(page + 1)} loading={status === "loading"} block>
          Show older events ({formatCount(remaining)} more)
        </Button>
      )}
    </Flex>
  );
}
