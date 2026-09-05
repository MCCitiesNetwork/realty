/** Shapes the API really sends, so a fixture cannot leave out a field a screen relies on. */

export const world = { id: "8f4d1c2e-0000-0000-0000-000000000099", name: "world" };
export const otherWorld = { id: "8f4d1c2e-0000-0000-0000-000000000100", name: "My World" };

export const player = (id: string, name: string | null = null) => ({ id, name });

export const alice = player("a1a1a1a1-0000-0000-0000-000000000001", "Alice");
export const bob = player("b2b2b2b2-0000-0000-0000-000000000002", "Bob");
/** Named by nobody: the module is down, so only the UUID is known. */
export const unnamed = player("c3c3c3c3-0000-0000-0000-000000000003");

export const emptyPage = <K extends string>(key: K) => ({
  page: 1, pageSize: 24, totalCount: 0, totalPages: 0, [key]: [],
}) as { page: number; pageSize: number; totalCount: number; totalPages: number } & Record<K, never[]>;

export const pageOf = <K extends string, T>(key: K, items: T[], totalCount = items.length) => ({
  page: 1, pageSize: 24, totalCount, totalPages: Math.max(1, Math.ceil(totalCount / 24)), [key]: items,
});

export const listing = {
  worldGuardRegionId: "plot_a",
  world,
  contractType: "freehold",
  price: 1500,
  state: "FOR_SALE",
  durationSeconds: null,
};

export const rental = {
  worldGuardRegionId: "flat_9",
  world,
  contractType: "leasehold",
  price: 200,
  state: "FOR_LEASE",
  durationSeconds: 2_592_000,
};

export const freeholdRegion = {
  worldGuardRegionId: "plot_a",
  world,
  state: "FOR_SALE",
  freehold: { titleHolder: null, authority: alice, price: 1500, lastSoldPrice: null, acceptingOffers: true },
  leasehold: null,
  auction: null,
  dimensions: null,
  tags: ["shop"],
};

export const stats = {
  regions: 7782,
  freehold: { contracts: 1649, occupied: 1532, averagePrice: 2.7e58 },
  leasehold: { contracts: 7907, occupied: 2410, averagePrice: 1.2e59, averageDurationSeconds: 2_592_000 },
  activeOffers: 180,
  activeAuctions: 0,
};

export const tags = [
  { id: "apartment", regionCount: 475 },
  { id: "shop", regionCount: 215 },
];

export const rentEvent = {
  kind: "leasehold",
  eventType: "RENT",
  eventTime: "2026-09-03T13:37:41Z",
  worldGuardRegionId: "m646-2",
  world,
  tenant: bob,
  landlord: alice,
  price: 1000,
  durationSeconds: 2_592_000,
};

export const auction = {
  worldGuardRegionId: "tower_1",
  world,
  auctioneer: alice,
  startDate: "2026-09-01T00:00:00Z",
  endDate: "2099-01-01T00:00:00Z",
  minBid: 5000,
  minStep: 100,
  biddingDurationSeconds: 86_400,
  paymentDurationSeconds: 3_600,
  highestBid: null,
  bidderCount: 0,
};
