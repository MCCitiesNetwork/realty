# Region Schematic Capture — Design

Date: 2026-09-04
Status: Approved, pending implementation plan

Companion specs: `2026-09-02-realty-rest-api-design.md` (the REST service this
feature adds an endpoint to), `2026-09-02-realty-query-service-design.md` (a
sibling example of an optional live-world concern — explicitly not the pattern
this feature follows).

## Purpose

Let an operator or landlord capture a region's current blocks as a Sponge
Schematic v3 (`.schem`), store it, and serve it over `realty-rest` so a
TypeScript frontend can render a 3D preview of the plot before a player
commits to buying, renting, or bidding on it.

## Scope

In scope:
- A command to capture a region's blocks into a schematic, with a per-region
  cooldown and an operator override.
- Persistence of one schematic per region (re-capture replaces).
- A read-only REST endpoint serving the raw schematic bytes.

Out of scope: the TypeScript frontend itself (confirmed separately to consume
raw bytes via `schematic-renderer`'s `async () => ArrayBuffer` loader, which
also accepts `.litematic`/`.schematic` — the format choice here does not lock
the frontend in); automatic/triggered capture on region create or modify;
historical versions of a schematic (only the latest is kept); write/delete
endpoints on `realty-rest` (v1 there is read-only by design).

## Why FAWE, live capture, and BLOB storage

**Capture library — FastAsyncWorldEdit (FAWE).** Added as a `compileOnly` /
softdepend, the same shape as the existing WorldGuard dependency. FAWE's
`Clipboard` API writes a spec-correct Sponge Schematic v3 in one call, so
Realty does not reimplement the NBT/palette format itself.

**Live capture, not reading `.mca` files directly.** Reading Anvil region
files directly was considered (see exploration below) and rejected for the
same reason `query-service`'s `MainThreadRegionSource` reads WorldGuard
geometry live rather than from a projection: "there is no WorldGuard lifecycle
event to keep a projection correct." A block edited in memory but not yet
flushed to disk would silently produce a stale capture, and concurrent writes
from the live server to the same `.mca` file while reading it need locking
this project does not otherwise need. Capture therefore runs on the main
thread against the live world, the same way every other Bukkit/WorldGuard
region read in this codebase does.

**BLOB storage in MariaDB, not a file + shared volume.** `realty-rest` already
reuses `realty-backend`'s persistence layer verbatim for every other entity —
it has no existing notion of a filesystem shared with the Paper server, and
introducing one only for this feature would be a new coupling the rest of the
REST design deliberately avoids. Storing the bytes as a `LONGBLOB` means
`realty-rest` serves them through the same DB read path as everything else.

**Command placement — core `realty-paper`, not an adapter module.** Unlike
`query-service`, this feature needs no persistent live-world projection and no
opt-in HTTP surface of its own; it is one command plus one table, the same
shape as any other command group already in core.

## Command

`/realty schematic capture <region> [--force]`

New `SchematicCommandGroup` record, following the shape of
`AuctionCommandGroup`: implements `CustomCommandBean`, resolves `<region>` via
the existing `WorldGuardRegionResolver`.

**Cooldown.** An in-memory `Map<RegionKey, Instant>` (last capture time),
held by the command group, resets on plugin restart — no persistence, per
the "in memory" requirement. The cooldown duration is a new `Settings` key
(`schematic-capture-cooldown`), parsed with the existing
`DurationParserUtil`. On capture, if `now - lastCapture < cooldown` and
`--force` was not passed, the command rejects with the remaining time
rendered via `DurationFormatter` (never `Duration.toString()`, per existing
project convention).

**`--force`.** A boolean flag parsed by Cloud, gated behind a second
permission, `realty.schematic.capture.force`. `--force` bypasses only the
cooldown check — normal capture still requires `realty.schematic.capture`
regardless of `--force`. Passing `--force` without the force permission is
rejected outright, before the cooldown check runs, rather than silently
ignored. Both permission checks run on the main thread, per the existing
permissions-main-thread convention. Both permissions are added to
`paper-plugin.yml`.

