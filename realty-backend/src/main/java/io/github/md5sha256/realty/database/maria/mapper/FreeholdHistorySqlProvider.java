package io.github.md5sha256.realty.database.maria.mapper;

import org.apache.ibatis.jdbc.SQL;

import java.util.Map;

public class FreeholdHistorySqlProvider {

    /** A player is on a record as its buyer, or as its authority when the authority is that player. */
    private static final String PLAYER_FILTER =
            "(fh.buyerId = #{playerId} OR " + PartySql.AUTHORITY_IS_PLAYER + ")";

    public String searchHistory(Map<String, Object> params) {
        return new SQL() {{
            SELECT("fh.historyId, fh.worldGuardRegionId, fh.worldId, fh.eventType, fh.buyerId,"
                    + PartySql.AUTHORITY_COLUMNS
                    + ", fh.price, fh.eventTime");
            FROM("FreeholdHistory fh");
            LEFT_OUTER_JOIN("PersonalParty app ON app.partyId = fh.authorityPartyId");
            LEFT_OUTER_JOIN("AccountParty aap ON aap.partyId = fh.authorityPartyId");
            LEFT_OUTER_JOIN("GroupParty agp ON agp.partyId = fh.authorityPartyId");
            LEFT_OUTER_JOIN("AccountParty aga ON aga.partyId = agp.accountPartyId");
            WHERE("fh.worldGuardRegionId = #{worldGuardRegionId}");
            WHERE("fh.worldId = #{worldId}");
            if (params.get("eventType") != null) {
                WHERE("fh.eventType = #{eventType}");
            }
            if (params.get("since") != null) {
                WHERE("fh.eventTime >= #{since}");
            }
            if (params.get("playerId") != null) {
                WHERE(PLAYER_FILTER);
            }
            ORDER_BY("fh.eventTime DESC");
            LIMIT("#{limit}");
            OFFSET("#{offset}");
        }}.toString();
    }

    public String countHistory(Map<String, Object> params) {
        return new SQL() {{
            SELECT("COUNT(*)");
            FROM("FreeholdHistory fh");
            LEFT_OUTER_JOIN("PersonalParty app ON app.partyId = fh.authorityPartyId");
            LEFT_OUTER_JOIN("AccountParty aap ON aap.partyId = fh.authorityPartyId");
            LEFT_OUTER_JOIN("GroupParty agp ON agp.partyId = fh.authorityPartyId");
            LEFT_OUTER_JOIN("AccountParty aga ON aga.partyId = agp.accountPartyId");
            WHERE("fh.worldGuardRegionId = #{worldGuardRegionId}");
            WHERE("fh.worldId = #{worldId}");
            if (params.get("eventType") != null) {
                WHERE("fh.eventType = #{eventType}");
            }
            if (params.get("since") != null) {
                WHERE("fh.eventTime >= #{since}");
            }
            if (params.get("playerId") != null) {
                WHERE(PLAYER_FILTER);
            }
        }}.toString();
    }
}
