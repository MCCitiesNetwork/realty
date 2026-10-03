package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.rest.json.PartyRef;
import io.github.md5sha256.realty.rest.json.RegionMembersResponse;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.ModuleResult;
import io.github.md5sha256.realty.rest.module.PartyNames;
import io.github.md5sha256.realty.rest.module.RegionMembers;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code GET /v1/region/members?world=&region=} -- the region's WorldGuard owner and member
 * lists.
 *
 * <p>The region must be one Realty manages: WorldGuard knows regions this API does not speak
 * for, and an unregistered id is a 404 here rather than a pass-through to WorldGuard.</p>
 *
 * <p>Entirely module-sourced, so unlike the enrichment paths there is nothing to degrade to and
 * an unreachable module is a 502.</p>
 */
final class RegionMembersHandler {

    private final Database database;
    private final WorldLookup worldLookup;
    private final ModuleClient moduleClient;

    RegionMembersHandler(@NotNull Database database,
                         @NotNull WorldLookup worldLookup,
                         @NotNull ModuleClient moduleClient) {
        this.database = database;
        this.worldLookup = worldLookup;
        this.moduleClient = moduleClient;
    }

    void handle(@NotNull Context ctx) {
        UUID worldId = this.worldLookup.resolve(QueryParams.required(ctx, "world"));
        String regionId = QueryParams.required(ctx, "region");
        requireRegistered(worldId, regionId);

        RegionMembers members = switch (this.moduleClient.members(worldId, regionId)) {
            case ModuleResult.Found<RegionMembers> result -> result.value();
            case ModuleResult.NotFound<RegionMembers> notFound -> throw ApiException.notFound(
                    "REGION_NOT_FOUND",
                    "Realty knows that region, but WorldGuard no longer does");
            case ModuleResult.Unavailable<RegionMembers> unavailable -> throw ApiException.badGateway(
                    "MEMBERS_UNAVAILABLE",
                    "Reading region members requires the query-service module, which is not reachable");
        };

        List<Party> players = new ArrayList<>();
        for (UUID id : members.owners().playerIds()) {
            players.add(new Party.Personal(id));
        }
        for (UUID id : members.members().playerIds()) {
            players.add(new Party.Personal(id));
        }
        PartyNames.Resolved names = PartyNames.resolve(this.moduleClient, players);
        ctx.json(new RegionMembersResponse(
                party(members.owners(), names), party(members.members(), names)));
    }

    private void requireRegistered(@NotNull UUID worldId, @NotNull String regionId) {
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            if (session.realtyRegionMapper().selectByWorldGuardRegion(regionId, worldId) == null) {
                throw ApiException.notFound("REGION_NOT_FOUND",
                        "No region is registered under that id in that world");
            }
        }
    }

    private static @NotNull RegionMembersResponse.Party party(@NotNull RegionMembers.Party party,
                                                              @NotNull PartyNames.Resolved names) {
        List<PartyRef> players = new ArrayList<>(party.playerIds().size());
        for (UUID id : party.playerIds()) {
            players.add(Objects.requireNonNull(names.ref(id)));
        }
        return new RegionMembersResponse.Party(players, party.playerNames(), party.groups());
    }
}
