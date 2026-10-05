package io.github.md5sha256.realty.rest.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The response for {@code GET /v1/players/regions} -- the HTTP form of {@code /realty list} --
 * and for {@code GET /v1/parties/{kind}/{id}/regions}. The field keeps the name {@code player}
 * so the shape of the player route does not change; on the party route it holds that party.
 *
 * <p>The four category lists are the regions whose title the party holds ({@code owned}), whose
 * freehold authority it is ({@code authority}), which it lets as the landlord of a lease
 * ({@code landlord}) and which it rents ({@code rented}).</p>
 *
 * <p>When a single category was requested, {@code regions} carries that category's entries and
 * the four category-specific lists are omitted rather than serialised as null, so a
 * single-category caller does not receive four empty keys it did not ask for.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerRegionsResponse(
        @NotNull PartyRef player,
        int page,
        int pageSize,
        int totalCount,
        int totalPages,
        @Nullable List<RegionRef> owned,
        @Nullable List<RegionRef> authority,
        @Nullable List<RegionRef> landlord,
        @Nullable List<RentedRef> rented,
        @Nullable List<Object> regions
) {

    public record RegionRef(
            @NotNull String worldGuardRegionId,
            @NotNull WorldRef world
    ) {
    }

    public record RentedRef(
            @NotNull String worldGuardRegionId,
            @NotNull WorldRef world,
            @Nullable String endDate,
            @Nullable Long secondsRemaining
    ) {
    }

}
