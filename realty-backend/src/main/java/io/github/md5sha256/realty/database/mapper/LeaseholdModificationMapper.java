package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.LeaseholdModificationEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Persistence for {@link LeaseholdModificationEntity} — pending proposals to change a leasehold's terms.
 */
public interface LeaseholdModificationMapper {

    int insert(int leaseholdContractId,
               @NotNull String proposerRole,
               @NotNull UUID proposerId,
               @Nullable Double newPrice,
               @Nullable Long newDurationSeconds,
               @Nullable Integer newMaxExtensions,
               @NotNull String status);

    /** The single non-terminal ({@code AWAITING_LANDLORD} or {@code ACTIVE}) modification for a contract. */
    @Nullable LeaseholdModificationEntity selectActiveByContract(int leaseholdContractId);

    /** The single non-terminal modification for the region's leasehold contract, or {@code null}. */
    @Nullable LeaseholdModificationEntity selectActiveByRegion(@NotNull String worldGuardRegionId,
                                                               @NotNull UUID worldId);

    /** Sets the status of a modification, stamping {@code resolvedAt = NOW()} for terminal statuses. */
    int updateStatus(int modificationId, @NotNull String status);
}
