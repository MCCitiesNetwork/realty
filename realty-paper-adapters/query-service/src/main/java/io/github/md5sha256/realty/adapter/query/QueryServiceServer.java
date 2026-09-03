package io.github.md5sha256.realty.adapter.query;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.md5sha256.realty.adapter.query.json.ErrorResponse;
import io.github.md5sha256.realty.api.PlayerNameService;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The module's private HTTP endpoint. Every route, including unknown ones, requires the shared
 * secret; this is a seam between two of our own services, not a public API, so the routes are
 * unversioned.
 */
public final class QueryServiceServer {

    public static final String SECRET_HEADER = "X-Realty-Secret";
    /** Every registered path, for documentation and for tests that pin the surface. */
    public static final List<String> ROUTES = List.of(
            "/health",
            "/regions/{worldId}/{regionId}/dimensions");

    private static final Logger LOGGER = Logger.getLogger(QueryServiceServer.class.getName());
    private static final String HANDLED_ATTRIBUTE = "realty.handled";

    private final byte[] secret;
    private final Duration requestTimeout;
    private final RegionDimensionsSource dimensions;
    private final PlayerNameService names;
    private final Javalin javalin;

    public QueryServiceServer(@NotNull String secret,
                              @NotNull Duration requestTimeout,
                              @NotNull RegionDimensionsSource dimensions,
                              @NotNull PlayerNameService names) {
        if (secret.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.dimensions = Objects.requireNonNull(dimensions, "dimensions");
        this.names = Objects.requireNonNull(names, "names");
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.javalin = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(objectMapper, false));
            config.showJavalinBanner = false;
        });
        registerRoutes();
    }

    private void registerRoutes() {
        this.javalin.before(ctx -> {
            String presented = ctx.header(SECRET_HEADER);
            if (presented == null || !MessageDigest.isEqual(
                    presented.getBytes(StandardCharsets.UTF_8), this.secret)) {
                throw ApiException.unauthorized();
            }
        });

        this.javalin.get("/health", ctx -> ctx.json(Map.of("status", "ok")));

        DimensionsHandler dimensionsHandler = new DimensionsHandler(this.dimensions, this.requestTimeout);
        this.javalin.get("/regions/{worldId}/{regionId}/dimensions", dimensionsHandler::handle);

        this.javalin.exception(ApiException.class, (ex, ctx) -> {
            ctx.attribute(HANDLED_ATTRIBUTE, true);
            ctx.status(ex.status()).json(new ErrorResponse(ex.code(), ex.getMessage()));
        });
        this.javalin.exception(Exception.class, (ex, ctx) -> {
            ctx.attribute(HANDLED_ATTRIBUTE, true);
            LOGGER.log(Level.SEVERE, "Unhandled failure serving " + ctx.path(), ex);
            ctx.status(500).json(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
        });
        this.javalin.error(404, ctx -> {
            if (ctx.attribute(HANDLED_ATTRIBUTE) == null) {
                ctx.json(new ErrorResponse("NOT_FOUND", "No such endpoint: " + ctx.path()));
            }
        });
    }

    public @NotNull Javalin javalin() {
        return this.javalin;
    }

    public void start(@NotNull String host, int port) {
        this.javalin.start(host, port);
    }

    /** Blocks until Jetty has stopped, so no in-flight request outlives the module. */
    public void stop() {
        this.javalin.stop();
    }
}
