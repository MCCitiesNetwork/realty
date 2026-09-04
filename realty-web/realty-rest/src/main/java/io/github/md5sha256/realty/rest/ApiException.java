package io.github.md5sha256.realty.rest;

import org.jetbrains.annotations.NotNull;

/**
 * An error with a chosen HTTP status and a stable machine-readable code.
 *
 * <p>Never thrown for an unexpected failure — those become a generic 500 whose
 * detail goes to the log, never to the client.</p>
 */
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

    public static @NotNull ApiException notFound(@NotNull String code, @NotNull String message) {
        return new ApiException(404, code, message);
    }

    public static @NotNull ApiException badGateway(@NotNull String code, @NotNull String message) {
        return new ApiException(502, code, message);
    }

    public int status() {
        return this.status;
    }

    public @NotNull String code() {
        return this.code;
    }

}
