package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.RealtyBackendImpl;
import io.github.md5sha256.realty.database.maria.MariaDatabase;
import io.github.md5sha256.realty.rest.module.HttpModuleClient;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import io.javalin.http.staticfiles.Location;

public final class RealtyRestMain {

    private static final Logger LOGGER = Logger.getLogger(RealtyRestMain.class.getName());

    private RealtyRestMain() {
    }

    public static void main(@NotNull String[] args) {
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

        // A configured web root turns this into the same service the bundled
        // realty-web-dist build runs; unset, it stays a pure API.
        StaticSite staticSite = config.rest().webRoot() == null
                ? null
                : new StaticSite(config.rest().webRoot(), Location.EXTERNAL);
        RealtyRestServer server = new RealtyRestServer(backend, database, config.rest(),
                moduleClient, staticSite);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }

}
