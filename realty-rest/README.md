# realty-rest

A standalone, **read-only** HTTP service over the Realty database. It exposes region,
world and player data as JSON for consumers outside the Minecraft server -- dashboards,
bots, web front ends -- without granting them database access.

`realty-rest` never runs migrations and never writes. It expects a database that
`realty-paper` has already created and migrated, at **exactly** the schema version this
build was compiled against. A newer schema may have changed the meaning of a column it
reads; an older one may be missing a table it depends on. It refuses to start in either
case rather than fail later at request time, and the message names which side is behind.

In practice this means `realty-rest` and `realty-paper` are upgraded together.

Configuration is **entirely** via environment variables. There is no config file and
nothing is templated onto disk -- this is deliberate, since both deployment targets
below (Docker, Pterodactyl) treat the filesystem as ephemeral and the panel/orchestrator
as the source of truth for configuration.

## Environment variables

| Variable | Required | Default | Meaning |
|---|---|---|---|
| `REALTY_DB_URL` | yes | -- | MariaDB JDBC URL, **without** the `jdbc:` prefix (the app prepends it), e.g. `mariadb://db-host:3306/realty` |
| `REALTY_DB_USERNAME` | yes | -- | Database user. Read-only access is sufficient. |
| `REALTY_DB_PASSWORD` | yes | -- | Database password. |
| `REALTY_REST_HOST` | no | `0.0.0.0` | Bind address. |
| `REALTY_REST_PORT` | no | `8080` | Bind port. |
| `REALTY_REST_MAX_PAGE_SIZE` | no | `100` | Upper bound on the `pageSize` query parameter. **Hard-capped at 100** -- a larger value is clamped, with a warning, not honoured. |
| `REALTY_REST_CORS_ORIGINS` | no | -- | Comma-separated allowlist of browser origins, e.g. `http://localhost:5173,https://realty.example`. Empty disables CORS; there is deliberately no wildcard default. |
| `REALTY_REST_MODULE_URL` | no | -- | Base URL of a query-service module used to enrich responses. Unset disables enrichment. |
| `REALTY_REST_MODULE_SECRET` | no | -- | Shared secret sent to that module. |
| `REALTY_REST_MODULE_TIMEOUT_MS` | no | `1500` | Per-call timeout before a module-sourced field degrades to `null`. |

The resolved configuration (secrets redacted) is logged once at startup.

### Enrichment

The last three variables point this service at a query-service module running
inside the Paper process. When configured, it supplies a region's `dimensions`
(in `/v1/region` responses) and every player `name` (in `/v1/region` and
`/v1/players/regions` responses), and lets `/v1/players/regions?player=<name>`
resolve a player name to a UUID. Without it -- or if it stops answering --
those fields degrade to `null` rather than failing the whole response, and
`/v1/health`'s `module` field reports `disabled` or `unreachable` accordingly.
The one exception is `?player=<name>`: since a name lookup has nothing else to
return, a module that is unreachable *or not configured* fails that request with
`502 NAME_LOOKUP_UNAVAILABLE`; looking a player up by UUID still works.

A wedged module therefore adds at most `REALTY_REST_MODULE_TIMEOUT_MS` to a
request, not a multiple of it: `/v1/region` needs two module calls and issues
them concurrently, so the two share one timeout budget.

## Endpoints

- `GET /v1/health` -- liveness/readiness check.
- `GET /v1/worlds` -- every world known to Realty.
- `GET /v1/region?world=&region=` -- a single region's state (the HTTP form of `/realty info`).
- `GET /v1/regions?page=&pageSize=` -- a page of every registered region, identity only,
  in a fixed total order.
- `GET /v1/regions/search?type=&world=&minPrice=&maxPrice=&tag=&occupancy=&sort=&page=&pageSize=` --
  browse and filter regions (the HTTP form of `/realty search`). Every filter is optional.
- `GET /v1/players/regions?player=&category=&page=&pageSize=` -- a player's owned/landlord/rented regions (the HTTP form of `/realty list`).
- `GET /v1/openapi.yaml`, `GET /v1/openapi.json` -- the OpenAPI document.
- `GET /v1/docs` -- an interactive Swagger UI page.

### Three region endpoints, three questions

They are easy to confuse, so: `/v1/region` answers *what is the state of this one
region*, `/v1/regions` answers *what regions exist*, and `/v1/regions/search`
answers *what is on the market*. Only the last two are paged, and only the search
one filters. A region Realty has registered but which carries no contract appears
in `/v1/regions` and never in `/v1/regions/search`.

### A note on percent-encoding

`world` and `player` are **query parameters**, never path segments, and their values
are frequently not URL-safe:

- A **world name is a folder name on disk** and may contain spaces or other characters
  needing encoding -- `My World` becomes `?world=My%20World`.
- A **Floodgate (Bedrock) player name** is a leading `.` followed by an Xbox gamertag,
  which may itself contain spaces -- `.Some Gamertag` becomes `?player=.Some%20Gamertag`.

Send the raw name percent-encoded; do not pre-decode it.

## Browser clients (CORS)

A page served from another origin -- a front end on `http://localhost:5173`, say --
cannot read this API until that origin is listed in `REALTY_REST_CORS_ORIGINS`. The
browser blocks the response before any JavaScript sees it, and nothing is logged on
this side, so a missing allowlist looks like a client bug rather than a configuration
one.

```bash
REALTY_REST_CORS_ORIGINS="http://localhost:5173,https://realty.example"
```

