package io.github.md5sha256.realty.adapter.query;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

class ServerLifecycleTest {

    /** Collects records so a test can assert on level and message. */
    private static final class RecordingHandler extends Handler {

        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            this.records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        boolean has(Level level, String fragment) {
            return this.records.stream()
                    .anyMatch(r -> r.getLevel().equals(level) && r.getMessage().contains(fragment));
        }
    }

    /** A server handle that counts its own closes. */
    private static final class Handle implements AutoCloseable {

        private final AtomicInteger closes = new AtomicInteger();

        @Override
        public void close() {
            this.closes.incrementAndGet();
        }

        int closeCount() {
            return this.closes.get();
        }
    }

    private static QueryServiceConfig config(String secret, int port) {
        return new QueryServiceConfig(secret, "127.0.0.1", port, Duration.ofMillis(1000));
    }

    private static Logger freshLogger(RecordingHandler handler) {
        Logger logger = Logger.getLogger("query-service-test-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
        return logger;
    }

    @Test
    void anEmptySecretStartsNothingAndWarns() {
        RecordingHandler handler = new RecordingHandler();
        AtomicInteger built = new AtomicInteger();
        ServerLifecycle lifecycle = new ServerLifecycle(
                () -> config("", 8123),
                cfg -> {
                    built.incrementAndGet();
                    return new Handle();
                },
                freshLogger(handler));

        lifecycle.start();

        Assertions.assertEquals(0, built.get());
        Assertions.assertTrue(handler.has(Level.WARNING, "shared-secret is empty"),
                "expected a WARNING naming the empty secret");
    }

    @Test
    void aConfiguredSecretStartsTheServer() {
        RecordingHandler handler = new RecordingHandler();
        Handle handle = new Handle();
        ServerLifecycle lifecycle = new ServerLifecycle(
                () -> config("hunter2", 8123), cfg -> handle, freshLogger(handler));

        lifecycle.start();

        Assertions.assertEquals(0, handle.closeCount());
        Assertions.assertTrue(handler.has(Level.INFO, "listening on http://127.0.0.1:8123"));
    }

    @Test
    void aReloadWhoseConfigFailsToReadKeepsTheRunningServer() {
        RecordingHandler handler = new RecordingHandler();
        Handle handle = new Handle();
        AtomicInteger built = new AtomicInteger();
        AtomicInteger reads = new AtomicInteger();
        ServerLifecycle lifecycle = new ServerLifecycle(
                () -> {
                    if (reads.getAndIncrement() > 0) {
                        throw new IllegalStateException("config.yml is not valid YAML");
                    }
                    return config("hunter2", 8123);
                },
                cfg -> {
                    built.incrementAndGet();
                    return handle;
                },
                freshLogger(handler));
        lifecycle.start();

        lifecycle.reload();

        Assertions.assertEquals(1, built.get(), "the factory must not run again");
        Assertions.assertEquals(0, handle.closeCount(), "the running server must be left alone");
        Assertions.assertTrue(handler.has(Level.SEVERE, "failed to read config.yml"));
    }

    @Test
    void aReloadWhoseStartFailsFallsBackToThePreviousConfig() {
        RecordingHandler handler = new RecordingHandler();
        Handle first = new Handle();
        Handle second = new Handle();
        QueryServiceConfig original = config("hunter2", 8123);
        QueryServiceConfig broken = config("hunter2", 9999);
        List<QueryServiceConfig> built = new ArrayList<>();
        AtomicInteger reads = new AtomicInteger();
        ServerLifecycle lifecycle = new ServerLifecycle(
                () -> reads.getAndIncrement() == 0 ? original : broken,
                cfg -> {
                    built.add(cfg);
                    if (cfg == broken) {
                        throw new IllegalStateException("port already in use");
                    }
                    return built.size() == 1 ? first : second;
                },
                freshLogger(handler));
        lifecycle.start();

        lifecycle.reload();

        Assertions.assertEquals(List.of(original, broken, original), built);
        Assertions.assertEquals(1, first.closeCount(), "the old server is stopped exactly once");
        Assertions.assertEquals(0, second.closeCount());
        Assertions.assertTrue(handler.has(Level.SEVERE, "restarting on the previous one"));
    }

    @Test
    void aReloadThatFailsBothWaysPropagatesAfterLogging() {
        RecordingHandler handler = new RecordingHandler();
        Handle first = new Handle();
        AtomicInteger built = new AtomicInteger();
        ServerLifecycle lifecycle = new ServerLifecycle(
                () -> config("hunter2", 8123),
                cfg -> {
                    if (built.getAndIncrement() > 0) {
                        throw new IllegalStateException("port already in use");
                    }
                    return first;
                },
                freshLogger(handler));
        lifecycle.start();

        // The new config fails to start, and so does the fallback to the previous one.
        Assertions.assertThrows(IllegalStateException.class, lifecycle::reload);
        Assertions.assertTrue(handler.has(Level.SEVERE, "restarting on the previous one"));
    }

    @Test
    void aFirstStartThatFailsWithNoPreviousConfigPropagates() {
        RecordingHandler handler = new RecordingHandler();
        ServerLifecycle lifecycle = new ServerLifecycle(
                () -> config("hunter2", 9999),
                cfg -> {
                    throw new IllegalStateException("port already in use");
                },
                freshLogger(handler));

        Assertions.assertThrows(IllegalStateException.class, lifecycle::start);
        Assertions.assertThrows(IllegalStateException.class, lifecycle::reload);
    }

    @Test
    void stopClosesTheHandleOnceAndIsIdempotent() {
        RecordingHandler handler = new RecordingHandler();
        Handle handle = new Handle();
        ServerLifecycle lifecycle = new ServerLifecycle(
                () -> config("hunter2", 8123), cfg -> handle, freshLogger(handler));
        lifecycle.start();

        lifecycle.stop();
        lifecycle.stop();

        Assertions.assertEquals(1, handle.closeCount());
    }
}
