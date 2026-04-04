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

## Modules

| Module | Role |
|--------|------|
| `realty-api` | Public API surface |
| `realty-common` | Shared logic and database access |
| `realty-paper` | Main Paper plugin |
| `realty-paper-plan-extension` | Optional [Plan](https://github.com/plan-player-analytics/Plan) integration |
| `realty-areashop-importer` | Optional AreaShop migration helper |

## Documentation

### Getting Started

For detailed setup instructions, visit the [Installation Guide](https://github.com/MCCitiesNetwork/realty/wiki/Installation).

For player, staff, and server-owner guides, visit the [GitHub wiki](https://github.com/MCCitiesNetwork/realty/wiki).
