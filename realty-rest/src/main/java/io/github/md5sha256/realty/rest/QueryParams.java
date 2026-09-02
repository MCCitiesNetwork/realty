package io.github.md5sha256.realty.rest;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Query parameter reading, with the decoding rules the API's names demand.
 *
 * <p>Neither kind of name this API accepts is URL-safe. A world name is a folder
 * name and may contain spaces or a percent sign; a Floodgate player name is an
 * Xbox gamertag behind a {@code .} prefix and may contain spaces. Clients differ
 * on whether they encode a space as {@code %20} or {@code +}, so both are
 * accepted.</p>
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

    /**
     * Decodes a value, treating {@code +} as a space, that may or may not already
     * have been percent-decoded.
     *
     * <p>Javalin's {@code ctx.queryParam} already percent-decodes the value but
     * leaves a literal {@code +} untouched (it is not treated as form encoding), so
     * a value read that way only ever needs its {@code +} turned into a space. This
     * method is also exercised directly against raw, still percent-encoded query
     * fragments (see {@code QueryParamsTest}), where the {@code %XX} sequences still
     * need decoding. Both cases are handled by attempting a percent-decode after
     * turning {@code +} into {@code %20}; if the input has already been
     * percent-decoded and happens to contain a lone {@code %} (for example a world
     * named {@code 100%}, arriving here as {@code 100%25} on the wire but already
     * {@code 100%} by the time Javalin hands it over), that decode is not valid
     * UTF-8 percent-encoding and the failure is treated as "already decoded" --
     * only the {@code +}-to-space substitution is applied.</p>
     */
    public static @NotNull String plusAwareDecode(@NotNull String raw) {
        String plusesAsSpaces = raw.replace("+", "%20");
        try {
            return URLDecoder.decode(plusesAsSpaces, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return raw.replace('+', ' ');
        }
    }

}
