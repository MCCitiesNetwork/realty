package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractAuctionEntity;
import io.github.md5sha256.realty.database.mapper.SaleContractAuctionMapper;
import org.apache.ibatis.annotations.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * MyBatis mapper for CRUD operations on the {@code SaleContractAuction} table.
 *
 * @see SaleContractAuctionEntity
 */
public interface MariaSaleContractAuctionMapper extends SaleContractAuctionMapper {

    @Override
    @Insert("INSERT INTO SaleContractAuction (startDate, biddingDurationSeconds, " +
            "paymentDurationSeconds, minBid, minStep, currentBidderId, currentBidPrice) " +
            "VALUES (#{startDate}, #{biddingDurationSeconds}, #{paymentDurationSeconds}, " +
            "#{minBid}, #{minStep}, #{currentBidderId}, #{currentBidPrice})")
    @Options(useGeneratedKeys = true, keyProperty = "saleContractAuctionId")
    void insert(@NotNull SaleContractAuctionEntity entity);

    @Override
    @Select("SELECT saleContractAuctionId, startDate, biddingDurationSeconds, " +
            "paymentDurationSeconds, minBid, minStep, currentBidderId, currentBidPrice " +
            "FROM SaleContractAuction WHERE saleContractAuctionId = #{id}")
    @ConstructorArgs({
            @Arg(column = "saleContractAuctionId", javaType = int.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "biddingDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDurationSeconds", javaType = long.class),
            @Arg(column = "minBid", javaType = double.class),
            @Arg(column = "minStep", javaType = double.class),
            @Arg(column = "currentBidderId", javaType = UUID.class),
            @Arg(column = "currentBidPrice", javaType = Double.class)
    })
    @Nullable SaleContractAuctionEntity selectById(@Param("id") int id);

    @Override
    @Select("SELECT saleContractAuctionId, startDate, biddingDurationSeconds, " +
            "paymentDurationSeconds, minBid, minStep, currentBidderId, currentBidPrice " +
            "FROM SaleContractAuction WHERE currentBidderId = #{bidderId}")
    @ConstructorArgs({
            @Arg(column = "saleContractAuctionId", javaType = int.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "biddingDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDurationSeconds", javaType = long.class),
            @Arg(column = "minBid", javaType = double.class),
            @Arg(column = "minStep", javaType = double.class),
            @Arg(column = "currentBidderId", javaType = UUID.class),
            @Arg(column = "currentBidPrice", javaType = Double.class)
    })
    @NotNull List<SaleContractAuctionEntity> selectByCurrentBidderId(@Param("bidderId") @NotNull UUID bidderId);

    @Override
    @Select("SELECT saleContractAuctionId, startDate, biddingDurationSeconds, " +
            "paymentDurationSeconds, minBid, minStep, currentBidderId, currentBidPrice " +
            "FROM SaleContractAuction")
    @ConstructorArgs({
            @Arg(column = "saleContractAuctionId", javaType = int.class),
            @Arg(column = "startDate", javaType = LocalDateTime.class),
            @Arg(column = "biddingDurationSeconds", javaType = long.class),
            @Arg(column = "paymentDurationSeconds", javaType = long.class),
            @Arg(column = "minBid", javaType = double.class),
            @Arg(column = "minStep", javaType = double.class),
            @Arg(column = "currentBidderId", javaType = UUID.class),
            @Arg(column = "currentBidPrice", javaType = Double.class)
    })
    @NotNull List<SaleContractAuctionEntity> selectAll();

    @Override
    @Update("UPDATE SaleContractAuction SET startDate = #{startDate}, " +
            "biddingDurationSeconds = #{biddingDurationSeconds}, " +
            "paymentDurationSeconds = #{paymentDurationSeconds}, " +
            "minBid = #{minBid}, minStep = #{minStep}, " +
            "currentBidderId = #{currentBidderId}, currentBidPrice = #{currentBidPrice} " +
            "WHERE saleContractAuctionId = #{saleContractAuctionId}")
    void update(@NotNull SaleContractAuctionEntity entity);

    @Override
    @Delete("DELETE FROM SaleContractAuction WHERE saleContractAuctionId = #{id}")
    void deleteById(@Param("id") int id);
}
