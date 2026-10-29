package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Base type for a buyer missing their payment deadline. The money they had paid is refunded and
 * the purchase is called off.
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

    protected PaymentExpiredEvent(@NotNull String regionId,
                                  @Nullable UUID worldId,
                                  @Nullable WorldGuardRegion region,
                                  double refundAmount) {
        this.regionId = regionId;
        this.worldId = worldId;
        this.region = region;
        this.refundAmount = refundAmount;
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
     * How much was refunded to the buyer.
     */
    public double getRefundAmount() {
        return this.refundAmount;
    }
}
