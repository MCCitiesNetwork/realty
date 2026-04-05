package io.github.md5sha256.realty.plan;

import com.djrapitops.plan.extension.CallEvents;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.annotation.DoubleProvider;
import com.djrapitops.plan.extension.annotation.NumberProvider;
import com.djrapitops.plan.extension.annotation.PercentageProvider;
import com.djrapitops.plan.extension.annotation.PluginInfo;
import com.djrapitops.plan.extension.FormatType;
import com.djrapitops.plan.extension.icon.Color;
import com.djrapitops.plan.extension.icon.Family;
import io.github.md5sha256.realty.api.RealtyBackend;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@PluginInfo(
        name = "Realty",
        iconName = "home",
        iconFamily = Family.SOLID,
        color = Color.BLUE
)
public record RealtyDataExtension(@NotNull RealtyBackend realtyApi) implements DataExtension {

    @Override
    public CallEvents[] callExtensionMethodsOn() {
        return new CallEvents[]{
                CallEvents.SERVER_PERIODICAL,
                CallEvents.PLAYER_PERIODICAL
        };
    }

    @NumberProvider(
            text = "Total Regions",
            description = "Total number of registered realty regions on the server",
            iconName = "globe",
            iconFamily = Family.SOLID,
            iconColor = Color.CYAN
    )
    public long serverTotalRegisteredRegions() {
        return realtyApi.countAllRegions();
    }

    @NumberProvider(
            text = "Freehold Contracts",
            description = "Total number of registered freehold contracts on the server",
            iconName = "file-contract",
            iconFamily = Family.SOLID,
            iconColor = Color.BLUE
    )
    public long serverTotalRegisteredFreeholdContracts() {
        return realtyApi.countAllFreeholdContracts();
    }

    @NumberProvider(
            text = "Leasehold Contracts",
            description = "Total number of registered leasehold contracts on the server",
            iconName = "file-signature",
            iconFamily = Family.SOLID,
            iconColor = Color.GREEN
    )
    public long serverTotalRegisteredLeaseholdContracts() {
        return realtyApi.countAllLeaseholdContracts();
    }

    @PercentageProvider(
            text = "Leasehold Occupancy",
            description = "Percentage of leasehold contracts that currently have a tenant",
            iconName = "percentage",
            iconFamily = Family.SOLID,
            iconColor = Color.GREEN
    )
    public double serverLeaseholdOccupancyRate() {
        int total = realtyApi.countAllLeaseholdContracts();
        if (total == 0) {
            return 0;
        }
        return (double) realtyApi.countOccupiedLeaseholdContracts() / total;
    }

    @PercentageProvider(
            text = "Freehold Occupancy",
            description = "Percentage of freehold contracts that currently have a title holder",
            iconName = "percentage",
            iconFamily = Family.SOLID,
            iconColor = Color.BLUE
    )
    public double serverFreeholdOccupancyRate() {
        int total = realtyApi.countAllFreeholdContracts();
        if (total == 0) {
            return 0;
        }
        return (double) realtyApi.countOccupiedFreeholdContracts() / total;
    }

    @NumberProvider(
            text = "Mean Leasehold Duration",
            description = "Average tenancy duration across all occupied leaseholds",
            iconName = "clock",
            iconFamily = Family.SOLID,
            iconColor = Color.GREEN,
            format = FormatType.TIME_MILLISECONDS
    )
    public long serverMeanLeaseholdDuration() {
        return realtyApi.averageLeaseholdDurationSeconds() * 1000L;
    }

    @DoubleProvider(
            text = "Mean Freehold Price",
            description = "Average listing price across all freehold contracts with a set price",
            iconName = "dollar-sign",
            iconFamily = Family.SOLID,
            iconColor = Color.BLUE
    )
    public double serverMeanFreeholdPrice() {
        return realtyApi.averageFreeholdPrice();
    }

    @DoubleProvider(
            text = "Mean Leasehold Price",
            description = "Average rental price across all leasehold contracts",
            iconName = "dollar-sign",
            iconFamily = Family.SOLID,
            iconColor = Color.GREEN
    )
    public double serverMeanLeaseholdPrice() {
        return realtyApi.averageLeaseholdPrice();
    }

    @NumberProvider(
            text = "Active Offers",
            description = "Total number of active purchase offers on the server",
            iconName = "hand-holding-usd",
            iconFamily = Family.SOLID,
            iconColor = Color.LIGHT_GREEN
    )
    public long serverTotalActiveOffers() {
        return realtyApi.countActiveOffers();
    }

    @NumberProvider(
            text = "Active Auctions",
            description = "Total number of active auctions on the server",
            iconName = "gavel",
            iconFamily = Family.SOLID,
            iconColor = Color.AMBER
    )
    public long serverTotalActiveAuctions() {
        return realtyApi.countActiveAuctions();
    }

    @NumberProvider(
            text = "Owned Regions",
            description = "Number of regions where this player is a landlord or title holder",
            iconName = "key",
            iconFamily = Family.SOLID,
            iconColor = Color.BLUE
    )
    public long playerTotalOwnedRegions(UUID playerUUID) {
        return realtyApi.countRegionsByTitleHolder(playerUUID)
                + realtyApi.countRegionsByLandlord(playerUUID);
    }

    @NumberProvider(
            text = "Titleholder Regions",
            description = "Number of freehold contracts where this player is the title holder",
            iconName = "house-user",
            iconFamily = Family.SOLID,
            iconColor = Color.BLUE
    )
    public long playerTotalTitleholderRegions(UUID playerUUID) {
        return realtyApi.countRegionsByTitleHolder(playerUUID);
    }

    @NumberProvider(
            text = "Landlord Regions",
            description = "Number of leasehold contracts where this player is the landlord",
            iconName = "building",
            iconFamily = Family.SOLID,
            iconColor = Color.AMBER
    )
    public long playerTotalLandlordRegions(UUID playerUUID) {
        return realtyApi.countRegionsByLandlord(playerUUID);
    }

    @NumberProvider(
            text = "Leasehold Tenancies",
            description = "Number of leasehold contracts where this player is the tenant",
            iconName = "file-signature",
            iconFamily = Family.SOLID,
            iconColor = Color.GREEN
    )
    public long playerLeaseholdTenancyCount(UUID playerUUID) {
        return realtyApi.countRegionsByTenant(playerUUID);
    }

    @PercentageProvider(
            text = "Leasehold Occupancy",
            description = "Percentage of this player's leasehold contracts that have a tenant",
            iconName = "percentage",
            iconFamily = Family.SOLID,
            iconColor = Color.GREEN
    )
    public double playerLeaseholdOccupancyRate(UUID playerUUID) {
        int total = realtyApi.countRegionsByLandlord(playerUUID);
        if (total == 0) {
            return 0;
        }
        return (double) realtyApi.countOccupiedLeaseholdsByLandlord(playerUUID) / total;
    }
}
