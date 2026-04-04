package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Base mapper interface for CRUD operations on the {@code FreeholdContract} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see FreeholdContractEntity
 */
public interface FreeholdContractMapper {

    /**
     * Inserts a new row into the {@code FreeholdContract} table.
     *
     * <p>The {@code regionId} parameter identifies the {@code RealtyRegion} this contract belongs
     * to. It is not stored directly on the {@code FreeholdContract} row (that association lives in the
     * {@code Contract} table), but implementations may use it for subquery-based validation or
     * linking.
     *
     * @param regionId    the {@code realtyRegionId} of the region being sold
     * @param price       the freehold price (must be &gt; 0), or {@code null} if not for freehold
     * @param authority   UUID of the authority overseeing the freehold
     * @param titleHolder UUID of the current title holder
     * @return number of rows inserted (1 on success)
     */
    int insertFreehold(int regionId,
                   @Nullable Double price,
                   @NotNull UUID authority,
                   @Nullable UUID titleHolder);

    /**
     * Checks whether the given player is the authority on any freehold contract for the
     * specified WorldGuard region, joining through the {@code RealtyRegion} and
     * {@code Contract} tables.
     *
     * @param worldGuardRegionId the WorldGuard region identifier
     * @param worldId            UUID of the world containing the region
     * @param playerId           UUID of the player to check
     * @return {@code true} if the player is an authority on at least one freehold contract
     */
    boolean existsByRegionAndAuthority(@NotNull String worldGuardRegionId,
                                       @NotNull UUID worldId,
                                       @NotNull UUID playerId);

    /**
     * Checks whether the given player is the title holder on any freehold contract for the
     * specified WorldGuard region, joining through the {@code RealtyRegion} and
     * {@code Contract} tables.
     *
     * @param worldGuardRegionId the WorldGuard region identifier
     * @param worldId            UUID of the world containing the region
     * @param playerId           UUID of the player to check
     * @return {@code true} if the player is the title holder on at least one freehold contract
     */
    boolean existsByRegionAndTitleHolder(@NotNull String worldGuardRegionId,
                                         @NotNull UUID worldId,
                                         @NotNull UUID playerId);

    /**
     * Selects the freehold contract associated with a WorldGuard region, joining through
     * the {@code RealtyRegion} and {@code Contract} tables.
     *
     * @param worldGuardRegionId the WorldGuard region identifier
     * @param worldId            UUID of the world containing the region
     * @return the freehold contract, or {@code null} if none exists
     */
    @Nullable FreeholdContractEntity selectByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    /**
     * Updates the price and title holder on the freehold contract associated with a
     * WorldGuard region, joining through the {@code RealtyRegion} and {@code Contract} tables.
     *
     * @param worldGuardRegionId the WorldGuard region identifier
     * @param worldId            UUID of the world containing the region
     * @param price              the new freehold price (must be &gt; 0)
     * @param titleHolder        UUID of the new title holder
     * @return number of rows updated (1 on success, 0 if no matching contract)
     */
    int updateFreeholdByRegion(@NotNull String worldGuardRegionId,
                           @NotNull UUID worldId,
                           double price,
                           @Nullable UUID titleHolder);

    /**
     * Updates only the price on the freehold contract associated with a WorldGuard region.
     *
     * @param worldGuardRegionId the WorldGuard region identifier
     * @param worldId            UUID of the world containing the region
     * @param price              the new freehold price (must be &gt; 0), or {@code null} to unset
     * @return number of rows updated (1 on success, 0 if no matching contract)
     */
    int updatePriceByRegion(@NotNull String worldGuardRegionId,
                            @NotNull UUID worldId,
                            @Nullable Double price);

    /**
     * Updates only the title holder on the freehold contract associated with a WorldGuard region.
     *
     * @param worldGuardRegionId the WorldGuard region identifier
     * @param worldId            UUID of the world containing the region
     * @param titleHolder        UUID of the new title holder, or {@code null} to clear
     * @return number of rows updated (1 on success, 0 if no matching contract)
     */
    int updateTitleHolderByRegion(@NotNull String worldGuardRegionId,
                                  @NotNull UUID worldId,
                                  @Nullable UUID titleHolder);

    /**
     * Updates only the accepting-offers flag on the freehold contract associated with a WorldGuard region.
     *
     * @param worldGuardRegionId the WorldGuard region identifier
     * @param worldId            UUID of the world containing the region
     * @param acceptingOffers    whether the region should accept offers
     * @return number of rows updated (1 on success, 0 if no matching contract)
     */
    int updateAcceptingOffersByRegion(@NotNull String worldGuardRegionId,
                                      @NotNull UUID worldId,
                                      boolean acceptingOffers);

    int countAll();

    int countOccupied();

    double averagePrice();

}
