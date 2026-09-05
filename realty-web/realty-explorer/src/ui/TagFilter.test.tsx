import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NO_TAGS, TagFilter, type TagQuery } from "./TagFilter";
import { stubClient } from "../test-support/stubClient";

const tags = [
  { id: "shop", regionCount: 215 },
  { id: "apartment", regionCount: 475 },
  { id: "cbd", regionCount: 40 },
  { id: "industrial", regionCount: 30 },
  { id: "park", regionCount: 20 },
  { id: "hotel", regionCount: 12 },
  { id: "farm", regionCount: 9 },
  { id: "island", regionCount: 5 },
  { id: "derelict", regionCount: 2 },
];

const show = (value: TagQuery, list = tags) => {
  const { client } = stubClient({ "/v1/tags": list });
  const onChange = vi.fn();
  render(<TagFilter client={client} value={value} onChange={onChange} />);
  return onChange;
};

/** The chip for a tag; its text is the id followed by the count. */
const chips = () => [...document.querySelectorAll<HTMLElement>(".ant-tag")];
const findChip = (id: string) => chips().find((entry) => entry.textContent?.startsWith(`${id} `));
const chip = (id: string) => {
  const found = findChip(id);
  if (!found) throw new Error(`no chip for ${id}`);
  return found;
};

describe("TagFilter", () => {
  it("offers every tag in use as a chip, most-used first, with its count", async () => {
    show(NO_TAGS);
    await waitFor(() => expect(chip("apartment")).toBeInTheDocument());
    const texts = chips().map((entry) => entry.textContent);
    expect(texts[0]).toBe("apartment 475");
    expect(texts[1]).toBe("shop 215");
    expect(texts).toHaveLength(9);
    expect(texts.at(-1)).toBe("derelict 2");
    expect(screen.getByRole("button", { name: "Advanced" })).toBeInTheDocument();
  });

  it("asks for a tag when its chip is pressed, and widens to either on a second", async () => {
    const onChange = show({ ...NO_TAGS, tags: ["shop"] });
    await waitFor(() => expect(chip("apartment")).toBeInTheDocument());
    expect(chip("shop")).toHaveClass("ant-tag-checkable-checked");

    fireEvent.click(chip("apartment"));
    expect(onChange).toHaveBeenLastCalledWith({ tags: ["shop", "apartment"], excluded: [], matchAll: false });

    fireEvent.click(chip("shop"));
    expect(onChange).toHaveBeenLastCalledWith({ tags: [], excluded: [], matchAll: false });
  });

  it("opens advanced when asked, with every tag, the match, and the exclusions", async () => {
    show(NO_TAGS);
    fireEvent.click(await screen.findByRole("button", { name: "Advanced" }));
    expect(screen.getByLabelText("Tags")).toBeInTheDocument();
    expect(screen.getByLabelText("Excluded tags")).toBeInTheDocument();
    expect(screen.getByLabelText("Tag match")).toBeInTheDocument();
    expect(findChip("apartment")).toBeUndefined();
  });

  it("opens in advanced when the question is one chips cannot show", async () => {
    // A link with an exclusion in it, or all-of matching.
    for (const value of [
      { tags: [], excluded: ["derelict"], matchAll: false },
      { tags: ["shop", "cbd"], excluded: [], matchAll: true },
    ]) {
      const { unmount } = render(
        <TagFilter client={stubClient({ "/v1/tags": tags }).client} value={value} onChange={vi.fn()} />,
      );
      await waitFor(() => expect(screen.getByLabelText("Excluded tags")).toBeInTheDocument());
      unmount();
    }
  });

  it("switches the match between any and all", async () => {
    const onChange = show({ tags: ["shop", "cbd"], excluded: [], matchAll: true });
    await waitFor(() => expect(screen.getByLabelText("Tag match")).toBeInTheDocument());
    fireEvent.click(screen.getByText("Any of"));
    expect(onChange).toHaveBeenLastCalledWith({ tags: ["shop", "cbd"], excluded: [], matchAll: false });
  });

  it("drops what chips cannot show on the way back to simple, and keeps the tags", async () => {
    const onChange = show({ tags: ["shop", "derelict"], excluded: ["park"], matchAll: true });
    fireEvent.click(await screen.findByRole("button", { name: "Simple" }));
    expect(onChange).toHaveBeenLastCalledWith({ tags: ["shop", "derelict"], excluded: [], matchAll: false });
  });

  it("says so when no tag is in use", async () => {
    show(NO_TAGS, []);
    expect(await screen.findByText("No region carries a tag yet.")).toBeInTheDocument();
  });
});
