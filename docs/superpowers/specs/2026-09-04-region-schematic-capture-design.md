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
- A tick-sliced capture that never blocks the server for the whole copy, and a
  hard cap on region volume.
- Persistence of one schematic per region (re-capture replaces).
- A read-only REST endpoint serving the raw schematic bytes.

Out of scope: the TypeScript frontend itself (confirmed separately to consume
raw bytes via `schematic-renderer`'s `async () => ArrayBuffer` loader, which
also accepts `.litematic`/`.schematic` — the format choice here does not lock
the frontend in); automatic/triggered capture on region create or modify;
historical versions of a schematic (only the latest is kept); write/delete
endpoints on `realty-rest` (v1 there is read-only by design).

## Why the WorldEdit clipboard API, live capture, and BLOB storage

**Capture library — the WorldEdit clipboard API, already on the classpath.**
No new dependency is added. WorldEdit 7.3.18 already resolves transitively
through the existing `compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.18")`
in `realty-paper/build.gradle.kts`, and `realty-paper` already imports
`com.sk89q.worldedit` types directly (`BukkitAdapter`, `CuboidRegion`,
`BlockVector3` in `CreateCommand`, `SubregionState`, `SignCommand`). That jar
contains `BlockArrayClipboard`, `ForwardExtentCopy`, `ClipboardFormats`, and
`BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC` — verified directly against the
resolved artifact.

Compiling against the WorldEdit API rather than FAWE specifically is
deliberate: FAWE is a drop-in that provides the same `com.sk89q.worldedit`
classes at runtime, so one implementation works whether the operator runs
WorldEdit or FAWE. Either way Realty does not reimplement the NBT/palette
format itself.

**Live capture, not reading `.mca` files directly.** Reading Anvil region
files directly was considered (see exploration below) and rejected for the
same reason `query-service`'s `MainThreadRegionSource` reads WorldGuard
geometry live rather than from a projection: "there is no WorldGuard lifecycle
event to keep a projection correct." A block edited in memory but not yet
flushed to disk would silently produce a stale capture, and concurrent writes
from the live server to the same `.mca` file while reading it need locking
this project does not otherwise need. Capture therefore reads the live world
on the main thread, the same way every other Bukkit/WorldGuard read in this
codebase does — spread across ticks rather than done in one, per *Tick-sliced
copy*.

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
the "in memory" requirement. The cooldown duration is a new `Settings` key,
`schematic-capture-cooldown-seconds`, defaulting to `3600` — one hour. It is
a `long` of seconds rather than a parsed duration string, matching the
existing `offer-payment-duration-seconds` and
`lease-termination-notice-seconds` keys. Zero disables the cooldown; only a
negative is corrected to the default. On capture, if `now - lastCapture < cooldown` and
`--force` was not passed, the command rejects with the remaining time
rendered via `DurationFormatter` (never `Duration.toString()`, per existing
project convention).

**`--force`.** A boolean flag parsed by Cloud, gated behind a second
permission, `realty.command.schematic.capture.force` (the
`realty.command.<group>.<sub>` shape every existing permission uses, e.g.
`realty.command.set.price`). `--force` bypasses only the cooldown check —
normal capture still requires `realty.command.schematic.capture`
regardless of `--force`. Passing `--force` without the force permission is
rejected outright, before the cooldown check runs, rather than silently
ignored. Both permission checks run on the main thread, per the existing
permissions-main-thread convention. Both permissions are added to
`paper-plugin.yml`.

**Volume cap.** Before anything is copied, the region's volume
(`width × height × length`, from its bounds — no block reads) is checked
against a new `Settings` key, `schematic-max-volume`, defaulting to
`1000000`. Above it the command refuses and names both the region's volume
and the cap.

The cap is **hard**: `--force` does not lift it, and there is no second
permission that does. It exists to protect the database write — MariaDB's
`max_allowed_packet` (commonly 16–64 MB) rejects an oversized `LONGBLOB`
regardless of what the column type allows — and a limit that can be waived by
whoever hits it does not protect anything. An operator who genuinely needs
larger captures raises the setting, which is a deliberate act with a
server-wide blast radius, rather than appending a flag.

The cap matters more once capture is tick-sliced: an oversized region no
longer announces itself by freezing the server, so without the cap it would
run invisibly for minutes and fail only at the final write.

