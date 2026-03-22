package io.github.md5sha256.realty.database.maria.mapper;

import org.apache.ibatis.jdbc.SQL;

import java.util.Map;

public class LeaseHistorySqlProvider {

    public String searchHistory(Map<String, Object> params) {
        return new SQL() {{
            SELECT("historyId, worldGuardRegionId, worldId, eventType, tenantId, landlordId, price, durationSeconds, extensionsRemaining, eventTime");
            FROM("LeaseHistory");
            WHERE("worldGuardRegionId = #{worldGuardRegionId}");
            WHERE("worldId = #{worldId}");
            if (params.get("eventType") != null) {
                WHERE("eventType = #{eventType}");
            }
            if (params.get("since") != null) {
                WHERE("eventTime >= #{since}");
            }
            if (params.get("playerId") != null) {
                WHERE("(tenantId = #{playerId} OR landlordId = #{playerId})");
            }
            ORDER_BY("eventTime DESC");
            LIMIT("#{limit}");
            OFFSET("#{offset}");
        }}.toString();
    }

    public String countHistory(Map<String, Object> params) {
        return new SQL() {{
            SELECT("COUNT(*)");
            FROM("LeaseHistory");
            WHERE("worldGuardRegionId = #{worldGuardRegionId}");
            WHERE("worldId = #{worldId}");
            if (params.get("eventType") != null) {
                WHERE("eventType = #{eventType}");
            }
            if (params.get("since") != null) {
                WHERE("eventTime >= #{since}");
            }
            if (params.get("playerId") != null) {
                WHERE("(tenantId = #{playerId} OR landlordId = #{playerId})");
            }
        }}.toString();
    }
}
