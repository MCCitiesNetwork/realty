package io.github.md5sha256.realty.rest.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.md5sha256.realty.rest.RestSettings;
import io.github.md5sha256.realty.rest.json.RegionResponse;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

class HttpModuleClientTest {

    private static HttpModuleClient client(int port, String secret, Duration timeout) {
        return new HttpModuleClient(URI.create("http://localhost:" + port), secret, timeout,
                HttpClient.newHttpClient(), new ObjectMapper());
    }

    @Test
    void readsDimensions() {
        JavalinTest.test(new FakeModule(0).app(), (server, http) -> {
            Optional<RegionResponse.Dimensions> dims =
                    client(server.port(), FakeModule.SECRET, Duration.ofSeconds(2))
                            .dimensions(FakeModule.WORLD, "downtown_plot_14");
            Assertions.assertTrue(dims.isPresent());
            Assertions.assertEquals("POLYGONAL", dims.get().shape());
            Assertions.assertEquals(62, dims.get().minY());
            Assertions.assertEquals(140, dims.get().maxY());
            Assertions.assertEquals(4, dims.get().points().size());
            Assertions.assertEquals(new RegionResponse.Point(104, -88), dims.get().points().get(0));
        });
    }

    @Test
    void anUnknownRegionIsEmptyNotAnError() {
        JavalinTest.test(new FakeModule(0).app(), (server, http) -> {
            Assertions.assertEquals(Optional.empty(),
                    client(server.port(), FakeModule.SECRET, Duration.ofSeconds(2))
                            .dimensions(FakeModule.WORLD, "plot_9"));
        });
    }

    @Test
    void resolvesNamesInOneBatchAndOmitsUnknowns() {
        FakeModule module = new FakeModule(0);
        UUID unknown = UUID.randomUUID();
        JavalinTest.test(module.app(), (server, http) -> {
            Map<UUID, String> names = client(server.port(), FakeModule.SECRET, Duration.ofSeconds(2))
                    .names(List.of(FakeModule.NOTCH, unknown, FakeModule.BEDROCK, FakeModule.NOTCH));
            Assertions.assertEquals(Map.of(FakeModule.NOTCH, "Notch", FakeModule.BEDROCK, ".Cool Guy 123"), names);
            Assertions.assertEquals(1, module.receivedBodies.size(), "one HTTP call for the whole batch");
        });
    }

    @Test
    void anEmptyBatchMakesNoCall() {
        FakeModule module = new FakeModule(0);
        JavalinTest.test(module.app(), (server, http) -> {
            Assertions.assertTrue(client(server.port(), FakeModule.SECRET, Duration.ofSeconds(2))
                    .names(List.of()).isEmpty());
            Assertions.assertTrue(module.receivedBodies.isEmpty());
        });
    }

    @Test
    void resolvesABedrockNameWithSpacesViaTheBody() {
        JavalinTest.test(new FakeModule(0).app(), (server, http) -> {
            NameLookup result = client(server.port(), FakeModule.SECRET, Duration.ofSeconds(2)).uuidOf(".Cool Guy 123");
            Assertions.assertEquals(new NameLookup.Resolved(FakeModule.BEDROCK, ".Cool Guy 123"), result);
        });
    }

    @Test
    void anUnknownNameIsUnknownNotUnavailable() {
        JavalinTest.test(new FakeModule(0).app(), (server, http) -> {
            Assertions.assertInstanceOf(NameLookup.Unknown.class,
                    client(server.port(), FakeModule.SECRET, Duration.ofSeconds(2)).uuidOf("nobody"));
        });
    }

    @Test
    void aWrongSecretDegradesEverythingAndSaysSo() {
        List<LogRecord> logged = new ArrayList<>();
        Handler handler = collectInto(logged);
        Logger logger = Logger.getLogger(HttpModuleClient.class.getName());
        logger.addHandler(handler);
        try {
            JavalinTest.test(new FakeModule(0).app(), (server, http) -> {
                HttpModuleClient client = client(server.port(), "wrong", Duration.ofSeconds(2));
                Assertions.assertEquals(Optional.empty(), client.dimensions(FakeModule.WORLD, "downtown_plot_14"));
                Assertions.assertTrue(client.names(List.of(FakeModule.NOTCH)).isEmpty());
                Assertions.assertInstanceOf(NameLookup.Unavailable.class, client.uuidOf("Notch"));
                Assertions.assertEquals(ModuleClient.Status.UNREACHABLE, client.status());
            });
        } finally {
            logger.removeHandler(handler);
        }
        String messages = logged.stream().map(LogRecord::getMessage).collect(Collectors.joining("\n"));
        Assertions.assertTrue(messages.contains("REALTY_REST_MODULE_SECRET") && messages.contains("shared secret"),
                "a 401 must be reported as a secret mismatch, not as unreachable, was:\n" + messages);
    }

