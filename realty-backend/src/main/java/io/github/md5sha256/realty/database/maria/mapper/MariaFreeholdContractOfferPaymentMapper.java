package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.FreeholdContractOfferPaymentEntity;
import io.github.md5sha256.realty.database.mapper.FreeholdContractOfferPaymentMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * MariaDB-specific MyBatis mapper for query operations on the {@code FreeholdContractOfferPayment} table.
 *
 * @see FreeholdContractOfferPaymentEntity
 */
public interface MariaFreeholdContractOfferPaymentMapper extends FreeholdContractOfferPaymentMapper {

    @Override
    @Select("""
            SELECT scop.offerId, scop.realtyRegionId, scop.offererId, scop.offerPrice,
                   scop.paymentDeadline, scop.currentPayment
            FROM FreeholdContractOfferPayment scop
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = scop.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    @ConstructorArgs({
            @Arg(column = "offerId", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "offererId", javaType = UUID.class),
            @Arg(column = "offerPrice", javaType = double.class),
            @Arg(column = "paymentDeadline", javaType = LocalDateTime.class),
            @Arg(column = "currentPayment", javaType = double.class)
    })
    @Nullable FreeholdContractOfferPaymentEntity selectByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                                                             @Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT scop.offerId, scop.realtyRegionId, scop.offererId, scop.offerPrice,
                   scop.paymentDeadline, scop.currentPayment
            FROM FreeholdContractOfferPayment scop
            WHERE scop.paymentDeadline < NOW()
            """)
    @ConstructorArgs({
            @Arg(column = "offerId", javaType = int.class),
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "offererId", javaType = UUID.class),
            @Arg(column = "offerPrice", javaType = double.class),
            @Arg(column = "paymentDeadline", javaType = LocalDateTime.class),
            @Arg(column = "currentPayment", javaType = double.class)
    })
    @NotNull List<FreeholdContractOfferPaymentEntity> selectAllExpired();

    @Override
    @Insert("""
            INSERT INTO FreeholdContractOfferPayment (offerId, realtyRegionId, offererId, offerPrice, paymentDeadline, currentPayment)
            SELECT sco.offerId, rr.realtyRegionId, sco.offererId, sco.offerPrice, #{paymentDeadline}, 0
            FROM FreeholdContractOffer sco
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = sco.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND sco.offererId = #{offererId}
            """)
    int insertPayment(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                      @Param("worldId") @NotNull UUID worldId,
                      @Param("offererId") @NotNull UUID offererId,
                      @Param("offerPrice") double offerPrice,
                      @Param("paymentDeadline") @NotNull LocalDateTime paymentDeadline);

    @Override
    @Update("""
            UPDATE FreeholdContractOfferPayment scop
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = scop.realtyRegionId
            SET scop.currentPayment = #{payment}
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            AND scop.offererId = #{offererId}
            """)
    int updatePayment(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                      @Param("worldId") @NotNull UUID worldId,
                      @Param("offererId") @NotNull UUID offererId,
                      @Param("payment") double payment);

    @Override
    @Delete("""
            DELETE FROM FreeholdContractOfferPayment
            WHERE offerId = #{offerId}
            """)
    int deleteByOfferId(@Param("offerId") int offerId);

    @Override
    @Delete("""
            DELETE scop FROM FreeholdContractOfferPayment scop
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = scop.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    int deleteByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                       @Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT COUNT(*) > 0
            FROM FreeholdContractOfferPayment scop
            INNER JOIN RealtyRegion rr ON rr.realtyRegionId = scop.realtyRegionId
            WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
            AND rr.worldId = #{worldId}
            """)
    boolean existsByRegion(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
                           @Param("worldId") @NotNull UUID worldId);

}
