package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called before a fee is set on a freehold, or an existing fee is changed.
 *
 * <p>Cancelling prevents the change. {@link #getPreviousPercent()} is empty here: the fee being
 * replaced is only known once the change is made, and {@link FeeChangedEvent} reports it.</p>
 */
public class FeeChangeEvent extends RealtyRegionEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Party recipient;
    private final FeeSide side;
    private final double percent;
    private final Double previousPercent;
    private final UUID actorId;
    private boolean cancelled;

    public FeeChangeEvent(@NotNull WorldGuardRegion region,
                          @Nullable Party recipient,
                          @NotNull FeeSide side,
                          double percent,
                          @Nullable Double previousPercent,
                          @Nullable UUID actorId) {
        super(region);
        this.recipient = recipient;
        this.side = side;
        this.percent = percent;
        this.previousPercent = previousPercent;
        this.actorId = actorId;
    }

    /**
     * Who the fee is paid to, or empty when that cannot be named before the change is made, such
     * as when the console sets a fee for itself.
     */
    public @NotNull Optional<Party> getRecipient() {
        return Optional.ofNullable(this.recipient);
    }

    /**
     * Who pays the fee.
     */
    public @NotNull FeeSide getSide() {
        return this.side;
    }

    /**
     * The fee as a percentage of the price.
     */
    public double getPercent() {
        return this.percent;
    }

    /**
     * The recipient's fee this one replaces, as a percentage, or empty when it had none or it is
     * not known yet.
     */
    public @NotNull Optional<Double> getPreviousPercent() {
        return Optional.ofNullable(this.previousPercent);
    }

    /**
     * The player who made the change, or empty when the console or a plugin did.
     */
    public @NotNull Optional<UUID> getActorId() {
        return Optional.ofNullable(this.actorId);
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
