package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;

/**
 * The response for {@code GET /v1/stats} -- server-wide totals.
 *
 * <p>Every figure is an aggregate over the whole estate, with no player or region
 * identity in it, which is why this is the one listing-shaped route that needs no
 * disclosure argument at all.</p>
 *
 * <p>An empty server reports zeroes rather than nulls or absent fields: a consumer
 * drawing a dashboard should not have to distinguish "no data" from "nothing
 * registered yet", and the backend's own counters already answer zero.</p>
 */
public record StatsResponse(
        int regions,
        @NotNull Freehold freehold,
        @NotNull Leasehold leasehold,
        int activeOffers,
        int activeAuctions
) {

    public record Freehold(int contracts, int occupied, double averagePrice) {
    }

    /**
     * @param averageDurationSeconds the mean lease term, in whole seconds, matching
     *                               the duration convention used elsewhere in v1
     */
    public record Leasehold(int contracts, int occupied, double averagePrice,
                            long averageDurationSeconds) {
    }

}
