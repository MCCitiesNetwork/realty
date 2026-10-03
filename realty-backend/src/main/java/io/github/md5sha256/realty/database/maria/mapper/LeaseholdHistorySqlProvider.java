package io.github.md5sha256.realty.database.maria.mapper;

import org.apache.ibatis.jdbc.SQL;

import java.util.Map;

public class LeaseholdHistorySqlProvider {

    /** A player is on a record as its tenant, or as its landlord when the landlord is that player. */
    private static final String PLAYER_FILTER =
            "(lh.tenantId = #{playerId} OR " + PartySql.LANDLORD_IS_PLAYER + ")";

    public String searchHistory(Map<String, Object> params) {
        return new SQL() {{
            SELECT("lh.historyId, lh.worldGuardRegionId, lh.worldId, lh.eventType, lh.tenantId,"
                    + PartySql.LANDLORD_COLUMNS
                    + ", lh.price, lh.durationSeconds, lh.extensionsRemaining, lh.eventTime");
            FROM("LeaseholdHistory lh");
            LEFT_OUTER_JOIN("PersonalParty lpp ON lpp.partyId = lh.landlordPartyId");
            LEFT_OUTER_JOIN("AccountParty lap ON lap.partyId = lh.landlordPartyId");
            LEFT_OUTER_JOIN("GroupParty lgp ON lgp.partyId = lh.landlordPartyId");
            LEFT_OUTER_JOIN("AccountParty lga ON lga.partyId = lgp.accountPartyId");
            WHERE("lh.worldGuardRegionId = #{worldGuardRegionId}");
            WHERE("lh.worldId = #{worldId}");
            if (params.get("eventType") != null) {
                WHERE("lh.eventType = #{eventType}");
            }
            if (params.get("since") != null) {
                WHERE("lh.eventTime >= #{since}");
            }
            if (params.get("playerId") != null) {
                WHERE(PLAYER_FILTER);
            }
            ORDER_BY("lh.eventTime DESC");
            LIMIT("#{limit}");
            OFFSET("#{offset}");
        }}.toString();
    }

    public String countHistory(Map<String, Object> params) {
        return new SQL() {{
            SELECT("COUNT(*)");
            FROM("LeaseholdHistory lh");
            LEFT_OUTER_JOIN("PersonalParty lpp ON lpp.partyId = lh.landlordPartyId");
            LEFT_OUTER_JOIN("AccountParty lap ON lap.partyId = lh.landlordPartyId");
            LEFT_OUTER_JOIN("GroupParty lgp ON lgp.partyId = lh.landlordPartyId");
            LEFT_OUTER_JOIN("AccountParty lga ON lga.partyId = lgp.accountPartyId");
            WHERE("lh.worldGuardRegionId = #{worldGuardRegionId}");
            WHERE("lh.worldId = #{worldId}");
            if (params.get("eventType") != null) {
                WHERE("lh.eventType = #{eventType}");
            }
            if (params.get("since") != null) {
                WHERE("lh.eventTime >= #{since}");
            }
            if (params.get("playerId") != null) {
                WHERE(PLAYER_FILTER);
            }
        }}.toString();
    }
}
