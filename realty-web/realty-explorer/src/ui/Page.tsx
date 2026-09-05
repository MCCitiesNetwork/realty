import type { ReactNode } from "react";

/** The column every screen sits in. */
export function Page({ children, width = 1200 }: { children: ReactNode; width?: number }) {
  return (
    <div style={{ width: "100%", maxWidth: width, margin: "0 auto", padding: "24px 24px 64px" }}>
      {children}
    </div>
  );
}
