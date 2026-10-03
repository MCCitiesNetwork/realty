package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.rest.json.PartyRef;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * {@code GET /v1/players/regions?player=...} -- the HTTP form of {@code /realty list}.
 */
final class PlayerRegionsHandler {

    private final PartyRegionsListing listing;
    private final ModuleClient moduleClient;

    PlayerRegionsHandler(@NotNull PartyRegionsListing listing, @NotNull ModuleClient moduleClient) {
        this.listing = listing;
        this.moduleClient = moduleClient;
    }

    void handle(@NotNull Context ctx) {
        PartyRef player = Objects.requireNonNull(
                PlayerNameResolution.fromRequest(ctx, this.moduleClient, true));
        this.listing.respond(ctx, new Party.Personal(UUID.fromString(player.id())), player);
    }

}
