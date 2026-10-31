package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.PendingRefundEntity;
import io.github.md5sha256.realty.database.mapper.PendingRefundMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * MariaDB-specific MyBatis mapper for the {@code PendingRefund} table.
 *
 * @see PendingRefundEntity
 */
public interface MariaPendingRefundMapper extends PendingRefundMapper {

    /**
     * The new refund's id comes back with {@code RETURNING}, as the party inserts' do. Flushes the
     * session cache so that two inserts in one session never return one id.
     */
    @Override
    @Select("""
            INSERT INTO PendingRefund (buyerId, amount, regionName, worldId, reason)
            VALUES (#{buyerId}, #{amount}, #{regionName}, #{worldId}, #{reason})
            RETURNING pendingRefundId
            """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    int insert(@Param("buyerId") @NotNull UUID buyerId,
               @Param("amount") double amount,
               @Param("regionName") @NotNull String regionName,
               @Param("worldId") @Nullable UUID worldId,
               @Param("reason") @NotNull String reason);

    @Override
    @Select("""
            SELECT pendingRefundId, buyerId, amount, regionName, worldId, reason, status, attempts,
                   lastAttemptAt, lastError
            FROM PendingRefund
            WHERE status = 'pending'
            AND attempts < #{maxAttempts}
            AND (lastAttemptAt IS NULL OR lastAttemptAt < #{retryBefore})
            ORDER BY pendingRefundId
            """)
    @ConstructorArgs({
            @Arg(column = "pendingRefundId", javaType = int.class),
            @Arg(column = "buyerId", javaType = UUID.class),
            @Arg(column = "amount", javaType = double.class),
            @Arg(column = "regionName", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "reason", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "attempts", javaType = int.class),
            @Arg(column = "lastAttemptAt", javaType = LocalDateTime.class),
            @Arg(column = "lastError", javaType = String.class)
    })
    @NotNull List<PendingRefundEntity> selectDue(@Param("retryBefore") @NotNull LocalDateTime retryBefore,
                                                 @Param("maxAttempts") int maxAttempts);

    @Override
    @Update("""
            UPDATE PendingRefund
            SET status = 'paying'
            WHERE pendingRefundId = #{pendingRefundId}
            AND status = 'pending'
            """)
    int claim(@Param("pendingRefundId") int pendingRefundId);

    @Override
    @Delete("""
            DELETE FROM PendingRefund
            WHERE pendingRefundId = #{pendingRefundId}
            """)
    int delete(@Param("pendingRefundId") int pendingRefundId);

    @Override
    @Update("""
            UPDATE PendingRefund
            SET status = 'pending',
                attempts = attempts + 1,
                lastAttemptAt = NOW(),
                lastError = LEFT(#{error}, 1024)
            WHERE pendingRefundId = #{pendingRefundId}
            """)
    int markFailed(@Param("pendingRefundId") int pendingRefundId, @Param("error") @NotNull String error);

}
