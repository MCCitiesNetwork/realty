import { Affix, Card, Col, Flex, Grid, InputNumber, Pagination, Radio, Row, Segmented, Select, Space, Typography } from "antd";
import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import type { ApiClient } from "../../api/client";
import type { paths } from "../../api/schema";
import { useQuery } from "../../api/useQuery";
import { formatCount } from "../../ui/format";
import { ListingGrid } from "../../ui/ListingGrid";
import { Page } from "../../ui/Page";
import { TagSelect } from "../../ui/TagSelect";
import { WorldSelect } from "../../ui/WorldSelect";
import { useVisibility, worldFor } from "../../visibility";

const { Title, Text } = Typography;

type SearchQuery = NonNullable<paths["/v1/regions/search"]["get"]["parameters"]["query"]>;
type ContractFilter = NonNullable<SearchQuery["type"]>;
type Occupancy = NonNullable<SearchQuery["occupancy"]>;
type Sort = NonNullable<SearchQuery["sort"]>;

const CONTRACT_FILTERS: ReadonlyArray<{ value: ContractFilter }> = [
  { value: "all" }, { value: "sale" }, { value: "rent" }, { value: "freehold" }, { value: "leasehold" },
];

/**
 * What a visitor asks to see, in their words, mapped onto the two API filters that
 * together express it: the contract type and whether somebody holds the plot.
 *
 * The API has no state filter. "Sold" is every titled freehold -- some of which carry
 * an asking price and show as for sale on their cards, since a holder who has priced a
 * plot is selling -- and "Leased" is every leasehold with a tenant. `freehold` and
 * `leasehold` widen to every contract of that kind, including never-listed regions,
 * which come back with a null price.
 */
type Show = { value: string; label: string; type: ContractFilter; occupancy: Occupancy };

const SHOWS: ReadonlyArray<Show> = [
  { value: "all", label: "Everything", type: "all", occupancy: "any" },
  { value: "sale", label: "For sale", type: "sale", occupancy: "any" },
  { value: "rent", label: "For rent", type: "rent", occupancy: "unoccupied" },
  { value: "sold", label: "Sold", type: "freehold", occupancy: "occupied" },
  { value: "leased", label: "Leased", type: "leasehold", occupancy: "occupied" },
  { value: "freehold", label: "All freeholds", type: "freehold", occupancy: "any" },
  { value: "leasehold", label: "All leaseholds", type: "leasehold", occupancy: "any" },
];

/** The entry the URL's two filters spell, so the control reads back what a link set. */
function showFor(type: ContractFilter, occupancy: Occupancy): string {
  const exact = SHOWS.find((show) => show.type === type && show.occupancy === occupancy);
  if (exact) return exact.value;
  // `rent` is the same rows as `leasehold`; a rent search without vacancy is that.
  if (type === "rent") return "leasehold";
  return SHOWS.find((show) => show.type === type && show.occupancy === "any")?.value ?? "all";
}

const OCCUPANCIES: ReadonlyArray<{ value: Occupancy; label: string }> = [
  { value: "any", label: "Any" },
  { value: "unoccupied", label: "Vacant" },
  { value: "occupied", label: "Occupied" },
];

const SORTS: ReadonlyArray<{ value: Sort; label: string }> = [
  { value: "price_desc", label: "Highest first" },
  { value: "price_asc", label: "Lowest first" },
];

export const PAGE_SIZE = 24;

const oneOf = <T extends string>(
  options: ReadonlyArray<{ value: T }>,
  value: string | null,
  fallback: T,
): T => (options.some((option) => option.value === value) ? (value as T) : fallback);

/** A price bound from the URL: a finite, non-negative number, or nothing. */
const bound = (value: string | null): number | undefined => {
  if (value === null || value === "") return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : undefined;
};

/**
 * The search, with its filters in the URL.
 *
 * A filter set is a link: the home page, the tag chips and the header all arrive here
 * with one, the back button restores the last one, and a visitor can hand one to
 * someone else. Nothing about the search is held in component state except the text
 * in a price box before it is applied.
 */
