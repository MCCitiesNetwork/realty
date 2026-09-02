package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The region payload, mirroring what {@code /realty info} renders.
 *
 * <p>{@code dimensions} is always null until the query-service enrichment client
 * ships, and null thereafter whenever the module is unreachable. It is deliberately
 * present-and-null rather than absent, so adding it later is not a breaking change.</p>
 */
public record RegionResponse(
        @NotNull String worldGuardRegionId,
        @NotNull WorldRef world,
        @Nullable String state,
        @Nullable Freehold freehold,
        @Nullable Leasehold leasehold,
        @Nullable Auction auction,
        @Nullable Object dimensions,
        @NotNull List<String> tags
) {

    /**
     * @param price a null price means the region is not currently for sale, which is
     *              how {@code InfoCommand} distinguishes its for-sale and sold renderings
     */
    public record Freehold(
            @Nullable PlayerRef titleHolder,
            @NotNull PlayerRef authority,
            @Nullable Double price,
            @Nullable Double lastSoldPrice
    ) {
    }

    /**
     * @param maxExtensions null means unlimited extensions
     */
    public record Leasehold(
            @NotNull PlayerRef landlord,
            @Nullable PlayerRef tenant,
            double price,
            long durationSeconds,
            @Nullable String startDate,
            @Nullable String endDate,
            @Nullable Integer extensionsUsed,
            @Nullable Integer maxExtensions
    ) {
    }

    public record Auction(
            @Nullable String endDate,
            @Nullable Bid highestBid
    ) {
    }

    public record Bid(
            @NotNull PlayerRef bidder,
            double amount
    ) {
    }

}
