package io.github.md5sha256.realty.database.maria.mapper;

/**
 * SQL fragments shared by every mapper that joins the party kind tables for a contract's landlord or
 * authority. The column lists let a joining mapper select a party's own columns under a
 * table-alias-specific prefix, which {@link MariaPartyMapper}'s {@code party} result map (see
 * {@link #RESULT_MAP}) then reassembles into a {@link io.github.md5sha256.realty.api.Party} via
 * {@code @Arg(resultMap = PartySql.RESULT_MAP, columnPrefix = "...")}.
 */
final class PartySql {

    private PartySql() {
    }

    /** Result map id, for {@code @Arg(resultMap = PartySql.RESULT_MAP, columnPrefix = "landlord_")}. */
    static final String RESULT_MAP = "io.github.md5sha256.realty.database.maria.mapper.MariaPartyMapper.party";

    /**
     * Statement id of {@link MariaPartyMapper#selectById}, for a nested
     * {@code @Arg(column = "...PartyId", select = PartySql.SELECT_BY_ID)} where joining the
     * kind tables into the statement is unwanted, such as in a locking read.
     */
    static final String SELECT_BY_ID = "io.github.md5sha256.realty.database.maria.mapper.MariaPartyMapper.selectById";

    // The three kind tables left-joined on a party id, plus the account behind a group. Exactly one
    // of pp, ap, gp matches; the base table is not joined. The prefix letter (l or a) keeps the
    // landlord's and the authority's aliases apart in one statement. Written out four times rather
    // than built by a method: an annotation value must be a compile-time constant.

    static final String LANDLORD_JOINS_CONTRACT =
            " LEFT JOIN PersonalParty lpp ON lpp.partyId = lc.landlordPartyId"
                    + " LEFT JOIN AccountParty lap ON lap.partyId = lc.landlordPartyId"
                    + " LEFT JOIN GroupParty lgp ON lgp.partyId = lc.landlordPartyId"
                    + " LEFT JOIN AccountParty lga ON lga.partyId = lgp.accountPartyId ";

    static final String LANDLORD_JOINS_HISTORY =
            " LEFT JOIN PersonalParty lpp ON lpp.partyId = lh.landlordPartyId"
                    + " LEFT JOIN AccountParty lap ON lap.partyId = lh.landlordPartyId"
                    + " LEFT JOIN GroupParty lgp ON lgp.partyId = lh.landlordPartyId"
                    + " LEFT JOIN AccountParty lga ON lga.partyId = lgp.accountPartyId ";

    static final String AUTHORITY_JOINS_CONTRACT =
            " LEFT JOIN PersonalParty app ON app.partyId = fc.authorityPartyId"
                    + " LEFT JOIN AccountParty aap ON aap.partyId = fc.authorityPartyId"
                    + " LEFT JOIN GroupParty agp ON agp.partyId = fc.authorityPartyId"
                    + " LEFT JOIN AccountParty aga ON aga.partyId = agp.accountPartyId ";

    static final String AUTHORITY_JOINS_HISTORY =
            " LEFT JOIN PersonalParty app ON app.partyId = fh.authorityPartyId"
                    + " LEFT JOIN AccountParty aap ON aap.partyId = fh.authorityPartyId"
                    + " LEFT JOIN GroupParty agp ON agp.partyId = fh.authorityPartyId"
                    + " LEFT JOIN AccountParty aga ON aga.partyId = agp.accountPartyId ";

    /** The party columns of the joins with prefix letter {@code l}, aliased under {@code landlord_}. */
    static final String LANDLORD_COLUMNS =
            " COALESCE(lpp.kind, lap.kind, lgp.kind) AS landlord_kind, lpp.playerUuid AS landlord_playerUuid,"
                    + " lap.accountId AS landlord_accountId, lap.accountKind AS landlord_accountKind,"
                    + " lgp.groupName AS landlord_groupName,"
                    + " lga.accountId AS landlord_groupAccountId, lga.accountKind AS landlord_groupAccountKind ";

    /** The party columns of the joins with prefix letter {@code a}, aliased under {@code authority_}. */
    static final String AUTHORITY_COLUMNS =
            " COALESCE(app.kind, aap.kind, agp.kind) AS authority_kind, app.playerUuid AS authority_playerUuid,"
                    + " aap.accountId AS authority_accountId, aap.accountKind AS authority_accountKind,"
                    + " agp.groupName AS authority_groupName,"
                    + " aga.accountId AS authority_groupAccountId, aga.accountKind AS authority_groupAccountKind ";

    /** The landlord's columns under the {@code second_} prefix the activity feed reads its second party with. */
    static final String LANDLORD_AS_SECOND_COLUMNS =
            " COALESCE(lpp.kind, lap.kind, lgp.kind) AS second_kind, lpp.playerUuid AS second_playerUuid,"
                    + " lap.accountId AS second_accountId, lap.accountKind AS second_accountKind,"
                    + " lgp.groupName AS second_groupName,"
                    + " lga.accountId AS second_groupAccountId, lga.accountKind AS second_groupAccountKind ";

    /** The authority's columns under the {@code second_} prefix. */
    static final String AUTHORITY_AS_SECOND_COLUMNS =
            " COALESCE(app.kind, aap.kind, agp.kind) AS second_kind, app.playerUuid AS second_playerUuid,"
                    + " aap.accountId AS second_accountId, aap.accountKind AS second_accountKind,"
                    + " agp.groupName AS second_groupName,"
                    + " aga.accountId AS second_groupAccountId, aga.accountKind AS second_groupAccountKind ";

    /**
     * The columns of a party other than {@code kind} and {@code playerUuid}, under the {@code second_}
     * prefix, all {@code NULL}. A branch of the activity feed whose second party is always a player selects {@code 'PERSONAL' AS
     * second_kind} and the player's UUID {@code AS second_playerUuid}, then this.
     */
    static final String NULL_SECOND_ACCOUNT_COLUMNS =
            " CAST(NULL AS SIGNED) AS second_accountId, CAST(NULL AS CHAR(16)) AS second_accountKind,"
                    + " CAST(NULL AS CHAR(64)) AS second_groupName,"
                    + " CAST(NULL AS SIGNED) AS second_groupAccountId, CAST(NULL AS CHAR(16)) AS second_groupAccountKind ";

    /** The landlord is this player: only a personal party has a UUID, so no kind test is needed. */
    static final String LANDLORD_IS_PLAYER = "lpp.playerUuid = #{playerId}";

    /** The authority is this player. */
    static final String AUTHORITY_IS_PLAYER = "app.playerUuid = #{playerId}";
}
