package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The response for {@code GET /v1/leaderboard/owners} -- title holders by plot count.
 *
 * <p>Ownership is public per player via {@code /realty list <player>}, so no row here
 * reveals anything new. What the route adds is the ranking, which no command performs;
 * see the aggregation note in the v1.x spec.</p>
 */
public record OwnersLeaderboardResponse(
        int page,
        int pageSize,
        int totalCount,
        int totalPages,
        @NotNull List<Entry> owners
) {

    /**
     * @param rank the row's 1-based position across the whole leaderboard, not within
     *             the page, so page 2 at {@code pageSize=10} starts at 11
     */
    public record Entry(int rank, @NotNull PlayerRef player, int plotCount) {
    }

}
