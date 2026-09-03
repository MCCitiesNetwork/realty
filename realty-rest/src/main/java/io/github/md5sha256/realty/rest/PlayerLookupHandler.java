package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

/**
 * {@code GET /v1/players/lookup?playerName=...} -- bare name-to-UUID resolution.
 *
 * <p>Every other player-scoped route accepts a name and resolves it internally, which
 * makes a client that only has a name pay the module hop on every call. This route lets
 * it resolve once and cache the UUID.</p>
 *
 * <p>It is the one route whose entire answer comes from the module, so unlike the
 * enrichment paths -- where an unreachable module degrades to a null name -- there is
 * nothing to degrade to, and an unreachable module is a 502.</p>
 */
final class PlayerLookupHandler {

    private final ModuleClient moduleClient;

    PlayerLookupHandler(@NotNull ModuleClient moduleClient) {
        this.moduleClient = moduleClient;
    }

    void handle(@NotNull Context ctx) {
        String name = QueryParams.required(ctx, "playerName");
        ctx.json(PlayerNameResolution.byName(this.moduleClient, name, "playerName"));
    }

}