    @Test
    void a404DoesNotRearmTheUnreachableWarning() {
        List<LogRecord> logged = new ArrayList<>();
        Handler handler = collectInto(logged);
        Logger logger = Logger.getLogger(HttpModuleClient.class.getName());
        logger.addHandler(handler);
        try {
            JavalinTest.test(alwaysFailingHealthApp(), (server, http) -> {
                HttpModuleClient client = client(server.port(), FakeModule.SECRET, Duration.ofSeconds(2));
                Assertions.assertEquals(ModuleClient.Status.UNREACHABLE, client.status());
                // An unknown region answers 404 -- a normal answer, not a recovery.
                Assertions.assertEquals(Optional.empty(), client.dimensions(FakeModule.WORLD, "plot_9"));
                Assertions.assertEquals(ModuleClient.Status.UNREACHABLE, client.status());
            });
        } finally {
            logger.removeHandler(handler);
        }
        long warnings = logged.stream().filter(record -> record.getLevel() == Level.WARNING).count();
        Assertions.assertEquals(1, warnings,
                "mixed unknown-region traffic must not re-arm the warning, saw " + warnings);
    }

    @Test
    void aSchemeLessBaseUrlDegradesInsteadOfThrowing() {
        HttpModuleClient client = new HttpModuleClient(URI.create("game-server:8123"), FakeModule.SECRET,
                Duration.ofMillis(200), HttpClient.newHttpClient(), new ObjectMapper());
        Assertions.assertEquals(Optional.empty(), client.dimensions(FakeModule.WORLD, "downtown_plot_14"));
        Assertions.assertTrue(client.names(List.of(FakeModule.NOTCH)).isEmpty());
        Assertions.assertInstanceOf(NameLookup.Unavailable.class, client.uuidOf("Notch"));
        Assertions.assertEquals(ModuleClient.Status.UNREACHABLE, client.status());
    }

    @Test
    void aSchemeLessConfiguredUrlDisablesTheClient() {
        RestSettings settings = new RestSettings("0.0.0.0", 8080, 100, List.of(),
                "game-server:8123", FakeModule.SECRET, 1500, 0, null);
        Assertions.assertEquals(ModuleClient.Status.DISABLED, HttpModuleClient.from(settings).status());
    }

    @Test
    void aHostLessConfiguredUrlDisablesTheClient() {
        RestSettings settings = new RestSettings("0.0.0.0", 8080, 100, List.of(),
                "http:///dimensions", FakeModule.SECRET, 1500, 0, null);
        Assertions.assertEquals(ModuleClient.Status.DISABLED, HttpModuleClient.from(settings).status());
    }

    /** {@code /health} always 500s; every other route 404s. */
    private static Javalin alwaysFailingHealthApp() {
        return Javalin.create(config -> {
            config.startup.showJavalinBanner = false;
            config.routes.get("/health", ctx -> ctx.status(500).result("{}"));
        });
    }

    private static Handler collectInto(List<LogRecord> sink) {
        return new Handler() {
            @Override
            public void publish(LogRecord record) {
                sink.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
    }

    @Test
    void aStalledModuleTimesOutWithinTheBudget() {
        JavalinTest.test(new FakeModule(3_000).app(), (server, http) -> {
            HttpModuleClient client = client(server.port(), FakeModule.SECRET, Duration.ofMillis(200));
            long started = System.nanoTime();
            Assertions.assertEquals(Optional.empty(), client.dimensions(FakeModule.WORLD, "downtown_plot_14"));
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            Assertions.assertTrue(elapsedMs < 2_000, "took " + elapsedMs + "ms");
        });
    }

    @Test
    void anUnreachableHostIsUnreachable() {
        HttpModuleClient client = client(1, FakeModule.SECRET, Duration.ofMillis(500));
        Assertions.assertEquals(ModuleClient.Status.UNREACHABLE, client.status());
        Assertions.assertInstanceOf(NameLookup.Unavailable.class, client.uuidOf("Notch"));
    }

    @Test
    void healthyModuleReportsOk() {
        JavalinTest.test(new FakeModule(0).app(), (server, http) -> {
            Assertions.assertEquals(ModuleClient.Status.OK,
                    client(server.port(), FakeModule.SECRET, Duration.ofSeconds(2)).status());
        });
    }

    @Test
    void aMalformedPlayerIdIsSkippedNotThrown() {
        JavalinTest.test(FakeModule.malformedIdApp(), (server, http) -> {
            HttpModuleClient client = client(server.port(), FakeModule.SECRET, Duration.ofSeconds(2));
            Assertions.assertTrue(client.names(List.of(FakeModule.NOTCH)).isEmpty());
            Assertions.assertInstanceOf(NameLookup.Unavailable.class, client.uuidOf("Notch"));
        });
    }

    @Test
    void aRegionIdWithAPlusIsEncodedAsALiteralPlus() {
        JavalinTest.test(new FakeModule(0).app(), (server, http) -> {
            Optional<RegionResponse.Dimensions> dims =
                    client(server.port(), FakeModule.SECRET, Duration.ofSeconds(2))
                            .dimensions(FakeModule.WORLD, "plot+1");
            Assertions.assertTrue(dims.isPresent());
        });
    }
}
