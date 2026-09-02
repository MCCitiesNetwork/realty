package io.github.md5sha256.realty.rest;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * @return the parameter's value, or {@code null} when it is absent or blank.
     * A blank value is treated as absent throughout this API, so
     * {@code ?world=} means the same as omitting {@code world} entirely.
     */
    public static @Nullable String optional(@NotNull Context ctx, @NotNull String name) {
        String raw = ctx.queryParam(name);
        return raw == null || raw.isBlank() ? null : raw;
    }

    /**
     * @return every value given for a repeatable parameter, blanks dropped.
     * Empty when the parameter was not given at all.
     */
    public static @NotNull List<String> values(@NotNull Context ctx, @NotNull String name) {
        List<String> values = new ArrayList<>();
        for (String raw : ctx.queryParams(name)) {
            if (!raw.isBlank()) {
                values.add(raw.trim());
            }
        }
        return values;
    }

    /**
     * @return the parameter parsed as a double, or {@code null} when absent.
     * @throws ApiException with {@code code} when the value is not a finite number.
     */
    public static @Nullable Double optionalDouble(@NotNull Context ctx,
                                                  @NotNull String name,
                                                  @NotNull String code) {
        String raw = optional(ctx, name);
        if (raw == null) {
            return null;
        }
        double value;
        try {
            value = Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest(code,
                    "Query parameter '" + name + "' must be a number");
        }
        // Double.parseDouble accepts "NaN" and "Infinity"; neither is a price, and
        // both would compare falsely against the bounds check below.
        if (!Double.isFinite(value)) {
            throw ApiException.badRequest(code,
                    "Query parameter '" + name + "' must be a finite number");
        }
        return value;
    }

    /**
     * Reads the shared 1-based {@code page} parameter, defaulting to 1.
     *
     * @throws ApiException {@code INVALID_PAGE} when it is not an integer >= 1.
     */
    public static int page(@NotNull Context ctx) {
        String raw = optional(ctx, "page");
        if (raw == null) {
            return 1;
        }
        int page;
        try {
            page = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("INVALID_PAGE", "Query parameter 'page' must be an integer");
        }
        if (page < 1) {
            throw ApiException.badRequest("INVALID_PAGE", "Query parameter 'page' must be >= 1");
        }
        return page;
    }

    /**
     * Reads the shared {@code pageSize} parameter, defaulting to 10 and clamped to
     * {@code maxPageSize}.
     *
     * <p>The configured maximum is operator-controlled and could be misconfigured to
     * {@code <= 0}; clamping the effective size to at least 1 keeps a caller's
     * {@code totalPages} division well-defined regardless.</p>
     *
     * @throws ApiException {@code INVALID_PAGE_SIZE} when it is not an integer >= 1.
     */
    public static int pageSize(@NotNull Context ctx, int maxPageSize) {
        int effectiveMax = Math.max(1, maxPageSize);
        String raw = optional(ctx, "pageSize");
        if (raw == null) {
            return Math.min(10, effectiveMax);
        }
        int pageSize;
        try {
            pageSize = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("INVALID_PAGE_SIZE", "Query parameter 'pageSize' must be an integer");
        }
        if (pageSize < 1) {
            throw ApiException.badRequest("INVALID_PAGE_SIZE", "Query parameter 'pageSize' must be >= 1");
        }
        return Math.min(pageSize, effectiveMax);
    }

}
