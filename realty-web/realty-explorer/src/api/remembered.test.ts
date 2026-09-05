import { describe, expect, it, vi } from "vitest";
import type { ApiClient } from "./client";
import { remembered } from "./remembered";

const aClient = () => ({ GET: vi.fn() }) as unknown as ApiClient;

describe("remembered", () => {
  it("asks once and hands every caller within the interval the same answer", async () => {
    const client = aClient();
    const fetch = vi.fn(async () => ({ data: ["world"], error: undefined }));
    let clock = 1_000;

    const first = remembered(client, "worlds", 60_000, fetch, () => clock);
    clock += 30_000;
    const second = remembered(client, "worlds", 60_000, fetch, () => clock);

    expect(second).toBe(first);
    await first;
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("asks again once the interval has passed", async () => {
    const client = aClient();
    const fetch = vi.fn(async () => ({ data: [], error: undefined }));
    let clock = 1_000;

    await remembered(client, "tags", 60_000, fetch, () => clock);
    clock += 60_000;
    await remembered(client, "tags", 60_000, fetch, () => clock);

    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("keeps different questions apart", async () => {
    const client = aClient();
    const worlds = vi.fn(async () => ({ data: "worlds", error: undefined }));
    const tags = vi.fn(async () => ({ data: "tags", error: undefined }));

    await expect(remembered(client, "worlds", 60_000, worlds)).resolves.toEqual({ data: "worlds", error: undefined });
    await expect(remembered(client, "tags", 60_000, tags)).resolves.toEqual({ data: "tags", error: undefined });
  });

  it("keeps different clients apart, so a stub never sees another's answers", async () => {
    const fetch = vi.fn(async () => ({ data: 1, error: undefined }));
    await remembered(aClient(), "stats", 60_000, fetch);
    await remembered(aClient(), "stats", 60_000, fetch);
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("does not remember a failed answer", async () => {
    // openapi-fetch resolves with { error } rather than rejecting; neither is kept.
    const client = aClient();
    const fetch = vi.fn()
      .mockResolvedValueOnce({ data: undefined, error: { error: "INTERNAL_ERROR" } })
      .mockRejectedValueOnce(new TypeError("Failed to fetch"))
      .mockResolvedValue({ data: ["ok"], error: undefined });

    await remembered(client, "worlds", 60_000, fetch);
    await expect(remembered(client, "worlds", 60_000, fetch)).rejects.toThrow(/failed to fetch/i);
    await expect(remembered(client, "worlds", 60_000, fetch)).resolves.toEqual({ data: ["ok"], error: undefined });
    expect(fetch).toHaveBeenCalledTimes(3);
  });
});
