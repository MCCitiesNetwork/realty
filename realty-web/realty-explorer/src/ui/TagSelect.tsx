import { Select } from "antd";
import type { ApiClient } from "../api/client";
import { TTL, remembered } from "../api/remembered";
import { useQuery } from "../api/useQuery";
import { formatCount } from "./format";

type Props = {
  client: ApiClient;
  value: string[];
  onChange: (tags: string[]) => void;
  style?: React.CSSProperties;
  size?: "small" | "middle" | "large";
};

/**
 * Every tag in use, with how many regions carry it -- exactly what `/v1/tags` reports.
 * Tags are raw ids: their display names live in the plugin's config, which the API
 * does not read, so none is dressed up here either.
 */
export function TagSelect({ client, value, onChange, style, size }: Props) {
  const tags = useQuery(() => remembered(client, "tags", TTL.tags, () => client.GET("/v1/tags", {})), [client]);
  const options = tags.status === "ready" && Array.isArray(tags.data)
    ? tags.data.map((tag) => ({ value: tag.id, label: `${tag.id} (${formatCount(tag.regionCount)})` }))
    : [];
  return (
    <Select
      aria-label="Tags"
      mode="multiple"
      placeholder="Select a tag"
      allowClear
      optionFilterProp="label"
      loading={tags.status === "loading"}
      value={value}
      onChange={onChange}
      options={options}
      maxTagCount="responsive"
      size={size}
      style={{ width: "100%", ...style }}
    />
  );
}
