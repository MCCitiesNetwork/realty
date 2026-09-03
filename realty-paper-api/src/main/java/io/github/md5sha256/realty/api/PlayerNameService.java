package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves player identities in both directions using the server's own knowledge first.
 *
 * <p>Prefers the local usercache, which holds the right name for anyone who has joined — including
 * Bedrock/Floodgate players whose {@code .}-prefixed names Mojang cannot resolve — and only then
 * falls back to a Mojang-backed lookup. Callers should resolve this rather than hand-rolling
 * {@code Bukkit.getOfflinePlayer(uuid).getName()}.</p>
 *
 * <p>Every method is safe to call from any thread and never completes exceptionally: an identity
 * that cannot be resolved completes with {@link Optional#empty()}.</p>
 */
public interface PlayerNameService {

    @NotNull CompletableFuture<Optional<String>> nameOf(@NotNull UUID id);

    @NotNull CompletableFuture<Optional<UUID>> uuidOf(@NotNull String name);

    /**
     * Resolves many ids at once. The returned map contains every requested id, in request order,
     * so a caller can distinguish "no name" from "not asked".
     */
    default @NotNull CompletableFuture<Map<UUID, Optional<String>>> namesOf(
            @NotNull Collection<UUID> ids) {
        Map<UUID, CompletableFuture<Optional<String>>> pending = new LinkedHashMap<>();
        for (UUID id : ids) {
            pending.computeIfAbsent(id, this::nameOf);
        }
        return CompletableFuture.allOf(pending.values().toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    Map<UUID, Optional<String>> resolved = new LinkedHashMap<>();
                    pending.forEach((id, future) -> resolved.put(id, future.join()));
                    return resolved;
                });
    }

    /** Reverse of {@link #namesOf}; same ordering and completeness guarantees. */
    default @NotNull CompletableFuture<Map<String, Optional<UUID>>> uuidsOf(
            @NotNull Collection<String> names) {
        Map<String, CompletableFuture<Optional<UUID>>> pending = new LinkedHashMap<>();
        for (String name : names) {
            pending.computeIfAbsent(name, this::uuidOf);
        }
        return CompletableFuture.allOf(pending.values().toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    Map<String, Optional<UUID>> resolved = new LinkedHashMap<>();
                    pending.forEach((name, future) -> resolved.put(name, future.join()));
                    return resolved;
                });
    }
}
