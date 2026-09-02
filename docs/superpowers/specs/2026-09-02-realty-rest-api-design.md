# Realty REST API — Design

Date: 2026-09-02
Status: Approved, pending implementation plan

## Purpose

Expose Realty's public-facing property data over HTTP so external consumers — a
web map, a server website, a Discord bot — can read what `/realty info` and
`/realty list` show in game, without a Minecraft client.

v1 is read-only and unauthenticated. There are no accounts, no tokens, and no
write endpoints. Every response is data a player could already obtain by running
a command in game.

## Scope

In scope for v1:

- The `/realty info` payload for a single region.
- The `/realty list` payload for a single player.
- A health endpoint.
- A published OpenAPI 3.1 document describing all of the above.

Explicitly out of scope for v1: authentication, any write or mutation, auction
browsing, offer listings, server-wide statistics, and player name resolution.
These are deliberate omissions, not oversights; each is a candidate for a later
version behind the same version prefix scheme.

## Deployment model

`realty-rest` is a **standalone service**, not a plugin or a plugin module. It is
its own JVM process reading the same MariaDB database the Realty plugin writes
to.

This decouples API uptime from Minecraft server uptime and lets the API be
scaled, restarted and firewalled independently. The cost is a second deployment
to operate and a coupling to the database schema, addressed under *Schema
coupling* below.

## Module layout

A new Gradle subproject **`realty-rest`**, registered in `settings.gradle.kts`,
depending on `realty-backend` (and transitively `realty-backend-api`). It uses
the existing `realty-conventions` plugin, so it inherits the Java 25 toolchain
and JUnit 5 setup.

It reuses the existing persistence layer verbatim — the MyBatis mappers, the
entity records, `Database`/`SqlSessionWrapper`, and `DatabaseSettings`. No SQL is
duplicated and no entity is redefined.

Dependencies added:

- **Javalin** — HTTP routing, embedding Jetty.
- **Jackson** — JSON serialisation.
- **Shadow** — to produce `realty-rest-all.jar`.

It does **not** depend on Configurate (see *Configuration*), on Paper, on
WorldGuard, or on `plugin-infrastructure`.

## Startup sequence

1. Read configuration from environment variables. Fail fast, naming the missing
   variable, if a required one is absent.
2. Construct `MariaDatabase` from the resolved `DatabaseSettings`.
3. Construct `RealtyBackendImpl(database, nameResolver, dateFormatter,
   offerPaymentDurationSeconds)` where:
   - `nameResolver` is `uuid -> CompletableFuture.completedFuture(uuid.toString())`
   - `dateFormatter` is an ISO-8601 UTC formatter
   - `offerPaymentDurationSeconds` is `() -> 0`, unused on read paths
4. Verify the schema version (see *Schema coupling*).
5. Log every resolved setting, with the database password redacted.
6. Start Javalin on the configured bind host and port and register the `/v1`
   routes.

The identity `nameResolver` is the structural enforcement of the UUIDs-only
decision. Because the only name resolver the service has returns the UUID's own
string form, no code path can accidentally emit a player name.

## Configuration

Configuration is **environment variables only**. There is no config file.

This is a deliberate departure from the project's config-reference-copy rule in
`CLAUDE.md`. That rule exists so an upgrading operator can discover new and
changed keys; it presumes an operator-edited YAML file. `realty-rest`'s primary
deployment targets are Docker and Pterodactyl, where the filesystem is ephemeral
and the panel — not a file — owns the values. Shipping a `defaults/` copy of a
file nobody edits would document a workflow that does not exist.

The rule's *intent* is met three other ways:

1. Every resolved setting is logged at startup, password redacted, so the running
   configuration is always visible.
2. Startup fails with a message naming the specific missing variable.
3. The full variable table is documented in the subproject README and declared in
   the Pterodactyl egg, which is where a panel operator actually looks.

| Variable | Required | Default | Meaning |
|---|---|---|---|
| `REALTY_DB_URL` | yes | — | MariaDB JDBC URL, as `DatabaseSettings.url` |
| `REALTY_DB_USERNAME` | yes | — | Database username |
| `REALTY_DB_PASSWORD` | yes | — | Database password |
| `REALTY_REST_HOST` | no | `0.0.0.0` | Bind address |
| `REALTY_REST_PORT` | no | `8080` | Bind port |
| `REALTY_REST_MAX_PAGE_SIZE` | no | `100` | Upper bound on `pageSize` |

`RestSettings` is a plain record holding host, port and max page size, built from
the environment alongside the reused `DatabaseSettings` record.

