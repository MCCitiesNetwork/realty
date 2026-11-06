package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Base type for a buyer missing their payment deadline. The purchase is called off, and the money
 * they had paid is refunded from the holding account, unless the payment began before sale fees
 * ({@link #isOldRules()}), when it went to the seller.
 *
 * <p>This is not a {@link RealtyRegionEvent}: the region may have been deleted by the time the
 * deadline passes, so {@link #getRegion()} can be empty. Realty fires it synchronously on the
 * server main thread.</p>
 */
public abstract class PaymentExpiredEvent extends Event {

    private final String regionId;
    private final UUID worldId;
    private final WorldGuardRegion region;
    private final double refundAmount;
    private final boolean refundPaid;
    private final boolean oldRules;

    /**
     * @param refundPaid whether the refund has been paid; {@code false} when it failed and is
     *                   being retried, or when nothing was refunded
     * @param oldRules   whether the payment began before sale fees, so its instalments went to the
     *                   seller and nothing is refunded
     */
    protected PaymentExpiredEvent(@NotNull String regionId,
                                  @Nullable UUID worldId,
                                  @Nullable WorldGuardRegion region,
                                  double refundAmount,
                                  boolean refundPaid,
                                  boolean oldRules) {
        this.regionId = regionId;
        this.worldId = worldId;
        this.region = region;
        this.refundAmount = refundAmount;
        this.refundPaid = refundPaid;
        this.oldRules = oldRules;
    }

    /**
     * @deprecated says nothing of whether the refund was paid; use the constructor that does.
     */
    @Deprecated
    protected PaymentExpiredEvent(@NotNull String regionId,
                                  @Nullable UUID worldId,
                                  @Nullable WorldGuardRegion region,
                                  double refundAmount) {
        this(regionId, worldId, region, refundAmount, false, false);
    }

    /**
     * The WorldGuard id of the region.
     */
    public @NotNull String getRegionId() {
        return this.regionId;
    }

    /**
     * The world the region was in, or empty when the region has been deleted.
     */
    public @NotNull Optional<UUID> getWorldId() {
        return Optional.ofNullable(this.worldId);
    }

    /**
     * The region, or empty when it has been deleted or WorldGuard no longer knows it.
     */
    public @NotNull Optional<WorldGuardRegion> getRegion() {
        return Optional.ofNullable(this.region);
    }

    /**
     * How much is refunded to the buyer: what they had paid, or zero for a payment begun before
     * sale fees, whose instalments went to the seller.
     */
    public double getRefundAmount() {
        return this.refundAmount;
    }

    /**
     * Whether the refund has been paid. {@code false} when it could not be paid now, in which case
     * it is recorded and retried, or when there was nothing to refund.
     */
    public boolean isRefundPaid() {
        return this.refundPaid;
    }

    /**
     * Whether the payment began before sale fees. Its instalments went straight to the seller, so
     * Realty refunds nothing and {@link #getRefundAmount()} is zero.
     */
    public boolean isOldRules() {
        return this.oldRules;
    }
}
