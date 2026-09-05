import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { SiteHeader } from "./SiteHeader";
import { stubClient } from "../test-support/stubClient";

const { client } = stubClient({});

describe("SiteHeader", () => {
  it("shows the operator's emblem beside the site name when one is configured", () => {
    const { container } = render(
      <MemoryRouter><SiteHeader client={client} logoUrl="https://example.net/emblem.png" /></MemoryRouter>,
    );
    const link = container.querySelector('a[href="/"]');
    expect(link?.querySelector("img")?.getAttribute("src")).toBe("https://example.net/emblem.png");
    expect(container.querySelector(".anticon-home")).toBeNull();
  });

  it("falls back to a house when none is", () => {
    const { container } = render(<MemoryRouter><SiteHeader client={client} /></MemoryRouter>);
    expect(container.querySelector(".anticon-home")).not.toBeNull();
    expect(container.querySelector('a[href="/"] img')).toBeNull();
  });
});
