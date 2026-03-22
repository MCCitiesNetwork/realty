package io.github.md5sha256.realty.database.maria.mapper;

import org.apache.ibatis.jdbc.SQL;

import java.util.Map;

public class AgentHistorySqlProvider {

    public String searchHistory(Map<String, Object> params) {
        return new SQL() {{
            SELECT("historyId, worldGuardRegionId, worldId, eventType, agentId, actorId, eventTime");
            FROM("AgentHistory");
            WHERE("worldGuardRegionId = #{worldGuardRegionId}");
            WHERE("worldId = #{worldId}");
            if (params.get("eventType") != null) {
                WHERE("eventType = #{eventType}");
            }
            if (params.get("since") != null) {
                WHERE("eventTime >= #{since}");
            }
            if (params.get("playerId") != null) {
                WHERE("(agentId = #{playerId} OR actorId = #{playerId})");
            }
            ORDER_BY("eventTime DESC");
            LIMIT("#{limit}");
            OFFSET("#{offset}");
        }}.toString();
    }

    public String countHistory(Map<String, Object> params) {
        return new SQL() {{
            SELECT("COUNT(*)");
            FROM("AgentHistory");
            WHERE("worldGuardRegionId = #{worldGuardRegionId}");
            WHERE("worldId = #{worldId}");
            if (params.get("eventType") != null) {
                WHERE("eventType = #{eventType}");
            }
            if (params.get("since") != null) {
                WHERE("eventTime >= #{since}");
            }
            if (params.get("playerId") != null) {
                WHERE("(agentId = #{playerId} OR actorId = #{playerId})");
            }
        }}.toString();
    }
}