## Schema coupling

The plugin owns the schema. `realty-rest` **never** calls `initializeSchema` and
never runs a migration.

At startup it reads the applied schema version and refuses to boot if that
version is newer than the version this build was compiled against. A service
that silently serves columns it misunderstands is worse than one that does not
start: the failure is loud, immediate, and points at the real cause, which is a
plugin upgraded ahead of the API.

The version the build expects is a constant in `realty-rest`, bumped
deliberately whenever a migration lands that the API must understand.

## Read-only guarantee

The service calls only query methods on `RealtyBackend`. No v1 code path opens a
non-autocommit session or invokes a mutating backend method.

Where practical this is reinforced at the database level: the deployment
documentation recommends granting the API's database user `SELECT` only. That is
an operator action rather than something the code can enforce, so it is a
recommendation, not a guarantee.

## API surface

All routes are `GET`, return `application/json`, and sit under the `/v1` prefix.
Versioning is by URL path prefix: a breaking change to a response shape ships as
`/v2` alongside a still-serving `/v1`.

Conventions:

- Money is a raw JSON number. No currency symbols, no thousands separators —
  `CurrencyFormatter` is presentation and belongs to the consumer.
- Timestamps are ISO-8601 in UTC.
- Durations are integer seconds. `DurationFormatter` is likewise presentation.
- Player identities are UUID strings.

### `GET /v1/regions/{worldId}/{regionId}`

The `/realty info` payload. Backed by `RealtyBackend.getRegionInfo`,
`getRegionState`, and `RegionTagMapper.selectTagIdsByRegionId`.

```json
{
  "worldGuardRegionId": "downtown_plot_14",
  "worldId": "8f4d...",
  "state": "FOR_SALE",
  "freehold": {
    "titleHolderId": "3a1c...",
    "authorityId": "0000...",
    "price": 25000.0,
    "lastSoldPrice": 21500.0
  },
  "leasehold": null,
  "auction": {
    "endDate": "2026-09-05T18:00:00Z",
    "highestBid": { "bidderId": "9b2e...", "amount": 26000.0 }
  },
  "tags": ["commercial", "waterfront"]
}
```

`freehold`, `leasehold` and `auction` are each independently nullable, matching
`RealtyBackend.RegionInfo`. `price` within `freehold` is nullable — a null price
means not currently for sale, which is how `InfoCommand` distinguishes its
for-sale and sold renderings.

The `leasehold` object, when present, carries `landlordId`, nullable `tenantId`,
`price`, `durationSeconds`, nullable `startDate` and `endDate`, and the
extension counts as nullable `extensionsUsed` and `maxExtensions`, where a null
`maxExtensions` means unlimited.

**There is no `members` field.** The WorldGuard member and owner lists that
`/realty info` renders are read live from WorldGuard in the Minecraft process and
do not exist in the database. Omitting the field is honest; an always-empty array
would falsely assert that regions have no members.

Tags are raw tag IDs. Display names live in the plugin's `RealtyTags` config,
which the service does not have.

Responses: `200`, or `404` if no Realty region exists for that world and ID.

### `GET /v1/players/{uuid}/regions`

The `/realty list` payload. Backed by `listRegions`, `listOwnedRegions` and
`listRentedRegions`.

Query parameters:

| Parameter | Default | Meaning |
|---|---|---|
| `category` | `all` | One of `all`, `owned`, `rented` |
| `page` | `1` | 1-based page number |
| `pageSize` | `10` | Clamped to `REALTY_REST_MAX_PAGE_SIZE` |

`category=all` returns three lists mirroring `ListResult`:

```json
{
  "playerId": "3a1c...",
  "page": 1,
  "pageSize": 10,
  "totalCount": 23,
  "totalPages": 3,
  "owned":    [ { "worldGuardRegionId": "plot_1", "worldId": "8f4d..." } ],
  "landlord": [ { "worldGuardRegionId": "plot_2", "worldId": "8f4d..." } ],
  "rented":   [ { "worldGuardRegionId": "plot_3", "worldId": "8f4d...",
                  "endDate": "2026-10-01T12:00:00Z", "secondsRemaining": 2505600 } ]
}
```

`category=owned` and `category=rented` return a single `regions` list with the
corresponding shape, plus the same paging fields.

A rented entry carries `endDate` and `secondsRemaining`, the machine-readable
form of the command's time-left column. Both are null for a lease with no end
date.

