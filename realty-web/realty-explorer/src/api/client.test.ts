import { describe, expect, it, vi } from "vitest";
import { createApiClient, fetchSchematic } from "./client";

/** Captures the URL openapi-fetch actually requested. */
const requestedUrl = (spy: ReturnType<typeof vi.fn>): string => {
  const first = spy.mock.calls[0][0];
  return typeof first === "string" ? first : String((first as Request).url);
};

describe("fetchSchematic", () => {
  it("reads the response as an ArrayBuffer", async () => {
    // openapi-fetch does not type-enforce parseAs, so this assertion is the only
    // thing standing between us and a runtime failure on the binary endpoint.
    const bytes = new Uint8Array([1, 2, 3]);
    const fetchSpy = vi.fn(async () =>
      new Response(bytes, {
        status: 200,
        headers: { "Content-Type": "application/octet-stream" },
      }));

    const client = createApiClient("", fetchSpy as unknown as typeof fetch);
    const result = await fetchSchematic(client, "world", "plot_a")();

    expect(result).toBeInstanceOf(ArrayBuffer);
    expect(new Uint8Array(result)).toEqual(bytes);
  });

  it("passes world and region as query parameters", async () => {
    const fetchSpy = vi.fn(async () => new Response(new ArrayBuffer(0), { status: 200 }));
    const client = createApiClient("", fetchSpy as unknown as typeof fetch);

    await fetchSchematic(client, "My World", "plot_a")();

    const url = requestedUrl(fetchSpy);
    expect(url).toContain("region=plot_a");
    expect(url).toMatch(/world=My(\+|%20)World/);
  });

  it("defaults to the page's own origin when no base URL is configured", async () => {
    // The bundled deployment ships no config.json, so this is its every request.
    const fetchSpy = vi.fn(async () => new Response(new ArrayBuffer(0), { status: 200 }));
    const client = createApiClient("", fetchSpy as unknown as typeof fetch);

    await fetchSchematic(client, "world", "plot_a")();

    expect(requestedUrl(fetchSpy)).toContain(`${window.location.origin}/v1/region/schematic`);
  });

  it("targets the configured base URL", async () => {
    const fetchSpy = vi.fn(async () => new Response(new ArrayBuffer(0), { status: 200 }));
    const client = createApiClient("https://api.example.com", fetchSpy as unknown as typeof fetch);

    await fetchSchematic(client, "world", "plot_a")();

    expect(requestedUrl(fetchSpy)).toContain("https://api.example.com/v1/region/schematic");
  });

  it("throws when the region has no schematic, so the caller can show a panel", async () => {
    const fetchSpy = vi.fn(async () =>
      new Response(JSON.stringify({ error: "SCHEMATIC_NOT_FOUND" }), {
        status: 404,
        headers: { "Content-Type": "application/json" },
      }));
    const client = createApiClient("", fetchSpy as unknown as typeof fetch);

    await expect(fetchSchematic(client, "world", "plot_a")()).rejects.toThrow(/no schematic/i);
  });
});
