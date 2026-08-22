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
| `realty-paper-adapters/pn-adapter` | Notification delivery via [PlayerNotifications](https://github.com/MCCitiesNetwork/player-notifications) |

The adapter modules are **not bundled in the plugin jar**. Each is published as its own jar; install
the ones you want by placing them in `plugins/Realty/modules` and restarting the server. Realty
delivers no notifications until at least one delivery module is installed, and logs a warning at
startup while none is.

## Documentation

### Getting Started

For detailed setup instructions, visit the [Installation Guide](https://github.com/MCCitiesNetwork/realty/wiki/Installation).

For player, staff, and server-owner guides, visit the [GitHub wiki](https://github.com/MCCitiesNetwork/realty/wiki).
