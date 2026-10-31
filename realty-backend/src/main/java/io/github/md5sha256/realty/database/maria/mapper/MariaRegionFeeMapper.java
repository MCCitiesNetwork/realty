package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.database.entity.RegionFeeEntity;
import io.github.md5sha256.realty.database.mapper.RegionFeeMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * MariaDB-specific MyBatis mapper for the {@code RegionFee} table.
 *
 * @see RegionFeeEntity
 */
public interface MariaRegionFeeMapper extends RegionFeeMapper {

    @Override
    @Select("""
            SELECT regionFeeId, realtyRegionId, recipientPartyId, side, percent, role, setBy, approved, createdAt
            FROM RegionFee
            WHERE realtyRegionId = #{realtyRegionId}
            ORDER BY createdAt, regionFeeId
            """)
    @ConstructorArgs({
            @Arg(column = "regionFeeId", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "recipientPartyId", javaType = int.class),
            @Arg(column = "side", javaType = FeeSide.class),
            @Arg(column = "percent", javaType = double.class),
            @Arg(column = "role", javaType = String.class),
            @Arg(column = "setBy", javaType = UUID.class),
            @Arg(column = "approved", javaType = boolean.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class)
    })
    @NotNull List<RegionFeeEntity> selectByRegion(@Param("realtyRegionId") int realtyRegionId);

    @Override
    @Insert("""
            INSERT INTO RegionFee (realtyRegionId, recipientPartyId, side, percent, role, setBy, approved)
            VALUES (#{realtyRegionId}, #{recipientPartyId}, #{side}, #{percent}, #{role}, #{setBy}, #{approved})
            ON DUPLICATE KEY UPDATE
                side = VALUE(side),
                percent = VALUE(percent),
                role = VALUE(role),
                setBy = VALUE(setBy),
                approved = VALUE(approved)
            """)
    int upsert(@Param("realtyRegionId") int realtyRegionId,
               @Param("recipientPartyId") int recipientPartyId,
               @Param("side") @NotNull FeeSide side,
               @Param("percent") double percent,
               @Param("role") @NotNull String role,
               @Param("setBy") @NotNull UUID setBy,
               @Param("approved") boolean approved);

    @Override
    @Delete("""
            DELETE FROM RegionFee
            WHERE realtyRegionId = #{realtyRegionId}
            AND recipientPartyId = #{recipientPartyId}
            """)
    int delete(@Param("realtyRegionId") int realtyRegionId,
               @Param("recipientPartyId") int recipientPartyId);

    @Override
    @Update("""
            UPDATE RegionFee
            SET approved = #{approved}
            WHERE realtyRegionId = #{realtyRegionId}
            AND recipientPartyId = #{recipientPartyId}
            """)
    int setApproved(@Param("realtyRegionId") int realtyRegionId,
                    @Param("recipientPartyId") int recipientPartyId,
                    @Param("approved") boolean approved);

    @Override
    @Delete("""
            DELETE FROM RegionFee
            WHERE realtyRegionId = #{realtyRegionId}
            AND setBy = #{setBy}
            AND role = #{role}
            """)
    int deleteBySetByAndRole(@Param("realtyRegionId") int realtyRegionId,
                             @Param("setBy") @NotNull UUID setBy,
                             @Param("role") @NotNull String role);

    @Override
    @Update("""
            UPDATE RegionFee
            SET approved = FALSE
            WHERE realtyRegionId = #{realtyRegionId}
            AND side = 'SELLER'
            AND setBy <> #{keep}
            """)
    int unapproveSellerFeesNotSetBy(@Param("realtyRegionId") int realtyRegionId,
                                    @Param("keep") @NotNull UUID keep);

    @Override
    @Delete("""
            DELETE FROM RegionFee
            WHERE realtyRegionId = #{realtyRegionId}
            AND role = #{role}
            """)
    int deleteByRole(@Param("realtyRegionId") int realtyRegionId,
                     @Param("role") @NotNull String role);

    @Override
    @Update("""
            UPDATE RegionFee
            SET approved = FALSE
            WHERE realtyRegionId = #{realtyRegionId}
            AND side = 'SELLER'
            AND role <> #{keepRole}
            """)
    int unapproveSellerFeesNotOfRole(@Param("realtyRegionId") int realtyRegionId,
                                     @Param("keepRole") @NotNull String keepRole);

}
