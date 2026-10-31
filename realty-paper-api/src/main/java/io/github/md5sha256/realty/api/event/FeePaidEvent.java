package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.Party;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Called after a fee of a completed sale has been paid to its recipient. A fee paid on a retry,
 * after an earlier attempt failed, is announced when it is finally paid.
 *
 * <p>This is not a {@link RealtyRegionEvent}: the region may have been deleted by the time the fee
 * is paid. Realty fires it synchronously on the server main thread.</p>
 */
public class FeePaidEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String regionId;
    private final UUID worldId;
    private final String source;
    private final Party recipient;
    private final double amount;
    private final UUID buyerId;

    public FeePaidEvent(@NotNull String regionId,
                        @Nullable UUID worldId,
                        @NotNull String source,
                        @NotNull Party recipient,
                        double amount,
                        @NotNull UUID buyerId) {
        this.regionId = regionId;
        this.worldId = worldId;
        this.source = source;
        this.recipient = recipient;
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
     * What the fee is for: a levy's name or the role of the player who set the fee.
     */
    public @NotNull String getSource() {
        return this.source;
    }

    /**
     * Who was paid the fee.
     */
    public @NotNull Party getRecipient() {
        return this.recipient;
    }

    /**
     * How much was paid.
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
