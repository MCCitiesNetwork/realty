package io.github.md5sha256.realty.notify;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.settings.AccountManagers;
import io.github.md5sha256.realty.settings.Settings;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.AccountMember;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Turns a contract party into the players a notice about it should reach. A player is their own
 * recipient. An account has no inbox, so its notices go to the people who act for it: its
 * authorizers, and its members too when {@code account-managers} is {@code members}. A group's
 * notices go to the players of the group who are online, since Vault cannot tell the groups of an
 * offline player.
 *
 * <p>Without Treasury an account has no recipients; without Vault's permission service a group has
 * none.</p>
 */
public final class PartyRecipients {

    private final @NotNull Server server;
    private final @Nullable TreasuryApi treasury;
    private final @Nullable Permission vaultPermission;
    private final @NotNull AtomicReference<Settings> settings;

    public PartyRecipients(@NotNull Server server, @Nullable TreasuryApi treasury,
                           @Nullable Permission vaultPermission, @NotNull AtomicReference<Settings> settings) {
        this.server = server;
        this.treasury = treasury;
        this.vaultPermission = vaultPermission;
        this.settings = settings;
    }

    /** Does blocking I/O: call it on the database executor. Never null; may be empty; no duplicates. */
    public @NotNull List<UUID> expand(@NotNull Party party) {
        Set<UUID> recipients = new LinkedHashSet<>();
        switch (party) {
            case Party.Personal personal -> recipients.add(personal.playerUuid());
            case Party.Account account -> addManagers(recipients, account.accountId());
            case Party.Group group -> addOnlineMembers(recipients, group.groupName());
        }
        return List.copyOf(recipients);
    }

    private void addManagers(@NotNull Set<UUID> recipients, int accountId) {
        if (this.treasury == null) {
            return;
        }
        addAll(recipients, this.treasury.getAuthorizers(accountId));
        if (this.settings.get().accountManagers() == AccountManagers.MEMBERS) {
            addAll(recipients, this.treasury.getMembers(accountId));
        }
    }

    private void addOnlineMembers(@NotNull Set<UUID> recipients, @NotNull String groupName) {
        if (this.vaultPermission == null) {
            return;
        }
        for (Player player : this.server.getOnlinePlayers()) {
            if (this.vaultPermission.playerInGroup((String) null, player, groupName)) {
                recipients.add(player.getUniqueId());
            }
        }
    }

    private static void addAll(@NotNull Set<UUID> recipients, @NotNull List<AccountMember> rows) {
        for (AccountMember row : rows) {
            recipients.add(row.getMemberUuid());
        }
    }
}
