# Realty REST v1.x — Proposed Endpoints

Date: 2026-09-03
Status: Proposed

Companion specs: `2026-09-02-realty-rest-api-design.md` (owns the v1 contract),
`2026-09-02-realty-query-service-design.md` (owns the in-game module).

Read-only additions under the existing `/v1` prefix. Every proposal names the
`RealtyBackend` method or mapper query it sits on, so "feasible" means "the SQL
already exists" unless marked **new query**.

Conventions carried over from the v1 spec: `GET` only, JSON only, money as raw
numbers, timestamps ISO-8601 UTC, durations in integer seconds, every player or
world identity as `{id, name}` with a nullable `name`, paging via `page`/`pageSize`
with the same clamping, and the region/world addressed by query parameter, never
path segment.

## What has already shipped

An earlier draft of this document assumed a state of the code that has since moved.
The following are done and are **not** work items:

- `RegionResponse.state` — already a field.
- `RegionResponse.dimensions` — already a field, already served through the module's
  `GET /regions/{worldId}/{regionId}/dimensions`.
- `RegionResponse.auction` — already carries `endDate` and `highestBid`, so a
  separate `/v1/region/auction` route has no reason to exist.
- The `type` filter on `/v1/regions/search` already accepts `freehold` and
  `leasehold` (commit `0ac8635`).
- **The query-service module has shipped.** It serves `/health`,
  `/regions/{worldId}/{regionId}/dimensions`, `/players/{uuid}/name`,
  `/players/names` and `/players/uuids`, and `realty-rest`'s `ModuleClient`
  already exposes `dimensions`, `names` and `uuidOf`. Nothing below is gated on
  the module existing; the only remaining module work is the three *new routes*
  named in section E.

## A. Field additions to existing responses

Not new endpoints. One-liners the entities already carry, which consumers will
ask for first.

| Response | Field | Source |
|---|---|---|
| `RegionResponse.Freehold` | `acceptingOffers` | `FreeholdContractEntity.acceptingOffers` |
| `RegionResponse.Leasehold` | `acceptingTenants` | `LeaseholdContractEntity.acceptingTenants` |
| `RegionResponse.Leasehold` | `terminationEffectiveDate`, `terminatedByRole` (nullable) | `LeaseholdContractEntity` |
| `RegionResponse.Auction` | `auctioneer`, `startDate`, `minBid`, `minStep`, `biddingDurationSeconds`, `paymentDurationSeconds` | `FreeholdContractAuctionEntity` |
| `SearchResponse.Result` / `RegionListResponse.Entry` | `state` | `getRegionState` / `getAllRegionsWithState` |
| `/v1/regions` | `world` filter parameter | trivial `WHERE` on the existing page query |

`state` on list rows matters because a consumer rendering a browse grid currently
has to call `/v1/region` once per row to learn whether a plot is sold or for sale.

Note this one is **not** free. `RegionListResponse.Entry` is identity only
(`worldGuardRegionId`, `world`) and its page query reads `RealtyRegion` alone in a
fixed total order; carrying `state` needs a join, not a projection tweak. Everything
else in the table is a field already loaded and simply not serialised.

The two rows turned out to differ in cost. `SearchResponse.Result` is cheap: the
search query already joins `Contract` to `FreeholdContract`/`LeaseholdContract` and
each `UNION` branch knows its own contract type, so `state` is a `CASE` over
`titleHolderId`/`tenantId` nullity on columns already in hand.

`RegionListResponse.Entry` needs the joins added, via new `selectPageWithState` /
`selectPageWithStateByWorld` queries projecting `RegionStateRow`. What it must not do
is call `getRegionState` per row: that is two queries per region, and
`getAllRegionsWithState` -- the obvious-looking helper -- is worse still, adding a
placeholder map per region and *dropping* regions with no contract rather than
reporting them. A listing has to report them, so the two rows disagree on nullability
by design: `state` is nullable on `/v1/regions`, which lists every registered region,
and non-null on `/v1/regions/search`, where a row exists only because a contract
matched.

