# Realty

Realty is a real estate economy for [Paper](https://papermc.io/) Minecraft servers, built on
[WorldGuard](https://enginehub.org/worldguard/) regions. Put a region up for sale or lease, collect
rent, run auctions, take offers, appoint agents, carve out subregions to sublet, and tax the lot --
all through one command tree, with every contract persisted in MariaDB.

## Scope

The repository holds more than the plugin. In dependency order:

| Part | What it is |
|---|---|
| `realty-backend-api` | Domain API: immutable entities, sealed result types, enums, formatters. No server types. |
| `realty-backend` | Persistence and business logic: MyBatis mappers, schema migrations, the `RealtyBackend` implementation. |
| `realty-paper-api` | Paper-facing API other plugins compile against: `RealtyPaperApi`, region profiles, signs, `RealtyNotificationEvent`, `PlayerNameService`. |
| `realty-paper` | The plugin itself: commands, listeners, economy providers, localisation, tax, schematic capture. |
| `realty-paper-adapters/*` | Optional in-server modules (notification delivery, the REST query seam). Separate jars, dropped into `plugins/Realty/modules`. |
| `realty-paper-plan-extension` | Optional [Plan](https://github.com/plan-player-analytics/Plan) analytics integration. A separate plugin jar. |
| `realty-web/realty-rest` | A standalone JVM service serving a read-only `/v1` HTTP API over the Realty database. Runs outside the game server. |
| `realty-web/realty-explorer` | The browser front end for that API. React + Vite, built to static files. |
| `realty-web/realty-web-dist` | Both of the above in one jar, for a single-process deployment. |
| `realty-areashop-importer` | AreaShop migration helper. **Excluded from the build** -- see `settings.gradle.kts`. |

See [`realty-web/README.md`](realty-web/README.md) for the web half's deployment options.

## Requirements

To run the plugin:

- **Paper** 26.1.2 (the plugin declares `api-version: 26.1.2`)
- **Java 25** -- Paper 26.x ships Java 25 class files, and Realty compiles to the same release
- **MariaDB/MySQL** for region data
- **WorldGuard** 7.0.18 and **WorldEdit** 7.4.5 -- both required
- An economy: **Treasury** *or* **Vault** with a Vault-compatible provider

To build from source you additionally need a JDK 25 for the Gradle toolchain; the web front end's
Node (22.18.0) is downloaded by the build itself.

## Plugin dependencies

Only WorldGuard is hard-required. Everything else is optional, and Realty degrades rather than
refusing to start -- with the exception of the economy, where *neither* provider present is fatal.

| Plugin | Required | What it gives you |
|---|---|---|
| [WorldGuard](https://enginehub.org/worldguard/) | **yes** | Realty regions *are* WorldGuard regions; ownership changes sync to WG member and owner lists |
| [WorldEdit](https://enginehub.org/worldedit/) | **yes** (WorldGuard's own dependency) | The clipboard API behind `/realty schematic capture`. FAWE installs work unchanged -- they provide the same `com.sk89q.worldedit` classes |
| [Treasury](https://github.com/ArcanePlugins/Treasury) | one of | Preferred economy provider (full ledger support) |
| [Vault](https://github.com/MilkBowl/Vault) | one of | Fallback economy provider. With neither Treasury nor Vault at enable time, Realty logs why and self-disables |
| [EssentialsX](https://essentialsx.net/) | no | Notification delivery as mail, plus a teleport-safety predicate -- via `essentials-adapter` |
| [PlayerNotifications](https://github.com/MCCitiesNetwork/player-notifications) | no | Per-player notification preferences and an inbox -- via `player-notifications-adapter` |
| [Plan](https://github.com/plan-player-analytics/Plan) | no | Realty data on player analytics pages -- via `realty-paper-plan-extension` |

Realty ships no notification delivery of its own: it renders a message and fires
`RealtyNotificationEvent`, and a module delivers it. Install at least one adapter or players are
told nothing. Startup warns while none is installed.

## Build

From the repository root:

```bash
./gradlew :realty-paper:shadowJar
```

Install the JAR from `realty-paper/build/libs/` whose name ends with `-all.jar`.

Other artifacts:

```bash
./gradlew :realty-paper-plan-extension:shadowJar   # Plan integration plugin
./gradlew :realty-paper-adapters:chat-adapter:shadowJar   # and the other adapters
./gradlew :realty-web:realty-web-dist:shadowJar   # REST API + front end, one jar
```

`realty-areashop-importer` is currently excluded from the build -- see
`settings.gradle.kts`. Re-enable its `include` there before building it.

## Maven

Published artefacts are hosted at **https://maven.minecraftcitiesnetwork.com**, split into
`/releases` and `/snapshots` — a version ending in `-SNAPSHOT` goes to the latter, everything
else to the former. Three modules are published; `realty-paper` itself is not.

```kotlin
repositories {
    maven("https://maven.minecraftcitiesnetwork.com/releases")
}

dependencies {
    compileOnly("io.github.md5sha256:realty-paper-api:1.4.1")
}
```

| Artefact | Contents |
|----------|----------|
| `io.github.md5sha256:realty-backend-api` | Domain API: entities, result types, enums |
| `io.github.md5sha256:realty-backend` | Persistence and business logic |
| `io.github.md5sha256:realty-paper-api` | Paper-facing API; depends on the two above |

Most integrations only need `realty-paper-api`, which pulls the backend modules transitively.

Publishing runs from the `Deploy Maven` workflow on a published GitHub release, or by manual
dispatch. It reads the target URL and credentials from the `MAVEN_REPOSITORY_URL`,
`MAVEN_REPOSITORY_USERNAME` and `MAVEN_REPOSITORY_PASSWORD` repository secrets. Releases are
immutable: republishing a version that already exists fails with a 409, so bump the version in
`buildSrc/src/main/kotlin/realty-conventions.gradle.kts` instead.

## Modules

| Module | Role |
|--------|------|
| `realty-paper-adapters/chat-adapter` | Notification delivery to online players via chat |
| `realty-paper-adapters/essentials-adapter` | Notification delivery via EssentialsX mail, plus teleport safety |
| `realty-paper-adapters/player-notifications-adapter` | Notification delivery via [PlayerNotifications](https://github.com/MCCitiesNetwork/player-notifications) |
| `realty-paper-adapters/query-service` | Private HTTP endpoint serving live WorldGuard geometry and player names to `realty-rest` |

`realty-paper-plan-extension` is *not* a module: it is an ordinary plugin jar and goes in `plugins/`,
not `plugins/Realty/modules`.

The adapter modules are **not bundled in the plugin jar**. Each is published as its own jar; install
the ones you want by placing them in `plugins/Realty/modules` and restarting the server. Realty
delivers no notifications until at least one delivery module is installed, and logs a warning at
startup while none is.

### Notification categories

`player-notifications-adapter` registers five notification categories with PlayerNotifications —
agents, auctions, offers, leases, and a general catch-all — each one a data type players switch on and
off in `/notifications preferences`.

**You configure them in PlayerNotifications, not here.** PlayerNotifications writes every category a
module registered into its generated `categories-defaults.yml`; copy the blocks you care about into its
`categories.yml` and edit them there. That is where a label, a description, or a regrouping of Realty's
data types into categories of your own takes effect.

The adapter's own `config.yml`
(`plugins/Realty/modules/player-notifications-adapter/config.yml`) holds one setting, `expiry-days`:
how long an enqueued notification stays in a player's inbox before PlayerNotifications expires it.

Realty also supplies a display name for each of its data types; rename one in PlayerNotifications'
`type-names.yml` if you want something different.

A Realty message key that no category claims still reaches players, routed to the general category.

### Turning off EssentialsX mail delivery

`essentials-adapter` writes a `config.yml` into its data folder. Setting `notifications-enabled: false`
stops Realty notifications being delivered as EssentialsX mail — useful when another delivery module
already covers offline players and you do not want the same notification arriving twice. The module's
teleport-safety integration is not affected by the setting and always applies.

### query-service

`realty-rest` runs outside the game server and can only read MariaDB, which holds neither
WorldGuard geometry nor player names. `query-service` answers for both from inside the server over a
private, secret-gated HTTP endpoint. Its `config.yml` (`plugins/Realty/modules/query-service/config.yml`):

| Key | Default | Meaning |
|---|---|---|
| `shared-secret` | *(empty)* | Required in every request's `X-Realty-Secret` header. **Empty disables the endpoint** rather than running it open; set the same value in `realty-rest`'s `REALTY_REST_MODULE_SECRET`. |
| `bind-host` | `127.0.0.1` | Localhost by default. Widen only if `realty-rest` runs on another host, and put a reverse proxy (with TLS) in front if that crosses a network you do not control. |
| `port` | `8123` | |
| `request-timeout-ms` | `1000` | Geometry is read on the main thread and names are resolved through it too; a request that cannot get an answer within this budget returns `504`. |

Routes (all require the secret; unversioned because both sides ship from this repository):

| Route | Answers |
|---|---|
| `GET /health` | `{"status":"ok"}` |
| `GET /regions/{worldId}/{regionId}/dimensions` | `shape` (`CUBOID`/`POLYGONAL`), `minY`, `maxY`, ordered footprint `points` — read live, never cached. `404` if WorldGuard has no such region. |
| `GET /players/{uuid}/name` | `{"id","name"}`, `name` null when unknown |
| `POST /players/names` `{"ids":[…]}` | `{"players":[{"id","name"}]}` in request order, unknowns kept with null `name`. At most **256** ids per request; more is `400 BATCH_TOO_LARGE`. |
| `POST /players/uuids` `{"names":[…]}` | `{"players":[{"id","name"}]}` in request order, unknowns kept with null `id`. At most **256** names per request; more is `400 BATCH_TOO_LARGE`. A body, not a query string, because Floodgate names like `.Cool Guy 123` are not URL-safe. |

Names come from the server's own usercache first, so Bedrock/Floodgate players resolve; Mojang is
only consulted for a UUID the server has never seen. The same lookups are available in-process to
other plugins as the `PlayerNameService` Bukkit service.

`/realty module reload query-service` re-reads the config and restarts the endpoint. If the edited
config fails to read or the new server fails to start, the previous configuration keeps running and
the failure is logged. The reload runs on the main thread and waits for the HTTP server to drain, so
it can pause the tick for up to `request-timeout-ms` if a request is in flight when it happens.

## Schematic capture

`/realty schematic capture [region] [--force]` snapshots a registered region's blocks into the
database, from the block the player stands on up to the region's ceiling, and serves it read-only at
`GET /v1/region/schematic`. It is players only -- the floor is taken from where the sender stands, and
the console stands nowhere.

Because a capture publishes the region's blocks to an endpoint anyone can read, the region's own
people decide when that snapshot is taken:

| Permission | Default | Grants |
|---|---|---|
| `realty.command.schematic.capture` | op | Use of the command at all |
| `realty.command.schematic.capture.others` | op | Capturing a region you are not a member or owner of |
| `realty.command.schematic.capture.force` | op | `--force`, which bypasses the per-region cooldown |

Membership is matched on player UUID, so WorldGuard *group* members (`g:` entries) do not qualify --
they need `.others`. The `schematic-max-volume` cap is hard: `--force` does not lift it, and no
permission does either.

## Documentation

### Getting Started

For detailed setup instructions, visit the [Installation Guide](https://github.com/MCCitiesNetwork/realty/wiki/Installation).

For player, staff, and server-owner guides, visit the [GitHub wiki](https://github.com/MCCitiesNetwork/realty/wiki).
