package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.InboundOfferView;
import io.github.md5sha256.realty.database.entity.OutboundOfferView;
import io.github.md5sha256.realty.database.entity.FreeholdContractOfferEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Base mapper interface for query operations on the {@code FreeholdContractOffer} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see FreeholdContractOfferEntity
 */
public interface FreeholdContractOfferMapper {

    @Nullable List<FreeholdContractOfferEntity> selectByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    int insertOffer(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID offererId, double offerPrice);

    int deleteOffers(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    int deleteOfferByOfferer(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID offererId);

    int deleteOtherOffers(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID excludedOffererId);

    /**
     * Checks whether the region has any offer at all, regardless of offerer.
     *
     * @param worldGuardRegionId the WorldGuard region identifier
     * @param worldId            UUID of the world containing the region
     * @return {@code true} if at least one offer exists on the region
     */
    boolean existsByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    boolean existsByOfferer(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID offererId);

    @Nullable FreeholdContractOfferEntity selectByOfferer(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID offererId);

    @NotNull List<OutboundOfferView> selectAllByOfferer(@NotNull UUID offererId);

    @NotNull List<InboundOfferView> selectAllByTitleHolder(@NotNull UUID titleHolderId);

    int countAll();

}
