export type AppConfig = {
  /** Absolute API origin, or "" meaning same-origin (requests go to a relative /v1). */
  apiBaseUrl: string;
};

const SAME_ORIGIN: AppConfig = { apiBaseUrl: "" };

/**
 * Reads /config.json if it is there.
 *
 * Every failure resolves to same-origin rather than throwing. The bundled
 * (realty-web-dist) deployment ships no config.json at all, so a 404 here is its
 * normal case, not an error -- and this single fallback is what lets one built
 * bundle serve both the split and bundled deployments.
 */
export async function loadConfig(): Promise<AppConfig> {
  try {
    const response = await fetch("/config.json", { cache: "no-store" });
    if (!response.ok) return SAME_ORIGIN;
    const parsed = (await response.json()) as Partial<AppConfig>;
    const base = typeof parsed.apiBaseUrl === "string" ? parsed.apiBaseUrl.trim() : "";
    // A trailing slash would double up against the leading slash of every path.
    return { apiBaseUrl: base.replace(/\/+$/, "") };
  } catch {
    return SAME_ORIGIN;
  }
}
