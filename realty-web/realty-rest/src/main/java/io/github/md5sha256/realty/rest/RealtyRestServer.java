package io.github.md5sha256.realty.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.rest.json.ErrorResponse;
import io.github.md5sha256.realty.rest.json.HealthResponse;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.javalin.Javalin;
import io.javalin.config.RoutesConfig;
import io.javalin.json.JavalinJackson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The HTTP surface. Read-only: every handler calls query methods only.
 */
public final class RealtyRestServer {

    private static final Logger LOGGER = Logger.getLogger(RealtyRestServer.class.getName());

    /**
     * Prefix of the line logged once the port is bound.
     *
     * <p>Process supervisors match this text to decide the service has started; the
     * Pterodactyl egg's {@code config.startup.done} carries exactly this string, and
     * {@code PterodactylEggTest} asserts the two stay in step. Changing it here without
     * changing the egg leaves a panel waiting forever on a server that is already up,
     * so the test fails the build instead.</p>
     *
     * <p>Deliberately prefixed with the service's own name. Javalin logs its own
     * {@code "Listening on http://..."} line at the same moment, and a marker that
     * matched both would be ambiguous — and would silently start matching a
     * third-party string that a Javalin upgrade is free to reword.</p>
     */
    public static final String LISTENING_LOG_PREFIX = "Realty REST listening on http://";

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
            "/v1/region",
            "/v1/region/history",
            "/v1/regions",
            "/v1/regions/search",
            "/v1/regions/at",
            "/v1/region/members",
            "/v1/region/schematic",
            "/v1/resource-pack",
            "/v1/worlds/geometry",
            "/v1/tags",
            "/v1/stats",
            "/v1/leaderboard/owners",
            "/v1/auctions",
            "/v1/activity",
            "/v1/players/regions",
            "/v1/players/lookup",
            "/v1/players/summary",
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
    private final ModuleClient moduleClient;
    private final @Nullable StaticSite staticSite;

    public RealtyRestServer(@NotNull RealtyBackend backend,
                            @NotNull Database database,
                            @NotNull RestSettings settings) {
        this(backend, database, settings, ModuleClient.disabled());
    }

    public RealtyRestServer(@NotNull RealtyBackend backend,
                            @NotNull Database database,
                            @NotNull RestSettings settings,
                            @NotNull ModuleClient moduleClient) {
        this(backend, database, settings, moduleClient, null);
    }

    /**
     * @param staticSite a built front end to serve alongside the API, or {@code null}
     *                   for a pure API -- which is what the standalone service passes
     */
    public RealtyRestServer(@NotNull RealtyBackend backend,
                            @NotNull Database database,
                            @NotNull RestSettings settings,
                            @NotNull ModuleClient moduleClient,
                            @Nullable StaticSite staticSite) {
        this.backend = backend;
        this.database = database;
        this.settings = settings;
        this.moduleClient = moduleClient;
        this.staticSite = staticSite;
        this.worldLookup = new WorldLookup(database);
        this.javalin = buildJavalin();
    }