**Capture procedure**, on the main thread:
1. Resolve the WorldGuard region's bounds in its world via the existing
   region-resolution path.
2. Build a FAWE `Clipboard` from those bounds; write it to an in-memory
   Sponge Schematic v3 byte array via FAWE's clipboard writer.
3. Pass the bytes to `RealtyBackend` to persist (replacing any existing row
   for that region).

## Persistence

New table, one row per region (replaced on re-capture — the same
one-row-per-region shape used for contracts):

```sql
CREATE TABLE RealtySchematic (
    regionId    BINARY(16)   NOT NULL,
    worldId     BINARY(16)   NOT NULL,
    data        LONGBLOB     NOT NULL,
    capturedAt  DATETIME     NOT NULL,
    capturedBy  BINARY(16)   NOT NULL,
    PRIMARY KEY (regionId, worldId)
);
```

New migration `V17__realty_schematics.sql`, registered as the next entry in
`MariaSchemaMigrator.DEFAULT_MIGRATIONS`. New `RealtySchematicMapper`
(base interface, method signatures only) plus a `MariaRealtySchematicMapper`
implementation with the SQL, following the existing mapper split. A
`RealtySchematicEntity` record alongside the other entities.

`RealtyBackend` gains two methods: one to upsert a captured schematic, one to
fetch the current bytes for a region (nullable — no schematic captured yet).

## REST delivery

`realty-rest` adds `GET /v1/regions/{id}/schematic`:
- `200` with `Content-Type: application/octet-stream` and the raw `.schem`
  bytes, when a schematic exists for the region.
- `404` when the region exists but has no captured schematic.
- The existing region-not-found handling applies unchanged for an unknown
  `{id}`.

Backed directly by the new `RealtyBackend` fetch method — no new coupling to
the game server's filesystem or to `query-service`.

Because `realty-rest` refuses to boot unless its compiled-in expected schema
version exactly matches the database's applied version, landing `V17`
requires bumping that constant in `realty-rest` in the same change, even
though the new endpoint is the only consumer of the new table.

The published OpenAPI 3.1 document gains this endpoint, consistent with the
existing "every endpoint is documented" scope for `realty-rest`.

## Frontend contract (informational)

Not implemented here, but the endpoint is designed to be consumed as:

```ts
const bytes = await fetch(`/v1/regions/${id}/schematic`).then(r => r.arrayBuffer());
// schematic-renderer's SchematicRenderer accepts:
// { [schematicId]: async () => ArrayBuffer }
```

`schematic-renderer` (Three.js + Rust/WASM meshing) reads `.schem`,
`.schematic`, and `.litematic` from raw `ArrayBuffer`/`Uint8Array` with no
file-path requirement, so this format choice does not constrain the frontend.

## Testing

- Mapper test: round-trip a byte array through insert/fetch, and
  replace-on-recapture.
- Command test: cooldown rejects a second capture within the window;
  `--force` bypasses it only with the force permission; `--force` without
  the force permission is rejected before the cooldown check; capture
  without any flag still requires the base capture permission.
- `realty-rest`: extend the existing conformance-test style to cover
  `200` (bytes returned), `404` (no schematic), and the existing
  region-not-found case.

## Alternatives considered

**Reading `.mca` region files directly**, bypassing Bukkit/FAWE entirely.
Rejected: reimplements Anvil chunk/palette decoding this codebase does not
otherwise need, and reintroduces the staleness/locking problem that
`query-service`'s live-read pattern exists specifically to avoid.

**Automatic capture on region create/update.** Rejected for v1: couples
capture into every mutation path and costs I/O on writes that do not need a
preview. An on-demand command is cheaper and sufficient; automatic capture
remains a candidate for a later version.

**Self-implemented Sponge Schematic NBT writer**, avoiding the FAWE
dependency. Rejected: FAWE already implements the spec correctly and is
well maintained; reimplementing it duplicates that work for no benefit,
and the project already accepts WorldGuard as a comparable soft dependency.
