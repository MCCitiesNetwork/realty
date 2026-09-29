package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves the display names of Treasury accounts, for callers that cannot reach Treasury
 * themselves, such as the query-service module.
 *
 * <p>The name is the account's bare display name, without a kind suffix. Every method is safe to
 * call from any thread and never completes exceptionally: an account that cannot be resolved,
 * including every account on a server without Treasury, completes with {@link Optional#empty()}.</p>
 */
public interface AccountNameService {

    @NotNull CompletableFuture<Optional<String>> nameOf(int accountId);

    /**
     * Resolves many accounts at once. The returned map contains every requested id, in request
     * order, so a caller can distinguish "no name" from "not asked".
     */
    default @NotNull CompletableFuture<Map<Integer, Optional<String>>> namesOf(
            @NotNull Collection<Integer> accountIds) {
        Map<Integer, CompletableFuture<Optional<String>>> pending = new LinkedHashMap<>();
        for (int accountId : accountIds) {
            pending.computeIfAbsent(accountId, this::nameOf);
        }
        return CompletableFuture.allOf(pending.values().toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    Map<Integer, Optional<String>> resolved = new LinkedHashMap<>();
                    pending.forEach((id, future) -> resolved.put(id, future.join()));
                    return resolved;
                });
    }
}
