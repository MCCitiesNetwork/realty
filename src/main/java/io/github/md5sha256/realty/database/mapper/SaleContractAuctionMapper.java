package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractAuctionEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Base mapper interface for CRUD operations on the {@code SaleContractAuction} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see SaleContractAuctionEntity
 */
public interface SaleContractAuctionMapper {

    void insert(@NotNull SaleContractAuctionEntity entity);

    @Nullable SaleContractAuctionEntity selectById(int id);

    @NotNull List<SaleContractAuctionEntity> selectByCurrentBidderId(@NotNull UUID bidderId);

    @NotNull List<SaleContractAuctionEntity> selectAll();

    void update(@NotNull SaleContractAuctionEntity entity);

    void deleteById(int id);
}
