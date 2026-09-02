# Realty query-service module — Design

Date: 2026-09-02
Status: Approved, pending implementation plan

Companion spec: `2026-09-02-realty-rest-api-design.md` (the public API that calls
this module). That document owns the public contract; this one owns the module.

## Purpose

`realty-rest` runs as a separate process and can only see MariaDB. Two kinds of
data it needs are not there and cannot be: WorldGuard region geometry, and player
names. Both live in the Minecraft process.

This module runs inside that process and answers for both — over a private HTTP
endpoint for `realty-rest`, and through an in-process service interface for
Realty core and sibling modules.

## Why live, not projected

An earlier draft persisted geometry into MariaDB and kept it fresh with a
fingerprint sweep, an enable-time full walk, and an operator refresh command.
That is dropped. The module serves live.

The deciding fact: **WorldGuard 7.0.18 exposes no region lifecycle events.** Every
event class in `worldguard-bukkit` and `worldguard-core` is a protection or
blacklist event (`BreakBlockEvent`, `DisallowedPVPEvent`, `BlacklistEvent`, …).
There is no add, remove or redefine event to listen to. A projection therefore
cannot be kept correct against `/rg redefine`, an edit by another plugin, or a
direct file change — it can only be re-swept and hoped over.

Serving live deletes the cache and, with it, everything that existed to manage the
cache's staleness: two tables, a migration, the fingerprint scheme, the enable
sweep, a `measuredAt` field, and the operator refresh command. Geometry is never
stale because there is nothing to be stale.

Worlds are handled differently — projected into MariaDB by Realty core — because
they are a bounded, effectively immutable set *and* Bukkit does fire
`WorldLoadEvent`/`WorldUnloadEvent`. That projection is specified in the API
document, not here; this module has no part in it.

## Module layout

A new Gradle subproject **`realty-paper-adapters/query-service`**, built and
published like the existing adapters and installed by the operator into
`plugins/Realty/modules`. It is not bundled in the plugin jar.

It ships a `module-manifest.yml` at the jar root naming its entry class, with
`expected-plugin-class: io.github.md5sha256.realty.Realty`, and extends
`SimplePluginModule<Realty>` as the other adapters do. It needs its own
`compileOnly` on `plugin-infrastructure`, since `realty-paper` exposes that as
`implementation` rather than `api`.

Dependencies: **Javalin** and **Jackson** — the same HTTP stack as `realty-rest`,
so there is one to learn rather than two. WorldGuard and Paper are `compileOnly`.

`reloadable: true`: a reload re-reads config. Per the project's module rules, that
requires overriding `reload()` — `reloadable: true` alone does not re-read
anything.

## Configuration

Unlike `realty-rest`, this module runs on a real filesystem inside the game
server, so the project's config rules apply in full: a `config.yml` the operator
edits, and a `defaults/default-config.yml` reference copy rewritten on **every**
start via `writeReferenceCopy`, following `AdapterConfig`.

| Key | Default | Meaning |
|---|---|---|
| `bind-host` | `127.0.0.1` | Interface to bind. Localhost by default |
| `port` | `8123` | Port to bind |
| `shared-secret` | *(empty)* | Required secret; empty disables the HTTP server entirely |
| `request-timeout-ms` | `1000` | Cap on a main-thread round trip before the request fails |

An empty `shared-secret` disables the HTTP server rather than running it open. A
misconfigured deployment then fails closed, and `realty-rest` degrades to nulls as
it does for any unreachable module — which is a safe outcome, unlike silently
exposing an unauthenticated query port on the game server. The module logs a
`WARNING` naming the reason on that path.

## HTTP surface

This endpoint is **private**: a seam between two of our own services, not a second
public API. Every route requires the shared secret in a header; a missing or wrong
secret is `401`. It binds `127.0.0.1` by default, so a same-host deployment is
closed to the network without further configuration, and an operator running
`realty-rest` in a separate container opens it deliberately.

The routes are unversioned. Both sides of this seam ship from one repository and
upgrade together; a version prefix here would be ceremony. The public API's `/v1`
prefix exists because its consumers are third parties, which is not true here.

### `GET /regions/{worldId}/{regionId}/dimensions`

Shape, ordered points, and vertical bounds, read live from WorldGuard.

```json
{
  "shape": "POLYGONAL",
  "minY": 62,
  "maxY": 140,
  "points": [ {"x": 104, "z": -88}, {"x": 131, "z": -88},
              {"x": 131, "z": -61}, {"x": 104, "z": -61} ]
}
```

`shape` is `CUBOID` or `POLYGONAL`. For a cuboid the points are the four corners
of its footprint, so a consumer can treat both shapes uniformly.

Bounding box, footprint area and volume are all derivable from these fields, and
so are deliberately neither stored nor sent. In particular the module does **not**
reuse `SubregionSelectionValidator.blockVolume`: that operates on a WorldEdit
`Region` (a player selection), not a WorldGuard `ProtectedRegion`, and volume is
not part of this payload. Two block-counting implementations that could drift
apart is a worse outcome than a consumer multiplying two numbers.

`404` when no such region exists in WorldGuard.

### `POST /players/names`

Batch UUID → name. The batch form is the one that matters: a ten-region response
resolved one name at a time would turn the N+1 removed from SQL into an N+1 over
the network. A single-lookup `GET /players/{uuid}/name` exists alongside for
convenience.