Empty (the default) disables CORS entirely. Server-to-server callers -- `curl`, a bot,
another backend -- are unaffected either way: CORS is a browser rule, not an
authorisation one, and it grants nothing this read-only API does not already serve to
anyone who can reach the port.

## Worked examples

```bash
# Health check
curl -s http://localhost:8080/v1/health
# {"status":"ok"}

# Every known world
curl -s http://localhost:8080/v1/worlds
# [{"id":"...","name":"world"},{"id":"...","name":"world_nether"}]

# Every registered region, paged
curl -s "http://localhost:8080/v1/regions?page=1&pageSize=25"

# A region by world UUID (or name) + WorldGuard region id
curl -s "http://localhost:8080/v1/region?world=world&region=spawn-shop-3"

# The same, with a space-containing world name -- percent-encoded as %20
curl -s "http://localhost:8080/v1/region?world=My%20World&region=downtown-1"

# Browse: the cheapest rentals in one world, tagged commercial
curl -s "http://localhost:8080/v1/regions/search?type=rent&world=My%20World&tag=commercial&sort=price_asc&pageSize=25"

# Browse: everything for sale under 10000, most expensive first (the default order)
curl -s "http://localhost:8080/v1/regions/search?type=sale&maxPrice=10000"

# Every freehold, listed or not -- an unlisted one carries "price": null
curl -s "http://localhost:8080/v1/regions/search?type=freehold"

# A player's regions, paged
curl -s "http://localhost:8080/v1/players/regions?player=069a79f4-44e9-4726-a5be-fca90e38aaf5&category=all&page=1&pageSize=25"

# The OpenAPI document and interactive docs
curl -s http://localhost:8080/v1/openapi.yaml
curl -s http://localhost:8080/v1/openapi.json
# Open http://localhost:8080/v1/docs in a browser for Swagger UI.
```

## Running it

### 1. Plain jar

```bash
./gradlew :realty-rest:shadowJar
REALTY_DB_URL="mariadb://localhost:3306/realty" \
REALTY_DB_USERNAME=realty \
REALTY_DB_PASSWORD=realty \
java -jar realty-rest/build/libs/realty-rest-*-all.jar
```

### 2. Docker

```bash
docker build -t realty-rest -f realty-rest/Dockerfile .
docker run --rm -p 8080:8080 \
  -e REALTY_DB_URL="mariadb://host.docker.internal:3306/realty" \
  -e REALTY_DB_USERNAME=realty \
  -e REALTY_DB_PASSWORD=realty \
  realty-rest
```

The image is a multi-stage build: a JDK 25 stage runs `:realty-rest:shadowJar`, and the
runtime stage (JRE 25) copies out only the resulting jar, runs as a non-root user, and
declares a container `HEALTHCHECK` against `/v1/health`.

### 3. Docker Compose

`compose.yml` at the repository root brings up `mariadb:11.7` and the API together,
with the API's `depends_on` gated on the database's `service_healthy` condition:

```bash
docker compose up -d
curl -s http://localhost:8080/v1/health
docker compose down
```

This is a separate file from `compose.dev.yml`, which exists only for
`./gradlew runServer` and is not used here. `compose.yml` stands up its own empty
database -- point it at a database `realty-paper` has already migrated (or run the
plugin against it once) before expecting `/v1/regions` or `/v1/players/regions` to
return real data; `/v1/health` and `/v1/worlds` work against a migrated-but-empty
schema.

### 4. Pterodactyl egg

`realty-rest/pterodactyl-egg.json` is importable under Admin > Nests > Import Egg. It
declares every variable from the table above as a panel variable, plus one the service
itself never reads:

| Variable | Rules | Meaning |
|---|---|---|
| `REALTY_REST_VERSION` | `required|string|max:32` | The released version to install, e.g. `1.5.1`. Tags carry no `v` prefix; typing one is tolerated. |

The install step **downloads a prebuilt jar** from the matching GitHub Release —
`https://github.com/MCCitiesNetwork/realty/releases/download/<version>/realty-rest-<version>-all.jar`
— rather than cloning and compiling the project on the panel. Installs are therefore
fast, need no JDK or Gradle on the node, and produce a byte-identical jar to everyone
else running that version. The download is anonymous: release assets need no token,
which is why the jar is attached to a release rather than published to GitHub Packages,
whose Maven registry requires a credential even for public packages.

The version is **pinned, never `latest`**. `realty-rest` refuses to start unless the
database schema is exactly the version it was built against, so it must move in lockstep
with the Realty plugin; a reinstall must reproduce the same jar rather than silently
cross a schema boundary and exit. Upgrading is an explicit edit an operator makes when
they upgrade the plugin.

Startup command, unchanged:

```
java -jar realty-rest-all.jar
```

No file is templated -- every runtime setting is a panel-managed environment variable,
matching the table above exactly.

### Publishing a release

`.github/workflows/release-rest.yml` builds and attaches the asset when a GitHub Release
is **published** (or via `workflow_dispatch` with an existing tag, to re-run a failed
upload).

The **tag drives the version**: the workflow builds with
`-PreleaseVersion=<tag>`, so no version-bump commit is needed to
cut a release and the tag cannot disagree with the artifact. `realty-conventions.gradle.kts`
keeps `1.5.1` as the default every local and CI build uses.

```bash
git tag 1.6.0 && git push origin 1.6.0
gh release create 1.6.0 --generate-notes    # publishing triggers the workflow
```

The workflow fails loudly if the expected `realty-rest-<version>-all.jar` is not produced,
because that file name is the contract the egg's download URL is built from — a rename
would otherwise 404 on every install rather than break the build.
