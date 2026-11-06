package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import java.util.UUID;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Called after the seller has refused a seller-paid fee. The fee is deleted.
 */
public class FeeRejectedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Party recipient;
    private final FeeSide side;
    private final double percent;
    private final UUID approverId;

    public FeeRejectedEvent(@NotNull WorldGuardRegion region,
                            @NotNull Party recipient,
                            @NotNull FeeSide side,
                            double percent,
                            @Nullable UUID approverId) {
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
     * The seller who refused the fee, or empty when the console or a plugin did.
     */
    public @NotNull Optional<UUID> getApproverId() {
        return Optional.ofNullable(this.approverId);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