## B. Region-scoped endpoints

### `GET /v1/region/history?world=&region=&type=&since=&player=&page=&pageSize=`

The HTTP form of `/realty history`. Backed directly by
`RealtyBackend.searchHistory(...)`, which fans out over the three
`*HistoryMapper.searchHistory` SQL providers and returns
`HistoryResult(entries, totalCount)`.

- `type` is one of `HistoryEventType` (`BUY`, `AUCTION_BUY`, `OFFER_BUY`, `RENT`,
  `RENEW`, `LEASEHOLD_EXPIRY`, `SET_PRICE`, `TERMINATE`, …). Reject unknown values
  with `400 INVALID_EVENT_TYPE`.
- `since` is an ISO-8601 instant. The command takes a relative duration; an absolute
  timestamp is the better HTTP contract, and `now - duration` is a trivial
  client-side conversion.
- Entries are polymorphic, mirroring the sealed `HistoryEntry`: a `kind`
  discriminator of `freehold`, `leasehold` or `agent`, plus the record's fields.

```json
{
  "page": 1, "pageSize": 10, "totalCount": 3, "totalPages": 1,
  "entries": [
    { "kind": "freehold", "eventType": "BUY", "eventTime": "2026-08-30T14:02:11Z",
      "buyer": {"id": "...", "name": null}, "authority": {"id": "...", "name": null}, "price": 21500.0 },
    { "kind": "leasehold", "eventType": "RENT", "eventTime": "2026-08-12T09:40:00Z",
      "tenant": {"id": "..."}, "landlord": {"id": "..."}, "price": 800.0,
      "durationSeconds": 604800, "extensionsRemaining": 3 },
    { "kind": "agent", "eventType": "AGENT_ADD", "eventTime": "2026-08-01T18:00:00Z",
      "agent": {"id": "..."}, "actor": {"id": "..."} }
  ]
}
```

This single endpoint also answers "price history" (`type=BUY`) and "who has rented
this before" (`type=RENT`), so it removes the need for separate sold-price routes.

## C. Player-scoped endpoints

### `GET /v1/players/summary?player=`

A profile card, one query per counter, all already on the backend:

| Field | Backend method |
|---|---|
| `titleHeld` | `countRegionsByTitleHolder` |
| `landlordOf` | `countRegionsByLandlord` |
| `occupiedLandlordOf` | `countOccupiedLeaseholdsByLandlord` |
| `renting` | `countRegionsByTenant` |
| `authorityOver` | `countRegionsByAuthority` |

Cheaper than paging `/v1/players/regions` to the end just to get a total, and it is
the shape a Discord `/profile` command wants.

### `GET /v1/players/lookup?name=`

Bare name-to-UUID resolution. `ModuleClient.uuidOf` already exists, so this is a
handler and a spec change with no module work — it degrades to the existing
`NameLookup.Unavailable` path when the module is disabled or unreachable. It lets a
client resolve once and cache the UUID instead of paying the module hop on every
`/v1/players/regions` call, and it is the natural home for the `.Gamertag` encoding
rules.

### Aggregation, and where it stops being "already public"

Three routes here survive the leak audit only on an aggregation argument, and that is
worth stating rather than assuming. `/realty history <region>` is `default: true` and
`/realty list <player>` accepts any target, so every individual row in a player
history, a global activity feed, or an ownership leaderboard is already obtainable in
game. What is not obtainable is the *sweep*: a player must already know which region
to ask about, and no command enumerates one player's activity across regions or ranks
the server.

They are kept because the underlying facts are public and a server website is the
intended consumer — but they are the routes to reconsider first if the rule tightens
from "could a player learn this?" to "could a player learn this at this scale?".

### `GET /v1/players/history?player=&type=&since=&page=&pageSize=`

Every history event a player took part in, across all regions. **New query**: the
existing `searchHistory` is scoped to one region, so this needs a `UNION` across the
three history tables filtered by `buyerId`/`tenantId`/`landlordId`/`agentId`/`actorId`.
Same polymorphic entry shape as `/v1/region/history`, with `worldGuardRegionId` and
`world` added to each entry.

