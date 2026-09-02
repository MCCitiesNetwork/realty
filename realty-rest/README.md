# realty-rest

A standalone, **read-only** HTTP service over the Realty database. It exposes region,
world and player data as JSON for consumers outside the Minecraft server -- dashboards,
bots, web front ends -- without granting them database access.

`realty-rest` never runs migrations and never writes. It expects a database that
`realty-paper` has already created and migrated; if the applied schema version is
newer than this build understands, it refuses to start rather than misread it.

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
| `REALTY_REST_MAX_PAGE_SIZE` | no | `100` | Upper bound on the `pageSize` query parameter. |
| `REALTY_REST_MODULE_URL` | no | -- | Base URL of a query-service module used to enrich responses. Unset disables enrichment. |
| `REALTY_REST_MODULE_SECRET` | no | -- | Shared secret sent to that module. |
| `REALTY_REST_MODULE_TIMEOUT_MS` | no | `1500` | Per-call timeout before a module-sourced field degrades to `null`. |

The last three are read and validated by `RestConfiguration` but are otherwise
**inert in this build** -- no query-service module client exists yet. Every field it
would fill in (see below) is always `null`. The resolved configuration (secrets
redacted) is logged once at startup.

### What is always null right now

`worlds[].dimensions` and every player `name` field (in `/v1/players/regions`
responses) are `null` in this build. They are populated by a separate query-service
module that has not been built yet -- this is expected, not a bug. Passing a player
*name* (rather than a UUID) to `/v1/players/regions` currently always fails with
`502 NAME_LOOKUP_UNAVAILABLE`, for the same reason: name resolution needs that module.

## Endpoints

- `GET /v1/health` -- liveness/readiness check.
- `GET /v1/worlds` -- every world known to Realty.
- `GET /v1/regions?world=&region=` -- a single region's state (the HTTP form of `/realty info`).
- `GET /v1/players/regions?player=&category=&page=&pageSize=` -- a player's owned/landlord/rented regions (the HTTP form of `/realty list`).
- `GET /v1/openapi.yaml`, `GET /v1/openapi.json` -- the OpenAPI document.
- `GET /v1/docs` -- an interactive Swagger UI page.

### A note on percent-encoding

`world` and `player` are **query parameters**, never path segments, and their values
are frequently not URL-safe:

- A **world name is a folder name on disk** and may contain spaces or other characters
  needing encoding -- `My World` becomes `?world=My%20World`.
- A **Floodgate (Bedrock) player name** is a leading `.` followed by an Xbox gamertag,
  which may itself contain spaces -- `.Some Gamertag` becomes `?player=.Some%20Gamertag`.

Send the raw name percent-encoded; do not pre-decode it.

## Worked examples

```bash
# Health check
curl -s http://localhost:8080/v1/health
# {"status":"ok"}

# Every known world
curl -s http://localhost:8080/v1/worlds
# [{"id":"...","name":"world"},{"id":"...","name":"world_nether"}]

# A region by world UUID (or name) + WorldGuard region id
curl -s "http://localhost:8080/v1/regions?world=world&region=spawn-shop-3"

# The same, with a space-containing world name -- percent-encoded as %20
curl -s "http://localhost:8080/v1/regions?world=My%20World&region=downtown-1"

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
declares every variable from the table above as a panel variable (with the two
required database variables marked `required|string` and `REALTY_REST_PORT` validated
as `integer|between:1,65535`), builds the shadow jar from source during installation,
and starts the service with:

```
java -jar realty-rest-all.jar
```

No file is templated -- every setting is a panel-managed environment variable, matching
the table above exactly.
