package io.github.md5sha256.realty.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

class SquirrelIdPlayerNameServiceTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f64f65c7e1");

    /** Runs every task immediately, counting them, so a main-thread hop is observable. */
    private static final class RecordingExecutor implements Executor {

        private final AtomicInteger tasks = new AtomicInteger();

        @Override
        public void execute(Runnable command) {
            this.tasks.incrementAndGet();
            command.run();
        }

        int taskCount() {
            return this.tasks.get();
        }
    }

    private static SquirrelIdPlayerNameService service() {
        return service(new RecordingExecutor(), false);
    }

    private static SquirrelIdPlayerNameService service(Executor mainThread, boolean onMainThread) {
        return new SquirrelIdPlayerNameService(
                id -> CompletableFuture.completedFuture(
                        id.equals(NOTCH) ? "Notch"
                                : id.equals(BEDROCK) ? ".Cool Guy 123"
                                : id.toString()),
                name -> CompletableFuture.completedFuture(
                        name.equals("Notch") ? Optional.of(NOTCH)
                                : name.equals(".Cool Guy 123") ? Optional.of(BEDROCK)
                                : Optional.empty()),
                mainThread,
                () -> onMainThread);
    }

    @Test
    void resolvesAJavaEditionName() {
        Assertions.assertEquals(Optional.of("Notch"), service().nameOf(NOTCH).join());
    }

    @Test
    void resolvesABedrockNameWithSpaces() {
        Assertions.assertEquals(Optional.of(".Cool Guy 123"), service().nameOf(BEDROCK).join());
    }

    @Test
    void anUnresolvableUuidIsEmptyRatherThanTheUuidString() {
        UUID unknown = UUID.randomUUID();
        Assertions.assertEquals(Optional.empty(), service().nameOf(unknown).join());
    }

    @Test
    void reverseLookupWorksForBothNameForms() {
        Assertions.assertEquals(Optional.of(NOTCH), service().uuidOf("Notch").join());
        Assertions.assertEquals(Optional.of(BEDROCK), service().uuidOf(".Cool Guy 123").join());
        Assertions.assertEquals(Optional.empty(), service().uuidOf("nobody").join());
    }

    @Test
    void aFailedLookupCompletesEmptyNotExceptionally() {
        SquirrelIdPlayerNameService failing = new SquirrelIdPlayerNameService(
                id -> CompletableFuture.failedFuture(new IllegalStateException("boom")),
                name -> CompletableFuture.failedFuture(new IllegalStateException("boom")),
                Runnable::run,
                () -> false);
        Assertions.assertEquals(Optional.empty(), failing.nameOf(NOTCH).join());
        Assertions.assertEquals(Optional.empty(), failing.uuidOf("Notch").join());
    }

    @Test
    void batchLookupKeepsEveryRequestedIdInOrder() {
        UUID unknown = UUID.randomUUID();
        Map<UUID, Optional<String>> names = service().namesOf(List.of(BEDROCK, unknown, NOTCH)).join();
        Assertions.assertEquals(List.of(BEDROCK, unknown, NOTCH), List.copyOf(names.keySet()));
        Assertions.assertEquals(Optional.empty(), names.get(unknown));
        Assertions.assertEquals(Optional.of("Notch"), names.get(NOTCH));
    }

    @Test
    void anOffMainThreadCallRunsTheLookupOnTheMainThreadExecutor() {
        RecordingExecutor mainThread = new RecordingExecutor();
        SquirrelIdPlayerNameService service = service(mainThread, false);
        Assertions.assertEquals(Optional.of("Notch"), service.nameOf(NOTCH).join());
        Assertions.assertEquals(1, mainThread.taskCount());
        Assertions.assertEquals(Optional.of(NOTCH), service.uuidOf("Notch").join());
        Assertions.assertEquals(2, mainThread.taskCount());
    }

    @Test
    void aMainThreadCallDoesNotTouchTheExecutor() {
        RecordingExecutor mainThread = new RecordingExecutor();
        SquirrelIdPlayerNameService service = service(mainThread, true);
        Assertions.assertEquals(Optional.of("Notch"), service.nameOf(NOTCH).join());
        Assertions.assertEquals(Optional.of(NOTCH), service.uuidOf("Notch").join());
        Assertions.assertEquals(0, mainThread.taskCount());
    }
}
