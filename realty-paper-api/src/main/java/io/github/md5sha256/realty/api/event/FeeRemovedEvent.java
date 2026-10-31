package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Called after a fee has been removed from a freehold by the one it was paid to.
 */
public class FeeRemovedEvent extends RealtyRegionEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Party recipient;

    public FeeRemovedEvent(@NotNull WorldGuardRegion region, @Nullable Party recipient) {
        super(region);
        this.recipient = recipient;
    }

    /**
     * Who the removed fee was paid to, or empty when that cannot be named, such as when the
     * console removed its own fee.
     */
    public @NotNull Optional<Party> getRecipient() {
        return Optional.ofNullable(this.recipient);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
