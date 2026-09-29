package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who is acting, and which parties they act for. The plugin builds it before calling the backend;
 * the backend only compares parties against it and never asks Treasury or Vault itself.
 *
 * @param player    the acting player, or {@code null} for the console
 * @param manages   the parties the player may act for; a player always manages themself
 * @param reassigns the parties whose role the player may hand to another party; a player always
 *                  reassigns themself
 * @param bypass    whether the actor holds the command's admin permission
 */
public record ActorContext(@Nullable UUID player,
                           @NotNull Set<Party> manages,
                           @NotNull Set<Party> reassigns,
                           boolean bypass) {

    public ActorContext {
        manages = withPlayer(manages, player);
        reassigns = withPlayer(reassigns, player);
    }

    /**
     * A player who acts for no party beyond themself.
     */
    public static @NotNull ActorContext player(@NotNull UUID player, boolean bypass) {
        return new ActorContext(player, Set.of(), Set.of(), bypass);
    }

    /**
     * The console: no player, and every check is bypassed.
     */
    public static @NotNull ActorContext console() {
        return new ActorContext(null, Set.of(), Set.of(), true);
    }

    /**
     * @return the acting player
     * @throws IllegalStateException when the actor is the console
     */
    public @NotNull UUID requirePlayer() {
        if (this.player == null) {
            throw new IllegalStateException("this action needs a player");
        }
        return this.player;
    }

    /**
     * @return whether the actor may act for {@code party}, or bypasses the check
     */
    public boolean mayManage(@NotNull Party party) {
        return this.bypass || this.manages.contains(party);
    }

    /**
     * @return whether the actor may hand {@code party}'s role to another party, or bypasses the check
     */
    public boolean mayReassign(@NotNull Party party) {
        return this.bypass || this.reassigns.contains(party);
    }

    private static @NotNull Set<Party> withPlayer(@NotNull Set<Party> parties, @Nullable UUID player) {
        if (player == null) {
            return Set.copyOf(parties);
        }
        Set<Party> copy = new HashSet<>(parties);
        copy.add(new Party.Personal(player));
        return Set.copyOf(copy);
    }
}