**Capture procedure.** The block reads stay on the main thread — Paper's
`AsyncCatcher` throws `IllegalStateException` on chunk access from any other
thread, so off-thread reads are not available to a plugin compiling against
the WorldEdit API. What changes is that the copy no longer runs to completion
inside one tick:

1. Resolve the WorldGuard region's bounds via the existing region-resolution
   path, and reject above `schematic-max-volume`.
2. Copy the region into a `BlockArrayClipboard` **across ticks**, a budgeted
   number of blocks per tick (see below), rather than in one
   `ForwardExtentCopy`.
3. Once the copy completes, encode to Sponge v3 bytes and write to the
   database **off the main thread** — neither step touches the world, so both
   are safe there, and they are the expensive half.
4. Return to the main thread to report the result to the player.

### Tick-sliced copy

A `Region` is `Iterable<BlockVector3>`, so the copy holds an
`Iterator<BlockVector3>` across ticks and drains a fixed budget per tick from
a repeating task. The budget is a new `Settings` key,
`schematic-capture-blocks-per-tick`, defaulting to `20000` — the same shape as
the existing `profile-reapply-per-tick` setting, which already establishes
per-tick budgeted work as this plugin's way of spreading a large job.

Blocks are read with `getFullBlock`, and each block entity is then **stripped to
its identity**: the `id` is kept and everything else discarded.

Both halves matter, and an earlier draft got this wrong by capturing block
state alone. A sign or a chest has no geometry in its block model — vanilla's
`models/block/oak_sign.json` and `chest.json` define only a particle texture
and no elements, because the game draws both with a block-entity renderer. A
schematic without block entities therefore does not render those blocks
*untextured*; it does not render them **at all**. A plot comes back as a few
objects floating in space and reads as a failed capture. This was observed,
not theorised.

What is discarded is the payload: a chest's `Items`, a sign's text. Neither is
of any use to a preview, and this schematic is served over a public,
unauthenticated endpoint, so a copy of every chest's contents is exactly what
should not be in it. The filter is an **allowlist** keeping only `id`, so a
block entity type nobody anticipated cannot leak a field nobody thought to
strip.

Three lifecycle concerns the implementation must handle, none of which existed
when the copy was atomic:

- **Concurrent captures of the same region.** An in-flight set keyed by
  region-and-world rejects a second capture while one is running. The cooldown
  usually prevents this, but `--force` bypasses the cooldown, so the guard
  cannot rely on it.
- **Plugin disable mid-capture.** Running tasks are cancelled in `onDisable`;
  a partially-filled clipboard is discarded rather than stored. A half-region
  schematic is worse than none, because nothing downstream could tell.
- **World unload mid-capture.** The capture aborts and reports the reason
  rather than reading from an unloaded world.

The result is a capture that is *non-blocking*, not *asynchronous*: the world
reads remain on the main thread throughout, spread thin enough not to stall a
tick. Calling it async in code or messages would misdescribe it.

## Persistence

New table, one row per region. **A re-capture always replaces the previous
schematic, silently and unconditionally** — there is no confirmation, no
flag, and no versioning. The latest snapshot is the only one that matters to
a preview, so keeping older ones would grow the table without a reader; and
requiring a flag to overwrite would make the common case (refresh a plot
after building on it) the awkward one. The same one-row-per-region shape used
for contracts.

Only `capturedAt` accompanies the bytes. An earlier draft also stored
`capturedBy`, but nothing ever read it — the REST endpoint does not expose it
and no command consults it — so it was removed rather than kept as an audit
trail nobody consults. It also forced a nil-UUID sentinel for console
captures, which had no meaning beyond "not a player".

It keys on `realtyRegionId`,
matching how every other Realty table references a region — `RealtyRegion`
has an `INT AUTO_INCREMENT` primary key, not a region UUID — and uses the
`UUID` column type the existing migrations use (`V16__realty_worlds.sql`),
not `BINARY(16)`:

```sql
CREATE TABLE IF NOT EXISTS RealtySchematic
(
    realtyRegionId INT      NOT NULL PRIMARY KEY,
    data           LONGBLOB NOT NULL,
    capturedAt     DATETIME NOT NULL
);
```

