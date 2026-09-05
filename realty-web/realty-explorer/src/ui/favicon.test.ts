import { describe, expect, it } from "vitest";
import { applyFavicon } from "./favicon";

const emblem = "https://example.net/emblem.png";

describe("applyFavicon", () => {
  it("adds an icon link when the page has none", () => {
    const doc = document.implementation.createHTMLDocument();
    applyFavicon(emblem, doc);
    expect(doc.querySelector('link[rel="icon"]')?.getAttribute("href")).toBe(emblem);
  });

  it("repoints an existing icon link rather than adding a second", () => {
    const doc = document.implementation.createHTMLDocument();
    const existing = doc.createElement("link");
    existing.rel = "icon";
    existing.href = "https://example.net/old.png";
    doc.head.appendChild(existing);

    applyFavicon(emblem, doc);

    expect(doc.querySelectorAll('link[rel="icon"]')).toHaveLength(1);
    expect(existing.getAttribute("href")).toBe(emblem);
  });

  it("leaves the page alone when no emblem is configured", () => {
    const doc = document.implementation.createHTMLDocument();
    applyFavicon("", doc);
    expect(doc.querySelector('link[rel="icon"]')).toBeNull();
  });
});