### Removed: offers

`GET /v1/players/offers?player=&direction=inbound|outbound` is dropped on leak
grounds. The backend has ready-made views for it (`listInboundOffers` /
`listOutboundOffers`), and `realty.command.offer.inbox` / `outbox` are both
`default: true`, so an earlier draft proposed reserving the path for a later
authenticated version.

That was wrong on the merits: the commands are **self-scoped**. They show the calling
player their own offers, and no permission, op or otherwise, lets one player enumerate
another's. A route taking an arbitrary `?player=` therefore has no in-game counterpart
at any permission level. It is not a route waiting on authentication; it is a route
that should not exist in a read-only public API. Dropped, not reserved.

`offerCount` on `RegionResponse.Freehold` goes with it, for the same reason. It was
proposed as a safe public stand-in "revealing demand without identities", but there is
no command that tells a passer-by how many offers stand on someone else's plot, so the
aggregate is itself the leak.

### Removed: modifications and signs

`GET /v1/players/modifications?player=` and `GET /v1/region/signs?world=&region=` are
both dropped, but on scope rather than on leak grounds: **neither is needed at this
time.** No consumer has asked for either, and each carries ongoing cost -- a schema, a
handler, an OpenAPI block and a conformance obligation -- for a demand that does not
yet exist.

Recording the distinction matters because it says what would bring each back. Offers
would need the disclosure rule itself to change; these two need only a consumer with a
use for them. If that consumer appears:

- **Modifications** would still need the disclosure question answered first.
  `ModifyCommandGroup`'s inbox and outbox take no target argument -- both read
  `sender.getUniqueId()` directly -- so pending lease terms reach nobody in game but
  the two parties, and the same objection that removed offers would apply.
- **Signs** carry no such objection in principle: the block positions are visible to
  anyone walking past. But `realty.command.sign.list` is `default: op`, so a queryable
  dump of every sign coordinate is a wider capability than any player has, and the
  route would want that squared away rather than assumed.

## D. Market and server-wide endpoints

### `GET /v1/stats`

The v1 spec lists statistics as a deliberate omission that is "cheap to add later".
All ten counters exist on `RealtyBackend` and nothing in `realty-paper` calls them,
so this is their first consumer:

```json
{
  "regions": 412,
  "freehold": { "contracts": 300, "occupied": 214, "averagePrice": 18250.5 },
  "leasehold": { "contracts": 112, "occupied": 87, "averagePrice": 640.0, "averageDurationSeconds": 604800 },
  "activeOffers": 19,
  "activeAuctions": 4
}
```

Methods: `countAllRegions`, `countAllFreeholdContracts`, `countOccupiedFreeholdContracts`,
`averageFreeholdPrice`, `countAllLeaseholdContracts`, `countOccupiedLeaseholdContracts`,
`averageLeaseholdPrice`, `averageLeaseholdDurationSeconds`, `countActiveOffers`,
`countActiveAuctions`. Nine round trips per call; the first place a short response
cache would pay for itself.

### `GET /v1/tags`

Every tag id in use with its region count, from `getAllTagIds` + `countRegionsByTag`.

```json
[ { "id": "commercial", "regionCount": 42 }, { "id": "waterfront", "regionCount": 7 } ]
```

A consumer of `/v1/regions/search?tag=` currently has no way to discover valid tag
values. Display names stay out — they live in the plugin's `RealtyTags` config, same
as the existing `tags` array.

### `GET /v1/auctions?world=&sort=ending_soon|highest_bid&page=&pageSize=`

Auction browsing, the other named omission. `countActiveAuctions` exists; the paged
listing is a **new query** over `FreeholdContractAuction WHERE ended = 0` joined to
`RealtyRegion` and the highest bid. Each row carries region identity, `minBid`,
`highestBid`, and the computed bidding deadline (last bid time, or `startDate`, plus
`biddingDurationSeconds`). This is what a "live auctions" panel or bot countdown
needs.

