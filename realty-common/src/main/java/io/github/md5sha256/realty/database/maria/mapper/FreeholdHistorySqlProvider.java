package io.github.md5sha256.realty.database.maria.mapper;

import org.apache.ibatis.jdbc.SQL;

import java.util.Map;

public class FreeholdHistorySqlProvider {

    public String searchHistory(Map<String, Object> params) {
        return new SQL() {{
            SELECT("historyId, worldGuardRegionId, worldId, eventType, buyerId, authorityId, price, eventTime");
            FROM("FreeholdHistory");
            WHERE("worldGuardRegionId = #{worldGuardRegionId}");
            WHERE("worldId = #{worldId}");
            if (params.get("eventType") != null) {
                WHERE("eventType = #{eventType}");
            }
            if (params.get("since") != null) {
                WHERE("eventTime >= #{since}");
            }
            if (params.get("playerId") != null) {
                WHERE("(buyerId = #{playerId} OR authorityId = #{playerId})");
            }
            ORDER_BY("eventTime DESC");
            LIMIT("#{limit}");
            OFFSET("#{offset}");
        }}.toString();
    }

    public String countHistory(Map<String, Object> params) {
        return new SQL() {{
            SELECT("COUNT(*)");
            FROM("FreeholdHistory");
            WHERE("worldGuardRegionId = #{worldGuardRegionId}");
            WHERE("worldId = #{worldId}");
            if (params.get("eventType") != null) {
                WHERE("eventType = #{eventType}");
            }
            if (params.get("since") != null) {
                WHERE("eventTime >= #{since}");
            }
            if (params.get("playerId") != null) {
                WHERE("(buyerId = #{playerId} OR authorityId = #{playerId})");
            }
        }}.toString();
    }
}
