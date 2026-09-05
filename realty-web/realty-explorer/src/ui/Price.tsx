import { Typography } from "antd";
import { useCurrency } from "../currency";
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
  const currency = useCurrency();
  if (value === null || value === undefined) {
    return <Typography.Text type="secondary">No price set</Typography.Text>;
  }
  return (
    <Typography.Text
      strong
      title={formatPriceInFull(value, currency)}
      style={{ fontSize: SIZES[size], fontVariantNumeric: "tabular-nums", whiteSpace: "nowrap" }}
    >
      {formatPrice(value, currency)}
      {per && (
        <Typography.Text type="secondary" style={{ fontSize: "0.65em", fontWeight: 400 }}>
          {" "}/ {per}
        </Typography.Text>
      )}
    </Typography.Text>
  );
}
