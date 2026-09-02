package io.github.md5sha256.realty.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.rest.json.ErrorResponse;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The HTTP surface. Read-only: every handler calls query methods only.
 */
public final class RealtyRestServer {

    private static final Logger LOGGER = Logger.getLogger(RealtyRestServer.class.getName());

    /**
     * Context attribute set by the exception handlers below to mark a response as
     * already carrying a deliberate error body, so the catch-all 404 handler knows
     * not to overwrite it. See its comment for why this is needed at all.
     */
    private static final String HANDLED_ATTRIBUTE = "realty-rest.error-handled";

    /**
     * Every path this server registers. The OpenAPI conformance test asserts this
     * matches the document exactly, in both directions, so a new route must be
     * added here and to openapi.yaml together.
     */
    public static final List<String> ROUTES = List.of(
            "/v1/health",
            "/v1/worlds",
            "/v1/regions",
            "/v1/players/regions",
            "/v1/openapi.yaml",
            "/v1/openapi.json",
            "/v1/docs"
    );

    private static final String SWAGGER_UI_PAGE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>Realty REST API</title>
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui.css">
            </head>
            <body>
                <div id="swagger-ui"></div>
                <script src="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
                <script>
                    window.onload = function () {
                        window.ui = SwaggerUIBundle({
                            url: "/v1/openapi.yaml",
                            dom_id: "#swagger-ui"
                        });
                    };
                </script>
            </body>
            </html>
            """;

    private final RealtyBackend backend;
    private final Database database;
    private final RestSettings settings;
    private final Javalin javalin;
    private final WorldLookup worldLookup;

    public RealtyRestServer(@NotNull RealtyBackend backend,
                            @NotNull Database database,
                            @NotNull RestSettings settings) {
        this.backend = backend;
        this.database = database;
        this.settings = settings;
        this.worldLookup = new WorldLookup(database);
        this.javalin = buildJavalin();
        registerRoutes();
    }

    private static @NotNull Javalin buildJavalin() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper, false));
            config.showJavalinBanner = false;
        });
    }

    private void registerRoutes() {
        this.javalin.get("/v1/health", ctx -> {
            if (databaseReachable()) {
                ctx.status(200).json(Map.of("status", "ok"));
            } else {
                ctx.status(503).json(Map.of("status", "degraded"));
            }
        });

        this.javalin.get("/v1/worlds", ctx -> ctx.json(this.worldLookup.all()));

        RegionHandler regionHandler = new RegionHandler(this.backend, this.database, this.worldLookup);
        this.javalin.get("/v1/regions", regionHandler::handle);

        PlayerRegionsHandler playerRegionsHandler =
                new PlayerRegionsHandler(this.backend, this.database, this.worldLookup, this.settings);
        this.javalin.get("/v1/players/regions", playerRegionsHandler::handle);

        this.javalin.get("/v1/openapi.yaml", ctx -> ctx.contentType("application/yaml")
                .result(OpenApiRoutes.rawDocument()));

        this.javalin.get("/v1/openapi.json", ctx -> ctx.json(OpenApiRoutes.asParsedTree()));

        this.javalin.get("/v1/docs", ctx -> ctx.contentType("text/html").result(SWAGGER_UI_PAGE));

        this.javalin.exception(ApiException.class, (ex, ctx) -> {
            ctx.attribute(HANDLED_ATTRIBUTE, true);
            ctx.status(ex.status()).json(new ErrorResponse(ex.code(), ex.getMessage()));
        });

        this.javalin.exception(Exception.class, (ex, ctx) -> {
            ctx.attribute(HANDLED_ATTRIBUTE, true);
            LOGGER.log(Level.SEVERE, "Unhandled failure serving " + ctx.path(), ex);
            ctx.status(500).json(new ErrorResponse("INTERNAL_ERROR",
                    "An unexpected error occurred"));
        });

        // Javalin's error() callback fires for every response with a matching status,
        // including one an exception handler above already wrote a body for -- not just
        // truly unmatched routes. The attribute distinguishes the two so a handled
        // ApiException's body is not clobbered with a generic "no such endpoint" message.
        this.javalin.error(404, ctx -> {
            if (ctx.attribute(HANDLED_ATTRIBUTE) == null) {
                ctx.json(new ErrorResponse("NOT_FOUND", "No such endpoint: " + ctx.path()));
            }
        });
    }

    private boolean databaseReachable() {
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            session.realtyWorldMapper().selectAll();
            return true;
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Database health check failed", ex);
            return false;
        }
    }

    public @NotNull Javalin javalin() {
        return this.javalin;
    }

    public @NotNull RealtyBackend backend() {
        return this.backend;
    }

    public @NotNull RestSettings settings() {
        return this.settings;
    }

    public @NotNull Database database() {
        return this.database;
    }

    @NotNull WorldLookup worldLookup() {
        return this.worldLookup;
    }

    public void start() {
        this.javalin.start(this.settings.host(), this.settings.port());
    }

    public void stop() {
        this.javalin.stop();
    }

}
