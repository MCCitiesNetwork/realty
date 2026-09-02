package io.github.md5sha256.realty.rest;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

/**
 * Query parameter reading.
 *
 * <p>Neither kind of name this API accepts is URL-safe. A world name is a folder
 * name and may contain spaces or a percent sign; a Floodgate player name is an
 * Xbox gamertag behind a {@code .} prefix and may contain spaces. No extra decoding
 * step is needed for either: {@link Context#queryParam(String)} already fully
 * percent-decodes the value and already treats a literal {@code +} as a space
 * (verified empirically against Javalin 6.4.0 -- both {@code %20} and {@code +}
 * arrive here as a plain space, and {@code %25} arrives as a plain {@code %}), so
 * re-decoding it would corrupt a name containing a genuine {@code %} or {@code +}
 * (for example {@code 100%20} or {@code My+World}) rather than leave it alone.</p>
 */
public final class QueryParams {

    private QueryParams() {
    }

    public static @NotNull String required(@NotNull Context ctx, @NotNull String name) {
        String raw = ctx.queryParam(name);
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("MISSING_PARAMETER",
                    "Query parameter '" + name + "' is required");
        }
        return raw;
    }

}
