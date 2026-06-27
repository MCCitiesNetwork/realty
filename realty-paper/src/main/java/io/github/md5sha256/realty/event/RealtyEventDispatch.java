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
 * when necessary and reports whether the event proceeded.</p>
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
     * @return {@code false} if a listener cancelled the event, {@code true}
     *         otherwise (including non-cancellable events). A cancellable event
     *         that would require a thread hop throws, so a deferred fire is
     *         always non-cancellable and returns {@code true}.
     * @throws IllegalArgumentException if {@code event} is asynchronous
     * @throws IllegalStateException    if {@code event} is cancellable and the
     *                                  caller is off the main thread
     */
    public boolean fireSync(@NotNull RealtyRegionEvent event) {
        if (event.isAsynchronous()) {
            throw new IllegalArgumentException(
                    "fireSync requires a synchronous event; use fireAsync for " + event.getEventName());
        }
        return fireOrHop(event, this.server.isPrimaryThread(), this.mainThreadExecutor,
                "Cancellable synchronous events must be fired from the main thread: ");
    }

    /**
     * Fires an asynchronous Realty event, hopping off the main thread if the
     * caller is on it.
     *
     * @return {@code false} if a listener cancelled the event, {@code true}
     *         otherwise (including non-cancellable events). A cancellable event
     *         that would require a thread hop throws, so a deferred fire is
     *         always non-cancellable and returns {@code true}.
     * @throws IllegalArgumentException if {@code event} is synchronous
     * @throws IllegalStateException    if {@code event} is cancellable and the
     *                                  caller is on the main thread
     */
    public boolean fireAsync(@NotNull RealtyRegionEvent event) {
        if (!event.isAsynchronous()) {
            throw new IllegalArgumentException(
                    "fireAsync requires an asynchronous event; use fireSync for " + event.getEventName());
        }
        return fireOrHop(event, !this.server.isPrimaryThread(), this.asyncExecutor,
                "Cancellable asynchronous events must be fired off the main thread: ");
    }

    /**
     * Fires {@code event} inline when the caller is already on the thread the
     * event requires, reporting whether it proceeded (was not cancelled).
     * Otherwise hops onto {@code targetExecutor} and returns {@code true}; a
     * cancellable event that would require such a hop throws, since its
     * cancellation verdict could not be reported on return.
     */
    private boolean fireOrHop(@NotNull RealtyRegionEvent event,
                              boolean onRequiredThread,
                              @NotNull Executor targetExecutor,
                              @NotNull String cancellableHopError) {
        if (onRequiredThread) {
            this.pluginManager.callEvent(event);
            return !cancelled(event);
        }
        if (event instanceof Cancellable) {
            throw new IllegalStateException(cancellableHopError + event.getEventName());
        }
        targetExecutor.execute(() -> this.pluginManager.callEvent(event));
        return true;
    }

    private static boolean cancelled(@NotNull RealtyRegionEvent event) {
        return event instanceof Cancellable c && c.isCancelled();
    }
}
