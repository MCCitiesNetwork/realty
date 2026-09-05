import { describe, expect, it } from "vitest";
import { formatPrice } from "./format";

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
