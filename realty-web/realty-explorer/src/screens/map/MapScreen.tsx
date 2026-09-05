import { Alert, Card, Flex, Progress, Select, Skeleton, Typography } from "antd";
import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import type { ApiClient } from "../../api/client";
import { regionPath, worldLabel } from "../../api/paths";
import type { components } from "../../api/schema";
import { TTL, remembered } from "../../api/remembered";
import { useQuery } from "../../api/useQuery";
import type { MapConfig } from "../../config";
import { mapIdFor } from "../../map/blueMap";
import { RegionMap } from "../../map/lazyRegionMap";
import { LEGEND, hiddenByDefault } from "../../map/plotStyle";
import { useWorldMap } from "../../map/worldMap";
import { useCurrency } from "../../currency";
import { useVisibility, visibleWorlds } from "../../visibility";
import { Page } from "../../ui/Page";

const { Title, Text } = Typography;

type WorldRef = components["schemas"]["WorldRef"];

/**
 * Every registered plot in one world, drawn over the server's own render of it.
 *
 * <p>The world rides in the query string so a link to a district is a link someone can
 * send. It is a single world rather than an optional filter because a map has to be of
 * somewhere: two worlds share one coordinate system and would draw on top of each
 * other.</p>
 *
 * <p>A built-up world is thousands of plots, read a page at a time, so the map is drawn
 * as they arrive rather than after the last one, with no progress count or caveat
 * beside it: how the register was read is the operator's concern, not a visitor's.
 * Only a world that cannot be read at all is said so, since an empty map would
 * otherwise read as an empty world.</p>
 */
