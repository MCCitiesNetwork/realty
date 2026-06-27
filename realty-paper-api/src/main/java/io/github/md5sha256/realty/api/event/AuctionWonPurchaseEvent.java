package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after the winner of an auction has paid and ownership of the region has
 * transferred to them.
 */
public class AuctionWonPurchaseEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID winnerId;
    private final UUID previousTitleHolderId;
    private final double amount;

    public AuctionWonPurchaseEvent(@NotNull WorldGuardRegion region,
                                   @NotNull UUID winnerId,
                                   @Nullable UUID previousTitleHolderId,
                                   double amount) {
        super(region);
        this.winnerId = winnerId;
        this.previousTitleHolderId = previousTitleHolderId;
        this.amount = amount;
    }

    /**
     * The player who won the auction and is now the new title holder.
     */
    public @NotNull UUID getWinnerId() {
        return this.winnerId;
    }

    /**
     * The previous title holder who received the payment, or {@code null} if the
     * region was previously held only by an authority.
     */
    public @Nullable UUID getPreviousTitleHolderId() {
        return this.previousTitleHolderId;
    }

    /**
     * The amount paid.
     */
    public double getAmount() {
        return this.amount;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
