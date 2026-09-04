import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * The card's click target is a layout property, and jsdom has no layout -- every
 * rendering test passed happily while only about a seventh of the card was clickable
 * and a click anywhere else did nothing at all. So the invariant is asserted against
 * the stylesheet, which is where it actually lives.
 */
// Resolved from the project root: under jsdom, import.meta.url is an http:// URL.
const css = readFileSync(resolve(process.cwd(), "src/styles.css"), "utf8");

const ruleFor = (selector: string): string => {
  const at = css.indexOf(`${selector} {`);
  expect(at, `no rule for ${selector}`).toBeGreaterThan(-1);
  return css.slice(at, css.indexOf("}", at));
};

describe("card hit area", () => {
  it("stretches the region link over the whole card", () => {
    const overlay = ruleFor(".card-name::after");
    expect(overlay).toContain("position: absolute");
    expect(overlay).toContain("inset: 0");
  });

  it("positions the card, without which the overlay escapes to the page", () => {
    // An absolutely positioned overlay resolves against the nearest positioned
    // ancestor. Drop this and it covers the viewport instead of the card.
    expect(ruleFor(".card")).toContain("position: relative");
  });

  it("raises nothing inside the card above the overlay", () => {
    // Lifting the price or the badge back out with position:relative would carve the
    // dead zones straight back into the middle of the target.
    const inner = [".card > .badge", ".card-foot", ".card-world", ".price"];
    for (const selector of inner) {
      const at = css.indexOf(`${selector} {`);
      if (at === -1) continue;
      expect(css.slice(at, css.indexOf("}", at)), `${selector} is raised above the link`)
        .not.toContain("position: relative");
    }
  });
});
