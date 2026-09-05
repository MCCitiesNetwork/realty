import { Typography } from "antd";
import type { Attribution } from "../api/client";

/**
 * Credits for the resource pack a preview is textured with.
 *
 * <p>Rendered beside the viewer rather than in a page footer: the credit is owed for the
 * pack, so it belongs where the pack is actually used, and pages without a rendered
 * schematic have nothing to credit.</p>
 *
 * <p>Entries render as text, never as markup -- the value comes from whoever runs the
 * game server, and the API client has already dropped any link that is not absolute
 * http(s). Links are off-site, hence `rel="noopener noreferrer"`.</p>
 */
export function ResourcePackCredit({ attribution }: { attribution: Attribution[] }) {
  if (attribution.length === 0) return null;

  return (
    <Typography.Paragraph className="viewer-credit" type="secondary" style={{ fontSize: 12, margin: "8px 12px" }}>
      {attribution.map((entry, index) => (
        <span key={`${entry.text}|${entry.href ?? ""}`}>
          {index > 0 && " · "}
          {entry.href
            ? <Typography.Link href={entry.href} target="_blank" rel="noopener noreferrer">{entry.text}</Typography.Link>
            : entry.text}
        </span>
      ))}
    </Typography.Paragraph>
  );
}
