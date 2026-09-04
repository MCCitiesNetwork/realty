/** One credit line for the resource pack, optionally linking somewhere. */
export type Attribution = {
  text: string;
  /** Absolute http(s) target, or absent for plain text. */
  href?: string;
};

export type AppConfig = {
  /** Absolute API origin, or "" meaning same-origin (requests go to a relative /v1). */
  apiBaseUrl: string;
  /**
   * Credits for the resource pack the previews are textured with -- the pack's name and
   * a link to its licence, say. Shown only where a schematic actually renders, because
   * that is the only place the pack is used and so the only place a credit is owed.
   */
  resourcePackAttribution: Attribution[];
};

const EMPTY: AppConfig = { apiBaseUrl: "", resourcePackAttribution: [] };

/** Bounds on operator input, so a malformed config cannot produce an unusable page. */
const MAX_ENTRIES = 8;
const MAX_TEXT = 120;

/**
 * Reads /config.json if it is there.
 *
 * Every failure resolves to the empty config rather than throwing. The bundled
 * (realty-web-dist) deployment serves this file from realty-rest, and a static
 * deployment may not ship one at all, so a 404 is a normal case for one build that
 * has to serve both.
 */
export async function loadConfig(): Promise<AppConfig> {
  try {
    const response = await fetch("/config.json", { cache: "no-store" });
    if (!response.ok) return EMPTY;
    const parsed = (await response.json()) as Partial<AppConfig>;
    const base = typeof parsed.apiBaseUrl === "string" ? parsed.apiBaseUrl.trim() : "";
    return {
      // A trailing slash would double up against the leading slash of every path.
      apiBaseUrl: base.replace(/\/+$/, ""),
      resourcePackAttribution: attribution(parsed.resourcePackAttribution),
    };
  } catch {
    return EMPTY;
  }
}

/**
 * Validates operator-supplied pack credits.
 *
 * <p>This value is written by whoever runs the server and rendered into the page, so it
 * is treated as untrusted input: entries are kept as text and never as markup, and a
 * link is dropped unless it is an absolute http(s) URL. That excludes `javascript:`,
 * which is the reason to check the scheme rather than merely that a string is present.</p>
 */
function attribution(value: unknown): Attribution[] {
  if (!Array.isArray(value)) return [];
  return value
    .slice(0, MAX_ENTRIES)
    .map((entry) => {
      if (typeof entry !== "object" || entry === null) return null;
      const { text, href } = entry as Record<string, unknown>;
      if (typeof text !== "string" || !text.trim()) return null;
      return { text: text.trim().slice(0, MAX_TEXT), ...safeHref(href) };
    })
    .filter((entry): entry is Attribution => entry !== null);
}

function safeHref(href: unknown): { href?: string } {
  if (typeof href !== "string" || !href.trim()) return {};
  try {
    const url = new URL(href.trim());
    return url.protocol === "http:" || url.protocol === "https:" ? { href: url.href } : {};
  } catch {
    // Not an absolute URL. A relative one would resolve against this page, which is
    // never what a credit link means.
    return {};
  }
}
