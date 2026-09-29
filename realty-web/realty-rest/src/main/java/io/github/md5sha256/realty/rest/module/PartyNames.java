package io.github.md5sha256.realty.rest.module;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.rest.json.PartyRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Names for every party in a response. Handlers collect their parties first and build
 * {@link PartyRef}s afterwards, so a response costs at most one module call for players
 * and one for accounts, however many parties it names. A group needs no call: its name is
 * its id.
 */
public final class PartyNames {

    /** The names one response needs. A party or player it was not given comes back unnamed. */
    public interface Resolved {

        /** The reference for {@code party}, or {@code null} when there is no party. */
        @Nullable PartyRef ref(@Nullable Party party);

        /** The reference for a player, or {@code null} when there is no player. */
        @Nullable PartyRef ref(@Nullable UUID player);
    }

    private PartyNames() {
    }

    /**
     * Fetches the names of {@code parties}. A null entry is skipped, so a caller can add an
     * optional party without testing it first.
     */
    public static @NotNull Resolved resolve(@NotNull ModuleClient module,
                                            @NotNull Collection<? extends @Nullable Party> parties) {
        Set<UUID> players = new LinkedHashSet<>();
        Set<Integer> accounts = new LinkedHashSet<>();
        for (Party party : parties) {
            switch (party) {
                case null -> {
                }
                case Party.Personal personal -> players.add(personal.playerUuid());
                case Party.Account account -> accounts.add(account.accountId());
                case Party.Group group -> {
                }
            }
        }
        // The two calls are independent and each carries the module's full timeout, so they
        // run together: a wedged module then costs one timeout, not two.
        CompletableFuture<Map<Integer, String>> pendingAccounts = accounts.isEmpty()
                ? CompletableFuture.completedFuture(Map.of())
                : CompletableFuture.supplyAsync(() -> module.accountNames(accounts));
        Map<UUID, String> playerNames = players.isEmpty() ? Map.of() : module.names(players);
        Map<Integer, String> accountNames = pendingAccounts.join();
        return new Resolved() {
            @Override
            public @Nullable PartyRef ref(@Nullable Party party) {
                return switch (party) {
                    case null -> null;
                    case Party.Personal personal -> ref(personal.playerUuid());
                    case Party.Account account ->
                            new PartyRef(kind(account), id(account), accountNames.get(account.accountId()));
                    case Party.Group group -> new PartyRef(kind(group), id(group), group.groupName());
                };
            }

            @Override
            public @Nullable PartyRef ref(@Nullable UUID player) {
                return player == null ? null : PartyRef.personal(player, playerNames.get(player));
            }
        };
    }

    /** The party's kind as the API writes it: {@code personal}, {@code government}, ... */
    public static @NotNull String kind(@NotNull Party party) {
        return party.partyKind().name().toLowerCase(Locale.ROOT);
    }

    /** The party's id as the API writes it: a UUID, an account id or a group name. */
    public static @NotNull String id(@NotNull Party party) {
        return switch (party) {
            case Party.Personal personal -> personal.playerUuid().toString();
            case Party.Account account -> String.valueOf(account.accountId());
            case Party.Group group -> group.groupName();
        };
    }
}
