package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The response for {@code GET /v1/regions/search} -- a page of regions matching
 * the caller's filters.
 *
 * <p>A result carries only the identity, contract type and price the search query
 * projects. That is deliberate: it is what a browse listing needs, and a consumer
 * wanting a region's full state fetches {@code /v1/regions} for the one row the
 * user picked, rather than making every listing pay for detail it will not show.</p>
 *
 * <p>An empty {@code results} list is a normal 200 -- "nothing matched these
 * filters" is an answer, not a missing resource.</p>
 */
public record SearchResponse(
        int page,
        int pageSize,
        int totalCount,
        int totalPages,
        @NotNull List<Result> results
) {

    /**
     * @param contractType either {@code "freehold"} or {@code "leasehold"}, as the
     *                     search projection reports it
     * @param world        the full world identity rather than a bare UUID, so a
     *                     consumer grouping rows by world needs no second lookup
     * @param price        null for a freehold with no asking price, which only
     *                     appears under {@code type=freehold}
     */
    public record Result(
            @NotNull String worldGuardRegionId,
            @NotNull WorldRef world,
            @NotNull String contractType,
            @Nullable Double price
    ) {
    }

}
