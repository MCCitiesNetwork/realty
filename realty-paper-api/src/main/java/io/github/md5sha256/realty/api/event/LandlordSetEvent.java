package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.Party;
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

    private final Party newLandlord;
    private final Party previousLandlord;

    public LandlordSetEvent(@NotNull WorldGuardRegion region,
                            @NotNull Party newLandlord,
                            @NotNull Party previousLandlord) {
        super(region);
        this.newLandlord = newLandlord;
        this.previousLandlord = previousLandlord;
    }

    /**
     * The new landlord.
     */
    public @NotNull Party getNewLandlord() {
        return this.newLandlord;
    }

    /**
     * The previous landlord.
     */
    public @NotNull Party getPreviousLandlord() {
        return this.previousLandlord;
    }

    /**
     * The new landlord.
     *
     * @deprecated use {@link #getNewLandlord()}; {@code null} when the landlord is not a player.
     * Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @Nullable UUID getNewLandlordId() {
        return Party.playerUuidOf(this.newLandlord).orElse(null);
    }

    /**
     * The previous landlord.
     *
     * @deprecated use {@link #getPreviousLandlord()}; {@code null} when the landlord is not a player.
     * Removed in 3.0.0.
     */
    @Deprecated(forRemoval = true)
    public @Nullable UUID getPreviousLandlordId() {
        return Party.playerUuidOf(this.previousLandlord).orElse(null);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
