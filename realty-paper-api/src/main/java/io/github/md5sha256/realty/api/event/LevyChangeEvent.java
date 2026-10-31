package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import java.util.Optional;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called before a levy is chosen or cleared, for one freehold or for every freehold of an
 * authority. Exactly one of {@link #getRegion()} and {@link #getAuthority()} is present.
 *
 * <p>Cancelling prevents the change. {@link #getPreviousLevy()} is empty here: it is only known
 * once the change is made, and {@link LevyChangedEvent} reports it.</p>
 *
 * <p>This is not a {@link RealtyRegionEvent}, because a choice for an authority has no region.
 * Realty fires it synchronously on the server main thread.</p>
 */
public class LevyChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final WorldGuardRegion region;
    private final Party authority;
    private final String newLevy;
    private final String previousLevy;
    private boolean cancelled;

    public LevyChangeEvent(@Nullable WorldGuardRegion region,
                           @Nullable Party authority,
                           @Nullable String newLevy,
                           @Nullable String previousLevy) {
        this.region = region;
        this.authority = authority;
        this.newLevy = newLevy;
        this.previousLevy = previousLevy;
    }

    /**
     * The freehold the choice is for, or empty when it is for every freehold of an authority.
     */
    public @NotNull Optional<WorldGuardRegion> getRegion() {
        return Optional.ofNullable(this.region);
    }

    /**
     * The authority the choice is for, or empty when it is for one freehold.
     */
    public @NotNull Optional<Party> getAuthority() {
        return Optional.ofNullable(this.authority);
    }

    /**
     * The name of the levy now chosen, or empty when the choice is cleared.
     */
    public @NotNull Optional<String> getNewLevy() {
        return Optional.ofNullable(this.newLevy);
    }

    /**
     * The name of the levy chosen before, or empty when there was none or it is not known yet.
     */
    public @NotNull Optional<String> getPreviousLevy() {
        return Optional.ofNullable(this.previousLevy);
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
