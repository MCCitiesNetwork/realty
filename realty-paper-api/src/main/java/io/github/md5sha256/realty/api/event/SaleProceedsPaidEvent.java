package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.Party;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Called after the seller of a completed sale has been paid their proceeds: the price less the
 * fees the seller pays. Proceeds paid on a retry, after an earlier attempt failed, are announced
 * when they are finally paid.
 *
 * <p>This is not a {@link RealtyRegionEvent}: the region may have been deleted by the time the
 * proceeds are paid. Realty fires it synchronously on the server main thread.</p>
 */
public class SaleProceedsPaidEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String regionId;
    private final UUID worldId;
    private final Party seller;
    private final double amount;
    private final UUID buyerId;

    public SaleProceedsPaidEvent(@NotNull String regionId,
                                 @Nullable UUID worldId,
                                 @NotNull Party seller,
                                 double amount,
                                 @NotNull UUID buyerId) {
        this.regionId = regionId;
        this.worldId = worldId;
        this.seller = seller;
        this.amount = amount;
        this.buyerId = buyerId;
    }

    /**
     * The WorldGuard id of the region sold.
     */
    public @NotNull String getRegionId() {
        return this.regionId;
    }

    /**
     * The world the region was in, or empty when it is not known.
     */
    public @NotNull Optional<UUID> getWorldId() {
        return Optional.ofNullable(this.worldId);
    }

    /**
     * Who was paid: the titleholder who sold, or the authority when nobody held the title.
     */
    public @NotNull Party getSeller() {
        return this.seller;
    }

    /**
     * How much the seller was paid.
     */
    public double getAmount() {
        return this.amount;
    }

    /**
     * The player who bought the region.
     */
    public @NotNull UUID getBuyerId() {
        return this.buyerId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
