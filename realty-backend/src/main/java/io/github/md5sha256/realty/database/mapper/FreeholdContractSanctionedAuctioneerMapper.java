package io.github.md5sha256.realty.database.mapper;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Base mapper interface for CRUD operations on the {@code FreeholdContractSanctionedAuctioneers} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see io.github.md5sha256.realty.database.entity.FreeholdContractSanctionedAuctioneerEntity
 */
public interface FreeholdContractSanctionedAuctioneerMapper {

    boolean existsByRegionAndAuctioneer(@NotNull String worldGuardRegionId,
                                        @NotNull UUID worldId,
                                        @NotNull UUID auctioneerId);

    /** Everybody sanctioned to auction the region. */
    @NotNull List<UUID> selectByRegion(@NotNull String worldGuardRegionId,
                                       @NotNull UUID worldId);

    /**
     * Sanctions an auctioneer again after they were withdrawn. Does nothing if they are
     * sanctioned already.
     */
    int restore(@NotNull String worldGuardRegionId,
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
