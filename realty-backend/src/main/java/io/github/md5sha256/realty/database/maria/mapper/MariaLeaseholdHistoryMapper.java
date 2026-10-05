package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.database.entity.LeaseholdHistoryEntity;
import io.github.md5sha256.realty.database.mapper.LeaseholdHistoryMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MariaLeaseholdHistoryMapper extends LeaseholdHistoryMapper {

    @Override
    @Insert("""
            INSERT INTO LeaseholdHistory (worldGuardRegionId, worldId, eventType, tenantId, landlordPartyId,
                                          price, durationSeconds, extensionsRemaining)
            VALUES (#{worldGuardRegionId}, #{worldId}, #{eventType}, #{tenantId}, #{landlordPartyId},
                    #{price}, #{durationSeconds}, #{extensionsRemaining})
            """)
    int insert(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
               @Param("worldId") @NotNull UUID worldId,
               @Param("eventType") @NotNull String eventType,
               @Param("tenantId") @Nullable UUID tenantId,
               @Param("landlordPartyId") int landlordPartyId,
               @Param("price") @Nullable Double price,
               @Param("durationSeconds") @Nullable Long durationSeconds,
               @Param("extensionsRemaining") @Nullable Integer extensionsRemaining);

    @Override
    // A select, because it answers with a row, and one that is declared to write. See
    // MariaFreeholdHistoryMapper#insertReturningId.
    @Select(value = """
            INSERT INTO LeaseholdHistory (worldGuardRegionId, worldId, eventType, tenantId, landlordPartyId,
                                          price, durationSeconds, extensionsRemaining)
            VALUES (#{worldGuardRegionId}, #{worldId}, #{eventType}, #{tenantId}, #{landlordPartyId},
                    #{price}, #{durationSeconds}, #{extensionsRemaining})
            RETURNING historyId
            """, affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    int insertReturningId(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                          @Param("worldId") @NotNull UUID worldId,
                          @Param("eventType") @NotNull String eventType,
                          @Param("tenantId") @Nullable UUID tenantId,
                          @Param("landlordPartyId") int landlordPartyId,
                          @Param("price") @Nullable Double price,
                          @Param("durationSeconds") @Nullable Long durationSeconds,
                          @Param("extensionsRemaining") @Nullable Integer extensionsRemaining);

    @Override
    @Delete("""
            DELETE FROM LeaseholdHistory
            WHERE historyId = #{historyId}
            """)
    int deleteById(@Param("historyId") int historyId);

    @Override
    @SelectProvider(type = LeaseholdHistorySqlProvider.class, method = "searchHistory")
    @ConstructorArgs({
            @Arg(column = "historyId", javaType = int.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "eventType", javaType = String.class),
            @Arg(column = "tenantId", javaType = UUID.class),
            @Arg(resultMap = PartySql.RESULT_MAP, columnPrefix = "landlord_", javaType = Party.class),
            @Arg(column = "price", javaType = Double.class),
            @Arg(column = "durationSeconds", javaType = Long.class),
            @Arg(column = "extensionsRemaining", javaType = Integer.class),
            @Arg(column = "eventTime", javaType = LocalDateTime.class)
    })
    @NotNull List<LeaseholdHistoryEntity> searchHistory(
            @Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
            @Param("worldId") @NotNull UUID worldId,
            @Param("eventType") @Nullable String eventType,
            @Param("since") @Nullable LocalDateTime since,
            @Param("playerId") @Nullable UUID playerId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Override
    @SelectProvider(type = LeaseholdHistorySqlProvider.class, method = "countHistory")
    int countHistory(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                     @Param("worldId") @NotNull UUID worldId,
                     @Param("eventType") @Nullable String eventType,
                     @Param("since") @Nullable LocalDateTime since,
                     @Param("playerId") @Nullable UUID playerId);
}
