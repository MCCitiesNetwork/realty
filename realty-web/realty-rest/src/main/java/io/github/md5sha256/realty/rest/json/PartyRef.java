package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A contract party: a player, a Treasury account, or a permission group.
 *
 * <p>{@code kind} is {@code personal}, {@code business}, {@code government}, {@code system}
 * or {@code group}, and decides what {@code id} is: a player's UUID, an account id, or a
 * group name. The name is text a player may have chosen, returned as it is; it is null when
 * the module is disabled or unreachable, or cannot name the party.</p>
 */
public record PartyRef(@NotNull String kind, @NotNull String id, @Nullable String name) {

    public static @NotNull PartyRef personal(@NotNull UUID id, @Nullable String name) {
        return new PartyRef("personal", id.toString(), name);
    }
}
