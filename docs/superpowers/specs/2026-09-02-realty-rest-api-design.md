# Realty REST API — Design

Date: 2026-09-02
Status: Approved, pending implementation plan

Companion spec: `2026-09-02-realty-query-service-design.md` (the in-game module
this service calls). This document owns the public API; that one owns the module.

## Purpose

Expose Realty's public-facing property data over HTTP so external consumers — a
web map, a server website, a Discord bot — can read what `/realty info` and
`/realty list` show in game, without a Minecraft client.

v1 is read-only and unauthenticated. There are no accounts, no tokens, and no
write endpoints. Every response is data a player could already obtain by running
a command in game.

## The shape of the problem

Not all of Realty's data is in Realty's database. Contracts, prices, dates and
region identity are. WorldGuard geometry and player names are not — they live in
the Minecraft process.

The system is therefore two services with one seam between them:

- **`realty-rest`** — the public API, its own process, reading MariaDB.
- **`query-service`** — a Realty module inside the game server, answering for
  live geometry and player names over a secured HTTP endpoint.

Worlds are the one exception to that split, and are projected into MariaDB; see
*Worlds*.

## Scope

In scope for v1:

- The `/realty info` payload for a single region.
- The `/realty list` payload for a single player.
- A world listing.
- A health endpoint.
- A published OpenAPI 3.1 document describing all of the above.

Out of scope for v1: authentication, any write or mutation, auction browsing,
offer listings, and server-wide statistics. Each is a deliberate omission and a
candidate for a later version behind the same prefix scheme.

## Deployment model

`realty-rest` is a **standalone service**, not a plugin or a plugin module. It is
its own JVM process reading the same MariaDB database the Realty plugin writes
to.

This decouples API uptime from Minecraft server uptime and lets the API be
scaled, restarted and firewalled independently. The cost is a second deployment
to operate and a coupling to the database schema, addressed under *Schema
coupling*.

## Module layout

A new Gradle subproject **`realty-rest`**, registered in `settings.gradle.kts`,
depending on `realty-backend` (and transitively `realty-backend-api`). It uses
the existing `realty-conventions` plugin, so it inherits the Java 25 toolchain
and JUnit 5 setup.

It reuses the existing persistence layer verbatim — the MyBatis mappers, the
entity records, `Database`/`SqlSessionWrapper`, and `DatabaseSettings`. No SQL is
duplicated and no entity is redefined.

Dependencies added: **Javalin** (HTTP, embedding Jetty), **Jackson** (JSON),
**Shadow** (to produce `realty-rest-all.jar`). It does not depend on Configurate,
Paper, WorldGuard, or `plugin-infrastructure`.

## Startup sequence

1. Read configuration from environment variables. Fail fast, naming the missing
   variable, if a required one is absent.
2. Construct `MariaDatabase` from the resolved `DatabaseSettings`.
3. Construct `RealtyBackendImpl(database, nameResolver, dateFormatter,
   offerPaymentDurationSeconds)`, where `nameResolver` is
   `uuid -> completedFuture(uuid.toString())`, `dateFormatter` is an ISO-8601 UTC
   formatter, and `offerPaymentDurationSeconds` is `() -> 0` (unused on read
   paths).
4. Verify the schema version (see *Schema coupling*).
5. Log every resolved setting, with secrets redacted.
6. Start Javalin and register the `/v1` routes.

Note that the identity `nameResolver` remains in place. Names in responses do not
come from it; they come from the module, applied as an enrichment step after the
backend returns. Keeping the resolver as identity means no backend code path can
emit a name resolved by some other mechanism, so there is exactly one source of
names in the service.

## Configuration

Configuration is **environment variables only**. There is no config file.

This is a deliberate departure from the project's config-reference-copy rule in
`CLAUDE.md`. That rule exists so an upgrading operator can discover new and
changed keys; it presumes an operator-edited YAML file. `realty-rest`'s primary
deployment targets are Docker and Pterodactyl, where the filesystem is ephemeral
and the panel — not a file — owns the values. Shipping a `defaults/` copy of a
file nobody edits would document a workflow that does not exist.

The rule's *intent* is met three other ways: every resolved setting is logged at
startup with secrets redacted; startup fails with a message naming the specific
missing variable; and the full table below is documented in the subproject README
and declared in the Pterodactyl egg.

The module, which runs on a real filesystem inside the game server, keeps the
normal `config.yml` plus `defaults/` reference copy. The rule is departed from
only where it does not fit.

