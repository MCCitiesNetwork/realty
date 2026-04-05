package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.FreeholdHistoryEntity;
import io.github.md5sha256.realty.database.mapper.FreeholdHistoryMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MariaFreeholdHistoryMapper extends FreeholdHistoryMapper {

    @Override
    @Insert("""
            INSERT INTO FreeholdHistory (worldGuardRegionId, worldId, eventType, buyerId, authorityId, price)
            VALUES (#{worldGuardRegionId}, #{worldId}, #{eventType}, #{buyerId}, #{authorityId}, #{price})
            """)
    int insert(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
               @Param("worldId") @NotNull UUID worldId,
               @Param("eventType") @NotNull String eventType,
               @Param("buyerId") @NotNull UUID buyerId,
               @Param("authorityId") @NotNull UUID authorityId,
               @Param("price") double price);

    @Override
    @Select("""
            SELECT price
            FROM FreeholdHistory
            WHERE worldGuardRegionId = #{worldGuardRegionId}
            AND worldId = #{worldId}
            ORDER BY eventTime DESC
            LIMIT 1
            """)
    @Nullable Double selectLastFreeholdPrice(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                          @Param("worldId") @NotNull UUID worldId);

    @Override
    @SelectProvider(type = FreeholdHistorySqlProvider.class, method = "searchHistory")
    @ConstructorArgs({
            @Arg(column = "historyId", javaType = int.class),
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "eventType", javaType = String.class),
            @Arg(column = "buyerId", javaType = UUID.class),
            @Arg(column = "authorityId", javaType = UUID.class),
            @Arg(column = "price", javaType = double.class),
            @Arg(column = "eventTime", javaType = LocalDateTime.class)
    })
    @NotNull List<FreeholdHistoryEntity> searchHistory(
            @Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
            @Param("worldId") @NotNull UUID worldId,
            @Param("eventType") @Nullable String eventType,
            @Param("since") @Nullable LocalDateTime since,
            @Param("playerId") @Nullable UUID playerId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Override
    @SelectProvider(type = FreeholdHistorySqlProvider.class, method = "countHistory")
    int countHistory(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                     @Param("worldId") @NotNull UUID worldId,
                     @Param("eventType") @Nullable String eventType,
                     @Param("since") @Nullable LocalDateTime since,
                     @Param("playerId") @Nullable UUID playerId);

}
