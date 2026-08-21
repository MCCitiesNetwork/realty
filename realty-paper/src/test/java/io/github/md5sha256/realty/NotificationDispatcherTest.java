package io.github.md5sha256.realty;

import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import io.github.md5sha256.realty.api.event.RegionBoughtEvent;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Guards the fix for the defect where async notification events were fired straight from the
 * primary server thread, which Bukkit answers with an {@code IllegalStateException}.
 *
 * <p>If the main-thread hop in {@link NotificationDispatcher#fire} were removed,
 * {@link #primaryThreadFireIsHoppedOntoTheExecutor()} would see the event dispatched inline with
 * nothing queued on the executor and fail.</p>
 */
class NotificationDispatcherTest {

    private static RealtyNotificationEvent event() {
        UUID id = UUID.randomUUID();
        return new RegionBoughtEvent(id, Component.text("bought"), "plot_1", UUID.randomUUID(),
                id, "Notch", 10.0);
    }

    @AfterEach
    void reset() {
        NotificationDispatcher.resetForTesting();
    }

    @Test
    void primaryThreadFireIsHoppedOntoTheExecutor() {
        List<Runnable> queued = new ArrayList<>();
        List<RealtyNotificationEvent> dispatched = new ArrayList<>();
        NotificationDispatcher.installForTesting(queued::add, () -> true, dispatched::add);

        RealtyNotificationEvent event = event();
        NotificationDispatcher.fire(event);

        Assertions.assertEquals(List.of(), dispatched,
                "An async event must never be dispatched inline on the primary thread");
        Assertions.assertEquals(1, queued.size(), "The fire should have been queued on the executor");

        queued.get(0).run();
        Assertions.assertEquals(List.of(event), dispatched);
    }

    @Test
    void offThreadFireIsDispatchedInline() {
        List<Runnable> queued = new ArrayList<>();
        List<RealtyNotificationEvent> dispatched = new ArrayList<>();
        NotificationDispatcher.installForTesting(queued::add, () -> false, dispatched::add);

        RealtyNotificationEvent event = event();
        NotificationDispatcher.fire(event);

        Assertions.assertEquals(List.of(event), dispatched);
        Assertions.assertEquals(List.of(), queued, "No hop is needed when already off the main thread");
    }

    @Test
    void primaryThreadFireWithoutAnExecutorIsDroppedRatherThanThrown() {
        List<RealtyNotificationEvent> dispatched = new ArrayList<>();
        NotificationDispatcher.installForTesting(null, () -> true, dispatched::add);

        Assertions.assertDoesNotThrow(() -> NotificationDispatcher.fire(event()));
        Assertions.assertEquals(List.of(), dispatched);
    }

    @Test
    void everyRealExecutorPathRunsTheDispatch() {
        Executor immediate = Runnable::run;
        List<RealtyNotificationEvent> dispatched = new ArrayList<>();
        NotificationDispatcher.installForTesting(immediate, () -> true, dispatched::add);

        RealtyNotificationEvent event = event();
        NotificationDispatcher.fire(event);

        Assertions.assertEquals(List.of(event), dispatched);
    }
}
