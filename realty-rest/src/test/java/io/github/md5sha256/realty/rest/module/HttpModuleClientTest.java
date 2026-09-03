package io.github.md5sha256.realty.rest.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.md5sha256.realty.rest.json.RegionResponse;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    void aWrongSecretDegradesEverything() {
        JavalinTest.test(new FakeModule(0).app(), (server, http) -> {
            HttpModuleClient client = client(server.port(), "wrong", Duration.ofSeconds(2));
            Assertions.assertEquals(Optional.empty(), client.dimensions(FakeModule.WORLD, "downtown_plot_14"));
            Assertions.assertTrue(client.names(List.of(FakeModule.NOTCH)).isEmpty());
            Assertions.assertInstanceOf(NameLookup.Unavailable.class, client.uuidOf("Notch"));
            Assertions.assertEquals(ModuleClient.Status.UNREACHABLE, client.status());
        });
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
}
