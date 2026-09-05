package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The region payload, mirroring what {@code /realty info} renders.
 *
 * <p>{@code dimensions} is null when the module is disabled or unreachable. It is deliberately
 * present-and-null rather than absent, so adding it later is not a breaking change.</p>
 */
public record RegionResponse(
        @NotNull String worldGuardRegionId,
        @NotNull WorldRef world,
        @Nullable String state,
        @Nullable Freehold freehold,
        @Nullable Leasehold leasehold,
        @Nullable Auction auction,
        @Nullable Dimensions dimensions,
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
            @Nullable Double lastSoldPrice,
            boolean acceptingOffers
    ) {
    }

    /**
     * @param maxExtensions           null means unlimited extensions
     * @param terminationEffectiveDate null unless one party has given notice; the
     *                                lease still runs until this date
     * @param terminatedByRole        which party gave that notice, null alongside a
     *                                null {@code terminationEffectiveDate}
     */
    public record Leasehold(
            @NotNull PlayerRef landlord,
            @Nullable PlayerRef tenant,
            double price,
            long durationSeconds,
            @Nullable String startDate,
            @Nullable String endDate,
            @Nullable Integer extensionsUsed,
            @Nullable Integer maxExtensions,
            @Nullable String terminationEffectiveDate,
            @Nullable String terminatedByRole,
            boolean acceptingTenants
    ) {
    }

    /**
     * @param endDate the computed bidding deadline -- the last bid, or the start,
     *                plus {@code biddingDurationSeconds}
     */
    public record Auction(
            @Nullable String endDate,
            @Nullable Bid highestBid,
            @NotNull PlayerRef auctioneer,
            @NotNull String startDate,
            double minBid,
            double minStep,
            long biddingDurationSeconds,
            long paymentDurationSeconds
    ) {
    }

    public record Bid(
            @NotNull PlayerRef bidder,
            double amount
    ) {
    }

    /**
     * Live WorldGuard geometry from the query-service module. For a cuboid the four
     * points are its footprint corners, so both shapes read the same way.
     */
    public record Dimensions(@NotNull String shape,
                             int minY,
                             int maxY,
                             int priority,
                             @NotNull List<Point> points) {
    }

    public record Point(int x, int z) {
    }

}
