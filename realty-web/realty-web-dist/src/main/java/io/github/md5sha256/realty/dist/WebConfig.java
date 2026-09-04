package io.github.md5sha256.realty.dist;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Function;

/**
 * Builds the {@code /config.json} the bundled front end reads at startup.
 *
 * <p>A split deployment ships that file beside {@code index.html} and an operator edits it.
 * A bundled one cannot: the front end is inside the jar, so its configuration has to come
 * from the environment, like every other setting this service takes.</p>
 */
public final class WebConfig {

    private WebConfig() {
    }

    /**
     * Renders the config document, or {@code null} when nothing is configured -- in which
     * case no {@code /config.json} is served at all and the front end falls back to its
     * own defaults, which is exactly what an absent file means to it.
     *
     * <p>{@code apiBaseUrl} is deliberately omitted: the bundled build serves the API from
     * the same origin as the page, so a base URL would only be a way to get it wrong.</p>
     *
     * @param env reads an environment variable, or {@code null} when unset
     */
    public static @Nullable String render(@NotNull Function<String, String> env) {
        List<Credit> credits = attribution(env.apply("REALTY_WEB_RESOURCE_PACK_ATTRIBUTION"));
        if (credits.isEmpty()) {
            return null;
        }
        StringJoiner entries = new StringJoiner(",", "[", "]");
        for (Credit credit : credits) {
            entries.add(credit.toJson());
        }
        return "{\"resourcePackAttribution\":" + entries + "}";
    }

    /** One credit line, matching the front end's {@code Attribution} shape. */
    private record Credit(@NotNull String text, @Nullable String href) {

        @NotNull String toJson() {
            String body = "\"text\":" + quote(this.text);
            return this.href == null
                    ? "{" + body + "}"
                    : "{" + body + ",\"href\":" + quote(this.href) + "}";
        }
    }

    /**
     * Quotes a string as a JSON scalar.
     *
     * <p>Hand-rolled rather than pulled from a JSON library because this is the only
     * document this jar writes, and Javalin's Jackson binding is an optional dependency
     * the service does not otherwise need on its compile path.</p>
     */
    private static @NotNull String quote(@NotNull String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20) {
                // Control characters are not legal raw in a JSON string. Escaping them by
                // code point rather than by name keeps this exhaustive without a table.
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.append('"').toString();
    }

    /**
     * Parses {@code Text|https://url} entries separated by {@code ;}.
     *
     * <p>A flat string rather than JSON in an environment variable: a Pterodactyl panel
     * field and a compose file both make embedded JSON quoting painful, and this value is
     * a short list of short strings. The URL half is optional.</p>
     *
     * <p>Nothing here validates the URL. The front end already refuses any href that is
     * not an absolute http(s) URL, and that check has to live there regardless, because a
     * split deployment's config.json never passes through this code at all.</p>
     */
    private static @NotNull List<Credit> attribution(@Nullable String raw) {
        List<Credit> credits = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return credits;
        }
        for (String entry : raw.split(";")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int bar = trimmed.indexOf('|');
            String text = (bar < 0 ? trimmed : trimmed.substring(0, bar)).trim();
            String href = bar < 0 ? "" : trimmed.substring(bar + 1).trim();
            if (text.isEmpty()) {
                continue;
            }
            credits.add(new Credit(text, href.isEmpty() ? null : href));
        }
        return credits;
    }
}