New migration `V17__realty_schematics.sql`, registered as the next entry in
`MariaSchemaMigrator.DEFAULT_MIGRATIONS`. New `RealtySchematicMapper`
(base interface, method signatures only) plus a `MariaRealtySchematicMapper`
implementation with the SQL, following the existing mapper split, and
registered on both `SqlSessionWrapper` and `MariaSqlSession`. A
`RealtySchematicEntity` record alongside the other entities.

Mapper methods take `(String worldGuardRegionId, UUID worldId)` and JOIN
through `RealtyRegion` internally rather than making callers resolve
`realtyRegionId` first — the established convention for command-facing
queries in this codebase.

`RealtyBackend` gains two methods: one to upsert a captured schematic, one to
fetch the current bytes for a region (nullable — no schematic captured yet).

## REST delivery

`realty-rest` adds `GET /v1/region/schematic?world=...&region=...`. The
query-parameter form matches every existing region endpoint (`/v1/region`,
`/v1/region/history`, `/v1/region/members`) — this service does not use path
parameters for region identity, because a region is identified by a
world-plus-name pair, not a single id.

- `200` with `Content-Type: application/octet-stream` and the raw `.schem`
  bytes, when a schematic exists for the region.
- `404` `SCHEMATIC_NOT_FOUND` when the region exists but has no captured
  schematic.
- `404` `REGION_NOT_FOUND` for an unknown region, via the existing
  `ApiException.notFound` path used by `RegionHandler`.
- Missing `world` or `region` params are rejected by the existing
  `QueryParams.required` handling, unchanged.

The new route is added to `RealtyRestServer.ROUTES`, which
`OpenApiConformanceTest` asserts in both directions against `openapi.yaml` —
so the route and its documentation land together or the build fails.

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
const bytes = await fetch(
    `/v1/region/schematic?world=${encodeURIComponent(world)}&region=${encodeURIComponent(region)}`
).then(r => r.arrayBuffer());
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
- Volume cap test: a region above the cap is rejected before any copy starts,
  and `--force` does not lift it. Volume is computed from bounds, so this
  needs no world.
- Tick-slicing test: a capture spanning more blocks than the per-tick budget
  completes over multiple ticks and yields the same clipboard contents as an
  unsliced copy of the same region; a capture cancelled part-way stores
  nothing. Driven by a fake scheduler that runs ticks on demand, so the test
  needs no server and does not sleep.
- `realty-rest`: extend the existing conformance-test style to cover
  `200` (bytes returned), `404` (no schematic), and the existing
  region-not-found case.

## Alternatives considered

**Reading `.mca` region files directly**, bypassing Bukkit and WorldEdit
entirely. Rejected: reimplements Anvil chunk/palette decoding this codebase
does not otherwise need, and reintroduces the staleness/locking problem that
`query-service`'s live-read pattern exists specifically to avoid.

**Automatic capture on region create/update.** Rejected for v1: couples
capture into every mutation path and costs I/O on writes that do not need a
preview. An on-demand command is cheaper and sufficient; automatic capture
remains a candidate for a later version.

**Self-implemented Sponge Schematic NBT writer.** Rejected: WorldEdit
already implements the spec correctly, is well maintained, and is already
on the compile classpath — reimplementing it would duplicate that work
while adding no independence, since the plugin depends on WorldGuard (and
therefore WorldEdit) regardless.

**Adding FAWE as an explicit dependency.** Unnecessary for the capture
itself: the clipboard API this feature uses is WorldEdit's, which FAWE
implements. Compiling against WorldEdit covers both installations with no new
coordinate in the build.

**Truly asynchronous capture via FAWE.** FAWE processes edits asynchronously
and keeps WorldEdit API compatibility, so block reads could run off the main
thread entirely — plain WorldEdit cannot, because Paper's `AsyncCatcher`
throws on chunk access from any other thread. Rejected because it would make
FAWE a hard runtime requirement for the feature (or force two capture paths
to maintain, one per install), where tick-slicing achieves the goal — never
stalling the server — on every install with one path and no dependency.

**Detecting FAWE at runtime and branching.** Rejected as the worst of both:
two capture paths, of which the async one would be exercised only on operators'
servers and never in tests, for a benefit tick-slicing already delivers.

**A soft size cap that `--force` waives.** Rejected: the cap guards the
database write, and the flag would be reached for precisely by the person
whose capture is too large. The setting is the escape hatch.
