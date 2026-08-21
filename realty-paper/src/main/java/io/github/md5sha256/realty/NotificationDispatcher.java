package io.github.md5sha256.realty;

import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The single point through which every {@link RealtyNotificationEvent} is fired.
 *
 * <h2>Why this class exists</h2>
 *
 * <p>{@code RealtyNotificationEvent} is an asynchronous event ({@code super(true)}), and Bukkit
 * throws {@link IllegalStateException} from {@code PluginManager.callEvent} when an asynchronous
 * event is fired from the primary server thread. That constraint is easy to violate here: several
 * fire sites sit in {@code CompletableFuture} callbacks whose upstream stage completes on
 * {@code ExecutorState.mainThreadExec()}, and the leasehold expiry sweep fires from inside a
 * {@code BukkitScheduler.runTask} lambda. In both cases the throw would surface as a spurious
 * "your purchase failed" message after the money had already moved, or would abort the remainder
 * of an expiry batch.</p>
 *
 * <p>The events must stay asynchronous: making them synchronous would force every third-party
 * listener onto the main thread, contradicting the threading decision in the design spec. So
 * instead of a thread check scattered across two dozen call sites — where the next call site added
 * would inevitably forget it — every fire goes through {@link #fire(RealtyNotificationEvent)},
 * which hops onto the plugin's network executor when it finds itself on the primary thread.</p>
 *
 * <h2>Why the state is static</h2>
 *
 * <p>The fire sites are {@code record} command beans that hold neither the plugin nor its
 * {@code ExecutorState}. Threading a new constructor parameter through a dozen freshly cleaned-up
 * command constructors buys nothing over a single-writer static: {@link #initialize} is called from
 * {@code Realty.onEnable} before any command is registered, and {@link #shutdown} from
 * {@code Realty.onDisable}. Nothing else writes it, and the fields are {@code volatile} so a fire
 * from any thread sees the initialised value.</p>
 *
 * <p>The executor is {@code ExecutorState.networkExec()} — a thread-per-task executor, so event
 * dispatch cannot starve it, and its {@code ThreadFactory} installs the plugin class loader as the
 * thread context class loader, which module classes (loaded by a {@code URLClassLoader} parented to
 * it) rely on. Deliberately <em>not</em> {@code dbExec()}: that is a fixed pool of four and
 * blocking it on listener work risks starving database calls.</p>
 */
public final class NotificationDispatcher {

    private static final Logger LOGGER = Logger.getLogger(NotificationDispatcher.class.getName());

    private static volatile @Nullable Executor asyncExec;
    private static volatile @NotNull BooleanSupplier primaryThreadCheck = Bukkit::isPrimaryThread;
    private static volatile @NotNull Consumer<RealtyNotificationEvent> sink =
            event -> Bukkit.getPluginManager().callEvent(event);

    private NotificationDispatcher() {
    }

    /**
     * Installs the executor used to hop off the primary thread. Called once from
     * {@code Realty.onEnable}, before commands and listeners are registered.
     */
    public static void initialize(@NotNull Executor asyncExec) {
        NotificationDispatcher.asyncExec = Objects.requireNonNull(asyncExec, "asyncExec");
    }

    /**
     * Clears the executor. Called from {@code Realty.onDisable}; after this a fire from the primary
     * thread is dropped with a warning rather than throwing.
     */
    public static void shutdown() {
        NotificationDispatcher.asyncExec = null;
    }

    /**
     * Test seam: overrides the primary-thread predicate and the dispatch sink so the routing
     * decision can be exercised without a running server.
     */
    static void installForTesting(@Nullable Executor asyncExec,
                                  @NotNull BooleanSupplier primaryThreadCheck,
                                  @NotNull Consumer<RealtyNotificationEvent> sink) {
        NotificationDispatcher.asyncExec = asyncExec;
        NotificationDispatcher.primaryThreadCheck = Objects.requireNonNull(primaryThreadCheck);
        NotificationDispatcher.sink = Objects.requireNonNull(sink);
    }

    /** Test seam: restores the production predicate and sink. */
    static void resetForTesting() {
        NotificationDispatcher.asyncExec = null;
        NotificationDispatcher.primaryThreadCheck = Bukkit::isPrimaryThread;
        NotificationDispatcher.sink = event -> Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * Fires the given notification event, marshalling off the primary server thread first if
     * necessary. Never throws on account of the calling thread.
     */
    public static void fire(@NotNull RealtyNotificationEvent event) {
        Objects.requireNonNull(event, "event");
        Consumer<RealtyNotificationEvent> target = sink;
        if (!primaryThreadCheck.getAsBoolean()) {
            target.accept(event);
            return;
        }
        Executor executor = asyncExec;
        if (executor == null) {
            // Enable aborted, or a fire raced plugin shutdown. Firing here would throw; dropping
            // the notification is the graceful degradation.
            LOGGER.log(Level.WARNING,
                    "Dropping " + event.getClass().getSimpleName()
                            + ": fired from the primary thread with no dispatch executor available");
            return;
        }
        executor.execute(() -> target.accept(event));
    }
}
