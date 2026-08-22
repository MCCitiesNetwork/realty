# Realty

Realty is a plugin for [Paper](https://papermc.io/) Minecraft servers that allows you to put up [WorldGuard](https://enginehub.org/worldguard/) regions for sale or lease. You can collect rent, hold auctions, create subregions to rent out to other players, and place offers on other players' regions through one simple interface.

## Requirements

- **Paper** 1.21.8+
- **Java** 21
- **MariaDB/MySQL database** to store region data
- **Vault** and a Vault-compatible economy
- **WorldGuard amd WorldEdit** (required)
- **Essentials** (optional)

## Build

From the repository root:

```bash
./gradlew :realty-paper:shadowJar
```

Install the JAR from `realty-paper/build/libs/` whose name ends with `-all.jar`.

Other artifacts:

```bash
./gradlew :realty-paper-plan-extension:shadowJar
./gradlew :realty-areashop-importer:shadowJar
```

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
| `realty-api` | Public API surface |
| `realty-common` | Shared logic and database access |
| `realty-paper` | Main Paper plugin |
| `realty-paper-plan-extension` | Optional [Plan](https://github.com/plan-player-analytics/Plan) integration |
| `realty-areashop-importer` | Optional AreaShop migration helper |
| `realty-paper-adapters/chat-adapter` | Notification delivery to online players via chat |
| `realty-paper-adapters/essentials-adapter` | Notification delivery via EssentialsX mail |
| `realty-paper-adapters/player-notifications-adapter` | Notification delivery via [PlayerNotifications](https://github.com/MCCitiesNetwork/player-notifications) |

The adapter modules are **not bundled in the plugin jar**. Each is published as its own jar; install
the ones you want by placing them in `plugins/Realty/modules` and restarting the server. Realty
delivers no notifications until at least one delivery module is installed, and logs a warning at
startup while none is.

### Notification categories

`player-notifications-adapter` writes a `categories.yml` into its data folder
(`plugins/Realty/modules/player-notifications-adapter/`) on first start. Every category declared there is
registered with PlayerNotifications as a data type: the unit players switch on and off in
`/notifications preferences`. Each carries its own player-facing label and description, the title shown on the
notification, a delivery priority, and the Realty message keys routed to it.

The category set is read from that file rather than compiled in, so you can rename a category, split one into
several, or add your own without a new build of the adapter. A message key may belong to exactly one category,
and `fallback-category` must name one of the declared categories — the adapter refuses to start otherwise
instead of enqueueing notifications nobody can receive. A key you list nowhere routes to the fallback and is
never dropped.

### Turning off EssentialsX mail delivery

`essentials-adapter` writes a `config.yml` into its data folder. Setting `notifications-enabled: false`
stops Realty notifications being delivered as EssentialsX mail — useful when another delivery module
already covers offline players and you do not want the same notification arriving twice. The module's
teleport-safety integration is not affected by the setting and always applies.

## Documentation

### Getting Started

For detailed setup instructions, visit the [Installation Guide](https://github.com/MCCitiesNetwork/realty/wiki/Installation).

For player, staff, and server-owner guides, visit the [GitHub wiki](https://github.com/MCCitiesNetwork/realty/wiki).
