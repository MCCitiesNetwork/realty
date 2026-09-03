package io.github.md5sha256.realty.util;

import io.github.md5sha256.realty.api.PlayerNameService;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * {@link PlayerNameService} over {@link SquirrelIdUsernameResolver}.
 *
 * <p>Built over two functions rather than the resolver itself so the mapping rules can be tested
 * without a running server: the resolver answers an unknown UUID with the UUID's own string, and
 * this class is where that becomes {@link Optional#empty()}.</p>
 *
 * <p><b>Threading.</b> The lookups are only <i>mostly</i> asynchronous: both read the server's
 * usercache first ({@code Server#getOfflinePlayer}, {@code Server#getOfflinePlayerIfCached})
 * <i>before</i> returning their future, and that profile cache is mutated on the main thread.
 * Calling them from an arbitrary thread — a Javalin worker serving {@code query-service}, say — is
 * therefore a data race. This class is the single place that fixes it: an off-main-thread call hops
 * to {@code mainThread} for the lookup itself, while a main-thread caller invokes it directly,
 * because a main-thread caller that joins the returned future must not end up waiting for its own
 * next tick.</p>
 */
public final class SquirrelIdPlayerNameService implements PlayerNameService {

    private final Function<UUID, CompletableFuture<String>> nameLookup;
    private final Function<String, CompletableFuture<Optional<UUID>>> uuidLookup;
    private final Executor mainThread;
    private final BooleanSupplier onMainThread;

    public SquirrelIdPlayerNameService(@NotNull SquirrelIdUsernameResolver resolver,
                                       @NotNull Executor mainThread,
                                       @NotNull BooleanSupplier onMainThread) {
        this(resolver::getUsername, resolver::getUuid, mainThread, onMainThread);
    }

    public SquirrelIdPlayerNameService(@NotNull Function<UUID, CompletableFuture<String>> nameLookup,
                                       @NotNull Function<String, CompletableFuture<Optional<UUID>>> uuidLookup,
                                       @NotNull Executor mainThread,
                                       @NotNull BooleanSupplier onMainThread) {
        this.nameLookup = Objects.requireNonNull(nameLookup, "nameLookup");
        this.uuidLookup = Objects.requireNonNull(uuidLookup, "uuidLookup");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.onMainThread = Objects.requireNonNull(onMainThread, "onMainThread");
    }

    @Override
    public @NotNull CompletableFuture<Optional<String>> nameOf(@NotNull UUID id) {
        return onMainThread(this.nameLookup, id)
                .thenApply(name -> name == null || name.isEmpty() || name.equals(id.toString())
                        ? Optional.<String>empty()
                        : Optional.of(name))
                .exceptionally(ex -> Optional.empty());
    }

    @Override
    public @NotNull CompletableFuture<Optional<UUID>> uuidOf(@NotNull String name) {
        return this.<String, Optional<UUID>>onMainThread(this.uuidLookup, name)
                .exceptionally(ex -> Optional.empty());
    }

    /**
     * Runs {@code lookup} on the main thread unless the caller is already there. The lookup's own
     * future is flattened back out, so the returned future still completes when the underlying
     * asynchronous work does, not when the main-thread hop returns.
     */
    private <I, O> @NotNull CompletableFuture<O> onMainThread(
            @NotNull Function<I, CompletableFuture<O>> lookup, @NotNull I input) {
        if (this.onMainThread.getAsBoolean()) {
            return lookup.apply(input);
        }
        return CompletableFuture.supplyAsync(() -> lookup.apply(input), this.mainThread)
                .thenCompose(Function.identity());
    }
}
