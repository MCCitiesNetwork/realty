package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after a player has successfully rented a leasehold region. The economy
 * transfer and tenancy change have already been committed.
 */
public class RegionRentedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID tenantId;
    private final Party landlord;
    private final double price;
    private final long durationSeconds;

    public RegionRentedEvent(@NotNull WorldGuardRegion region,
                             @NotNull UUID tenantId,
                             @NotNull Party landlord,
                             double price,
                             long durationSeconds) {
        super(region);
        this.tenantId = tenantId;
        this.landlord = landlord;
        this.price = price;
        this.durationSeconds = durationSeconds;
    }

    /**
     * The new tenant.
     */
    public @NotNull Party getTenant() {
        return Party.personal(this.tenantId);
    }

    /**
     * The new tenant.
     *
     * @deprecated use {@link #getTenant()}. Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @NotNull UUID getTenantId() {
        return this.tenantId;
    }

    /**
     * The landlord who received the payment.
     */
    public @NotNull Party getLandlord() {
        return this.landlord;
    }

    /**
     * The landlord who received the payment.
     *
     * @deprecated use {@link #getLandlord()}; {@code null} when the landlord is not a player.
     * Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @Nullable UUID getLandlordId() {
        return Party.playerUuidOf(this.landlord).orElse(null);
    }

    /**
     * The rent paid.
     */
    public double getPrice() {
        return this.price;
    }

    /**
     * The lease duration in seconds.
     */
    public long getDurationSeconds() {
        return this.durationSeconds;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
