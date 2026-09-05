package io.github.md5sha256.realty.rest;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * HTTP-side settings, read from the environment.
 *
 * @param maxPageSize     the largest {@code pageSize} a request may ask for, itself
 *                        capped at {@link #MAX_PAGE_SIZE_LIMIT}
 * @param corsOrigins     origins permitted to make cross-origin browser requests.
 *                        Empty disables CORS entirely, which is the default: a
 *                        browser front end is a deployment choice, and a service
 *                        that allows every origin by default is one nobody chose.
 * @param moduleUrl       base URL of the query-service module, or {@code null} to
 *                        disable enrichment entirely
 * @param moduleSecret    shared secret sent to the module, or {@code null}
 * @param moduleTimeoutMs per-call timeout before a module-sourced field degrades to null
 * @param geometryCacheSeconds how long a reading of a world's region footprints is reused
 *                        before it is taken again. Footprints are read on the game
 *                        server's main thread, so this is what stops a map costing that
 *                        thread once per page per visitor; 0 reads every time. The term
 *                        is also how stale a redrawn region's shape may be, which is why
 *                        it is an operator's number rather than a constant.
 * @param webRoot         directory of a built front end to serve alongside the API, or
 *                        {@code null} for a pure API. Empty disables it, matching
 *                        {@code corsOrigins} and {@code moduleUrl}: serving a front end
 *                        is a deployment choice, so nothing is served unless asked for.
 *                        The bundled {@code realty-web-dist} build ignores this and
 *                        serves from its own classpath instead.
 */
public record RestSettings(
        @NotNull String host,
        int port,
        int maxPageSize,
        @NotNull List<String> corsOrigins,
        @Nullable String moduleUrl,
        @Nullable String moduleSecret,
        int moduleTimeoutMs,
        int geometryCacheSeconds,
        @Nullable String webRoot
) {

    /**
     * The hard ceiling on {@code maxPageSize}, applied no matter what an operator
     * configures.
     *
     * <p>A page size is a request for work: every row costs a world lookup and a
     * slice of the response, and an unbounded one lets a single anonymous caller
     * ask this read-only service to serialise the whole table. The operator's
     * setting narrows that ceiling; it cannot raise it.</p>
     */
    public static final int MAX_PAGE_SIZE_LIMIT = 100;

    public RestSettings {
        corsOrigins = List.copyOf(corsOrigins);
        // Clamped here rather than at the configuration boundary so no caller --
        // including a test constructing this record directly -- can hold settings
        // that promise a larger page than the service will ever serve.
        maxPageSize = Math.min(maxPageSize, MAX_PAGE_SIZE_LIMIT);
    }

}
