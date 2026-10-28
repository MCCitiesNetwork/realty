package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Called after a freehold starts or stops accepting offers. Not called when it is set to the
 * state it already had.
 */
public class OffersToggledEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean acceptingOffers;
    private final UUID actorId;

    public OffersToggledEvent(@NotNull WorldGuardRegion region,
                              boolean acceptingOffers,
                              @Nullable UUID actorId) {
        super(region);
        this.acceptingOffers = acceptingOffers;
        this.actorId = actorId;
    }

    /**
     * Whether the freehold now accepts offers.
     */
    public boolean isAcceptingOffers() {
        return this.acceptingOffers;
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