| Variable | Required | Default | Meaning |
|---|---|---|---|
| `REALTY_DB_URL` | yes | — | MariaDB JDBC URL, as `DatabaseSettings.url` |
| `REALTY_DB_USERNAME` | yes | — | Database username |
| `REALTY_DB_PASSWORD` | yes | — | Database password |
| `REALTY_REST_HOST` | no | `0.0.0.0` | Bind address |
| `REALTY_REST_PORT` | no | `8080` | Bind port |
| `REALTY_REST_MAX_PAGE_SIZE` | no | `100` | Upper bound on `pageSize` |
| `REALTY_REST_MODULE_URL` | no | — | `query-service` base URL; unset disables enrichment entirely |
| `REALTY_REST_MODULE_SECRET` | no | — | Shared secret, sent as a header |
| `REALTY_REST_MODULE_TIMEOUT_MS` | no | `1500` | Per-call timeout before degrading to null |

`RestSettings` is a plain record built from the environment alongside the reused
`DatabaseSettings` record.

## Schema coupling

The plugin owns the schema. `realty-rest` **never** calls `initializeSchema` and
never runs a migration.

At startup it reads the applied schema version and refuses to boot unless that
version is **exactly** the version this build was compiled against. Both
directions are rejected, for different reasons:

- A **newer** database may have changed the meaning of a column this build reads.
  Serving columns it misunderstands is worse than not starting.
- An **older** database may be missing tables this build depends on outright.
  `RealtyWorld` arrives in V16; without it, `/v1/worlds` and every `?world=`
  lookup fail at request time with a `500` and no indication of the real cause.

Either way the failure is loud, immediate, and names which side is behind — the
two messages give opposite instructions, because an operator who upgraded the
plugin and one who forgot to need opposite things.

The expected version is a constant in `realty-rest`, bumped whenever a migration
lands, whether or not the API reads what it adds. The cost of exactness is that an
irrelevant migration still forces a rebuild; that is accepted deliberately, because
a gate that guesses which migrations matter is one that eventually guesses wrong,
and its failure mode is a runtime `500` rather than a startup message.

## Read-only guarantee

The service calls only query methods on `RealtyBackend`. No v1 code path opens a
non-autocommit session or invokes a mutating backend method.

Where practical this is reinforced at the database level: the deployment
documentation recommends granting the API's database user `SELECT` only. That is
an operator action rather than something the code can enforce, so it is a
recommendation, not a guarantee.

## Worlds

The plugin's own `RealtyWorld` table maps world UUID to world name. Realty core
writes it — on enable for every loaded world, and on Bukkit's `WorldLoadEvent`
thereafter. It deliberately does not react to `WorldUnloadEvent`: rows are never
deleted, because a region in an unloaded world must still be nameable by the API.
It is defined by a new migration file, registered in
`MariaSchemaMigrator.DEFAULT_MIGRATIONS`.

This is the only projection in the design, and it is worth stating why it is not
inconsistent with refusing to project geometry or player names. Geometry changes
via `/rg redefine`, and WorldGuard fires no event that would let a projection stay
correct. Player names are an unbounded, genuinely volatile set. Worlds are
neither: a handful of rows, effectively immutable, and Bukkit *does* fire a load
event, so the table maintains itself without ever needing to shrink.

The payoff is that the primary lookup path does not depend on the module at all.
`?world=` resolves with a SQL join — no HTTP call, no cache, no TTL, no cold-start
failure — and it works even when no module is installed.

Core owning the write, rather than the module, is what makes that true.

## API surface

All public routes are `GET`, return `application/json`, and sit under the `/v1`
prefix. Versioning is by URL path prefix: a breaking change to a response shape
ships as `/v2` alongside a still-serving `/v1`.

Conventions:

- Money is a raw JSON number. No currency symbols, no separators —
  `CurrencyFormatter` is presentation and belongs to the consumer.
- Timestamps are ISO-8601 in UTC.
- Durations are integer seconds. `DurationFormatter` is likewise presentation.
- Every identity, player or world, is an object: `{"id": "...", "name": "..."}`
  with a nullable `name`.

### Addressing: query parameters, by name

End users have names, not UUIDs. Both primary routes therefore accept a name or a
UUID, passed as a **query parameter** rather than a path segment.

That position is not cosmetic. Neither kind of name is guaranteed URL-safe:

- A world's name is its folder on disk. `level-name=My World` is legal, and on
  Linux only `/` and NUL are forbidden in a directory name, so `%`, `#` and `?`
  are all possible.
- Player names are safe on Java Edition (`[a-zA-Z0-9_]`, 3–16 characters) but not
  via Floodgate, which prefixes an Xbox gamertag with `.` — and gamertags may
  contain spaces. `SquirrelIdUsernameResolver` already documents this case.

In a path segment, an unencoded `%` or `#` mis-routes or truncates before a
handler ever runs. In a query value the same input fails cleanly. Handlers must
therefore accept **both `%20` and `+`** as a space, since clients differ on which
they emit in query position.

A `GET` with a request body was considered and rejected: `fetch()` throws on it,
Swagger UI will not send one, and proxies strip it.

