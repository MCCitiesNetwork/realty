package io.github.md5sha256.realty.util;

import io.github.md5sha256.realty.api.PlayerNameService;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * {@link PlayerNameService} over {@link SquirrelIdUsernameResolver}.
 *
 * <p>Built over two functions rather than the resolver itself so the mapping rules can be tested
 * without a running server: the resolver answers an unknown UUID with the UUID's own string, and
 * this class is where that becomes {@link Optional#empty()}.</p>
 */
public final class SquirrelIdPlayerNameService implements PlayerNameService {

    private final Function<UUID, CompletableFuture<String>> nameLookup;
    private final Function<String, CompletableFuture<Optional<UUID>>> uuidLookup;

    public SquirrelIdPlayerNameService(@NotNull SquirrelIdUsernameResolver resolver) {
        this(resolver::getUsername, resolver::getUuid);
    }

    SquirrelIdPlayerNameService(@NotNull Function<UUID, CompletableFuture<String>> nameLookup,
                                @NotNull Function<String, CompletableFuture<Optional<UUID>>> uuidLookup) {
        this.nameLookup = Objects.requireNonNull(nameLookup, "nameLookup");
        this.uuidLookup = Objects.requireNonNull(uuidLookup, "uuidLookup");
    }

    @Override
    public @NotNull CompletableFuture<Optional<String>> nameOf(@NotNull UUID id) {
        return this.nameLookup.apply(id)
                .thenApply(name -> name == null || name.isEmpty() || name.equals(id.toString())
                        ? Optional.<String>empty()
                        : Optional.of(name))
                .exceptionally(ex -> Optional.empty());
    }

    @Override
    public @NotNull CompletableFuture<Optional<UUID>> uuidOf(@NotNull String name) {
        return this.uuidLookup.apply(name)
                .exceptionally(ex -> Optional.empty());
    }
}