**Known pagination quirk, preserved for parity.** `ListResult` pages all three
categories against one shared offset rather than paging each independently. This
is what `/realty list` does today. v1 matches the command rather than inventing a
different contract, and the OpenAPI description states the behaviour explicitly.

Responses: `200`, or `400` for a malformed UUID. An unknown player is `200` with
empty lists and `totalCount: 0` — a player owning nothing is a valid state, not
an error.

### `GET /v1/health`

Liveness plus a database round-trip. `200` with `{"status":"ok"}`, or `503` with
`{"status":"degraded"}` when the database is unreachable.

### Error responses

```json
{ "error": "REGION_NOT_FOUND", "message": "No region 'plot_9' in world 8f4d..." }
```

| Status | Condition |
|---|---|
| `400` | Malformed UUID or out-of-range paging parameter |
| `404` | Unknown region |
| `503` | Database unreachable |
| `500` | Unexpected failure; message is generic, detail goes to the log |

A `500` never echoes an exception message to the client. Internal detail is
logged, not served.

## Required backend change

`ListCommand` computes the rented time-left column by calling
`getLeaseholdContract(...).join()` once per rented region. In a chat command
issued at human pace this is acceptable. Copied into an HTTP handler on a public
unauthenticated endpoint, it becomes a per-request N+1 against the database and a
cheap way for a caller to generate load.

A single mapper query is added returning a player's rented regions joined to
their leasehold end dates, following the existing `SearchMapper` projection
pattern with a matching projection record. `realty-rest` uses it for the `rented`
list.

This lives in `realty-backend` rather than in the API subproject, because that is
where the SQL belongs and because the command can adopt it later. Changing
`ListCommand` to use it is **not** part of this work; the command keeps its
current behaviour.

## OpenAPI

The API is **defined** by an OpenAPI 3.1 document, spec-first. A hand-authored
`openapi.yaml` in `realty-rest`'s resources is the source of truth for the
contract; the Javalin handlers implement it. It is not generated from
annotations, so the contract can be reviewed and changed as a document rather
than inferred from code.

It is **published** two ways:

- Served at `GET /v1/openapi.yaml` (and `/v1/openapi.json`) by the running
  service, so any deployment is self-describing.
- Rendered as interactive Swagger UI at `GET /v1/docs`.

Both are unauthenticated, consistent with the rest of v1.

To keep the document honest, a test asserts that the set of routes Javalin
registers and the set of paths the document declares are **the same set, in both
directions**. An endpoint added without documentation fails the build, and so
does a documented path with no implementation. This follows the bidirectional
assertion `RealtyCategoryTest` already uses for notification categories.

## Packaging and deployment

Three targets, one configuration mechanism.

**Shadow jar.** `realty-rest-all.jar`, containing the service and its shaded
dependencies. Relocation follows the existing convention where it applies.

**Docker.** A multi-stage `Dockerfile`: a build stage running Gradle, and a
runtime stage on a JRE 25 base image carrying only the jar. The container runs as
a non-root user, exposes the configured port, and declares a `HEALTHCHECK`
against `/v1/health`.

A `compose.yml` — separate from and not replacing the dev-only `compose.dev.yml`
— brings up MariaDB and the API together for a self-contained deployment.

**Pterodactyl.** An egg JSON declaring each environment variable from the
configuration table as a panel variable with its default, description and
validation rules, and `java -jar realty-rest-all.jar` as the startup command.
Because configuration is environment-only, the egg needs no file templating and
the panel's variable UI is the whole configuration surface.

## Testing

- **Handler tests** against a Javalin test harness with a stubbed
  `RealtyBackend`, asserting status codes, JSON shape, and null handling for
  every nullable field.
- **Integration tests** using Testcontainers MariaDB, reusing the existing
  `AbstractDatabaseTest` pattern from `realty-backend`, covering the new mapper
  query and the full request path.
- **The OpenAPI conformance test** described above.
- **A configuration test** asserting that a missing required variable fails
  startup with a message naming it, and that defaults apply when optional
  variables are absent.

## Deliberate omissions

Each of these was considered and left out of v1:

- **Authentication and rate limiting.** No auth was requested. Rate limiting is
  an operational concern better handled by a reverse proxy than by the service.
  Worth revisiting before the API is exposed to the open internet.
- **Player name resolution.** Requires either a new name-cache table written by
  the plugin or an outbound Mojang lookup. Consumers can resolve UUIDs
  themselves.
- **Auctions, offers, statistics.** All available on `RealtyBackend` and cheap to
  add later under the same prefix.
- **Caching.** No response cache. Correctness first; add caching when a measured
  need appears.
