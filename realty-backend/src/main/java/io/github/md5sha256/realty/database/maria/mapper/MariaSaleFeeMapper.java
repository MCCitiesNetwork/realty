package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.database.entity.SaleFeeEntity;
import io.github.md5sha256.realty.database.mapper.SaleFeeMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * MariaDB-specific MyBatis mapper for the {@code SaleFee} table.
 *
 * @see SaleFeeEntity
 */
public interface MariaSaleFeeMapper extends SaleFeeMapper {

    /**
     * The new line's id comes back with {@code RETURNING}, as the party inserts' do. Flushes the
     * session cache so that two inserts in one session never return one id.
     */
    @Override
    @Select("""
            INSERT INTO SaleFee (saleKind, saleRef, realtyRegionId, regionName, worldId, buyerId, lineKind,
                                 source, recipientPartyId, side, percent, amount, status, completedAt,
                                 attempts, lastAttemptAt, lastError)
            VALUES (#{row.saleKind}, #{row.saleRef}, #{row.realtyRegionId}, #{row.regionName}, #{row.worldId},
                    #{row.buyerId}, #{row.lineKind}, #{row.source}, #{row.recipientPartyId}, #{row.side},
                    #{row.percent}, #{row.amount}, #{row.status}, #{row.completedAt}, #{row.attempts},
                    #{row.lastAttemptAt}, #{row.lastError})
            RETURNING saleFeeId
            """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    int insert(@Param("row") @NotNull SaleFeeEntity row);

    @Override
    @Select("""
            SELECT saleFeeId, saleKind, saleRef, realtyRegionId, regionName, worldId, buyerId, lineKind, source,
                   recipientPartyId, side, percent, amount, status, completedAt, attempts, lastAttemptAt, lastError
            FROM SaleFee
            WHERE saleKind = #{saleKind}
            AND saleRef = #{saleRef}
            ORDER BY saleFeeId
            """)
    @Results(id = "saleFeeRow")
    @ConstructorArgs({
            @Arg(column = "saleFeeId", javaType = int.class),
            @Arg(column = "saleKind", javaType = String.class),
            @Arg(column = "saleRef", javaType = Integer.class),
            @Arg(column = "realtyRegionId", javaType = Integer.class),
            @Arg(column = "regionName", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "buyerId", javaType = UUID.class),
            @Arg(column = "lineKind", javaType = String.class),
            @Arg(column = "source", javaType = String.class),
            @Arg(column = "recipientPartyId", javaType = int.class),
            @Arg(column = "side", javaType = FeeSide.class),
            @Arg(column = "percent", javaType = double.class),
            @Arg(column = "amount", javaType = double.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "completedAt", javaType = LocalDateTime.class),
            @Arg(column = "attempts", javaType = int.class),
            @Arg(column = "lastAttemptAt", javaType = LocalDateTime.class),
            @Arg(column = "lastError", javaType = String.class)
    })
    @NotNull List<SaleFeeEntity> selectBySale(@Param("saleKind") @NotNull String saleKind,
                                              @Param("saleRef") int saleRef);

    /**
     * Only lines still locked to an uncompleted sale. A line being paid is left alone, its payout
     * already under way, and a failed line is money a completed sale still owes.
     */
    @Override
    @Delete("""
            DELETE FROM SaleFee
            WHERE saleKind = #{saleKind}
            AND saleRef = #{saleRef}
            AND status = 'locked'
            AND completedAt IS NULL
            """)
    int deleteUnpaidBySale(@Param("saleKind") @NotNull String saleKind,
                           @Param("saleRef") int saleRef);

    @Override
    @Update("""
            UPDATE SaleFee
            SET completedAt = NOW()
            WHERE saleKind = #{saleKind}
            AND saleRef = #{saleRef}
            AND completedAt IS NULL
            """)
    int markCompleted(@Param("saleKind") @NotNull String saleKind,
                      @Param("saleRef") int saleRef);

    @Override
    @Select("""
            SELECT saleFeeId, saleKind, saleRef, realtyRegionId, regionName, worldId, buyerId, lineKind, source,
                   recipientPartyId, side, percent, amount, status, completedAt, attempts, lastAttemptAt, lastError
            FROM SaleFee
            WHERE attempts < #{maxAttempts}
            AND ((status = 'locked' AND completedAt IS NOT NULL)
                 OR (status = 'failed' AND lastAttemptAt < NOW() - INTERVAL #{retryAfterMinutes} MINUTE))
            ORDER BY saleFeeId
            """)
    @ResultMap("saleFeeRow")
    @NotNull List<SaleFeeEntity> selectDue(@Param("retryAfterMinutes") int retryAfterMinutes,
                                           @Param("maxAttempts") int maxAttempts);

    @Override
    @Select("""
            SELECT saleFeeId, saleKind, saleRef, realtyRegionId, regionName, worldId, buyerId, lineKind, source,
                   recipientPartyId, side, percent, amount, status, completedAt, attempts, lastAttemptAt, lastError
            FROM SaleFee
            WHERE status = 'paying'
            ORDER BY saleFeeId
            """)
    @ResultMap("saleFeeRow")
    @NotNull List<SaleFeeEntity> selectPaying();

    @Override
    @Update("""
            UPDATE SaleFee
            SET status = 'paying'
            WHERE saleFeeId = #{saleFeeId}
            AND status IN ('locked', 'failed')
            """)
    int claim(@Param("saleFeeId") int saleFeeId);

    @Override
    @Update("""
            UPDATE SaleFee
            SET status = 'paid'
            WHERE saleFeeId = #{saleFeeId}
            """)
    int markPaid(@Param("saleFeeId") int saleFeeId);

    @Override
    @Update("""
            UPDATE SaleFee
            SET status = 'failed',
                attempts = attempts + 1,
                lastAttemptAt = NOW(),
                lastError = LEFT(#{error}, 1024)
            WHERE saleFeeId = #{saleFeeId}
            """)
    int markFailed(@Param("saleFeeId") int saleFeeId, @Param("error") @NotNull String error);

}
