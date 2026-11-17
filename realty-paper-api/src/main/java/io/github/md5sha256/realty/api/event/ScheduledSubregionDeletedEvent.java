package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called after the scheduled sweep deleted a subregion its landlord had marked, now that its lease
 * has ended. The WorldGuard region in {@link #getRegion()} has already been removed. A subregion
 * deleted at once by a command fires {@link RegionDeletedEvent} instead.
 */
public class ScheduledSubregionDeletedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Party landlord;

    public ScheduledSubregionDeletedEvent(@NotNull WorldGuardRegion region, @NotNull Party landlord) {
        super(region);
        this.landlord = landlord;
    }

    /** The landlord the subregion's lease had. */
    public @NotNull Party getLandlord() {
        return this.landlord;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
