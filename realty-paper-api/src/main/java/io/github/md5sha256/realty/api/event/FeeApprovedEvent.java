package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import java.util.UUID;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called after the seller has approved a seller-paid fee, so it applies to sales.
 */
public class FeeApprovedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Party recipient;
    private final FeeSide side;
    private final double percent;
    private final UUID approverId;

    public FeeApprovedEvent(@NotNull WorldGuardRegion region,
                            @NotNull Party recipient,
                            @NotNull FeeSide side,
                            double percent,
                            @NotNull UUID approverId) {
        super(region);
        this.recipient = recipient;
        this.side = side;
        this.percent = percent;
        this.approverId = approverId;
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
     * The seller who approved the fee.
     */
    public @NotNull UUID getApproverId() {
        return this.approverId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
