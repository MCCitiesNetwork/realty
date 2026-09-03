package io.github.md5sha256.realty.adapter.query;

import org.jetbrains.annotations.NotNull;

/** A failure with a status code and a stable machine-readable code, rendered as {@code ErrorResponse}. */
public final class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    public ApiException(int status, @NotNull String code, @NotNull String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static @NotNull ApiException badRequest(@NotNull String code, @NotNull String message) {
        return new ApiException(400, code, message);
    }

    public static @NotNull ApiException unauthorized() {
        return new ApiException(401, "UNAUTHORIZED", "Missing or wrong X-Realty-Secret header");
    }

    public static @NotNull ApiException notFound(@NotNull String code, @NotNull String message) {
        return new ApiException(404, code, message);
    }

    /** The main thread did not answer a geometry request within the budget. */
    public static final String MAIN_THREAD_TIMEOUT = "MAIN_THREAD_TIMEOUT";
    /** A dependency this module calls out to — name resolution — did not answer in time. */
    public static final String UPSTREAM_TIMEOUT = "UPSTREAM_TIMEOUT";

    public static @NotNull ApiException gatewayTimeout(@NotNull String code,
                                                       @NotNull String message) {
        return new ApiException(504, code, message);
    }

    public int status() {
        return this.status;
    }

    public @NotNull String code() {
        return this.code;
    }
}