    /**
     * Builds and configures the instance in one pass. Javalin 7 removed routing methods
     * ({@code get}, {@code post}, {@code exception}, {@code error}, ...) from {@link Javalin}
     * itself; they now live on {@code config.routes} inside {@link Javalin#create(java.util.function.Consumer)},
     * so registration can no longer happen in a separate step after construction.
     */
    private @NotNull Javalin buildJavalin() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper, false));
            config.startup.showJavalinBanner = false;
            // Browser front ends live on a different origin to this service, so
            // without an allowlist every request from one fails at the preflight.
            // Enabled only when the operator names origins: no wildcard default,
            // and no CORS machinery at all for a deployment that has no browser
            // client.
            if (!this.settings.corsOrigins().isEmpty()) {
                config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> {
                    for (String origin : this.settings.corsOrigins()) {
                        rule.allowHost(origin);
                    }
                }));
            }
            if (this.staticSite != null) {
                config.staticFiles.add(staticFile -> {
                    staticFile.directory = this.staticSite.directory();
                    staticFile.location = this.staticSite.location();
                    // Without this, the spaRoot below answers /v1/nope with index.html
                    // and a 200 -- correct-looking in a browser, wrong for every client
                    // that checks status codes.
                    staticFile.skipFileFunction =
                            request -> request.getRequestURI().startsWith("/v1");
                });
                config.spaRoot.addFile("/", this.staticSite.directory() + "/index.html",
                        this.staticSite.location());
            }
            registerRoutes(config.routes);
        });
    }

    private void registerRoutes(@NotNull RoutesConfig routes) {
        routes.get("/v1/health", ctx -> {
            if (databaseReachable()) {
                ctx.status(200).json(new HealthResponse("ok",
                        this.moduleClient.status().name().toLowerCase(Locale.ROOT)));
            } else {
                ctx.status(503).json(Map.of("status", "degraded"));
            }
        });

        routes.get("/v1/worlds", ctx -> ctx.json(this.worldLookup.all()));

        RegionHandler regionHandler =
                new RegionHandler(this.backend, this.database, this.worldLookup, this.moduleClient);
        routes.get("/v1/region", regionHandler::handle);

        RegionListHandler regionListHandler =
                new RegionListHandler(this.database, this.worldLookup, this.settings);
        routes.get("/v1/regions", regionListHandler::handle);

        SearchHandler searchHandler = new SearchHandler(this.database, this.worldLookup, this.settings);
        routes.get("/v1/regions/search", searchHandler::handle);

        PlayerRegionsHandler playerRegionsHandler = new PlayerRegionsHandler(
                this.backend, this.database, this.worldLookup, this.settings, this.moduleClient);
        routes.get("/v1/players/regions", playerRegionsHandler::handle);

        PlayerLookupHandler playerLookupHandler = new PlayerLookupHandler(this.moduleClient);
        routes.get("/v1/players/lookup", playerLookupHandler::handle);

        PlayerSummaryHandler playerSummaryHandler =
                new PlayerSummaryHandler(this.backend, this.moduleClient);
        routes.get("/v1/players/summary", playerSummaryHandler::handle);

        RegionHistoryHandler regionHistoryHandler = new RegionHistoryHandler(
                this.backend, this.worldLookup, this.settings, this.moduleClient);
        routes.get("/v1/region/history", regionHistoryHandler::handle);

        OwnersLeaderboardHandler ownersLeaderboardHandler =
                new OwnersLeaderboardHandler(this.database, this.settings, this.moduleClient);
        routes.get("/v1/leaderboard/owners", ownersLeaderboardHandler::handle);

        AuctionsHandler auctionsHandler = new AuctionsHandler(
                this.database, this.worldLookup, this.settings, this.moduleClient);
        routes.get("/v1/auctions", auctionsHandler::handle);

        ActivityHandler activityHandler = new ActivityHandler(
                this.database, this.worldLookup, this.settings, this.moduleClient);
        routes.get("/v1/activity", activityHandler::handle);

        RegionsAtHandler regionsAtHandler =
                new RegionsAtHandler(this.database, this.worldLookup, this.moduleClient);
        routes.get("/v1/regions/at", regionsAtHandler::handle);

        RegionMembersHandler regionMembersHandler =
                new RegionMembersHandler(this.database, this.worldLookup, this.moduleClient);
        routes.get("/v1/region/members", regionMembersHandler::handle);

        ResourcePackHandler resourcePackHandler = new ResourcePackHandler(this.moduleClient);
        routes.get("/v1/resource-pack", resourcePackHandler::handle);

        RegionSchematicHandler regionSchematicHandler =
                new RegionSchematicHandler(this.backend, this.worldLookup);
        routes.get("/v1/region/schematic", regionSchematicHandler::handle);

        WorldGeometryHandler worldGeometryHandler = new WorldGeometryHandler(
                this.database, this.worldLookup, this.settings, this.moduleClient);
        routes.get("/v1/worlds/geometry", worldGeometryHandler::handle);

        StatsHandler statsHandler = new StatsHandler(this.backend);
        routes.get("/v1/stats", statsHandler::handle);

        TagsHandler tagsHandler = new TagsHandler(this.backend);
        routes.get("/v1/tags", tagsHandler::handle);

        routes.get("/v1/openapi.yaml", ctx -> ctx.contentType("application/yaml")
                .result(OpenApiRoutes.rawDocument()));

        routes.get("/v1/openapi.json", ctx -> ctx.json(OpenApiRoutes.asParsedTree()));

        routes.get("/v1/docs", ctx -> ctx.contentType("text/html").result(SWAGGER_UI_PAGE));

        routes.exception(ApiException.class, (ex, ctx) -> {
            ctx.attribute(HANDLED_ATTRIBUTE, true);
            ctx.status(ex.status()).json(new ErrorResponse(ex.code(), ex.getMessage()));
        });

        routes.exception(Exception.class, (ex, ctx) -> {
            ctx.attribute(HANDLED_ATTRIBUTE, true);
            LOGGER.log(Level.SEVERE, "Unhandled failure serving " + ctx.path(), ex);
            ctx.status(500).json(new ErrorResponse("INTERNAL_ERROR",
                    "An unexpected error occurred"));
        });

        if (this.staticSite != null) {
            // Registered last, and only when a front end is served: spaRoot does not
            // consult staticFiles' skipFileFunction, so without a real route claiming
            // the path an API client asking for a bad endpoint would get index.html
            // and a 200. Setting the status only lets the error(404) handler below
            // write the same JSON body every other unmatched path gets.
            //
            // HEAD as well as GET. Serving a front end otherwise makes HEAD /v1/nope
            // answer 200 while GET answers 404 -- the same bug, reached by the method
            // a client uses precisely when it wants the status and not the body.
            routes.get("/v1/*", ctx -> ctx.status(404));
            routes.head("/v1/*", ctx -> ctx.status(404));
        }

        // Javalin's error() callback fires for every response with a matching status,
        // including one an exception handler above already wrote a body for -- not just
        // truly unmatched routes. The attribute distinguishes the two so a handled
        // ApiException's body is not clobbered with a generic "no such endpoint" message.
        routes.error(404, ctx -> {
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

    public @NotNull ModuleClient moduleClient() {
        return this.moduleClient;
    }

    @NotNull WorldLookup worldLookup() {
        return this.worldLookup;
    }

    /**
     * Binds the configured host and port, then logs a single line naming the bound
     * address.
     *
     * <p>That line is the marker process supervisors watch for to decide the service
     * is up -- the Pterodactyl egg's {@code config.startup.done} matches it. It is
     * logged after {@link Javalin#start(String, int)} returns, so it appears only once
     * the port is genuinely accepting connections, unlike the configuration banner
     * logged earlier during startup.</p>
     */
    public void start() {
        this.javalin.start(this.settings.host(), this.settings.port());
        LOGGER.info(LISTENING_LOG_PREFIX + this.settings.host() + ":"
                + this.settings.port() + "/");
    }

    public void stop() {
        this.javalin.stop();
    }

}
