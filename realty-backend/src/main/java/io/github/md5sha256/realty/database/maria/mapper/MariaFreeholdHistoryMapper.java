package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.database.entity.FreeholdHistoryEntity;
import io.github.md5sha256.realty.database.mapper.FreeholdHistoryMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
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
            INSERT INTO FreeholdHistory (worldGuardRegionId, worldId, eventType, buyerId, authorityPartyId, price)
            VALUES (#{worldGuardRegionId}, #{worldId}, #{eventType}, #{buyerId}, #{authorityPartyId}, #{price})
            """)
    int insert(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
               @Param("worldId") @NotNull UUID worldId,
               @Param("eventType") @NotNull String eventType,
               @Param("buyerId") @NotNull UUID buyerId,
               @Param("authorityPartyId") int authorityPartyId,
               @Param("price") double price);

    @Override
    // A select, because it answers with a row, and one that is declared to write. Without
    // that the session does not count it as a change, and a transaction in which it was
    // the only statement would commit nothing and could not be rolled back. Never from
    // the session's cache: asked twice with the same values, it has to write twice.
    @Select(value = """
            INSERT INTO FreeholdHistory (worldGuardRegionId, worldId, eventType, buyerId, authorityPartyId, price)
            VALUES (#{worldGuardRegionId}, #{worldId}, #{eventType}, #{buyerId}, #{authorityPartyId}, #{price})
            RETURNING historyId
            """, affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    int insertReturningId(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                          @Param("worldId") @NotNull UUID worldId,
                          @Param("eventType") @NotNull String eventType,
                          @Param("buyerId") @NotNull UUID buyerId,
                          @Param("authorityPartyId") int authorityPartyId,
                          @Param("price") double price);

    @Override
    @Delete("""
            DELETE FROM FreeholdHistory
            WHERE historyId = #{historyId}
            """)
    int deleteById(@Param("historyId") int historyId);

    @Override
    // Sales only. Every freehold event carries a price -- SET_PRICE records the new asking
    // price, SET_TITLEHOLDER whatever was on the contract -- so the latest row of any kind
    // reported an asking price as the "last sold" figure the moment a holder listed a plot.
    @Select("""
            SELECT price
            FROM FreeholdHistory
            WHERE worldGuardRegionId = #{worldGuardRegionId}
            AND worldId = #{worldId}
            AND eventType IN ('BUY', 'AUCTION_BUY', 'OFFER_BUY')
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
            @Arg(resultMap = PartySql.RESULT_MAP, columnPrefix = "authority_", javaType = Party.class),
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
