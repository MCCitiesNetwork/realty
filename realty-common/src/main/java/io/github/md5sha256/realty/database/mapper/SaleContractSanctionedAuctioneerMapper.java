package io.github.md5sha256.realty.database.mapper;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Base mapper interface for CRUD operations on the {@code SaleContractSanctionedAuctioneers} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see io.github.md5sha256.realty.database.entity.SaleContractSanctionedAuctioneerEntity
 */
public interface SaleContractSanctionedAuctioneerMapper {

    boolean existsByRegionAndAuctioneer(@NotNull String worldGuardRegionId,
                                        @NotNull UUID worldId,
                                        @NotNull UUID auctioneerId);

    int insert(@NotNull String worldGuardRegionId,
               @NotNull UUID worldId,
               @NotNull UUID auctioneerId);

    int deleteByRegionAndAuctioneer(@NotNull String worldGuardRegionId,
                                     @NotNull UUID worldId,
                                     @NotNull UUID auctioneerId);

    int deleteAllByRegion(@NotNull String worldGuardRegionId,
                          @NotNull UUID worldId);
}
