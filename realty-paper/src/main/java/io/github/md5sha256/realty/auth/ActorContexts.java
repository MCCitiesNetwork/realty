package io.github.md5sha256.realty.auth;

import io.github.md5sha256.realty.api.ActorContext;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdContractEntity;
import io.github.md5sha256.realty.settings.AccountManagers;
import io.github.md5sha256.realty.settings.Settings;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.AccountMember;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds the {@link ActorContext} a command passes to the backend: which of a given list of parties
 * the player may act for, and which of them the player may hand to another party.
 *
 * <p>Treasury cannot list the accounts a player belongs to, so each candidate party is tested on its
 * own. An account is managed by its authorizers, and by its members too when {@code account-managers}
 * is {@code members}; only its authorizers may reassign it. A group is managed by the players Vault
 * places in it, and reassigned by the authorizers of the group's account. Without Treasury no account
 * or group is managed; without Vault's permission service no group is.</p>
 *
 * <p>Treasury's member and authorizer lists are direct rows only; a player granted access through a
 * permission group is not seen.</p>
 */
public final class ActorContexts {

    private final @Nullable TreasuryApi treasury;
    private final @Nullable Permission vaultPermission;
    private final @NotNull AtomicReference<Settings> settings;
    private final @NotNull RealtyBackend backend;

    public ActorContexts(@Nullable TreasuryApi treasury, @Nullable Permission vaultPermission,
                         @NotNull AtomicReference<Settings> settings, @NotNull RealtyBackend backend) {
        this.treasury = treasury;
        this.vaultPermission = vaultPermission;
        this.settings = settings;
        this.backend = backend;
    }

    /** Tests each candidate. Does blocking I/O: call it on the database executor. */
    public @NotNull ActorContext of(@NotNull OfflinePlayer player, boolean bypass,
                                    @NotNull Collection<Party> candidates) {
        UUID playerId = player.getUniqueId();
        AccountManagers accountManagers = this.settings.get().accountManagers();
        Set<Party> manages = new HashSet<>();
        Set<Party> reassigns = new HashSet<>();
        for (Party candidate : new LinkedHashSet<>(candidates)) {
            switch (candidate) {
                // The player is added by ActorContext itself; another player is never managed.
                case Party.Personal _ -> {
                }
                case Party.Account account -> {
                    if (this.treasury == null) {
                        continue;
                    }
                    if (isAuthorizer(this.treasury, account.accountId(), playerId)) {
                        manages.add(account);
                        reassigns.add(account);
                    } else if (accountManagers == AccountManagers.MEMBERS
                            && contains(this.treasury.getMembers(account.accountId()), playerId)) {
                        manages.add(account);
                    }
                }
                case Party.Group group -> {
                    if (this.treasury == null) {
                        continue;
                    }
                    if (this.vaultPermission != null
                            && this.vaultPermission.playerInGroup((String) null, player, group.groupName())) {
                        manages.add(group);
                    }
                    // Moving a role away from a group is decided by whoever controls its money.
                    if (isAuthorizer(this.treasury, group.accountId(), playerId)) {
                        reassigns.add(group);
                    }
                }
            }
        }
        return new ActorContext(playerId, manages, reassigns, bypass);
    }

    /** Candidates: the landlord and the authority of the region, plus {@code extra}. */
    public @NotNull ActorContext forRegion(@NotNull OfflinePlayer player, boolean bypass,
                                           @NotNull WorldGuardRegion region, @NotNull Party... extra) {
        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        List<Party> candidates = new ArrayList<>();
        LeaseholdContractEntity lease = this.backend.getLeaseholdContract(regionId, worldId);
        if (lease != null) {
            candidates.add(lease.landlord());
        }
        FreeholdContractEntity freehold = this.backend.getFreeholdContract(regionId, worldId);
        if (freehold != null) {
            candidates.add(freehold.authority());
        }
        candidates.addAll(Arrays.asList(extra));
        return of(player, bypass, candidates);
    }

    /** Candidates: every non-player party in the Party table. */
    public @NotNull ActorContext forEveryParty(@NotNull OfflinePlayer player) {
        return of(player, false, this.backend.listNonPlayerParties());
    }

    private static boolean isAuthorizer(@NotNull TreasuryApi treasury, int accountId, @NotNull UUID playerId) {
        return contains(treasury.getAuthorizers(accountId), playerId);
    }

    private static boolean contains(@NotNull List<AccountMember> rows, @NotNull UUID playerId) {
        for (AccountMember row : rows) {
            if (playerId.equals(row.getMemberUuid())) {
                return true;
            }
        }
        return false;
    }
}
