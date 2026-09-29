package io.github.md5sha256.realty.util;

import io.github.md5sha256.realty.api.Party;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.Account;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one place that turns a {@link Party} into the text shown in messages, info, history and signs.
 *
 * <p>Account names are read from Treasury, which is a database read, while messages are often built
 * on the main thread. A name is therefore kept for {@link #ACCOUNT_NAME_TTL} on the injected clock.</p>
 */
public final class PartyNames {

    private static final Duration ACCOUNT_NAME_TTL = Duration.ofSeconds(60);

    private record CachedName(@Nullable String name, @NotNull Instant expiresAt) {}

    private final Server server;
    private final TreasuryApi treasury;
    private final Clock clock;
    private final Map<Integer, CachedName> accountNames = new ConcurrentHashMap<>();

    public PartyNames(@NotNull Server server, @Nullable TreasuryApi treasury, @NotNull Clock clock) {
        this.server = server;
        this.treasury = treasury;
        this.clock = clock;
    }

    public @NotNull String display(@NotNull Party party) {
        return switch (party) {
            case Party.Personal personal -> display(personal.playerUuid());
            case Party.Account account -> accountName(account.accountId()) + " (" + kindName(account) + ")";
            case Party.Group group -> group(group.groupName());
        };
    }

    public @NotNull String display(@NotNull UUID player) {
        Player online = server.getPlayer(player);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = server.getOfflinePlayer(player);
        String name = offline.getName();
        return name != null ? name : player.toString();
    }

    public static @NotNull String group(@NotNull String groupName) {
        return groupName + " (group)";
    }

    private static @NotNull String kindName(@NotNull Party.Account account) {
        return account.kind().name().toLowerCase(Locale.ROOT);
    }

    private @NotNull String accountName(int accountId) {
        Instant now = clock.instant();
        CachedName cached = accountNames.get(accountId);
        if (cached == null || !now.isBefore(cached.expiresAt())) {
            cached = new CachedName(lookUp(accountId), now.plus(ACCOUNT_NAME_TTL));
            accountNames.put(accountId, cached);
        }
        return cached.name() != null ? cached.name() : "#" + accountId;
    }

    private @Nullable String lookUp(int accountId) {
        if (treasury == null) {
            return null;
        }
        Account account = treasury.getAccountById(accountId);
        return account != null ? account.getDisplayName() : null;
    }
}
