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

    /** Builds the client the settings describe, or {@link ModuleClient#disabled()} when no URL is set. */
    public static @NotNull ModuleClient from(@NotNull RestSettings settings) {
        String url = settings.moduleUrl();
        String secret = settings.moduleSecret();
        if (url == null || url.isBlank() || secret == null || secret.isBlank()) {
            return ModuleClient.disabled();
        }
        Duration timeout = Duration.ofMillis(settings.moduleTimeoutMs());
        HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).build();
        return new HttpModuleClient(URI.create(url.endsWith("/") ? url.substring(0, url.length() - 1) : url),
                secret, timeout, http, new ObjectMapper());
    }

    @Override
    public @NotNull Optional<RegionResponse.Dimensions> dimensions(@NotNull UUID worldId,
                                                                   @NotNull String regionId) {
        String path = "/regions/" + worldId + "/" + URLEncoder.encode(regionId, StandardCharsets.UTF_8) + "/dimensions";
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
    }

    @Override
    public @NotNull Map<UUID, String> names(@NotNull Collection<UUID> ids) {
        List<UUID> distinct = new ArrayList<>(new LinkedHashSet<>(ids));
        if (distinct.isEmpty()) {
            return Map.of();
        }
        if (distinct.size() > MAX_BATCH) {
            distinct = distinct.subList(0, MAX_BATCH);
        }
        JsonNode body = post("/players/names", Map.of("ids", distinct.stream().map(UUID::toString).toList()));
        Map<UUID, String> names = new LinkedHashMap<>();
        if (body == null) {
            return names;
        }
        for (JsonNode player : body.path("players")) {
            JsonNode name = player.path("name");
            if (!name.isNull() && name.isTextual()) {
                names.put(UUID.fromString(player.path("id").asText()), name.asText());
            }
        }
        return names;
    }

    @Override
    public @NotNull NameLookup uuidOf(@NotNull String name) {
        JsonNode body = post("/players/uuids", Map.of("names", List.of(name)));
        if (body == null) {
            return new NameLookup.Unavailable();
        }
        JsonNode player = body.path("players").path(0);
        JsonNode id = player.path("id");
        if (id.isNull() || !id.isTextual()) {
            return new NameLookup.Unknown();
        }
        return new NameLookup.Resolved(UUID.fromString(id.asText()), player.path("name").asText(name));
    }

    @Override
    public @NotNull Status status() {
        return get("/health") == null ? Status.UNREACHABLE : Status.OK;
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
                recovered();
                return null;
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

    private void recovered() {
        if (this.reachable.compareAndSet(false, true)) {
            LOGGER.info("query-service module reachable again at " + this.baseUrl);
        }
    }
}
