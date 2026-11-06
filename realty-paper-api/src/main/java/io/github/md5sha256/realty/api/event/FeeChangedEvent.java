package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called after a fee has been set on a freehold, or an existing fee changed. Not called when the
 * fee was already as requested. A seller-paid fee set by someone other than the seller still
 * waits for the seller's approval; {@link FeeApprovedEvent} is called when it is given.
 */
public class FeeChangedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Party recipient;
    private final FeeSide side;
    private final double percent;
    private final Double previousPercent;
    private final UUID actorId;

    public FeeChangedEvent(@NotNull WorldGuardRegion region,
                          @NotNull Party recipient,
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
     * Who the fee is paid to: the actor, or the authority when it is set for the authority.
     */
    public @NotNull Party getRecipient() {
        return this.recipient;
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
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
