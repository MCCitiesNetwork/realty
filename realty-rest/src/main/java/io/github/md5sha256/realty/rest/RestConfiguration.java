package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.DatabaseSettings;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

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

    public static @NotNull RestConfiguration load(@NotNull Function<String, String> env) {
        DatabaseSettings database = new DatabaseSettings(
                required(env, "REALTY_DB_URL"),
                required(env, "REALTY_DB_USERNAME"),
                required(env, "REALTY_DB_PASSWORD"));
        RestSettings rest = new RestSettings(
                optional(env, "REALTY_REST_HOST", "0.0.0.0"),
                integer(env, "REALTY_REST_PORT", 8080),
                integer(env, "REALTY_REST_MAX_PAGE_SIZE", 100),
                env.apply("REALTY_REST_MODULE_URL"),
                env.apply("REALTY_REST_MODULE_SECRET"),
                integer(env, "REALTY_REST_MODULE_TIMEOUT_MS", 1500));
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
                REALTY_REST_MODULE_URL=%s
                REALTY_REST_MODULE_SECRET=%s
                REALTY_REST_MODULE_TIMEOUT_MS=%d"""
                .formatted(this.database.url(),
                        this.database.username(),
                        "<redacted>",
                        this.rest.host(),
                        this.rest.port(),
                        this.rest.maxPageSize(),
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

}
