package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.database.entity.GroupMapping;
import io.github.md5sha256.realty.database.mapper.PartyMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.Case;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.TypeDiscriminator;
import org.apache.ibatis.annotations.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public interface MariaPartyMapper extends PartyMapper {

    @Override
    default int findOrInsert(@NotNull Party party) {
        return switch (party) {
            case Party.Personal personal -> {
                Integer existing = selectPersonalId(personal.playerUuid());
                if (existing != null) {
                    yield existing;
                }
                // Base row first, then the kind row. If another caller made the same party since
                // the read above, the kind row's unique key refuses ours; the caller rolls back
                // and reads theirs.
                int partyId = insertBase("PERSONAL");
                insertPersonal(partyId, personal.playerUuid());
                yield partyId;
            }
            case Party.Account account -> {
                // An account's identity is its accountId alone, so a row stored under another kind
                // is a genuine conflict rather than "not found".
                PartyAccountRow existing = selectAccountRow(account.accountId());
                if (existing != null) {
                    yield existing.requirePartyId(account.accountId(), account.kind());
                }
                int partyId = insertBase("ACCOUNT");
                insertAccount(partyId, account.accountId(), account.kind());
                yield partyId;
            }
            case Party.Group group -> {
                Integer id = selectGroupId(group.groupName());
                if (id == null) {
                    throw new IllegalStateException("group not mapped: " + group.groupName());
                }
                yield id;
            }
        };
    }

    @Override
    default @Nullable Integer findId(@NotNull Party party) {
        return switch (party) {
            case Party.Personal personal -> selectPersonalId(personal.playerUuid());
            case Party.Account account -> {
                PartyAccountRow row = selectAccountRow(account.accountId());
                yield row == null ? null : row.requirePartyId(account.accountId(), account.kind());
            }
            case Party.Group group -> selectGroupId(group.groupName());
        };
    }

    @Override
    default @Nullable Party.Account findAccountParty(int accountId) {
        PartyAccountRow row = selectAccountRow(accountId);
        return row == null ? null : new Party.Account(accountId, row.kind());
    }

    /**
     * The base row of a new party; its id comes back with {@code RETURNING}, as every insert here does.
     * Flushes the session cache like the other RETURNING inserts, so two calls in one session never
     * return one id.
     */
    @Select("""
            INSERT INTO Party (kind)
            VALUES (#{kind})
            RETURNING partyId
            """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    int insertBase(@Param("kind") @NotNull String kind);

    @Insert("""
            INSERT INTO PersonalParty (partyId, playerUuid)
            VALUES (#{partyId}, #{playerUuid})
            """)
    int insertPersonal(@Param("partyId") int partyId, @Param("playerUuid") @NotNull UUID playerUuid);

    @Select("""
            SELECT partyId
            FROM PersonalParty
            WHERE playerUuid = #{playerUuid}
            """)
    @Nullable Integer selectPersonalId(@Param("playerUuid") @NotNull UUID playerUuid);

    @Insert("""
            INSERT INTO AccountParty (partyId, accountId, accountKind)
            VALUES (#{partyId}, #{accountId}, #{accountKind})
            """)
    int insertAccount(@Param("partyId") int partyId,
                      @Param("accountId") int accountId,
                      @Param("accountKind") @NotNull AccountKind accountKind);

    /** The row stored for this accountId, whatever kind it was stored under. */
    @Select("""
            SELECT partyId, accountKind AS kind
            FROM AccountParty
            WHERE accountId = #{accountId}
            """)
    @ConstructorArgs({
            @Arg(column = "partyId", javaType = int.class),
            @Arg(column = "kind", javaType = AccountKind.class)
    })
    @Nullable PartyAccountRow selectAccountRow(@Param("accountId") int accountId);

    @Select("""
            SELECT partyId
            FROM GroupParty
            WHERE groupName = #{groupName}
            """)
    @Nullable Integer selectGroupId(@Param("groupName") @NotNull String groupName);

    @Override
    @Select("""
            SELECT COALESCE(pp.kind, ap.kind, gp.kind) AS kind, pp.playerUuid,
                   ap.accountId, ap.accountKind, gp.groupName,
                   ga.accountId AS groupAccountId, ga.accountKind AS groupAccountKind
            FROM Party p
            LEFT JOIN PersonalParty pp ON pp.partyId = p.partyId
            LEFT JOIN AccountParty ap ON ap.partyId = p.partyId
            LEFT JOIN GroupParty gp ON gp.partyId = p.partyId
            LEFT JOIN AccountParty ga ON ga.partyId = gp.accountPartyId
            WHERE p.partyId = #{partyId}
            AND COALESCE(pp.kind, ap.kind, gp.kind) IS NOT NULL
            """)
    @Results(id = "party")
    @TypeDiscriminator(column = "kind", javaType = String.class, cases = {
            @Case(value = "PERSONAL", type = Party.Personal.class, constructArgs = {
                    @Arg(column = "playerUuid", javaType = UUID.class)}),
            @Case(value = "ACCOUNT", type = Party.Account.class, constructArgs = {
                    @Arg(column = "accountId", javaType = int.class),
                    @Arg(column = "accountKind", javaType = AccountKind.class)}),
            @Case(value = "GROUP", type = Party.Group.class, constructArgs = {
                    @Arg(column = "groupName", javaType = String.class),
                    @Arg(column = "groupAccountId", javaType = int.class),
                    @Arg(column = "groupAccountKind", javaType = AccountKind.class)})
    })
    @Nullable Party selectById(@Param("partyId") int partyId);

    @Override
    @Select("""
            SELECT COALESCE(pp.kind, ap.kind, gp.kind) AS kind, pp.playerUuid,
                   ap.accountId, ap.accountKind, gp.groupName,
                   ga.accountId AS groupAccountId, ga.accountKind AS groupAccountKind
            FROM Party p
            LEFT JOIN PersonalParty pp ON pp.partyId = p.partyId
            LEFT JOIN AccountParty ap ON ap.partyId = p.partyId
            LEFT JOIN GroupParty gp ON gp.partyId = p.partyId
            LEFT JOIN AccountParty ga ON ga.partyId = gp.accountPartyId
            WHERE p.kind <> 'PERSONAL'
            AND COALESCE(pp.kind, ap.kind, gp.kind) IS NOT NULL
            ORDER BY p.partyId
            """)
    @ResultMap("party")
    @NotNull List<Party> selectNonPersonal();

    @Override
    default @Nullable Party.Group findGroupParty(@NotNull String groupName) {
        Integer partyId = selectGroupId(groupName.toLowerCase(Locale.ROOT));
        if (partyId == null) {
            return null;
        }
        Party party = selectById(partyId);
        return party instanceof Party.Group group ? group : null;
    }

    @Override
    @Select("""
            SELECT gp.partyId
            FROM GroupParty gp
            JOIN Party p ON p.partyId = gp.partyId
            WHERE gp.groupName = #{groupName}
            FOR UPDATE
            """)
    @Nullable Integer lockGroupId(@Param("groupName") @NotNull String groupName);

    @Insert("""
            INSERT INTO GroupParty (partyId, groupName, accountPartyId)
            VALUES (#{partyId}, #{groupName}, #{accountPartyId})
            """)
    int insertGroupRow(@Param("partyId") int partyId,
                       @Param("groupName") @NotNull String groupName,
                       @Param("accountPartyId") int accountPartyId);

    @Override
    default int insertGroup(@NotNull String groupName, int accountId, @NotNull AccountKind accountKind) {
        // The account's party first, so that the group's row can point at it.
        int accountPartyId = findOrInsert(new Party.Account(accountId, accountKind));
        int partyId = insertBase("GROUP");
        insertGroupRow(partyId, groupName, accountPartyId);
        return partyId;
    }

    @Override
    default int updateGroupAccount(int partyId, int accountId, @NotNull AccountKind accountKind) {
        int accountPartyId = findOrInsert(new Party.Account(accountId, accountKind));
        return repointGroup(partyId, accountPartyId);
    }

    @Update("""
            UPDATE GroupParty
            SET accountPartyId = #{accountPartyId}
            WHERE partyId = #{partyId}
            """)
    int repointGroup(@Param("partyId") int partyId, @Param("accountPartyId") int accountPartyId);

    @Override
    default int deleteGroup(int partyId) {
        int rows = deleteGroupRow(partyId);
        if (rows > 0) {
            deleteBase(partyId);
        }
        return rows;
    }

    @Delete("""
            DELETE FROM GroupParty
            WHERE partyId = #{partyId}
            """)
    int deleteGroupRow(@Param("partyId") int partyId);

    @Delete("""
            DELETE FROM Party
            WHERE partyId = #{partyId}
            """)
    int deleteBase(@Param("partyId") int partyId);

    @Override
    @Select("""
            SELECT (SELECT COUNT(*) FROM LeaseholdContract WHERE landlordPartyId = #{partyId})
                 + (SELECT COUNT(*) FROM FreeholdContract WHERE authorityPartyId = #{partyId})
            """)
    int countContractsNaming(@Param("partyId") int partyId);

    @Override
    @Select("""
            SELECT (SELECT COUNT(*) FROM LeaseholdHistory WHERE landlordPartyId = #{partyId})
                 + (SELECT COUNT(*) FROM FreeholdHistory WHERE authorityPartyId = #{partyId})
            """)
    int countHistoryNaming(@Param("partyId") int partyId);

    @Select("""
            SELECT gp.partyId, gp.groupName, ga.accountId AS groupAccountId, ga.accountKind AS groupAccountKind,
                   (SELECT COUNT(*) FROM LeaseholdContract lc WHERE lc.landlordPartyId = gp.partyId)
                 + (SELECT COUNT(*) FROM FreeholdContract fc WHERE fc.authorityPartyId = gp.partyId)
                   AS contractCount
            FROM GroupParty gp
            JOIN AccountParty ga ON ga.partyId = gp.accountPartyId
            ORDER BY gp.groupName
            """)
    @ConstructorArgs({
            @Arg(column = "partyId", javaType = int.class, id = true),
            @Arg(column = "groupName", javaType = String.class),
            @Arg(column = "groupAccountId", javaType = int.class),
            @Arg(column = "groupAccountKind", javaType = AccountKind.class),
            @Arg(column = "contractCount", javaType = int.class)
    })
    @NotNull List<GroupMappingRow> selectGroupMappingRows();

    @Override
    default @NotNull List<GroupMapping> selectGroupMappings() {
        return selectGroupMappingRows().stream().map(GroupMappingRow::toGroupMapping).toList();
    }
}
