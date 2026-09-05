package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.entity.StatisticsEntity;
import io.github.md5sha256.realty.rest.json.StatsResponse;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

/**
 * Serves {@code GET /v1/stats} -- the server-wide totals the v1 spec listed as a
 * deliberate omission that would be cheap to add later.
 *
 * <p>One statement, not ten counters: the front page asks for this on every visit,
 * and ten round trips per visit was most of the time it spent loading. The answer
 * is also marked cacheable for a minute, which is as stale as a market total may be.</p>
 */
public final class StatsHandler {

    private final RealtyBackend backend;

    public StatsHandler(@NotNull RealtyBackend backend) {
        this.backend = backend;
    }

    public void handle(@NotNull Context ctx) {
        StatisticsEntity stats = this.backend.statistics();
        StatsResponse.Freehold freehold = new StatsResponse.Freehold(
                stats.freeholdContracts(),
                stats.occupiedFreeholds(),
                stats.averageFreeholdPrice());
        StatsResponse.Leasehold leasehold = new StatsResponse.Leasehold(
                stats.leaseholdContracts(),
                stats.occupiedLeaseholds(),
                stats.averageLeaseholdPrice(),
                stats.averageLeaseholdDurationSeconds());
        ctx.header("Cache-Control", ResponseCaching.SHORT_LIVED);
        ctx.json(new StatsResponse(
                stats.regions(),
                freehold,
                leasehold,
                stats.activeOffers(),
                stats.activeAuctions()));
    }
}
