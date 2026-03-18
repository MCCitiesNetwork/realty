package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.SaleContractBidPaymentEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base mapper interface for query operations on the {@code SaleContractBidPayment} table.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see SaleContractBidPaymentEntity
 */
public interface SaleContractBidPaymentMapper {

    @Nullable SaleContractBidPaymentEntity selectByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    int insertPayment(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID bidderId, double bidPrice, @NotNull LocalDateTime paymentDeadline);

    int updatePayment(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull UUID bidderId, double payment);

    int deleteByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

    boolean existsByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

}
