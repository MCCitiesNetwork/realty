package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after the landlord of a region has been set.
 */
public class LandlordSetEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID newLandlordId;
    private final UUID previousLandlordId;

    public LandlordSetEvent(@NotNull WorldGuardRegion region,
                            @NotNull UUID newLandlordId,
                            @Nullable UUID previousLandlordId) {
        super(region);
        this.newLandlordId = newLandlordId;
        this.previousLandlordId = previousLandlordId;
    }

    /**
     * The new landlord.
     */
    public @NotNull UUID getNewLandlordId() {
        return this.newLandlordId;
    }

    /**
     * The previous landlord, or {@code null} if there was none.
     */
    public @Nullable UUID getPreviousLandlordId() {
        return this.previousLandlordId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
