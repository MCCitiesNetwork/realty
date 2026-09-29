package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * A contract party: a player, a Treasury account, or a named group backed by one.
 * The backend stores and compares parties and never calls Treasury or Vault itself.
 */
public sealed interface Party {

    record Personal(@NotNull UUID playerUuid) implements Party {}

    record Account(int accountId, @NotNull AccountKind kind) implements Party {}

    record Group(@NotNull String groupName, int accountId, @NotNull AccountKind accountKind) implements Party {

        public Group {
            groupName = groupName.toLowerCase(Locale.ROOT);
        }
    }

    /**
     * @return this party's kind.
     */
    default @NotNull PartyKind partyKind() {
        return switch (this) {
            case Personal _ -> PartyKind.PERSONAL;
            case Account account -> PartyKind.of(account.kind());
            case Group _ -> PartyKind.GROUP;
        };
    }

    /**
     * @return the player UUID of {@code party} if it is a {@link Personal}.
     */
    static @NotNull Optional<UUID> playerUuidOf(@Nullable Party party) {
        return party instanceof Personal personal ? Optional.of(personal.playerUuid()) : Optional.empty();
    }
}
