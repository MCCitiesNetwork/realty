package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.LeaseholdModificationEntity;
import io.github.md5sha256.realty.database.entity.LeaseholdModificationView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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

    /**
     * Makes an applied change of terms pending again, as it was before it was applied.
     * Only if it is still marked applied.
     */
    int reactivate(int modificationId);

    /**
     * Carries one change of terms forward into another from the same side: each term the
     * later one leaves unsaid is taken from the earlier. What proposing the later one
     * would have done, had the earlier still been pending when it was proposed.
     *
     * @return how many rows were changed: none if the two are from different sides
     */
    int carryForward(int fromModificationId, int intoModificationId);

    /** Sets the status of a modification, stamping {@code resolvedAt = NOW()} for terminal statuses. */
    int updateStatus(int modificationId, @NotNull String status);

    /** Tenant proposals awaiting a decision from the given landlord ({@code AWAITING_LANDLORD}). */
    @NotNull List<LeaseholdModificationView> selectAwaitingByLandlord(@NotNull UUID landlordId);

    /** Non-terminal proposals made by the given player (their own pending requests/changes). */
    @NotNull List<LeaseholdModificationView> selectPendingByProposer(@NotNull UUID proposerId);
}
