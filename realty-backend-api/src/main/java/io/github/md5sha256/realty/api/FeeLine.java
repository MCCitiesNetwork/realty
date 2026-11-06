package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * One fee on a sale.
 *
 * @param source    where the fee comes from: a levy's name, or for a fee a player set, its role
 *                  label, such as {@code agent fee} (see {@link #playerFeeSource(String)}).
 * @param recipient who receives the fee.
 * @param side      who pays it.
 * @param percent   the percentage of the price.
 * @param amount    the amount charged.
 */
public record FeeLine(
        @NotNull String source,
        @NotNull Party recipient,
        @NotNull FeeSide side,
        double percent,
        double amount
) {

    /** The roles a player can set a fee under. */
    public static final List<String> PLAYER_FEE_ROLES = List.of("titleholder", "agent", "authority");

    /** The source of a fee a player set under {@code role}: its role label, such as {@code agent fee}. */
    public static @NotNull String playerFeeSource(@NotNull String role) {
        return role + " fee";
    }

    /** Whether {@code source} is the role label of a fee a player set, rather than a levy's name. */
    public static boolean isPlayerFeeSource(@NotNull String source) {
        for (String role : PLAYER_FEE_ROLES) {
            if (playerFeeSource(role).equals(source)) {
                return true;
            }
        }
        return false;
    }

    /** Whether this line is a levy rather than a fee a player set. */
    public boolean isLevy() {
        return !isPlayerFeeSource(this.source);
    }
}
