package io.github.md5sha256.realty.event;

import io.github.md5sha256.realty.api.event.RealtyRegionEvent;
import org.bukkit.Server;
import org.bukkit.event.Cancellable;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

/**
 * Central firing point for Realty's custom events. Owns the Bukkit threading
 * invariant so call sites never have to: synchronous events must fire on the
 * server main thread, asynchronous events must fire off it (Bukkit's
 * {@link PluginManager#callEvent} throws otherwise).
 *
 * <p>Two explicit methods make the caller's intent clear and let each own a
 * guard for its own contract: {@link #fireSync} for synchronous events,
 * {@link #fireAsync} for asynchronous ones. Each hops to the correct thread
 * when necessary.</p>
 */
public final class RealtyEventDispatch {

    private final Server server;
    private final PluginManager pluginManager;
    private final Executor mainThreadExecutor;
    private final Executor asyncExecutor;

    public RealtyEventDispatch(@NotNull Server server,
                               @NotNull Executor mainThreadExecutor,
                               @NotNull Executor asyncExecutor) {
        this.server = server;
        this.pluginManager = server.getPluginManager();
        this.mainThreadExecutor = mainThreadExecutor;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * Fires a synchronous Realty event, hopping to the main thread if the caller
     * is not already on it.
     *
     * <p>The returned event's cancellation state is only meaningful when the
     * event fired inline (i.e. the caller was already on the main thread). A
     * cancellable event that would require a hop throws, since the verdict would
     * not be ready on return.</p>
     *
     * @throws IllegalArgumentException if {@code event} is asynchronous
     * @throws IllegalStateException    if {@code event} is cancellable and the
     *                                  caller is off the main thread
     */
    public <T extends RealtyRegionEvent> @NotNull T fireSync(@NotNull T event) {
        if (event.isAsynchronous()) {
            throw new IllegalArgumentException(
                    "fireSync requires a synchronous event; use fireAsync for " + event.getEventName());
        }
        if (this.server.isPrimaryThread()) {
            this.pluginManager.callEvent(event);
        } else {
            if (event instanceof Cancellable) {
                throw new IllegalStateException(
                        "Cancellable synchronous events must be fired from the main thread: "
                                + event.getEventName());
            }
            this.mainThreadExecutor.execute(() -> this.pluginManager.callEvent(event));
        }
        return event;
    }

    /**
     * Fires an asynchronous Realty event, hopping off the main thread if the
     * caller is on it.
     *
     * <p>The returned event's cancellation state is only meaningful when the
     * event fired inline (i.e. the caller was already off the main thread). A
     * cancellable event that would require a hop throws, since the verdict would
     * not be ready on return.</p>
     *
     * @throws IllegalArgumentException if {@code event} is synchronous
     * @throws IllegalStateException    if {@code event} is cancellable and the
     *                                  caller is on the main thread
     */
    public <T extends RealtyRegionEvent> @NotNull T fireAsync(@NotNull T event) {
        if (!event.isAsynchronous()) {
            throw new IllegalArgumentException(
                    "fireAsync requires an asynchronous event; use fireSync for " + event.getEventName());
        }
        if (!this.server.isPrimaryThread()) {
            this.pluginManager.callEvent(event);
        } else {
            if (event instanceof Cancellable) {
                throw new IllegalStateException(
                        "Cancellable asynchronous events must be fired off the main thread: "
                                + event.getEventName());
            }
            this.asyncExecutor.execute(() -> this.pluginManager.callEvent(event));
        }
        return event;
    }
}
