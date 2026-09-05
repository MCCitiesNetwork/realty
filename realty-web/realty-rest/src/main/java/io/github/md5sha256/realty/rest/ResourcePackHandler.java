package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.rest.json.ResourcePackAttribution;
import io.github.md5sha256.realty.rest.json.ResourcePackEntry;
import io.github.md5sha256.realty.rest.json.ResourcePackResponse;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.ModuleResult;
import io.github.md5sha256.realty.rest.module.ResourcePack;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code GET /v1/resource-pack} -- the resource pack the game server asks clients to use.
 *
 * <p>Answered entirely by the query-service module, because the setting lives with the game
 * server and not in the database this service reads. An unreachable module is
 * therefore a 502, not an empty pack: "no pack configured" and "could not ask" are different
 * answers, and a renderer should not treat the second as the first.</p>
 */
final class ResourcePackHandler {

    private final ModuleClient moduleClient;

    ResourcePackHandler(@NotNull ModuleClient moduleClient) {
        this.moduleClient = moduleClient;
    }

    void handle(@NotNull Context ctx) {
        ResourcePack pack = switch (this.moduleClient.resourcePack()) {
            case ModuleResult.Found<ResourcePack> found -> found.value();
            case ModuleResult.NotFound<ResourcePack> ignored -> throw ApiException.badGateway(
                    "RESOURCE_PACK_UNAVAILABLE",
                    "The query-service module did not answer with a resource pack");
            case ModuleResult.Unavailable<ResourcePack> ignored -> throw ApiException.badGateway(
                    "RESOURCE_PACK_UNAVAILABLE",
                    "Reading the resource pack requires the query-service module, "
                            + "which is not reachable");
        };

        ctx.json(new ResourcePackResponse(packs(pack), pack.hash(), pack.required()));
    }

    /** Crosses the module records into this service's own JSON records, order preserved. */
    private static @NotNull List<ResourcePackEntry> packs(@NotNull ResourcePack pack) {
        return pack.packs().stream()
                .map(entry -> new ResourcePackEntry(entry.url(), entry.attribution().stream()
                        .map(credit -> new ResourcePackAttribution(credit.text(), credit.url()))
                        .toList()))
                .toList();
    }
}