Unresolvable UUIDs come back with a null name rather than being omitted, so the
caller can distinguish "no name" from "not asked".

### `POST /players/uuids`

The reverse direction, name → UUID, backing the public API's `?player=` lookup.

A body rather than a query string, deliberately: player names are not reliably
URL-safe. Java Edition names are (`[a-zA-Z0-9_]`, 3–16 characters), but Floodgate
prefixes an Xbox gamertag with `.`, and gamertags may contain spaces — a real name
on this server can be `.Cool Guy 123`. A JSON body sidesteps encoding entirely on
this internal hop. (The *public* API cannot do the same, since `GET` cannot carry
a body; it uses query parameters with strict encoding instead.)

Unknown names come back with a null id.

## Player names

Name resolution delegates to Realty's existing `SquirrelIdUsernameResolver`. The
module adds no cache of its own; that one already exists and is already correct:

- SquirrelId over a `SQLiteCache`.
- Prefers the server's local usercache, which holds the right name for anyone who
  has joined — **including Bedrock/Floodgate players whose `.`-prefixed names
  Mojang cannot resolve**.
- Falls back to the Mojang-backed profile service only for a UUID the server has
  genuinely never seen, and returns the UUID string on failure.

Reverse lookup uses SquirrelId's name-based resolution on the same service.

Because that cache is on the game server's disk, `realty-rest` cannot read it
directly — projecting names into MariaDB was considered and rejected as an
unbounded, volatile set. This HTTP hop is what makes the existing cache reachable
from another process.

## In-process service

The same lookups are registered as a `PlayerNameService` interface in
`realty-paper-api`, alongside the existing `RegionProfileService`. Core, sibling
modules and other plugins resolve it rather than hand-rolling
`Bukkit.getOfflinePlayer(uuid).getName()` — there are eight such sites in
`realty-paper` today (`InfoCommand`, `HistoryCommand`, `AgentInviteCommand`,
`AgentInviteWithdrawCommand`, `AgentRemoveCommand`, `AuctionCommandGroup`,
`ModifyCommandGroup`, `OfferCommandGroup`).

One implementation, two doors. Migrating those call sites is **not** part of this
work; the interface exists so they can be migrated deliberately later.

The interface lives in `realty-paper-api` rather than in the module because module
classes load in a `URLClassLoader` parented to Realty's loader, which makes them
awkward for outside plugins to consume. The interface must be on the shared side.

## Threading

WorldGuard's `RegionManager` is not thread-safe, so a Javalin worker thread cannot
read it directly.

Each dimensions request schedules a task onto the main thread, completes a
`CompletableFuture`, and responds under `request-timeout-ms` — the same discipline
`InfoCommand` already follows when it resolves members. On timeout the request
returns `504` and `realty-rest` degrades that field to null.

The measurement itself is a handful of O(1) field reads (`getType`, `getPoints`,
`getMinimumPoint`, `getMaximumPoint`), so the thread hop is the cost, not the
work. This is the first thing to measure if the endpoint ever needs optimising,
and a short-TTL in-memory cache refreshed on the main thread is the obvious answer
if it does. It is not built now, because nothing has measured a need.

Name resolution already returns a `CompletableFuture` and needs no main-thread
hop.

## Lifecycle

`ModuleLifecycleManager` starts modules **last** in `onEnable` and stops them
first in `onDisable`. The HTTP server starts in the module's `initialize` and
stops in `shutdown`; an in-flight request must not outlive the server, so shutdown
awaits Javalin's stop before returning.

**The module registers no commands.** Modules start after commands are already
registered, and Paper does not accept new Brigadier commands at that point. This
is why the earlier draft's `/realty dimensions refresh` command could not have
lived here — and it is moot regardless, since serving live removed the cache that
command existed to refresh.

## Testing

- **Handler tests** against a Javalin test harness with a stubbed WorldGuard
  `RegionManager` and a stubbed name resolver: status codes, JSON shape, `401` on
  a missing or wrong secret, `404` on an unknown region.
- **Shape tests** asserting a cuboid and a polygon both serialise to the same
  four-field payload, and that a cuboid yields four footprint corners.
- **A threading test** asserting a request that cannot reach the main thread within
  `request-timeout-ms` returns `504` rather than hanging a Javalin worker.
- **Name fixtures including Bedrock forms** — `.Cool Guy 123` as well as `Notch` —
  in both lookup directions. A suite built only from Java-Edition names passes
  while every Bedrock player is unfindable.
- **A config test** asserting an empty `shared-secret` leaves the server unstarted
  and logs at `WARNING`, and that `defaults/default-config.yml` is rewritten on
  every start and itself parses.

## Deliberate omissions

- **WorldGuard member and owner lists.** The module is the only thing that *could*
  serve them, and `/realty info` does render them — but nothing has asked for them
  in the API. One endpoint and one nullable field to add later.
- **Region flags.** Same reasoning.
- **Any write path.** The module answers questions; it does not change WorldGuard
  or the database.
- **A response cache.** See *Threading*.
- **TLS.** The endpoint binds localhost by default. An operator exposing it across
  hosts is expected to front it with a reverse proxy, which is where TLS belongs.
