# Realty Plan Extension

A [Plan](https://github.com/plan-player-analytics/Plan) DataExtension plugin that exposes Realty real estate data to Plan's analytics dashboard.

## Requirements

- Paper 1.21.8+
- [Realty](../README.md) plugin
- [Plan](https://github.com/plan-player-analytics/Plan) 5.7+

## Data Providers

### Server Providers

| Provider | Type | Description |
|---|---|---|
| Total Regions | Number | Total number of registered realty regions |
| Freehold Contracts | Number | Total number of registered freehold contracts |
| Leasehold Contracts | Number | Total number of registered leasehold contracts |
| Freehold Occupancy | Percentage | Percentage of freehold contracts with a title holder |
| Leasehold Occupancy | Percentage | Percentage of leasehold contracts with a tenant |
| Mean Leasehold Duration | Time | Average tenancy duration across occupied leaseholds |
| Active Offers | Number | Total number of pending purchase offers |
| Active Auctions | Number | Total number of active (not ended) auctions |

### Player Providers

| Provider | Type | Description |
|---|---|---|
| Owned Regions | Number | Regions where this player is the authority |
| Freehold Title Holdings | Number | Freehold contracts where this player is the title holder |
| Leasehold Landlord Count | Number | Leasehold contracts where this player is the landlord |
| Leasehold Tenancies | Number | Leasehold contracts where this player is the tenant |
| Leasehold Occupancy | Percentage | Percentage of this player's leasehold contracts that have a tenant |

## Build

```bash
./gradlew :realty-paper-plan-extension:shadowJar
```

Output JAR is in `build/libs/`.
