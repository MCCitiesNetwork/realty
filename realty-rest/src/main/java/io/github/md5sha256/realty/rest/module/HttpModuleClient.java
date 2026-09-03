package io.github.md5sha256.realty.rest.module;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.md5sha256.realty.rest.RestSettings;
import io.github.md5sha256.realty.rest.json.RegionResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link ModuleClient} over HTTP. Every failure — connection refused, timeout, a
 * non-2xx status, an unparseable body — becomes an empty result, because the spec's
 * rule is that a request never fails because the game server is offline. The first
 * failure after a success is logged at WARNING and the first success after a
 * failure at INFO, so a flapping module does not flood the log.
 */
public final class HttpModuleClient implements ModuleClient {

    private static final Logger LOGGER = Logger.getLogger(HttpModuleClient.class.getName());
    static final String SECRET_HEADER = "X-Realty-Secret";
    /** The module rejects larger batches; callers never send more than a page of refs anyway. */
    static final int MAX_BATCH = 256;

    private final URI baseUrl;
    private final String secret;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final AtomicBoolean reachable = new AtomicBoolean(true);

    public HttpModuleClient(@NotNull URI baseUrl,
                            @NotNull String secret,
                            @NotNull Duration timeout,
                            @NotNull HttpClient http,
                            @NotNull ObjectMapper mapper) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.secret = Objects.requireNonNull(secret, "secret");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.http = Objects.requireNonNull(http, "http");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * Builds the client the settings describe, or {@link ModuleClient#disabled()} when no
     * URL is set or the URL is unusable, logging which of the two it was. A URL with no
     * {@code http}/{@code https} scheme or no host cannot address anything, so it is
     * rejected once here rather than throwing out of every later call.
     */
    public static @NotNull ModuleClient from(@NotNull RestSettings settings) {
        String url = settings.moduleUrl();
        String secret = settings.moduleSecret();
        if (url == null || url.isBlank() || secret == null || secret.isBlank()) {
            LOGGER.info("query-service enrichment: disabled (REALTY_REST_MODULE_URL unset or no secret)");
            return ModuleClient.disabled();
        }
        String trimmed = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        URI base;
        try {
            base = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, "REALTY_REST_MODULE_URL=" + url
                    + " is not a valid URL; query-service enrichment is disabled", ex);
            return ModuleClient.disabled();
        }
        String scheme = base.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            LOGGER.warning("REALTY_REST_MODULE_URL=" + url + " has no http:// or https:// scheme;"
                    + " query-service enrichment is disabled");
            return ModuleClient.disabled();
        }
        if (base.getHost() == null || base.getHost().isBlank()) {
            LOGGER.warning("REALTY_REST_MODULE_URL=" + url + " names no host;"
                    + " query-service enrichment is disabled");
            return ModuleClient.disabled();
        }
        Duration timeout = Duration.ofMillis(settings.moduleTimeoutMs());
        HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).build();
        LOGGER.info("query-service enrichment: enabled against " + base);
        return new HttpModuleClient(base, secret, timeout, http, new ObjectMapper());
    }

    @Override
    public @NotNull Optional<RegionResponse.Dimensions> dimensions(@NotNull UUID worldId,
                                                                   @NotNull String regionId) {
        String path = "/regions/" + worldId + "/dimensions";
        try {
            path = "/regions/" + worldId + "/" + pathSegment(regionId) + "/dimensions";
            JsonNode body = get(path);
            if (body == null || !body.has("shape")) {
                return Optional.empty();
            }
            List<RegionResponse.Point> points = new ArrayList<>();
            for (JsonNode point : body.path("points")) {
                points.add(new RegionResponse.Point(point.path("x").asInt(), point.path("z").asInt()));
            }
            return Optional.of(new RegionResponse.Dimensions(
                    body.path("shape").asText(), body.path("minY").asInt(), body.path("maxY").asInt(), points));
        } catch (RuntimeException ex) {
            failed(path, ex);
            return Optional.empty();
        }
    }

    @Override
    public @NotNull Map<UUID, String> names(@NotNull Collection<UUID> ids) {
        String path = "/players/names";
        try {
            List<UUID> distinct = new ArrayList<>(new LinkedHashSet<>(ids));
            if (distinct.isEmpty()) {
                return Map.of();
            }
            if (distinct.size() > MAX_BATCH) {
                LOGGER.fine("truncating a batch of " + distinct.size() + " player ids to " + MAX_BATCH
                        + "; the module rejects larger batches");
                distinct = distinct.subList(0, MAX_BATCH);
            }
            JsonNode body = post(path, Map.of("ids", distinct.stream().map(UUID::toString).toList()));
            Map<UUID, String> names = new LinkedHashMap<>();
            if (body == null) {
                return names;
            }
            for (JsonNode player : body.path("players")) {
                JsonNode name = player.path("name");
                if (name.isNull() || !name.isTextual()) {
                    continue;
                }
                JsonNode id = player.path("id");
                if (id.isNull() || !id.isTextual()) {
                    continue;
                }
                try {
                    names.put(UUID.fromString(id.asText()), name.asText());
                } catch (IllegalArgumentException ex) {
                    LOGGER.log(Level.FINE, "module returned a malformed player id in " + path, ex);
                }
            }
            return names;
        } catch (RuntimeException ex) {
            failed(path, ex);
            return Map.of();
        }
    }

    @Override
    public @NotNull NameLookup uuidOf(@NotNull String name) {
        String path = "/players/uuids";
        try {
            JsonNode body = post(path, Map.of("names", List.of(name)));
            if (body == null) {
                return new NameLookup.Unavailable();
            }
            JsonNode player = body.path("players").path(0);
            JsonNode id = player.path("id");
            if (id.isNull() || !id.isTextual()) {
                return new NameLookup.Unknown();
            }
            try {
                return new NameLookup.Resolved(UUID.fromString(id.asText()), player.path("name").asText(name));
            } catch (IllegalArgumentException ex) {
                failed(path, ex);
                return new NameLookup.Unavailable();
            }
        } catch (RuntimeException ex) {
            failed(path, ex);
            return new NameLookup.Unavailable();
        }
    }

    /**
     * URL-encodes a region id for use as a path segment. {@link URLEncoder} performs
     * form encoding, which turns a space into {@code +}; a path router does not decode
     * that back, and WorldGuard region ids may themselves contain a literal {@code +}
     * (already safely escaped to {@code %2B} by the encoder), so the space-to-plus
     * substitution has to be undone afterwards rather than left in place.
     */
    private static @NotNull String pathSegment(@NotNull String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override
    public @NotNull Status status() {
        try {
            return get("/health") == null ? Status.UNREACHABLE : Status.OK;
        } catch (RuntimeException ex) {
            failed("/health", ex);
            return Status.UNREACHABLE;
        }
    }

    private @Nullable JsonNode get(@NotNull String path) {
        return send(HttpRequest.newBuilder(URI.create(this.baseUrl + path)).GET(), path);
    }

    private @Nullable JsonNode post(@NotNull String path, @NotNull Object body) {
        try {
            String json = this.mapper.writeValueAsString(body);
            return send(HttpRequest.newBuilder(URI.create(this.baseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)), path);
        } catch (IOException ex) {
            return failed(path, ex);
        }
    }

    /** {@code null} on any failure, after recording it; a parsed body on 2xx. */
    private @Nullable JsonNode send(@NotNull HttpRequest.Builder request, @NotNull String path) {
        try {
            HttpResponse<String> response = this.http.send(
                    request.header(SECRET_HEADER, this.secret).timeout(this.timeout).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                // An unknown region or player is a normal answer, not a fault. Leaving the
                // reachability flag alone is the point: touching it here would re-arm the
                // WARNING on every real failure whenever unknown-region traffic is mixed in.
                return null;
            }
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return rejected(path, response.statusCode());
            }
            if (response.statusCode() / 100 != 2) {
                return failed(path, new IOException("HTTP " + response.statusCode()));
            }
            recovered();
            return this.mapper.readTree(response.body());
        } catch (IOException ex) {
            return failed(path, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failed(path, ex);
        }
    }

    private @Nullable JsonNode failed(@NotNull String path, @NotNull Exception ex) {
        if (this.reachable.compareAndSet(true, false)) {
            LOGGER.log(Level.WARNING, "query-service module unreachable at " + this.baseUrl + path
                    + "; responses degrade to null geometry and names until it returns", ex);
        }
        return null;
    }

    /**
     * A 401/403 is a configuration fault, not an outage: the module answered, it just
     * refused us. It degrades identically, but saying which it was saves an operator
     * hunting a network problem that is not there.
     */
    private @Nullable JsonNode rejected(@NotNull String path, int statusCode) {
        if (this.reachable.compareAndSet(true, false)) {
            LOGGER.warning("query-service module refused " + this.baseUrl + path + " with HTTP "
                    + statusCode + ": the shared secret does not match. REALTY_REST_MODULE_SECRET"
                    + " must equal the module's shared-secret. Responses degrade to null geometry"
                    + " and names until it does");
        }
        return null;
    }

    private void recovered() {
        if (this.reachable.compareAndSet(false, true)) {
            LOGGER.info("query-service module reachable again at " + this.baseUrl);
        }
    }
}
