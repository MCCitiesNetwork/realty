package io.github.md5sha256.realty.adapter.query;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.md5sha256.realty.adapter.query.json.ErrorResponse;
import io.github.md5sha256.realty.api.PlayerNameService;
import io.javalin.Javalin;
import io.javalin.config.RoutesConfig;
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
            "/regions/{worldId}/{regionId}/dimensions",
            "/regions/{worldId}/{regionId}/members",
            "/regions/{worldId}/dimensions",
            "/regions/{worldId}/at",
            "/players/{uuid}/name",
            "/players/names",
            "/players/uuids",
            "/resource-pack");

    /**
     * Cap on any batch route. Every entry can cost a main-thread hop and, in the worst case, a
     * Mojang round trip, so an unbounded list is a way to tie up the server from one request.
     */
    public static final int MAX_BATCH = 256;

    private static final Logger LOGGER = Logger.getLogger(QueryServiceServer.class.getName());
    private static final String HANDLED_ATTRIBUTE = "realty.handled";

    private final byte[] secret;
    private final Duration requestTimeout;
    private final RegionSource regions;
    private final PlayerNameService names;
    private final ResourcePackSource resourcePack;
    private final Javalin javalin;

    public QueryServiceServer(@NotNull String secret,
                              @NotNull Duration requestTimeout,
                              @NotNull RegionSource regions,
                              @NotNull PlayerNameService names,
                              @NotNull ResourcePackSource resourcePack) {
        if (secret.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.regions = Objects.requireNonNull(regions, "regions");
        this.names = Objects.requireNonNull(names, "names");
        this.resourcePack = Objects.requireNonNull(resourcePack, "resourcePack");
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // Javalin 7 removed routing methods (get, post, before, exception, error, ...) from
        // Javalin itself; they now live on config.routes inside create(), so registration can
        // no longer happen in a separate step after construction.
        this.javalin = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(objectMapper, false));
            config.startup.showJavalinBanner = false;
            registerRoutes(config.routes);
        });
    }

    private void registerRoutes(@NotNull RoutesConfig routes) {
        routes.before(ctx -> {
            String presented = ctx.header(SECRET_HEADER);
            if (presented == null || !MessageDigest.isEqual(
                    presented.getBytes(StandardCharsets.UTF_8), this.secret)) {
                throw ApiException.unauthorized();
            }
        });

        routes.get("/health", ctx -> ctx.json(Map.of("status", "ok")));

        DimensionsHandler dimensionsHandler = new DimensionsHandler(this.regions, this.requestTimeout);
        routes.get("/regions/{worldId}/{regionId}/dimensions", dimensionsHandler::handle);
        routes.post("/regions/{worldId}/dimensions", dimensionsHandler::batch);

        MembersHandler membersHandler = new MembersHandler(this.regions, this.requestTimeout);
        routes.get("/regions/{worldId}/{regionId}/members", membersHandler::handle);

        RegionsAtHandler regionsAtHandler = new RegionsAtHandler(this.regions, this.requestTimeout);
        routes.get("/regions/{worldId}/at", regionsAtHandler::handle);

        PlayerNamesHandler playerNames = new PlayerNamesHandler(this.names, this.requestTimeout);
        routes.get("/players/{uuid}/name", playerNames::single);
        routes.post("/players/names", playerNames::names);
        routes.post("/players/uuids", playerNames::uuids);

        // The URL only, never the pack itself: realty-rest and the browser learn where the
        // operator already hosts it, so Realty redistributes nothing.
        ResourcePackHandler resourcePackHandler = new ResourcePackHandler(this.resourcePack);
        routes.get("/resource-pack", resourcePackHandler::handle);

        routes.exception(ApiException.class, (ex, ctx) -> {
            ctx.attribute(HANDLED_ATTRIBUTE, true);
            ctx.status(ex.status()).json(new ErrorResponse(ex.code(), ex.getMessage()));
        });
        routes.exception(Exception.class, (ex, ctx) -> {
            ctx.attribute(HANDLED_ATTRIBUTE, true);
            LOGGER.log(Level.SEVERE, "Unhandled failure serving " + ctx.path(), ex);
            ctx.status(500).json(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
        });
        routes.error(404, ctx -> {
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
