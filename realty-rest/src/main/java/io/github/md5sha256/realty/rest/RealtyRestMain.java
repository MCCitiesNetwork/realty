package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.RealtyBackendImpl;
import io.github.md5sha256.realty.database.maria.MariaDatabase;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

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
                RealtyRestMain::formatIso,
                () -> 0L);

        RealtyRestServer server = new RealtyRestServer(backend, database, config.rest());
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }

    private static @NotNull String formatIso(@NotNull LocalDateTime dateTime) {
        return dateTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    }

}
