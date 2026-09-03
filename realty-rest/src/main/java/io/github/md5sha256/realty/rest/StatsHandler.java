package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.rest.json.StatsResponse;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

/**
 * Serves {@code GET /v1/stats} -- the server-wide totals the v1 spec listed as a
 * deliberate omission that would be cheap to add later.
 *
 * <p>Ten counters means ten round trips per request. That is acceptable for a route
 * a dashboard polls at human intervals, and it is the first place a short response
 * cache would pay for itself if one is ever added.</p>
 */
public final class StatsHandler {

    private final RealtyBackend backend;

    public StatsHandler(@NotNull RealtyBackend backend) {
        this.backend = backend;
    }

    public void handle(@NotNull Context ctx) {
        StatsResponse.Freehold freehold = new StatsResponse.Freehold(
                this.backend.countAllFreeholdContracts(),
                this.backend.countOccupiedFreeholdContracts(),
                this.backend.averageFreeholdPrice());
        StatsResponse.Leasehold leasehold = new StatsResponse.Leasehold(
                this.backend.countAllLeaseholdContracts(),
                this.backend.countOccupiedLeaseholdContracts(),
                this.backend.averageLeaseholdPrice(),
                this.backend.averageLeaseholdDurationSeconds());
        ctx.json(new StatsResponse(
                this.backend.countAllRegions(),
                freehold,
                leasehold,
                this.backend.countActiveOffers(),
                this.backend.countActiveAuctions()));
    }
}
