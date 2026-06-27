package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Base type for Realty events that concern a single region. Realty fires these
 * synchronously on the server main thread, so listeners may freely use the
 * Bukkit API.
 */
public abstract class RealtyRegionEvent extends Event {

    private final WorldGuardRegion region;

    protected RealtyRegionEvent(@NotNull WorldGuardRegion region) {
        this(region, false);
    }

    protected RealtyRegionEvent(@NotNull WorldGuardRegion region, boolean async) {
        super(async);
        this.region = region;
    }

    /**
     * The region this event concerns.
     */
    public @NotNull WorldGuardRegion getRegion() {
        return this.region;
    }

    /**
     * Convenience accessor for the WorldGuard region id.
     */
    public @NotNull String getRegionId() {
        return this.region.region().getId();
    }

    /**
     * Convenience accessor for the world the region belongs to.
     */
    public @NotNull UUID getWorldId() {
        return this.region.world().getUID();
    }
}
