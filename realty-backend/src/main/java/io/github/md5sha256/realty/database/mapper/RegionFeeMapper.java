package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.database.entity.RegionFeeEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Base mapper interface for the {@code RegionFee} table: the fees players set on a region.
 * A region holds at most one fee per recipient party.
 * SQL annotations are provided by database-specific sub-interfaces.
 *
 * @see RegionFeeEntity
 */
public interface RegionFeeMapper {

    /** The region's fees, oldest first. */
    @NotNull List<RegionFeeEntity> selectByRegion(int realtyRegionId);

    /** Sets the recipient's fee on the region, replacing any fee it already has there. */
    int upsert(int realtyRegionId, int recipientPartyId, @NotNull FeeSide side, double percent,
               @NotNull String role, @NotNull UUID setBy, boolean approved);

    int delete(int realtyRegionId, int recipientPartyId);

    int setApproved(int realtyRegionId, int recipientPartyId, boolean approved);

    /** Removes the fees a player set on the region under one role. */
    int deleteBySetByAndRole(int realtyRegionId, @NotNull UUID setBy, @NotNull String role);

    /** Withdraws approval from the region's seller-side fees, except those set by {@code keep}. */
    int unapproveSellerFeesNotSetBy(int realtyRegionId, @NotNull UUID keep);

    /** Removes every fee on the region set under {@code role}. */
    int deleteByRole(int realtyRegionId, @NotNull String role);

    /** Withdraws approval from the region's seller-side fees, except those set under {@code keepRole}. */
    int unapproveSellerFeesNotOfRole(int realtyRegionId, @NotNull String keepRole);

}