### `GET /v1/regions?world={name|uuid}&region={id}`

The `/realty info` payload. Backed by `RealtyBackend.getRegionInfo`,
`getRegionState`, and `RegionTagMapper.selectTagIdsByRegionId`, then enriched with
`dimensions` and player names from the module.

```json
{
  "worldGuardRegionId": "downtown_plot_14",
  "world": { "id": "8f4d...", "name": "world_nether" },
  "state": "FOR_SALE",
  "freehold": {
    "titleHolder": { "id": "3a1c...", "name": "Notch" },
    "authority":   { "id": "0000...", "name": "DCGovernment" },
    "price": 25000.0,
    "lastSoldPrice": 21500.0
  },
  "leasehold": null,
  "auction": {
    "endDate": "2026-09-05T18:00:00Z",
    "highestBid": { "bidder": { "id": "9b2e...", "name": null }, "amount": 26000.0 }
  },
  "dimensions": {
    "shape": "POLYGONAL", "minY": 62, "maxY": 140,
    "points": [ {"x": 104, "z": -88}, {"x": 131, "z": -88},
                {"x": 131, "z": -61}, {"x": 104, "z": -61} ]
  },
  "tags": ["commercial", "waterfront"]
}
```

`freehold`, `leasehold` and `auction` are each independently nullable, matching
`RealtyBackend.RegionInfo`. `price` within `freehold` is nullable — a null price
means not currently for sale, which is how `InfoCommand` distinguishes its
for-sale and sold renderings.

The `leasehold` object, when present, carries `landlord` and nullable `tenant`
identities, `price`, `durationSeconds`, nullable `startDate` and `endDate`, and
the extension counts as nullable `extensionsUsed` and `maxExtensions`, where a
null `maxExtensions` means unlimited.

`dimensions` is null when the module is unreachable or not installed.

**There is no `members` field.** The WorldGuard member and owner lists that
`/realty info` renders are read live from WorldGuard and are not in the database.
The module could serve them, but nothing has asked for them; an always-empty array
would falsely assert that regions have no members.

Tags are raw tag IDs. Display names live in the plugin's `RealtyTags` config,
which neither service has.

Responses: `200`; `404` for an unknown world name or no such region.

### `GET /v1/players/regions?player={name|uuid}`

The `/realty list` payload. Backed by `listRegions`, `listOwnedRegions` and
`listRentedRegions`. A name is resolved to a UUID through the module.

| Parameter | Default | Meaning |
|---|---|---|
| `player` | — | Required. A player name or a UUID |
| `category` | `all` | One of `all`, `owned`, `rented` |
| `page` | `1` | 1-based page number |
| `pageSize` | `10` | Clamped to `REALTY_REST_MAX_PAGE_SIZE` |

`category=all` returns three lists mirroring `ListResult`:

```json
{
  "player": { "id": "3a1c...", "name": ".Cool Guy 123" },
  "page": 1, "pageSize": 10,
  "totalCount": 23, "totalPages": 3,
  "owned":    [ { "worldGuardRegionId": "plot_1", "world": { "id": "8f4d...", "name": "world" } } ],
  "landlord": [ { "worldGuardRegionId": "plot_2", "world": { "id": "8f4d...", "name": "world" } } ],
  "rented":   [ { "worldGuardRegionId": "plot_3", "world": { "id": "8f4d...", "name": "world" },
                  "endDate": "2026-10-01T12:00:00Z", "secondsRemaining": 2505600 } ]
}
```

`category=owned` and `category=rented` return a single `regions` list with the
corresponding shape, plus the same paging fields.

A rented entry carries `endDate` and `secondsRemaining`, the machine-readable form
of the command's time-left column. Both are null for a lease with no end date.

**Known pagination quirk, preserved for parity.** `ListResult` pages all three
categories against one shared offset rather than paging each independently. This
is what `/realty list` does today. v1 matches the command rather than inventing a
different contract, and the OpenAPI description states the behaviour explicitly.

Responses: `200`, including for a player who owns nothing — that is a valid state,
not an error. `400` for a malformed UUID. `502` when `player` was given as a name
and the module is unreachable.

### `GET /v1/worlds`

Every known world as `{"id", "name"}`, straight from the `RealtyWorld` table.
Makes the API self-sufficient: a consumer holding no identifiers at all can start
here and obtain valid `?world=` values. Requires no module.

### `GET /v1/health`

Liveness plus a database round-trip, reporting module reachability separately. An
unreachable module is **degraded, not unhealthy** — the API still serves. `503`
only when the database is unreachable.

### Error responses

```json
{ "error": "REGION_NOT_FOUND", "message": "No region 'plot_9' in world 'world_nether'" }
```

