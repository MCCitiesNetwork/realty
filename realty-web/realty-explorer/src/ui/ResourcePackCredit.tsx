import type { Attribution } from "../config";

/**
 * Credits for the resource pack a preview is textured with.
 *
 * <p>Rendered beside the viewer rather than in a page footer: the credit is owed for the
 * pack, so it belongs where the pack is actually used, and pages without a rendered
 * schematic have nothing to credit.</p>
 *
 * <p>Entries render as text, never as markup -- the value comes from whoever runs the
 * server, and the config loader has already dropped any link that is not absolute
 * http(s). Links are off-site, hence `rel="noopener noreferrer"`.</p>
 */
export function ResourcePackCredit({ attribution }: { attribution: Attribution[] }) {
  if (attribution.length === 0) return null;

  return (
    <p className="viewer-credit">
      {attribution.map((entry, index) => (
        <span key={`${entry.text}|${entry.href ?? ""}`}>
          {index > 0 && " · "}
          {entry.href
            ? <a href={entry.href} target="_blank" rel="noopener noreferrer">{entry.text}</a>
            : entry.text}
        </span>
      ))}
    </p>
  );
}
