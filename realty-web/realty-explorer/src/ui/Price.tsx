import { Typography } from "antd";
import { formatPrice, formatPriceInFull } from "./format";

type Props = {
  /** Null or undefined is "no price set", which is a real state and rendered as one. */
  value: number | null | undefined;
  /** What the figure is per -- a lease term, say. Omitted for a one-off price. */
  per?: string;
  size?: "inline" | "card" | "hero";
};

const SIZES = { inline: undefined, card: 20, hero: 32 } as const;

/** A price, abbreviated to fit and spelled out on hover. */
export function Price({ value, per, size = "inline" }: Props) {
  if (value === null || value === undefined) {
    return <Typography.Text type="secondary">No price set</Typography.Text>;
  }
  return (
    <Typography.Text
      strong
      title={formatPriceInFull(value)}
      style={{ fontSize: SIZES[size], fontVariantNumeric: "tabular-nums", whiteSpace: "nowrap" }}
    >
      {formatPrice(value)}
      {per && (
        <Typography.Text type="secondary" style={{ fontSize: "0.65em", fontWeight: 400 }}>
          {" "}/ {per}
        </Typography.Text>
      )}
    </Typography.Text>
  );
}
