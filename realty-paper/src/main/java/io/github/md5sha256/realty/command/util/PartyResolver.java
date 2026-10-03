package io.github.md5sha256.realty.command.util;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.Account;
import net.democracycraft.treasury.model.economy.AccountType;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Turns a name and an optional type flag into a {@link Party}, the way {@code /realty} commands
 * accept one: a plain name is a player, found the way {@link AuthorityParser} finds one today;
 * {@code --government}, {@code --business}, {@code --system} or {@code --group} makes it a
 * Treasury account or a mapped group instead.
 *
 * <p>An account that Treasury would refuse to move money for - archived, requiring
 * authorization, or not of the requested type - is refused here too, so that the refusal is
 * seen before the role is assigned rather than the first time a payment fails.</p>
 */
public final class PartyResolver {

    private final Server server;
    private final TreasuryApi treasury;
    private final RealtyBackend backend;

    public PartyResolver(@NotNull Server server, @Nullable TreasuryApi treasury, @NotNull RealtyBackend backend) {
        this.server = server;
        this.treasury = treasury;
        this.backend = backend;
    }

    public sealed interface Resolution {
        record Resolved(@NotNull Party party) implements Resolution {}
        record Refused(@NotNull String messageKey, @NotNull String name) implements Resolution {}
    }

    /**
     * Does blocking I/O (Treasury and the database); call it on the database executor.
     *
     * @param sender who is resolving the name, whose own accounts a {@code --business} or
     *               {@code --system} name is searched among; {@code null} for the console
     */
    public @NotNull Resolution resolve(@NotNull String name, @Nullable PartyFlag flag, @Nullable UUID sender) {
        if (flag == null) {
            return resolvePlayer(name);
        }
        if (treasury == null) {
            return refused(MessageKeys.PARTY_REQUIRES_TREASURY, name);
        }
        if (flag == PartyFlag.GROUP) {
            return resolveGroup(name);
        }
        return resolveAccount(name, flag, sender);
    }

    private @NotNull Resolution resolvePlayer(@NotNull String name) {
        Player online = server.getPlayerExact(name);
        if (online != null) {
            return new Resolution.Resolved(Party.personal(online.getUniqueId()));
        }
        OfflinePlayer offline = server.getOfflinePlayerIfCached(name);
        if (offline == null || !offline.hasPlayedBefore()) {
            return refused(MessageKeys.COMMON_PLAYER_NOT_FOUND, name);
        }
        return new Resolution.Resolved(Party.personal(offline.getUniqueId()));
    }

    private @NotNull Resolution resolveGroup(@NotNull String name) {
        Party.Group group = backend.findGroupParty(name);
        if (group == null) {
            return refused(MessageKeys.PARTY_GROUP_NOT_MAPPED, name);
        }
        Account account = treasury.getAccountById(group.accountId());
        if (account == null) {
            return refused(MessageKeys.PARTY_UNKNOWN_ACCOUNT, name);
        }
        // The mapping stores the account's type; an account whose type has changed since, or
        // that is PERSONAL, is no longer the account the group was mapped to.
        if (account.getAccountType() != AccountType.valueOf(group.accountKind().name())) {
            return refused(MessageKeys.PARTY_TYPE_MISMATCH, name);
        }
        Resolution unavailable = checkAvailable(account, name);
        return unavailable != null ? unavailable : new Resolution.Resolved(group);
    }

    private @NotNull Resolution resolveAccount(@NotNull String name, @NotNull PartyFlag flag, @Nullable UUID sender) {
        AccountKind wanted = AccountKind.valueOf(flag.name());
        if (name.startsWith("#")) {
            Integer id = parseId(name.substring(1));
            Account account = id != null ? treasury.getAccountById(id) : null;
            if (account == null) {
                return refused(MessageKeys.PARTY_UNKNOWN_ACCOUNT, name);
            }
            return checkAccount(account, wanted, name);
        }
        if (flag == PartyFlag.GOVERNMENT) {
            Account account = treasury.getGovernmentAccountByName(name);
            if (account == null) {
                return refused(MessageKeys.PARTY_UNKNOWN_ACCOUNT, name);
            }
            return checkAccount(account, wanted, name);
        }
        // Treasury has no global name lookup for BUSINESS or SYSTEM accounts, so they are
        // found among the sender's own accounts instead.
        if (sender == null) {
            return refused(MessageKeys.PARTY_UNKNOWN_ACCOUNT, name);
        }
        List<Account> matches = treasury.getAccountsByMember(sender).stream()
                .filter(account -> account.getAccountType() == AccountType.valueOf(wanted.name()))
                .filter(account -> name.equalsIgnoreCase(account.getDisplayName()))
                .toList();
        if (matches.isEmpty()) {
            return refused(MessageKeys.PARTY_UNKNOWN_ACCOUNT, name);
        }
        if (matches.size() > 1) {
            return refused(MessageKeys.PARTY_ACCOUNT_AMBIGUOUS, name);
        }
        return checkAccount(matches.getFirst(), wanted, name);
    }

    private @NotNull Resolution checkAccount(@NotNull Account account, @NotNull AccountKind wanted, @NotNull String name) {
        if (account.getAccountType() != AccountType.valueOf(wanted.name())) {
            return refused(MessageKeys.PARTY_TYPE_MISMATCH, name);
        }
        Resolution unavailable = checkAvailable(account, name);
        return unavailable != null ? unavailable : new Resolution.Resolved(Party.account(account.getAccountId(), wanted));
    }

    /**
     * @return the refusal if the account cannot hold a role, else {@code null}
     */
    private @Nullable Resolution checkAvailable(@NotNull Account account, @NotNull String name) {
        if (account.isArchived()) {
            return refused(MessageKeys.PARTY_ARCHIVED_ACCOUNT, name);
        }
        if (account.isRequiresAuthorization()) {
            return refused(MessageKeys.PARTY_REQUIRES_AUTHORIZATION, name);
        }
        return null;
    }

    private static @Nullable Integer parseId(@NotNull String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static @NotNull Resolution.Refused refused(@NotNull String messageKey, @NotNull String name) {
        return new Resolution.Refused(messageKey, name);
    }
}
