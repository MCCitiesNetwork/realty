package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The response for {@code GET /v1/auctions} -- every auction currently taking bids.
 *
 * <p>Auction terms are public in game: {@code /realty auction info} is granted by
 * default and renders the auctioneer, minimum bid, minimum step and bidding duration
 * to anyone. This route is the browse form of that.</p>
 */
public record AuctionsResponse(
        int page,
        int pageSize,
        int totalCount,
        int totalPages,
        @NotNull List<Entry> auctions
) {

    /**
     * @param endDate     when bidding closes: the last bid, or the start where there is
     *                    none, plus the bidding duration
     * @param highestBid  null until someone bids
     * @param bidderCount how many distinct players have bid. It names nobody, and how
     *                    contested a lot is shows in the bid history the auction
     *                    already publishes
     */
    public record Entry(
            @NotNull String worldGuardRegionId,
            @NotNull WorldRef world,
            @NotNull PlayerRef auctioneer,
            @NotNull String startDate,
            @NotNull String endDate,
            double minBid,
            double minStep,
            long biddingDurationSeconds,
            long paymentDurationSeconds,
            @Nullable Bid highestBid,
            int bidderCount
    ) {
    }

    public record Bid(@NotNull PlayerRef bidder, double amount, @NotNull String bidTime) {
    }

}
