package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.RealtyBackendImpl;
import io.github.md5sha256.realty.database.maria.MariaDatabase;
import io.github.md5sha256.realty.rest.module.HttpModuleClient;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.javalin.http.staticfiles.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class RealtyRestMain {

    private static final Logger LOGGER = Logger.getLogger(RealtyRestMain.class.getName());

    private RealtyRestMain() {
    }

    public static void main(@NotNull String[] args) {
        run(null);
    }

    /**
     * Starts the service.
     *
     * <p>Shared with the bundled {@code realty-web-dist} build rather than copied into
     * it: the two deployments differ only in where the front end comes from, and two
     * startup sequences that could drift apart is exactly the failure bundling is meant
     * to avoid.</p>
     *
     * @param staticSiteOverride the front end the bundled build serves from its own
     *                           classpath, or {@code null} to honour
     *                           {@code REALTY_REST_WEB_ROOT} — which is itself usually
     *                           unset, leaving a pure API
     */
    public static void run(@Nullable StaticSite staticSiteOverride) {
        RestConfiguration config;
        try {
            config = RestConfiguration.load(System::getenv);
        } catch (IllegalStateException ex) {
            LOGGER.severe(ex.getMessage());
            System.exit(1);
            return;
        }

        LOGGER.info("Resolved configuration:\n" + config.describeRedacted());

        try {
            SchemaVersionCheck.verify(SchemaVersionCheck.readAppliedVersion(config.database()));
        } catch (SQLException | IllegalStateException ex) {
            LOGGER.severe(ex.getMessage());
            System.exit(1);
            return;
        }

        Database database = new MariaDatabase(config.database(), LOGGER);
        RealtyBackend backend = new RealtyBackendImpl(
                database,
                uuid -> CompletableFuture.completedFuture(uuid.toString()),
                IsoDates::format,
                () -> 0L);

        // from(...) logs what it chose. Asking the client here instead would mean a
        // blocking probe of the module before the port is even bound.
        ModuleClient moduleClient = HttpModuleClient.from(config.rest());

        StaticSite staticSite = staticSiteOverride != null
                ? staticSiteOverride
                : webRootFrom(config);
        if (staticSite != null) {
            LOGGER.info("Serving a front end from " + staticSite.location() + " "
                    + staticSite.directory());
        }

        RealtyRestServer server = new RealtyRestServer(backend, database, config.rest(),
                moduleClient, staticSite);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }

    /** A configured web root turns the standalone service into one that also serves a front end. */
    private static @Nullable StaticSite webRootFrom(@NotNull RestConfiguration config) {
        String webRoot = config.rest().webRoot();
        return webRoot == null ? null : new StaticSite(webRoot, Location.EXTERNAL);
    }

}
