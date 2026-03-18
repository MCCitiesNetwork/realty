package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractOfferPaymentEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Base mapper interface for query operations on the {@code SaleContractOfferPayment} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see SaleContractOfferPaymentEntity
 */
public interface SaleContractOfferPaymentMapper {

    @Nullable SaleContractOfferPaymentEntity selectByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    @NotNull List<SaleContractOfferPaymentEntity> selectAllExpired();

    int insertPayment(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID offererId, double offerPrice, @NotNull LocalDateTime paymentDeadline);

    int updatePayment(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID offererId, double payment);

    int deleteByOfferId(int offerId);

    int deleteByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    boolean existsByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

}
