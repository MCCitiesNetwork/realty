import { theme, type ThemeConfig } from "antd";
import { useEffect, useState } from "react";

export type ColorScheme = "light" | "dark";

const DARK_QUERY = "(prefers-color-scheme: dark)";

/**
 * The operating system's preference, followed live. There is no toggle to remember:
 * a visitor who wants the other scheme has already said so to their system.
 */
export function useColorScheme(): ColorScheme {
  const [scheme, setScheme] = useState<ColorScheme>(() =>
    typeof window !== "undefined" && window.matchMedia?.(DARK_QUERY).matches ? "dark" : "light");

  useEffect(() => {
    if (typeof window === "undefined" || !window.matchMedia) return;
    const media = window.matchMedia(DARK_QUERY);
    const onChange = (event: MediaQueryListEvent) => setScheme(event.matches ? "dark" : "light");
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, []);

  return scheme;
}

/**
 * The whole visual identity, in Ant Design's own vocabulary. One accent, an estate
 * green, and otherwise the library's defaults -- which is what keeps every screen
 * looking like the same site without a stylesheet to keep in step.
 */
export function themeFor(scheme: ColorScheme): ThemeConfig {
  const dark = scheme === "dark";
  return {
    algorithm: dark ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: {
      colorPrimary: "#1f6f4a",
      colorLink: "#1f6f4a",
      borderRadius: 8,
    },
    components: {
      Layout: {
        headerBg: dark ? "#141414" : "#ffffff",
        headerColor: dark ? "rgba(255, 255, 255, 0.88)" : "rgba(0, 0, 0, 0.88)",
        headerPadding: "0 24px",
        bodyBg: dark ? "#0f0f0f" : "#f5f5f5",
        footerBg: dark ? "#0f0f0f" : "#f5f5f5",
      },
    },
  };
}