export function MapScreen({ client, map }: { client: ApiClient; map: MapConfig }) {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();

  const worlds = useQuery(
    () => remembered(client, "worlds", TTL.worlds, () => client.GET("/v1/worlds", {})), [client]);
  const visibility = useVisibility();
  const currency = useCurrency();
  // A world the operator has kept off the site is not a world to offer a map of.
  const known: WorldRef[] = worlds.status === "ready" && Array.isArray(worlds.data)
    ? visibleWorlds(visibility, worlds.data)
    : [];
  // The first world stands in until someone picks one, so the screen opens on a map
  // rather than on a prompt to choose.
  const world = params.get("world") ?? (known.length > 0 ? worldLabel(known[0]) : undefined);

  const drawn = useWorldMap(client, world);

  // The kinds of plot switched off, by legend label. Per visit rather than in the
  // URL: which colours someone has dimmed to look at the rest is a way of looking,
  // not a place to send anyone.
  const [hidden, setHidden] = useState<ReadonlySet<string>>(hiddenByDefault);
  const toggle = useCallback((label: string) => setHidden((current) => {
    const next = new Set(current);
    if (!next.delete(label)) next.add(label);
    return next;
  }), []);

  const tiles = useMemo(
    () => (map.baseUrl && world ? { baseUrl: map.baseUrl, mapId: mapIdFor(map, world) } : null),
    [map, world],
  );

  const openRegion = useCallback((regionId: string) => {
    const reference = known.find((entry) => worldLabel(entry) === world) ?? { id: world ?? "" };
    navigate(regionPath(reference, regionId));
  }, [known, navigate, world]);

  const nothingReadYet = drawn.read === 0 && !drawn.done;

  return (
    <Page width={1600}>
      <Flex align="center" justify="space-between" wrap gap={12} style={{ marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>Map</Title>
        <Select
          aria-label="World"
          placeholder="World"
          showSearch
          optionFilterProp="label"
          loading={worlds.status === "loading"}
          value={world}
          onChange={(next: string) => setParams({ world: next })}
          options={known.map((entry) => ({ value: worldLabel(entry), label: worldLabel(entry) }))}
          style={{ minWidth: 220 }}
        />
      </Flex>

      <Card
        size="small"
        styles={{ body: { padding: 0 } }}
        style={{ overflow: "hidden", marginBottom: 12 }}
      >
        <div style={{ position: "relative", height: "min(72vh, 900px)", minHeight: 360 }}>
          <ReadingBar read={drawn.read} total={drawn.total} done={drawn.done} />
          {/* Also while the world is unknown: the map would otherwise flash empty
              between the world list arriving and its regions doing so. */}
          {nothingReadYet || !world
            ? <Skeleton.Node active style={{ width: "100%", height: "100%" }} />
            : (
              <Suspense fallback={<Skeleton.Node active style={{ width: "100%", height: "100%" }} />}>
                <RegionMap
                  // Keyed by world: a new world is new ground, new plots and a new
                  // view, and rebuilding is both simpler and less to get wrong than
                  // migrating one.
                  key={world}
                  tiles={tiles}
                  footprints={drawn.footprints}
                  market={drawn.market}
                  hidden={hidden}
                  settled={drawn.done}
                  currency={currency}
                  onSelect={openRegion}
                />
              </Suspense>
            )}
        </div>
      </Card>

      {/* The key is also the switches: press a kind of plot to hide it, press again to
          bring it back. */}
      <Flex gap={16} wrap align="center" style={{ marginBottom: 12 }}>
        {LEGEND.map((entry) => {
          const shown = !hidden.has(entry.label);
          return (
            <button
              key={entry.label}
              type="button"
              aria-pressed={shown}
              title={shown ? `Hide ${entry.label.toLowerCase()}` : `Show ${entry.label.toLowerCase()}`}
              onClick={() => toggle(entry.label)}
              style={{
                display: "inline-flex", alignItems: "center", gap: 6,
                padding: "2px 4px", border: "none", borderRadius: 4, background: "transparent",
                cursor: "pointer", font: "inherit", opacity: shown ? 1 : 0.45,
              }}
            >
              <span
                aria-hidden="true"
                style={{
                  width: 12, height: 12, borderRadius: 3,
                  background: shown ? entry.colour : "transparent",
                  opacity: shown ? 0.55 : 1, border: `1px solid ${entry.colour}`,
                }}
              />
              <Text type="secondary" style={{ fontSize: 13, textDecoration: shown ? "none" : "line-through" }}>
                {entry.label}
              </Text>
            </button>
          );
        })}
      </Flex>

      {drawn.error && (
        <Alert
          type="error"
          showIcon
          title="This world's plots could not be read"
          description={drawn.error.message ?? "The register did not answer."}
        />
      )}
    </Page>
  );
}

/**
 * A hairline along the top of the map that fills as the world's regions are read,
 * then fades. The only sign of progress on the page: enough to say the map is still
 * filling in, without a number or a caption over it.
 */
function ReadingBar({ read, total, done }: { read: number; total: number; done: boolean }) {
  // Kept for a moment after the reading finishes, so the bar is seen to complete
  // rather than vanish from three quarters of the way.
  const [shown, setShown] = useState(!done);
  useEffect(() => {
    if (!done) {
      setShown(true);
      return;
    }
    const timer = setTimeout(() => setShown(false), 600);
    return () => clearTimeout(timer);
  }, [done]);
  if (!shown) return null;

  // A sliver before the first page answers, so the bar is there to be watched.
  const percent = done ? 100 : total > 0 ? Math.max(4, Math.round((read / total) * 100)) : 4;
  return (
    <Progress
      percent={percent}
      status={done ? "success" : "active"}
      showInfo={false}
      size={["100%", 2]}
      strokeLinecap="butt"
      trailColor="transparent"
      aria-label="Reading the world's regions"
      style={{
        position: "absolute", top: 0, left: 0, right: 0, zIndex: 500, margin: 0, lineHeight: 0,
        opacity: done ? 0 : 1, transition: "opacity 0.4s ease 0.2s", pointerEvents: "none",
      }}
    />
  );
}
