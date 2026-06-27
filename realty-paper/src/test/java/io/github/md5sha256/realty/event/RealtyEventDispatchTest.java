package io.github.md5sha256.realty.event;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.RealtyRegionEvent;
import io.github.md5sha256.realty.api.event.RegionRentEvent;
import io.github.md5sha256.realty.api.event.RegionRentedEvent;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtyEventDispatchTest {

    @Mock
    private Server server;
    @Mock
    private PluginManager pluginManager;

    private final CapturingExecutor mainExec = new CapturingExecutor();
    private final CapturingExecutor asyncExec = new CapturingExecutor();

    private RealtyEventDispatch dispatch;
    private WorldGuardRegion region;

    @BeforeEach
    void setUp() {
        when(server.getPluginManager()).thenReturn(pluginManager);
        dispatch = new RealtyEventDispatch(server, mainExec, asyncExec);
        region = new WorldGuardRegion(mock(ProtectedRegion.class), mock(World.class));
    }

    // ── fireSync ──────────────────────────────────────────────────────────

    @Test
    void fireSync_syncEvent_onMainThread_firesInlineAndReturnsTrue() {
        when(server.isPrimaryThread()).thenReturn(true);
        RegionRentEvent event = new RegionRentEvent(region, UUID.randomUUID());

        assertTrue(dispatch.fireSync(event));

        verify(pluginManager).callEvent(event);
        assertTrue(mainExec.tasks.isEmpty());
    }

    @Test
    void fireSync_cancellableEvent_cancelledByListener_returnsFalse() {
        when(server.isPrimaryThread()).thenReturn(true);
        RegionRentEvent event = new RegionRentEvent(region, UUID.randomUUID());
        doAnswer(invocation -> {
            ((Cancellable) invocation.getArgument(0)).setCancelled(true);
            return null;
        }).when(pluginManager).callEvent(event);

        assertFalse(dispatch.fireSync(event));
    }

    @Test
    void fireSync_syncEvent_offMainThread_nonCancellable_routedToMainThread() {
        when(server.isPrimaryThread()).thenReturn(false);
        RegionRentedEvent event = new RegionRentedEvent(
                region, UUID.randomUUID(), UUID.randomUUID(), 10.0, 60L);

        // Deferred — not fired inline, scheduled onto the main-thread executor; reports proceed.
        assertTrue(dispatch.fireSync(event));

        verify(pluginManager, never()).callEvent(event);
        assertEquals(1, mainExec.tasks.size());
        mainExec.runAll();
        verify(pluginManager).callEvent(event);
    }

    @Test
    void fireSync_asyncEvent_throwsIllegalArgument() {
        AsyncEvent event = new AsyncEvent(region);
        assertThrows(IllegalArgumentException.class, () -> dispatch.fireSync(event));
        verify(pluginManager, never()).callEvent(event);
    }

    @Test
    void fireSync_cancellableSyncEvent_offMainThread_throwsIllegalState() {
        when(server.isPrimaryThread()).thenReturn(false);
        RegionRentEvent event = new RegionRentEvent(region, UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> dispatch.fireSync(event));
        verify(pluginManager, never()).callEvent(event);
        assertTrue(mainExec.tasks.isEmpty());
    }

    // ── fireAsync ─────────────────────────────────────────────────────────

    @Test
    void fireAsync_asyncEvent_offMainThread_firesInlineAndReturnsTrue() {
        when(server.isPrimaryThread()).thenReturn(false);
        AsyncEvent event = new AsyncEvent(region);

        assertTrue(dispatch.fireAsync(event));

        verify(pluginManager).callEvent(event);
        assertTrue(asyncExec.tasks.isEmpty());
    }

    @Test
    void fireAsync_cancellableEvent_cancelledByListener_returnsFalse() {
        when(server.isPrimaryThread()).thenReturn(false);
        CancellableAsyncEvent event = new CancellableAsyncEvent(region);
        doAnswer(invocation -> {
            ((Cancellable) invocation.getArgument(0)).setCancelled(true);
            return null;
        }).when(pluginManager).callEvent(event);

        assertFalse(dispatch.fireAsync(event));
    }

    @Test
    void fireAsync_asyncEvent_onMainThread_routedToAsyncExecutor() {
        when(server.isPrimaryThread()).thenReturn(true);
        AsyncEvent event = new AsyncEvent(region);

        assertTrue(dispatch.fireAsync(event));

        verify(pluginManager, never()).callEvent(event);
        assertEquals(1, asyncExec.tasks.size());
        asyncExec.runAll();
        verify(pluginManager).callEvent(event);
    }

    @Test
    void fireAsync_syncEvent_throwsIllegalArgument() {
        RegionRentEvent event = new RegionRentEvent(region, UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> dispatch.fireAsync(event));
        verify(pluginManager, never()).callEvent(event);
    }

    @Test
    void fireAsync_cancellableAsyncEvent_onMainThread_throwsIllegalState() {
        when(server.isPrimaryThread()).thenReturn(true);
        CancellableAsyncEvent event = new CancellableAsyncEvent(region);

        assertThrows(IllegalStateException.class, () -> dispatch.fireAsync(event));
        verify(pluginManager, never()).callEvent(event);
        assertTrue(asyncExec.tasks.isEmpty());
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static final class CapturingExecutor implements Executor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(@NotNull Runnable command) {
            this.tasks.add(command);
        }

        void runAll() {
            for (Runnable task : this.tasks) {
                task.run();
            }
        }
    }

    /** Minimal asynchronous, non-cancellable Realty event for test purposes. */
    private static final class AsyncEvent extends RealtyRegionEvent {
        private static final HandlerList HANDLERS = new HandlerList();

        AsyncEvent(@NotNull WorldGuardRegion region) {
            super(region, true);
        }

        @Override
        public @NotNull HandlerList getHandlers() {
            return HANDLERS;
        }

        public static @NotNull HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    /** Minimal asynchronous, cancellable Realty event for test purposes. */
    private static final class CancellableAsyncEvent extends RealtyRegionEvent implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private boolean cancelled;

        CancellableAsyncEvent(@NotNull WorldGuardRegion region) {
            super(region, true);
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
}
