import { Button, Flex, Segmented, Select, Skeleton, Tag, Typography } from "antd";
import { useState } from "react";
import type { ApiClient } from "../api/client";
import { TTL, remembered } from "../api/remembered";
import { useQuery } from "../api/useQuery";
import { formatCount } from "./format";

const { Text } = Typography;

/** A tag question as the search API takes it: some of these, or all of them, and none of those. */
export type TagQuery = {
  tags: string[];
  excluded: string[];
  /** Every one of `tags` rather than any; meaningless without them. */
  matchAll: boolean;
};

export const NO_TAGS: TagQuery = { tags: [], excluded: [], matchAll: false };

/** How many tags the simple mode offers as chips: the most-used ones. */
const QUICK_TAGS = 8;

type Props = {
  client: ApiClient;
  value: TagQuery;
  onChange: (query: TagQuery) => void;
  size?: "small" | "middle" | "large";
};

/**
 * The tag filter, in two modes.
 *
 * <p>Simple is a row of the most-used tags as chips: press one to ask for it, press
 * another to widen to either. That is the whole question most visitors have.
 * Advanced is the full list, searchable, with the two things chips cannot say: that a
 * plot must carry every tag named rather than any, and that some tags rule a plot out.
 * The control opens in whichever mode can show the question it was given, so a link
 * with an exclusion in it opens advanced; going back to simple drops what simple
 * cannot show.</p>
 *
 * <p>Tags are raw ids with their region counts -- exactly what `/v1/tags` reports.
 * Their display names live in the plugin's config, which the API does not read, so
 * none is dressed up here either.</p>
 */
export function TagFilter({ client, value, onChange, size = "middle" }: Props) {
  const tags = useQuery(() => remembered(client, "tags", TTL.tags, () => client.GET("/v1/tags", {})), [client]);
  const ready = tags.status === "ready" && Array.isArray(tags.data);
  const all = ready ? [...tags.data].sort((a, b) => b.regionCount - a.regionCount) : [];
  const quick = all.slice(0, QUICK_TAGS);
  const quickIds = new Set(quick.map((tag) => tag.id));

  const [advancedChosen, setAdvancedChosen] = useState(false);
  const needsAdvanced = ready
    && (value.excluded.length > 0 || value.matchAll || value.tags.some((tag) => !quickIds.has(tag)));
  const advanced = advancedChosen || needsAdvanced;

  if (tags.status === "loading") return <Skeleton.Input active size="small" block />;
  if (all.length === 0) return <Text type="secondary">No region carries a tag yet.</Text>;

  const option = (tag: { id: string; regionCount: number }) =>
    ({ value: tag.id, label: `${tag.id} (${formatCount(tag.regionCount)})` });

  if (!advanced) {
    return (
      <Flex wrap gap={4} align="center">
        {quick.map((tag) => (
          <Tag.CheckableTag
            key={tag.id}
            checked={value.tags.includes(tag.id)}
            onChange={(checked) => onChange({
              ...value,
              tags: checked ? [...value.tags, tag.id] : value.tags.filter((entry) => entry !== tag.id),
            })}
            style={{ fontSize: size === "large" ? 14 : undefined }}
          >
            {tag.id} <span style={{ opacity: 0.65, fontSize: "0.85em" }}>{formatCount(tag.regionCount)}</span>
          </Tag.CheckableTag>
        ))}
        <Button type="link" size="small" onClick={() => setAdvancedChosen(true)} style={{ padding: "0 4px" }}>
          {all.length > quick.length ? `All ${formatCount(all.length)} tags` : "Advanced"}
        </Button>
      </Flex>
    );
  }

  const toSimple = () => {
    setAdvancedChosen(false);
    onChange({ tags: value.tags.filter((tag) => quickIds.has(tag)), excluded: [], matchAll: false });
  };

  return (
    <Flex vertical gap={8}>
      <Flex gap={8} wrap>
        <Segmented
          aria-label="Tag match"
          size={size}
          value={value.matchAll ? "all" : "any"}
          onChange={(match) => onChange({ ...value, matchAll: match === "all" })}
          options={[{ value: "any", label: "Any of" }, { value: "all", label: "All of" }]}
        />
        <Select
          aria-label="Tags"
          mode="multiple"
          placeholder="Choose tags"
          allowClear
          optionFilterProp="label"
          value={value.tags}
          onChange={(tags: string[]) => onChange({ ...value, tags })}
          options={all.filter((tag) => !value.excluded.includes(tag.id)).map(option)}
          maxTagCount="responsive"
          size={size}
          style={{ flex: "1 1 180px", minWidth: 0 }}
        />
      </Flex>
      <Select
        aria-label="Excluded tags"
        mode="multiple"
        placeholder="Leave out plots tagged…"
        prefix={<Text type="secondary" style={{ fontSize: "0.9em" }}>None of</Text>}
        allowClear
        optionFilterProp="label"
        value={value.excluded}
        onChange={(excluded: string[]) => onChange({ ...value, excluded })}
        options={all.filter((tag) => !value.tags.includes(tag.id)).map(option)}
        maxTagCount="responsive"
        size={size}
        style={{ width: "100%" }}
      />
      <div>
        <Button type="link" size="small" onClick={toSimple} style={{ padding: "0 4px" }}>Simple</Button>
      </div>
    </Flex>
  );
}
