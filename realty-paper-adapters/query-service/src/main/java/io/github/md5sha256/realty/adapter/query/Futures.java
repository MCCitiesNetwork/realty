package io.github.md5sha256.realty.adapter.query;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Joining a future on a Jetty worker thread, bounded.
 *
 * <p>Every request handler waits on work owned by someone else — the main thread for geometry, the
 * name resolver for identities — and neither is allowed to pin a worker thread indefinitely. This
 * is the single place that applies the bound and turns the resulting {@link TimeoutException} into
 * a {@code 504}, so no handler grows its own copy of the pattern.</p>
 */
final class Futures {

    private Futures() {
    }

    /**
     * Joins {@code future}, failing with a {@code 504} carrying {@code code} if it has not
     * completed within {@code timeout}. Any other failure propagates unchanged, to be logged and
     * rendered as a {@code 500} by the server's generic handler.
     */
    static <T> T joinWithin(@NotNull CompletableFuture<T> future,
                            @NotNull Duration timeout,
                            @NotNull String code) {
        try {
            return future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof TimeoutException) {
                throw ApiException.gatewayTimeout(code,
                        "Did not answer within " + timeout.toMillis() + "ms");
            }
            throw ex;
        }
    }
}
