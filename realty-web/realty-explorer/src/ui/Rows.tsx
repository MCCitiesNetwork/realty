import { Empty, Skeleton, theme } from "antd";
import type { ReactNode } from "react";

type Props<T> = {
  items: ReadonlyArray<T>;
  renderItem: (item: T) => ReactNode;
  itemKey: (item: T, index: number) => string | number;
  loading?: boolean;
  /** What an empty list means here -- the caller knows, this component does not. */
  emptyText: string;
};

/**
 * A short list of rows with a rule between them.
 *
 * Ant Design's `List` is deprecated in favour of a virtualised `Listy`, which is built
 * for thousands of rows; these lists hold a page of them, so plain rows on the theme's
 * own divider colour are the whole of what is needed.
 */
export function Rows<T>({ items, renderItem, itemKey, loading = false, emptyText }: Props<T>) {
  const { token } = theme.useToken();

  if (loading) {
    return <div style={{ padding: "12px 0" }}><Skeleton active paragraph={{ rows: 4 }} title={false} /></div>;
  }
  if (items.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} style={{ padding: "16px 0" }} />;
  }
  return (
    <div role="list">
      {items.map((item, index) => (
        <div
          key={itemKey(item, index)}
          role="listitem"
          style={{
            padding: "10px 0",
            borderTop: index === 0 ? "none" : `1px solid ${token.colorSplit}`,
          }}
        >
          {renderItem(item)}
        </div>
      ))}
    </div>
  );
}
