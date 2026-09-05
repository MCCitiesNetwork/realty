import { describe, expect, it } from "vitest";
import { EVENT_TYPES, eventLabel, formatCount, formatDuration, formatPrice, formatPriceInFull, formatRelative, humanise } from "./format";

describe("formatPrice, with a currency", () => {
  it("puts the operator's symbol in front of the figure, abbreviated or in full", () => {
    expect(formatPrice(78_000, "$")).toBe("$78k");
    expect(formatPrice(12.5, "$")).toBe("$12.5");
    expect(formatPriceInFull(78_000, "$")).toBe("$78,000");
  });

  it("attaches nothing when none is configured", () => {
    expect(formatPrice(78_000)).toBe("78k");
  });
});

describe("formatPrice", () => {
  it("shows amounts under a thousand in full", () => {
    expect(formatPrice(0)).toBe("0");
    expect(formatPrice(999)).toBe("999");
    expect(formatPrice(12.5)).toBe("12.5");
  });

  it("abbreviates with a suffix from a thousand up", () => {
    expect(formatPrice(1000)).toBe("1k");
    expect(formatPrice(10_000)).toBe("10k");
    expect(formatPrice(12_500)).toBe("12.5k");
    expect(formatPrice(1_234_567)).toBe("1.23m");
    expect(formatPrice(10_000_000)).toBe("10m");
    expect(formatPrice(2.5e9)).toBe("2.5b");
    expect(formatPrice(7e12)).toBe("7t");
  });

  it("does not round up into the next suffix's territory", () => {
    expect(formatPrice(999_999)).toBe("1m");
    expect(formatPrice(999_994)).toBe("999.99k");
  });

  it("keeps the sign", () => {
    expect(formatPrice(-1500)).toBe("-1.5k");
  });

  it("names every short-scale power up to a vigintillion", () => {
    expect(formatPrice(1e15)).toBe("1quad");
    expect(formatPrice(1e18)).toBe("1quint");
    expect(formatPrice(1e33)).toBe("1dec");
    expect(formatPrice(1e63)).toBe("1vig");
  });

  it("falls back to scientific notation past the last suffix", () => {
    expect(formatPrice(1e66)).toBe("1E66");
    expect(formatPrice(1.7976931348623157e308)).toBe("1.8E308");
  });

  it("does not grow past a fixed width for any finite value", () => {
    for (const v of [999.99, 999_994, 1e15, 1e66, 123456789e100, -1.7976931348623157e308]) {
      expect(formatPrice(v).length).toBeLessThanOrEqual(10);
    }
  });
});

describe("formatCount", () => {
  it("groups thousands", () => {
    expect(formatCount(7782)).toBe("7,782");
    expect(formatCount(0)).toBe("0");
  });
});

describe("formatDuration", () => {
  it("names the largest unit that fits, in whole numbers where it can", () => {
    expect(formatDuration(2_592_000)).toBe("30 days");
    expect(formatDuration(86_400)).toBe("1 day");
    expect(formatDuration(3_600)).toBe("1 hour");
    expect(formatDuration(90)).toBe("1.5 minutes");
    expect(formatDuration(0)).toBe("0 seconds");
  });

  it("rounds to one decimal rather than adding a second unit", () => {
    expect(formatDuration(76_125_170)).toBe("881.1 days");
  });
});

describe("formatRelative", () => {
  const now = Date.parse("2026-09-05T12:00:00Z");

  it("speaks in the largest unit that has elapsed", () => {
    expect(formatRelative("2026-09-03T12:00:00Z", now)).toBe("2 days ago");
    expect(formatRelative("2026-09-05T11:30:00Z", now)).toBe("30 minutes ago");
    expect(formatRelative("2026-09-05T14:00:00Z", now)).toBe("in 2 hours");
  });

  it("says now for the present moment", () => {
    expect(formatRelative("2026-09-05T12:00:00Z", now)).toBe("now");
  });
});

describe("eventLabel", () => {
  it("names the event types the API lists in plain English", () => {
    expect(eventLabel("BUY")).toBe("Bought");
    expect(eventLabel("OFFER_BUY")).toBe("Bought by offer");
    expect(eventLabel("TERMINATE")).toBe("Notice given");
  });

  it("still renders a type it has no label for", () => {
    // An event type the API adds later must not vanish from the feed.
    expect(eventLabel("SOMETHING_NEW")).toBe("Something new");
  });

  it("has a label for every type the API accepts", () => {
    for (const type of EVENT_TYPES) {
      expect(eventLabel(type)).not.toBe(humanise(type));
    }
  });
});
