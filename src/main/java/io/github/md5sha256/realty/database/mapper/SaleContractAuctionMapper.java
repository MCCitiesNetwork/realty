package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractAuctionEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Base mapper interface for query operations on the {@code SaleContractAuction} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see SaleContractAuctionEntity
 */
public interface SaleContractAuctionMapper {

    @Nullable SaleContractAuctionEntity selectByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

}
