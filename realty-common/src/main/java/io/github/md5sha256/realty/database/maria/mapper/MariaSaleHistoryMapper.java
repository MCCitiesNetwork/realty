package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.mapper.SaleHistoryMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface MariaSaleHistoryMapper extends SaleHistoryMapper {

    @Override
    @Insert("""
            INSERT INTO SaleHistory (worldGuardRegionId, worldId, eventType, buyerId, authorityId, price)
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
            FROM SaleHistory
            WHERE worldGuardRegionId = #{worldGuardRegionId}
            AND worldId = #{worldId}
            ORDER BY eventTime DESC
            LIMIT 1
            """)
    @Nullable Double selectLastSalePrice(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                          @Param("worldId") @NotNull UUID worldId);

}
