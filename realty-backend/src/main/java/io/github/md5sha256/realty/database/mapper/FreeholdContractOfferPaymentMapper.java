package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.FreeholdContractOfferPaymentEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Base mapper interface for query operations on the {@code FreeholdContractOfferPayment} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see FreeholdContractOfferPaymentEntity
 */
public interface FreeholdContractOfferPaymentMapper {

    @Nullable FreeholdContractOfferPaymentEntity selectByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    @NotNull List<FreeholdContractOfferPaymentEntity> selectAllExpired();

    /**
     * @param totalDue   what the offerer owes in all: the price plus any buyer-paid fees
     * @param acceptedBy the player who accepted the offer
     */
    int insertPayment(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID offererId, double offerPrice,
                      @NotNull LocalDateTime paymentDeadline, double totalDue, @Nullable UUID acceptedBy);

    int updatePayment(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID offererId, double payment);

    int atomicUpdatePayment(@NotNull String worldGuardRegionId, @NotNull UUID worldId,
                            @NotNull UUID offererId, double expectedCurrent, double newPayment);

    int deleteByOfferId(int offerId);

    int deleteByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    boolean existsByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

}
