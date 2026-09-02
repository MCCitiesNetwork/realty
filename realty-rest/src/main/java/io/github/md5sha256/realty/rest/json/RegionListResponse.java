package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The response for {@code GET /v1/regions} -- a page of every region Realty has
 * registered, whether or not it currently carries a contract.
 *
 * <p>An entry is identity only. That is the whole point of this endpoint: it
 * answers "what regions exist", where {@code /v1/regions/search} answers "what is
 * on the market" and {@code /v1/region} answers "what is the state of this one".</p>
 */
public record RegionListResponse(
        int page,
        int pageSize,
        int totalCount,
        int totalPages,
        @NotNull List<Entry> regions
) {

    public record Entry(
            @NotNull String worldGuardRegionId,
            @NotNull WorldRef world
    ) {
    }

}
