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
     * Every path this server registers. The OpenAPI conformance test asserts this
     * matches the document exactly, in both directions, so a new route must be
     * added here and to openapi.yaml together.
     */
    public static final List<String> ROUTES = List.of(
            "/v1/health",
            "/v1/worlds"
    );

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

        // Task 7 registers /v1/regions here.
        // Task 8 registers /v1/players/regions here.

        this.javalin.exception(ApiException.class, (ex, ctx) ->
                ctx.status(ex.status()).json(new ErrorResponse(ex.code(), ex.getMessage())));

        this.javalin.exception(Exception.class, (ex, ctx) -> {
            LOGGER.log(Level.SEVERE, "Unhandled failure serving " + ctx.path(), ex);
            ctx.status(500).json(new ErrorResponse("INTERNAL_ERROR",
                    "An unexpected error occurred"));
        });

        this.javalin.error(404, ctx ->
                ctx.json(new ErrorResponse("NOT_FOUND", "No such endpoint: " + ctx.path())));
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
