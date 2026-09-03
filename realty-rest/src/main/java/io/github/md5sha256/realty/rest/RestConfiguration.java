package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.DatabaseSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * The service's entire configuration, resolved from environment variables.
 *
 * <p>There is deliberately no config file: the deployment targets are Docker and
 * Pterodactyl, where the panel owns the values and the filesystem is ephemeral.
 * Discoverability is served by {@link #describeRedacted()} being logged at startup
 * and by failures naming the exact variable at fault.</p>
 */
public record RestConfiguration(
        @NotNull DatabaseSettings database,
        @NotNull RestSettings rest
) {

    private static final Logger LOGGER = Logger.getLogger(RestConfiguration.class.getName());

    public static @NotNull RestConfiguration load(@NotNull Function<String, String> env) {
        DatabaseSettings database = new DatabaseSettings(
                required(env, "REALTY_DB_URL"),
                required(env, "REALTY_DB_USERNAME"),
                required(env, "REALTY_DB_PASSWORD"));
        int requestedMaxPageSize = integer(env, "REALTY_REST_MAX_PAGE_SIZE", 100);
        if (requestedMaxPageSize > RestSettings.MAX_PAGE_SIZE_LIMIT) {
            // Clamped rather than rejected: the service is still correct at the
            // ceiling, and refusing to start would turn a too-generous number into
            // an outage. The banner logged at startup then shows the value actually
            // in force, not the one that was asked for.
            LOGGER.warning("REALTY_REST_MAX_PAGE_SIZE=" + requestedMaxPageSize
                    + " exceeds the hard limit of " + RestSettings.MAX_PAGE_SIZE_LIMIT
                    + "; using " + RestSettings.MAX_PAGE_SIZE_LIMIT);
        }
        RestSettings rest = new RestSettings(
                optional(env, "REALTY_REST_HOST", "0.0.0.0"),
                integer(env, "REALTY_REST_PORT", 8080),
                requestedMaxPageSize,
                originList(env, "REALTY_REST_CORS_ORIGINS"),
                env.apply("REALTY_REST_MODULE_URL"),
                env.apply("REALTY_REST_MODULE_SECRET"),
                integer(env, "REALTY_REST_MODULE_TIMEOUT_MS", 1500));
        if (rest.moduleUrl() != null && !rest.moduleUrl().isBlank()
                && (rest.moduleSecret() == null || rest.moduleSecret().isBlank())) {
            LOGGER.warning("REALTY_REST_MODULE_URL is set but REALTY_REST_MODULE_SECRET is not; the module "
                    + "rejects unauthenticated calls, so enrichment is disabled until a secret is set");
        }
        return new RestConfiguration(database, rest);
    }

    /**
     * Every resolved setting, with secrets replaced. Logged at startup so the
     * running configuration is always visible.
     */
    public @NotNull String describeRedacted() {
        return """
                REALTY_DB_URL=%s
                REALTY_DB_USERNAME=%s
                REALTY_DB_PASSWORD=%s
                REALTY_REST_HOST=%s
                REALTY_REST_PORT=%d
                REALTY_REST_MAX_PAGE_SIZE=%d
                REALTY_REST_CORS_ORIGINS=%s
                REALTY_REST_MODULE_URL=%s
                REALTY_REST_MODULE_SECRET=%s
                REALTY_REST_MODULE_TIMEOUT_MS=%d"""
                .formatted(this.database.url(),
                        this.database.username(),
                        "<redacted>",
                        this.rest.host(),
                        this.rest.port(),
                        this.rest.maxPageSize(),
                        this.rest.corsOrigins().isEmpty()
                                ? "<none -- CORS disabled>"
                                : String.join(",", this.rest.corsOrigins()),
                        this.rest.moduleUrl() == null ? "<unset>" : this.rest.moduleUrl(),
                        this.rest.moduleSecret() == null ? "<unset>" : "<redacted>",
                        this.rest.moduleTimeoutMs());
    }

    private static @NotNull String required(@NotNull Function<String, String> env,
                                            @NotNull String key) {
        String value = env.apply(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable " + key + " is not set");
        }
        return value;
    }

    private static @NotNull String optional(@NotNull Function<String, String> env,
                                            @NotNull String key,
                                            @NotNull String fallback) {
        String value = env.apply(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int integer(@NotNull Function<String, String> env,
                               @NotNull String key,
                               int fallback) {
        String value = env.apply(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Environment variable " + key + " must be an integer, was: " + value, ex);
        }
    }

    /**
     * Splits a comma-separated allowlist, trimming each entry and dropping blanks.
     * An unset or blank variable yields an empty list, which callers read as
     * "disabled" -- there is deliberately no wildcard default.
     */
    private static @NotNull List<String> originList(@NotNull Function<String, String> env,
                                                    @NotNull String key) {
        String value = env.apply(key);
        List<String> origins = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return origins;
        }
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                origins.add(trimmed);
            }
        }
        return origins;
    }

}
