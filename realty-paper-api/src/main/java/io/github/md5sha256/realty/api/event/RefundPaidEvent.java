package io.github.md5sha256.realty.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Called after the instalments a buyer had paid towards a sale that fell through have been paid
 * back to them. A refund paid on a retry, after an earlier attempt failed, is announced when it is
 * finally paid.
 *
 * <p>This is not a {@link RealtyRegionEvent}: the region may have been deleted, which is one reason
 * for a refund. Realty fires it synchronously on the server main thread.</p>
 */
public class RefundPaidEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID buyerId;
    private final double amount;
    private final String regionId;
    private final UUID worldId;
    private final String reason;
    private final boolean retry;

    /**
     * @param retry whether the refund was paid on a retry, after an earlier attempt failed, rather
     *              than on the first attempt
     */
    public RefundPaidEvent(@NotNull UUID buyerId,
                           double amount,
                           @NotNull String regionId,
                           @Nullable UUID worldId,
                           @NotNull String reason,
                           boolean retry) {
        this.buyerId = buyerId;
        this.amount = amount;
        this.regionId = regionId;
        this.worldId = worldId;
        this.reason = reason;
        this.retry = retry;
    }

    /**
     * Whether the refund was paid on a retry, after an earlier attempt failed. A refund paid on the
     * first attempt, as when a payment deadline passes, is {@code false}: the buyer was already
     * told of it with the expiry.
     */
    public boolean isRetry() {
        return this.retry;
    }

    /**
     * The player refunded.
     */
    public @NotNull UUID getBuyerId() {
        return this.buyerId;
    }

    /**
     * How much was refunded.
     */
    public double getAmount() {
        return this.amount;
    }

    /**
     * The WorldGuard id of the region the instalments were paid towards.
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
     * Why the sale fell through, such as {@code bid-expired}, {@code offer-expired},
     * {@code auction-cancelled} or {@code region-deleted}.
     */
    public @NotNull String getReason() {
        return this.reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
