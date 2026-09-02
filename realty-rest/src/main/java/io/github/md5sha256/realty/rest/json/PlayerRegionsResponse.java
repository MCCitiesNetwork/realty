package io.github.md5sha256.realty.rest.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The response for {@code GET /v1/players/regions} -- the HTTP form of {@code /realty list}.
 *
 * <p>When a single category ({@code owned} or {@code rented}) was requested, {@code regions}
 * carries that category's entries and the three category-specific lists are omitted rather
 * than serialised as null, so a single-category caller does not receive three empty keys it
 * did not ask for.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerRegionsResponse(
        @NotNull PlayerRef player,
        int page,
        int pageSize,
        int totalCount,
        int totalPages,
        @Nullable List<RegionRef> owned,
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
