package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.database.entity.GroupMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PartyMapper {

    /** The id of the party's row, inserting the row if needed. A Group is never inserted here. */
    int findOrInsert(@NotNull Party party);

    /** A Group is found by groupName alone. */
    @Nullable Integer findId(@NotNull Party party);

    /**
     * The account party stored for {@code accountId}, whatever kind it was stored under, or
     * {@code null} when no row names it. Never inserts and never throws for a kind mismatch.
     */
    @Nullable Party.Account findAccountParty(int accountId);

    @Nullable Party selectById(int partyId);

    /** Every party that is not PERSONAL, in the order the rows were created. */
    @NotNull List<Party> selectNonPersonal();

    /**
     * The group's mapped party, or {@code null} if the group has no account yet. Group names
     * are stored in lower case; {@code groupName} is looked up case-insensitively.
     */
    @Nullable Party.Group findGroupParty(@NotNull String groupName);

    /**
     * The id of the group's row, locked until the transaction ends so that no contract or
     * history row can start to name the group meanwhile; {@code null} if the group has no
     * account. {@code groupName} must be in lower case. Locks the base row as well as the group's
     * row: a contract or history write that names the group takes a shared lock on the base row,
     * so it waits here rather than slipping in between the counts and the delete.
     */
    @Nullable Integer lockGroupId(@NotNull String groupName);

    /**
     * Creates the group's party, and the account's party first if it has none; returns the group's
     * party id. {@code groupName} must be in lower case. A plain insert, not an {@code INSERT IGNORE}:
     * that would also turn a name too long for the column into a warning and store it cut short.
     */
    int insertGroup(@NotNull String groupName, int accountId, @NotNull AccountKind accountKind);

    /** Points the group at another account's party, created if absent; its own party id does not change. */
    int updateGroupAccount(int partyId, int accountId, @NotNull AccountKind accountKind);

    int deleteGroup(int partyId);

    /** How many leasehold and freehold contracts name the party as their landlord or authority. */
    int countContractsNaming(int partyId);

    /** How many leasehold and freehold history entries name the party as their landlord or authority. */
    int countHistoryNaming(int partyId);

    /** Every group that has an account, ordered by group name. */
    @NotNull List<GroupMapping> selectGroupMappings();
}