export function ListingsScreen({ client }: { client: ApiClient }) {
  const [params, setParams] = useSearchParams();
  const screens = Grid.useBreakpoint();
  const visibility = useVisibility();

  const type = oneOf(CONTRACT_FILTERS, params.get("type"), "all");
  const occupancy = oneOf(OCCUPANCIES, params.get("occupancy"), "any");
  const sort = oneOf(SORTS, params.get("sort"), "price_desc");
  // Under a whitelist the search is always of one visible world; a link naming a hidden
  // one is answered with the default rather than honoured.
  const world = worldFor(visibility, params.get("world"));
  const tags = params.getAll("tag");
  const minPrice = bound(params.get("minPrice"));
  const maxPrice = bound(params.get("maxPrice"));
  const page = Math.max(1, Number(params.get("page")) || 1);

  /** Changes filters and goes back to the first page, since the pages just moved. */
  const update = (changes: Record<string, string | string[] | undefined>) => {
    const next = new URLSearchParams(params);
    next.delete("page");
    for (const [key, value] of Object.entries(changes)) {
      next.delete(key);
      if (value === undefined || value === "") continue;
      if (Array.isArray(value)) value.forEach((entry) => next.append(key, entry));
      else next.set(key, value);
    }
    setParams(next);
  };

  const goToPage = (next: number) => {
    const target = new URLSearchParams(params);
    if (next === 1) target.delete("page");
    else target.set("page", String(next));
    setParams(target);
  };

  const results = useQuery(
    () => client.GET("/v1/regions/search", {
      params: {
        query: {
          page,
          pageSize: PAGE_SIZE,
          // The API's own defaults are not restated: a request says only what differs.
          ...(type === "all" ? {} : { type }),
          ...(world ? { world } : {}),
          ...(tags.length > 0 ? { tag: tags } : {}),
          ...(minPrice !== undefined ? { minPrice } : {}),
          ...(maxPrice !== undefined ? { maxPrice } : {}),
          ...(occupancy === "any" ? {} : { occupancy }),
          ...(sort === "price_desc" ? {} : { sort }),
        },
      },
    }),
    [client, page, type, world, tags.join(" "), minPrice, maxPrice, occupancy, sort],
  );

  const total = results.status === "ready" ? results.data.totalCount : undefined;

  return (
    <Page>
      <Row gutter={[24, 24]}>
        <Col xs={24} lg={6}>
          {/* Pinned beside the results on a wide screen, so paging down the grid never
              loses the controls; stacked above them on a narrow one, where pinning
              would cover the very cards being browsed. */}
          <Affix offsetTop={screens.lg ? 80 : undefined} style={{ display: screens.lg ? undefined : "contents" }}>
          <Card title="Filters" size="small">
            <Space orientation="vertical" size={16} style={{ width: "100%" }}>
              <Field label="Show">
                <Select
                  aria-label="Show"
                  value={showFor(type, occupancy)}
                  onChange={(value) => {
                    const show = SHOWS.find((entry) => entry.value === value) ?? SHOWS[0];
                    update({
                      type: show.type === "all" ? undefined : show.type,
                      occupancy: show.occupancy === "any" ? undefined : show.occupancy,
                    });
                  }}
                  options={SHOWS.map((entry) => ({ value: entry.value, label: entry.label }))}
                  style={{ width: "100%" }}
                />
              </Field>
              <Field label="World">
                <WorldSelect client={client} value={world} onChange={(value) => update({ world: value })} />
              </Field>
              <Field label="Tags">
                <TagSelect client={client} value={tags} onChange={(value) => update({ tag: value })} />
              </Field>
              <Field label="Price">
                <Flex gap={8}>
                  <PriceBound
                    label="Minimum price"
                    placeholder="Min"
                    value={minPrice}
                    onApply={(value) => update({ minPrice: value })}
                  />
                  <PriceBound
                    label="Maximum price"
                    placeholder="Max"
                    value={maxPrice}
                    onApply={(value) => update({ maxPrice: value })}
                  />
                </Flex>
              </Field>
              <Field label="Sort by price">
                <Segmented
                  aria-label="Sort"
                  block
                  value={sort}
                  onChange={(value) => update({ sort: value === "price_desc" ? undefined : String(value) })}
                  options={SORTS.map((entry) => ({ value: entry.value, label: entry.label }))}
                />
              </Field>
              <Field label="Occupancy">
                <Radio.Group
                  aria-label="Occupancy"
                  value={occupancy}
                  onChange={(event) => update({
                    occupancy: event.target.value === "any" ? undefined : String(event.target.value),
                  })}
                  options={OCCUPANCIES.map((entry) => ({ value: entry.value, label: entry.label }))}
                />
              </Field>
            </Space>
          </Card>
          </Affix>
        </Col>

        <Col xs={24} lg={18}>
          <div style={{ marginBottom: 16 }}>
            <Title level={2} style={{ margin: 0 }}>Listings</Title>
            <Text type="secondary">
              {total === undefined
                ? "Searching the register"
                : `${formatCount(total)} ${total === 1 ? "region matches" : "regions match"}`}
            </Text>
          </div>

          <ListingGrid query={results} emptyText="No regions match these filters." skeletons={9} />

          {total !== undefined && total > PAGE_SIZE && (
            <Flex justify="center" style={{ marginTop: 24 }}>
              <Pagination
                current={page}
                pageSize={PAGE_SIZE}
                total={total}
                showSizeChanger={false}
                onChange={goToPage}
              />
            </Flex>
          )}
        </Col>
      </Row>
    </Page>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <Text type="secondary" style={{ display: "block", fontSize: 12, marginBottom: 4 }}>{label}</Text>
      {children}
    </div>
  );
}

type PriceBoundProps = {
  label: string;
  placeholder: string;
  value: number | undefined;
  onApply: (value: string | undefined) => void;
};

/**
 * One end of the price range. Applied on Enter or on leaving the box rather than on
 * every keystroke: a search per digit typed would fire a request for "1", "12", "120"
 * and so on, each of which is a filter nobody meant.
 */
function PriceBound({ label, placeholder, value, onApply }: PriceBoundProps) {
  const [draft, setDraft] = useState<number | null>(value ?? null);
  const apply = () => onApply(draft === null ? undefined : String(draft));
  return (
    <InputNumber
      aria-label={label}
      placeholder={placeholder}
      min={0}
      value={draft}
      onChange={(next) => setDraft(typeof next === "number" ? next : null)}
      onPressEnter={apply}
      onBlur={apply}
      style={{ width: "100%" }}
    />
  );
}
