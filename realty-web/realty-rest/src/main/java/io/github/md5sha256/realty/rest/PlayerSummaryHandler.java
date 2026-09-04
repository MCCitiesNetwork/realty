package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.rest.json.PlayerRef;
import io.github.md5sha256.realty.rest.json.PlayerSummaryResponse;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * {@code GET /v1/players/summary?player=...} -- one player's holdings as counts.
 *
 * <p>Five counters, each its own query. That is still cheaper than the alternative it
 * replaces, which is paging {@code /v1/players/regions} to its final page purely to
 * read {@code totalCount}.</p>
 */
final class PlayerSummaryHandler {

    private final RealtyBackend backend;
    private final ModuleClient moduleClient;

    PlayerSummaryHandler(@NotNull RealtyBackend backend, @NotNull ModuleClient moduleClient) {
        this.backend = backend;
        this.moduleClient = moduleClient;
    }

    void handle(@NotNull Context ctx) {
        PlayerRef player = Objects.requireNonNull(
                PlayerNameResolution.fromRequest(ctx, this.moduleClient, true));
        UUID playerId = UUID.fromString(player.id());

        ctx.json(new PlayerSummaryResponse(
                player,
                this.backend.countRegionsByTitleHolder(playerId),
                this.backend.countRegionsByLandlord(playerId),
                this.backend.countOccupiedLeaseholdsByLandlord(playerId),
                this.backend.countRegionsByTenant(playerId),
                this.backend.countRegionsByAuthority(playerId)));
    }
}
