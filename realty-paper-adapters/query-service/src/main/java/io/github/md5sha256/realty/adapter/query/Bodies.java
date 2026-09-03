package io.github.md5sha256.realty.adapter.query;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/** Request-body parsing shared by the batch routes. */
final class Bodies {

    private Bodies() {
    }

    static <T> @NotNull T read(@NotNull Context ctx, @NotNull Class<T> type) {
        try {
            return bodyAsClass(ctx, type);
        } catch (RuntimeException | IOException ex) {
            throw ApiException.badRequest("INVALID_BODY", "Body is not valid JSON for this route");
        }
    }

    /**
     * Javalin sneaky-throws Jackson's checked {@link IOException} out of {@code bodyAsClass}
     * without declaring it, so {@link #read} could not otherwise catch it by type without also
     * swallowing unrelated {@link RuntimeException}s from inside Javalin/Jackson internals. This
     * indirection declares the checked exception so the caller's catch is exhaustive and precise.
     */
    private static <T> T bodyAsClass(@NotNull Context ctx, @NotNull Class<T> type) throws IOException {
        return ctx.bodyAsClass(type);
    }

    static void requireWithinBatchLimit(int size) {
        if (size > QueryServiceServer.MAX_BATCH) {
            throw ApiException.badRequest("BATCH_TOO_LARGE",
                    "At most " + QueryServiceServer.MAX_BATCH + " entries per request");
        }
    }
}