### `GET /v1/activity?type=&world=&since=&page=&pageSize=`

A server-wide feed of recent history events, newest first. **New query**: the same
three-table `UNION` as the player history endpoint, without a player filter. A
default `type` set of `BUY`, `AUCTION_BUY`, `OFFER_BUY`, `RENT` gives a "recent
sales and lettings" ticker; the full set gives an audit trail. This is the single
most useful route for a Discord bot polling for announcements.

### `GET /v1/leaderboard/owners?page=&pageSize=`

Title holders ranked by plot count. `FreeholdContractMapper.selectPlotCountsByTitleHolder`
returns `PlotOwnerCount(titleHolderId, plotCount)` already; it just needs paging.
Ownership is public via `/realty list`, so this exposes nothing new.

### `GET /v1/leases/ending?world=&within=&page=&pageSize=`

Leaseholds whose `endDate` falls within the next `within` seconds, soonest first,
plus those carrying a `terminationEffectiveDate`. **New query** on
`LeaseholdContract`. It is a "coming to market" view. Alternative with less surface:
add `sort=end_date_asc` and an `endingWithin` filter to `/v1/regions/search` instead.

## E. Endpoints needing new query-service routes

The module is live; these are the three routes it does not yet answer. Each degrades
to `null`/`502` without the module, per the existing degradation rule.

### `GET /v1/region/members?world=&region=`

WorldGuard owners and members, which the v1 spec already flags as "one endpoint and
one nullable field to add later". Needs a new module route reading `ProtectedRegion`
owners/members on the main thread, same discipline as the dimensions route.

### `GET /v1/regions/at?world=&x=&z=&y=`

Which registered region contains a block. The module route calls
`RegionManager.getApplicableRegions`, then `realty-rest` intersects the result with
`RealtyRegion` so unregistered WorldGuard regions are dropped. This is what a map
click, a dynmap-style popup, or a "what plot am I standing on" bot command needs.

`y` is optional, and the two cases are genuinely different queries rather than one
with a default:

- **`y` given** — a true point-in-region test at that block. `getApplicableRegions`
  is asked for exactly that `BlockVector3`, and a region whose `minY`/`maxY` band
  excludes the point does not match. This is what a player standing somewhere means
  by "what plot am I on".
- **`y` omitted** — a column test: every region whose horizontal footprint contains
  `(x, z)`, at any height. This is what a map click means, since a 2-D map has no
  `y` to send, and it is why the answer is a list rather than one region — stacked
  regions in the same column all match.

Do not paper over the difference by defaulting `y` to the world's build floor or to
sea level: both silently answer the column question wrongly on a server that uses
vertical subdivision, and a caller cannot tell from the response which query it got.
The response says which test ran, so a consumer can too:

```json
{ "test": "column", "regions": [ { "worldGuardRegionId": "plot_a", "world": {"id": "...", "name": "world"} } ] }
```

A `y` that is not an integer is a `400 INVALID_COORDINATE`, the same as for `x`
and `z`.

### `GET /v1/worlds/geometry?world=`

Every registered region's dimensions in one world, for drawing a map overlay without
one module call per region. Needs a batch module route (`POST /regions/dimensions`
taking a list of ids). Bounded work, but it is the first route where the main-thread
hop cost should actually be measured; the v1 spec's suggested short-TTL cache would
live here.

## Suggested order

1. Section A field additions, `GET /v1/tags`, `GET /v1/players/lookup`. Handlers and
   spec only, no new SQL and no module work. Unblocks search consumers.
2. `GET /v1/region/history`, `GET /v1/players/summary`, `GET /v1/stats`,
   `GET /v1/leaderboard/owners`. Existing queries, new handlers only.
3. `GET /v1/activity` and `GET /v1/auctions`. New SQL, but the two routes an
   external bot or website will want most.
4. Section E, which is the only remaining module work; `/v1/regions/at` first.

The bidirectional OpenAPI conformance test (`OpenApiConformanceTest`) means each of
these lands as a spec change and a handler in the same PR, which is the right
granularity to review them one at a time.