| Status | Condition |
|---|---|
| `400` | Malformed UUID or out-of-range paging parameter |
| `404` | Unknown region, or unknown world name |
| `502` | Player looked up *by name* while the module is unreachable |
| `503` | Database unreachable — never used for an unreachable module |
| `500` | Unexpected failure; message is generic, detail goes to the log |

A `500` never echoes an exception message to the client.

## Degradation

The rule is: **a request never fails because the game server is offline.** When
the module is unreachable or `REALTY_REST_MODULE_URL` is unset, `dimensions` and
every player `name` are null, and everything sourced from MariaDB — contracts,
prices, dates, world names — is unaffected.

The single exception is looking a player up *by name*, which has nothing to fall
back on and returns `502`. Lookup by UUID continues to work.

## Required backend change

`ListCommand` computes the rented time-left column by calling
`getLeaseholdContract(...).join()` once per rented region. In a chat command
issued at human pace this is acceptable. Copied into an HTTP handler on a public
unauthenticated endpoint, it becomes a per-request N+1 and a cheap way for a
caller to generate load.

A single mapper query is added returning a player's rented regions joined to their
leasehold end dates, following the existing `SearchMapper` projection pattern with
a matching projection record. `realty-rest` uses it for the `rented` list.

This lives in `realty-backend` rather than the API subproject, because that is
where the SQL belongs and because the command can adopt it later. Changing
`ListCommand` to use it is **not** part of this work.

## OpenAPI

The API is **defined** by an OpenAPI 3.1 document, spec-first. A hand-authored
`openapi.yaml` in `realty-rest`'s resources is the source of truth; the Javalin
handlers implement it. It is not generated from annotations, so the contract can
be reviewed and changed as a document rather than inferred from code.

It is **published** two ways: served at `GET /v1/openapi.yaml` (and
`/v1/openapi.json`) so any deployment is self-describing, and rendered as
interactive Swagger UI at `GET /v1/docs`. Both are unauthenticated, consistent
with the rest of v1.

To keep the document honest, a test asserts that the set of routes Javalin
registers and the set of paths the document declares are **the same set, in both
directions**. An endpoint added without documentation fails the build, and so does
a documented path with no implementation. This follows the bidirectional assertion
`RealtyCategoryTest` already uses for notification categories.

## Packaging and deployment

**Shadow jar.** `realty-rest-all.jar`, containing the service and its shaded
dependencies.

**Docker.** A multi-stage `Dockerfile`: a Gradle build stage, and a runtime stage
on a JRE 25 base carrying only the jar. Runs as a non-root user, exposes the
configured port, declares a `HEALTHCHECK` against `/v1/health`. A `compose.yml` —
separate from the dev-only `compose.dev.yml` — brings up MariaDB and the API
together.

**Pterodactyl.** An egg JSON declaring each environment variable as a panel
variable with its default, description and validation rules, and
`java -jar realty-rest-all.jar` as the startup command. Because configuration is
environment-only, the egg needs no file templating.

## Testing

- **Handler tests** against a Javalin test harness with a stubbed `RealtyBackend`,
  asserting status codes, JSON shape, and null handling for every nullable field.
- **Integration tests** using Testcontainers MariaDB, reusing the existing
  `AbstractDatabaseTest` pattern, covering the new mapper query, the `RealtyWorld`
  table, and the full request path.
- **The OpenAPI conformance test** described above.
- **A configuration test** asserting a missing required variable fails startup with
  a message naming it, and that defaults apply when optional variables are absent.
- **A degradation test** asserting an unreachable module yields nulls and `200`,
  and that player-by-name yields `502`.
- **Encoding fixtures, which are not optional.** Lookup tests use a world named
  `My World` and a Floodgate-style player `.Cool Guy 123`, each exercised with both
  `%20` and `+`. A suite built only from `world` and `Notch` passes while every
  Bedrock player on the server is unfindable.

## Deliberate omissions

- **Authentication and rate limiting on the public API.** No auth was requested;
  rate limiting is better handled by a reverse proxy. Worth revisiting before the
  API is exposed to the open internet. The module's endpoint is a separate matter
  and does carry a shared secret.
- **WorldGuard member lists.** The module could serve them; nothing has asked. One
  endpoint and one nullable field to add later.
- **Auctions, offers, statistics.** All available on `RealtyBackend` and cheap to
  add later under the same prefix.
- **Response caching.** Correctness first. The module's per-request main-thread hop
  is the obvious first thing to measure.

## Shipping order

Three separate spec → plan → implementation cycles:

1. **`realty-rest`** — ships first, useful alone, returning bare UUIDs and no
   geometry. Requires the `RealtyWorld` table.
2. **`query-service`** — the module, independently testable. See its own spec.
3. **The client wiring** — the enrichment path in `realty-rest`, the only piece
   depending on both.
