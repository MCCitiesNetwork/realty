package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;

/**
 * The response for {@code GET /v1/players/summary} -- a player's holdings as counts.
 *
 * <p>Every figure here is public in game: {@code /realty list <player>} accepts any
 * target and is granted by default, so a passer-by can already enumerate what someone
 * holds. This route exists because counting via that listing means paging it to the
 * end purely to read a total.</p>
 *
 * <p>A player holding nothing is a 200 with zeroes, not a 404. The route answers
 * "what does this player hold", and "nothing" is an answer; there is no player
 * resource here that could be missing.</p>
 *
 * @param occupiedLandlordOf the subset of {@code landlordOf} that currently has a
 *                           tenant, so a caller can show let versus vacant without a
 *                           second call
 */
public record PlayerSummaryResponse(
        @NotNull PlayerRef player,
        int titleHeld,
        int landlordOf,
        int occupiedLandlordOf,
        int renting,
        int authorityOver
) {
}
