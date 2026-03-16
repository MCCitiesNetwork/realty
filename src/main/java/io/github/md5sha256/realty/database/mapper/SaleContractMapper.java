package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Base mapper interface for CRUD operations on the {@code SaleContract} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see SaleContractEntity
 */
public interface SaleContractMapper {

    /**
     * Inserts a new row into the {@code SaleContract} table.
     *
     * <p>The {@code regionId} parameter identifies the {@code RealtyRegion} this contract belongs
     * to. It is not stored directly on the {@code SaleContract} row (that association lives in the
     * {@code Contract} table), but implementations may use it for subquery-based validation or
     * linking.
     *
     * @param regionId    the {@code realtyRegionId} of the region being sold
     * @param price       the agreed sale price (must be &gt; 0)
     * @param authority   UUID of the authority overseeing the sale
     * @param titleHolder UUID of the current title holder
     * @return number of rows inserted (1 on success)
     */
    int insertSale(int regionId,
                   double price,
                   @NotNull UUID authority,
                   @NotNull UUID titleHolder);

    /**
     * Checks whether the given player is the authority on any sale contract for the
     * specified WorldGuard region, joining through the {@code RealtyRegion} and
     * {@code Contract} tables.
     *
     * @param worldGuardRegionId the WorldGuard region identifier
     * @param worldId            UUID of the world containing the region
     * @param playerId           UUID of the player to check
     * @return {@code true} if the player is an authority on at least one sale contract
     */
    boolean existsByRegionAndAuthority(@NotNull String worldGuardRegionId,
                                       @NotNull UUID worldId,
                                       @NotNull UUID playerId);

    /**
     * Selects the sale contract associated with a WorldGuard region, joining through
     * the {@code RealtyRegion} and {@code Contract} tables.
     *
     * @param worldGuardRegionId the WorldGuard region identifier
     * @param worldId            UUID of the world containing the region
     * @return the sale contract, or {@code null} if none exists
     */
    @Nullable SaleContractEntity selectByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

}
