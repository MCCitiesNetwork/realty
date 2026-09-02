package io.github.md5sha256.realty.rest;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * HTTP-side settings, read from the environment.
 *
 * @param moduleUrl       base URL of the query-service module, or {@code null} to
 *                        disable enrichment entirely
 * @param moduleSecret    shared secret sent to the module, or {@code null}
 * @param moduleTimeoutMs per-call timeout before a module-sourced field degrades to null
 */
public record RestSettings(
        @NotNull String host,
        int port,
        int maxPageSize,
        @Nullable String moduleUrl,
        @Nullable String moduleSecret,
        int moduleTimeoutMs
) {
}
