import { Select } from "antd";
import type { ApiClient } from "../api/client";
import { worldLabel } from "../api/paths";
import { TTL, remembered } from "../api/remembered";
import { useQuery } from "../api/useQuery";

type Props = {
  client: ApiClient;
  value?: string;
  onChange: (world: string | undefined) => void;
  style?: React.CSSProperties;
  size?: "small" | "middle" | "large";
};

/**
 * The worlds the database knows, and no others.
 *
 * A world name is a folder name on disk, so a filter that had to be typed exactly was
 * a filter that mostly returned nothing. When the list is unreachable the control
 * simply offers nothing -- an invented option would be a filter for a world that may
 * not exist.
 */
export function WorldSelect({ client, value, onChange, style, size }: Props) {
  const worlds = useQuery(() => remembered(client, "worlds", TTL.worlds, () => client.GET("/v1/worlds", {})), [client]);
  const options = worlds.status === "ready" && Array.isArray(worlds.data)
    ? worlds.data.map((world) => ({ value: worldLabel(world), label: worldLabel(world) }))
    : [];
  return (
    <Select
      aria-label="World"
      placeholder="Select a world"
      allowClear
      showSearch
      optionFilterProp="label"
      loading={worlds.status === "loading"}
      value={value}
      onChange={(next) => onChange(next ?? undefined)}
      options={options}
      size={size}
      style={{ width: "100%", ...style }}
    />
  );
}
