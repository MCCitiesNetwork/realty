package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.database.entity.ActivityRow;
import io.github.md5sha256.realty.database.mapper.ActivityMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * MariaDB implementation of the activity feed.
 *
 * <p>Each branch of the union projects the same columns, padding with typed NULLs
 * where its table has no equivalent. The two party columns are positional -- see
 * {@link ActivityRow} -- because the three tables name their parties differently. The
 * second party is spread over the {@code second_} columns that {@link PartySql#RESULT_MAP}
 * reads; a branch whose second party is always a player fills in a {@code PERSONAL} one.</p>
 *
 * <p>The ordering and the paging both sit outside the union, on the combined result.
 * Ordering inside each branch and merging afterwards would page each table separately
 * and interleave three partial pages, which is not the same feed.</p>
 */
public interface MariaActivityMapper extends ActivityMapper {

    @Override
    default @NotNull List<ActivityRow> selectPage(@NotNull Collection<String> eventTypes,
                                                  @Nullable UUID worldId,
                                                  @Nullable LocalDateTime since,
                                                  int limit,
                                                  int offset) {
        return selectFeedPage(eventTypes, worldId, since, limit, offset).stream()
                .map(ActivityFeedRow::toActivityRow)
                .toList();
    }

    // Each branch is cut to the page's window before the union, in parentheses so its
    // own ORDER BY and LIMIT apply. Sorted only after the union, the three eventTime
    // indexes went unused and every page sorted every event ever recorded.
    //
    // The order has to be total, and the same inside each branch as after the union:
    // events share a second all the time, and a cut taken in an order that leaves ties
    // to the engine can hand one page a row and the next page the same row again. The
    // history id breaks the last tie; a branch's kind is constant within it, so the
    // outer order restricted to one branch is exactly that branch's own.
    @Select("""
            <script>
            <bind name="window" value="limit + offset" />
            SELECT * FROM (
                (SELECT 'freehold' AS kind, historyId, worldGuardRegionId, worldId, eventType, eventTime,
                        buyerId AS firstPlayerId,
                        price, CAST(NULL AS SIGNED) AS durationSeconds,
                        CAST(NULL AS SIGNED) AS extensionsRemaining,
                        'PERSONAL' AS second_kind, authorityId AS second_playerUuid,
                 """ + PartySql.NULL_SECOND_ACCOUNT_COLUMNS + """
                 FROM FreeholdHistory
                 WHERE eventType IN
                 <foreach item="t" collection="eventTypes" open="(" separator="," close=")">#{t}</foreach>
                 <if test="worldId != null">AND worldId = #{worldId}</if>
                 <if test="since != null">AND eventTime &gt;= #{since}</if>
                 ORDER BY eventTime DESC, worldGuardRegionId, historyId DESC LIMIT #{window})
                UNION ALL
                (SELECT 'leasehold' AS kind, lh.historyId, lh.worldGuardRegionId, lh.worldId, lh.eventType,
                        lh.eventTime, lh.tenantId AS firstPlayerId,
                        lh.price, lh.durationSeconds, lh.extensionsRemaining,
                 """ + PartySql.LANDLORD_AS_SECOND_COLUMNS + """
                 FROM LeaseholdHistory lh
                 INNER JOIN Party lp ON lp.partyId = lh.landlordPartyId
                 WHERE lh.eventType IN
                 <foreach item="t" collection="eventTypes" open="(" separator="," close=")">#{t}</foreach>
                 <if test="worldId != null">AND lh.worldId = #{worldId}</if>
                 <if test="since != null">AND lh.eventTime &gt;= #{since}</if>
                 ORDER BY lh.eventTime DESC, lh.worldGuardRegionId, lh.historyId DESC LIMIT #{window})
                UNION ALL
                (SELECT 'agent' AS kind, historyId, worldGuardRegionId, worldId, eventType, eventTime,
                        agentId AS firstPlayerId,
                        CAST(NULL AS DECIMAL(20,2)) AS price, CAST(NULL AS SIGNED) AS durationSeconds,
                        CAST(NULL AS SIGNED) AS extensionsRemaining,
                        'PERSONAL' AS second_kind, actorId AS second_playerUuid,
                 """ + PartySql.NULL_SECOND_ACCOUNT_COLUMNS + """
                 FROM AgentHistory
                 WHERE eventType IN
                 <foreach item="t" collection="eventTypes" open="(" separator="," close=")">#{t}</foreach>
                 <if test="worldId != null">AND worldId = #{worldId}</if>
                 <if test="since != null">AND eventTime &gt;= #{since}</if>
                 ORDER BY eventTime DESC, worldGuardRegionId, historyId DESC LIMIT #{window})
            ) feed
            ORDER BY eventTime DESC, worldGuardRegionId, kind, historyId DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "kind", javaType = String.class),
            @Arg(column = "historyId", javaType = int.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "eventType", javaType = String.class),
            @Arg(column = "eventTime", javaType = LocalDateTime.class),
            @Arg(column = "firstPlayerId", javaType = UUID.class),
            @Arg(resultMap = PartySql.RESULT_MAP, columnPrefix = "second_", javaType = Party.class),
            @Arg(column = "price", javaType = Double.class),
            @Arg(column = "durationSeconds", javaType = Long.class),
            @Arg(column = "extensionsRemaining", javaType = Integer.class)
    })
    @NotNull List<ActivityFeedRow> selectFeedPage(@Param("eventTypes") @NotNull Collection<String> eventTypes,
                                                  @Param("worldId") @Nullable UUID worldId,
                                                  @Param("since") @Nullable LocalDateTime since,
                                                  @Param("limit") int limit,
                                                  @Param("offset") int offset);

    @Override
    @Select("""
            <script>
            SELECT
                (SELECT COUNT(*) FROM FreeholdHistory
                 WHERE eventType IN
                 <foreach item="t" collection="eventTypes" open="(" separator="," close=")">#{t}</foreach>
                 <if test="worldId != null">AND worldId = #{worldId}</if>
                 <if test="since != null">AND eventTime &gt;= #{since}</if>)
              + (SELECT COUNT(*) FROM LeaseholdHistory
                 WHERE eventType IN
                 <foreach item="t" collection="eventTypes" open="(" separator="," close=")">#{t}</foreach>
                 <if test="worldId != null">AND worldId = #{worldId}</if>
                 <if test="since != null">AND eventTime &gt;= #{since}</if>)
              + (SELECT COUNT(*) FROM AgentHistory
                 WHERE eventType IN
                 <foreach item="t" collection="eventTypes" open="(" separator="," close=")">#{t}</foreach>
                 <if test="worldId != null">AND worldId = #{worldId}</if>
                 <if test="since != null">AND eventTime &gt;= #{since}</if>)
            </script>
            """)
    int countMatching(@Param("eventTypes") @NotNull Collection<String> eventTypes,
                      @Param("worldId") @Nullable UUID worldId,
                      @Param("since") @Nullable LocalDateTime since);
}
