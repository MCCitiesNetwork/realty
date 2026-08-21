package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Base class for every notification Realty emits. Delivery is not Realty's concern: the plugin
 * fires these and adapter modules decide what, if anything, reaches the target.
 *
 * <p>All subclasses share this class's {@link HandlerList}, so a listener registered against
 * {@code RealtyNotificationEvent} receives every subclass.</p>
 *
 * <p>These events are always asynchronous. A listener that touches the Bukkit API must marshal
 * onto the main thread itself.</p>
 */
public abstract class RealtyNotificationEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final List<UUID> targetIds;
    private final Component message;
    private final String regionId;
    private final UUID worldId;

    protected RealtyNotificationEvent(@NotNull List<UUID> targetIds,
                                      @NotNull Component message,
                                      @NotNull String regionId,
                                      @NotNull UUID worldId) {
        super(true);
        this.targetIds = List.copyOf(targetIds);
        this.message = message;
        this.regionId = regionId;
        this.worldId = worldId;
    }

    protected RealtyNotificationEvent(@NotNull UUID targetId,
                                      @NotNull Component message,
                                      @NotNull String regionId,
                                      @NotNull UUID worldId) {
        this(List.of(targetId), message, regionId, worldId);
    }

    /**
     * The players this notification is addressed to. Never empty, never mutable.
     */
    public @NotNull List<UUID> targetIds() {
        return this.targetIds;
    }

    /**
     * The message as Realty rendered it, using the server's configured {@code messages.yml}.
     */
    public @NotNull Component message() {
        return this.message;
    }

    public @NotNull String regionId() {
        return this.regionId;
    }

    public @NotNull UUID worldId() {
        return this.worldId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
