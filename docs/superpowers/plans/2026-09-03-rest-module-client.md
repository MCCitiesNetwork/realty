# REST Module Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `realty-rest` call the in-server `query-service` module so region responses carry live `dimensions`, every `PlayerRef` carries a `name`, and `?player=<name>` resolves — degrading to nulls whenever the module is unreachable.

**Architecture:** A `ModuleClient` interface (one method per module route, plus a status probe) with an HTTP implementation over the JDK `HttpClient` and a no-op `disabled()` implementation. Handlers receive the client and apply enrichment as a final step after the backend returns, one batch name call per request. Nothing is cached.

**Tech Stack:** Java 25, Javalin 6.4.0 (existing), Jackson 2.18.2 (existing), `java.net.http.HttpClient`, `javalin-testtools` for a fake module in tests.

**Spec:** `docs/superpowers/specs/2026-09-02-realty-rest-api-design.md` (sections *Degradation*, *API surface*, *Startup sequence*, *Shipping order* item 3). Module wire contract: `docs/superpowers/specs/2026-09-02-realty-query-service-design.md` (*HTTP surface*) as implemented in `realty-paper-adapters/query-service`.

## Global Constraints

- No wildcard or static imports (`Assertions.assertEquals(...)`). Never fully-qualify a class inline; import it.
- Prefer constructor injection; no static service locators.
- **A request never fails because the game server is offline.** Unreachable module ⇒ `dimensions` null and every `name` null, `200`. The single exception: `?player=` given as a *name* ⇒ `502 NAME_LOOKUP_UNAVAILABLE`.
- Module settings: `REALTY_REST_MODULE_URL` (unset ⇒ enrichment disabled), `REALTY_REST_MODULE_SECRET` (sent as `X-Realty-Secret`), `REALTY_REST_MODULE_TIMEOUT_MS` (default `1500`, per call). Already parsed into `RestSettings.moduleUrl/moduleSecret/moduleTimeoutMs`.
- Module routes (all need the secret header; JSON): `GET /health` → `{"status":"ok"}`; `GET /regions/{worldId}/{regionId}/dimensions` → `{"shape","minY","maxY","points":[{"x","z"}]}` or `404`; `POST /players/names` `{"ids":[…]}` → `{"players":[{"id","name"}]}` (null name = unknown, max 256); `POST /players/uuids` `{"names":[…]}` → `{"players":[{"id","name"}]}` (null id = unknown).
- `/v1/health`: module unreachable is **degraded, not unhealthy** — `200`; `503` only when the database is unreachable.
- `openapi.yaml` is the source of truth and `OpenApiConformanceTest` must stay green; response-shape changes are documented there.
- Commit messages carry no `Co-Authored-By` email trailer and no `Claude-Session` line. Stage explicit paths only; `settings.gradle.kts`, `compose.yml` (edited only in Task 5, then staged deliberately — see that task) and `CLAUDE.md` carry the user's uncommitted state.
- Verification command for this plan: `./gradlew :realty-rest:test` — note `PterodactylEggTest.bothSecretsAreHiddenFromPanelViewers` fails on `main` today and is out of scope; every other test must pass. Run with `--tests` filters while iterating.

---

## File Structure

New package `io.github.md5sha256.realty.rest.module` in `realty-rest`:

| File | Responsibility |
|---|---|
| `ModuleClient.java` | Interface + `disabled()` factory + nested `Status` enum |
| `NameLookup.java` | Sealed result of a name→UUID lookup: `Resolved`, `Unknown`, `Unavailable` |
| `HttpModuleClient.java` | JDK `HttpClient` implementation; every failure ⇒ empty/Unavailable, logged once per status transition |
| `PlayerNames.java` | One-batch enrichment helper: `Set<UUID>` → `Map<UUID,String>`, `ref(id, names)` |

Modified:

| File | Change |
|---|---|
| `json/RegionResponse.java` | `Object dimensions` → typed `Dimensions` record |
| `RealtyRestServer.java` | Takes a `ModuleClient`; health reports `module`; handlers receive the client |
| `RealtyRestMain.java` | Builds `HttpModuleClient.from(settings)` or `disabled()` |
| `RestConfiguration.java` | Warns when URL is set without a secret (then disabled) |
| `RegionHandler.java` | dimensions + names enrichment |
| `PlayerRegionsHandler.java` | name→UUID via client; names enrichment |
| `src/main/resources/openapi.yaml` | `Dimensions` schema, health `module` field, `PLAYER_NOT_FOUND` |
| `compose.yml`, `compose.local.yml`, `realty-rest/README.md` | module variables |
| tests: `TestServers.java` (+ `withModule(...)` factories), `ModuleClientDisabledTest`, `HttpModuleClientTest`, `FakeModule.java`, additions to `HealthEndpointTest`, `RegionEndpointTest`, `PlayerRegionsEndpointTest`, `RestConfigurationTest` |

---

### Task 1: `ModuleClient` contract, `NameLookup`, typed `Dimensions`, and the disabled client

**Files:**
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/module/ModuleClient.java`
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/module/NameLookup.java`
- Modify: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/json/RegionResponse.java` (line 22 and the class Javadoc)
- Test: `realty-rest/src/test/java/io/github/md5sha256/realty/rest/module/ModuleClientDisabledTest.java`

**Interfaces:**
- Produces: `ModuleClient` with `Optional<RegionResponse.Dimensions> dimensions(UUID worldId, String regionId)`, `Map<UUID, String> names(Collection<UUID> ids)` (only resolved ids present), `NameLookup uuidOf(String name)`, `Status status()` (`OK`, `UNREACHABLE`, `DISABLED`), `static ModuleClient disabled()`.
- Produces: `NameLookup` sealed: `record Resolved(UUID id, String name)`, `record Unknown()`, `record Unavailable()`.
- Produces: `RegionResponse.Dimensions(String shape, int minY, int maxY, List<Point> points)`, `RegionResponse.Point(int x, int z)`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.md5sha256.realty.rest.module;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

class ModuleClientDisabledTest {

    @Test
    void everyCallDegradesWithoutTouchingTheNetwork() {
        ModuleClient client = ModuleClient.disabled();
        Assertions.assertEquals(Optional.empty(), client.dimensions(UUID.randomUUID(), "plot"));
        Assertions.assertTrue(client.names(List.of(UUID.randomUUID())).isEmpty());
        Assertions.assertInstanceOf(NameLookup.Unavailable.class, client.uuidOf("Notch"));
        Assertions.assertEquals(ModuleClient.Status.DISABLED, client.status());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :realty-rest:test --tests "*ModuleClientDisabledTest"`
Expected: compilation failure — package `module` does not exist.

- [ ] **Step 3: Write `NameLookup`**

```java
package io.github.md5sha256.realty.rest.module;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Outcome of resolving a player name through the module. Three cases, because the
 * caller must answer differently to each: a resolved player, a name nobody knows
 * (a client error), and a module that could not be asked (a gateway error).
 */
public sealed interface NameLookup {

    record Resolved(@NotNull UUID id, @NotNull String name) implements NameLookup {
    }

    record Unknown() implements NameLookup {
    }

    record Unavailable() implements NameLookup {
    }
}
```

- [ ] **Step 4: Write `ModuleClient`**

```java
package io.github.md5sha256.realty.rest.module;

import io.github.md5sha256.realty.rest.json.RegionResponse;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The seam to the in-server {@code query-service} module.
 *
 * <p>Every method degrades rather than throws: an unreachable module yields an empty
 * result, and callers render that as null. The one place a caller must know the
 * difference between "unknown" and "could not ask" is {@link #uuidOf}, which is why
 * it returns a {@link NameLookup} rather than an {@code Optional}.</p>
 */
public interface ModuleClient {

    enum Status { OK, UNREACHABLE, DISABLED }

    @NotNull Optional<RegionResponse.Dimensions> dimensions(@NotNull UUID worldId, @NotNull String regionId);

    /** Resolved names only; an id absent from the map could not be named. One HTTP call. */
    @NotNull Map<UUID, String> names(@NotNull Collection<UUID> ids);

    @NotNull NameLookup uuidOf(@NotNull String name);

    /** A live probe of the module's {@code /health}; never cached. */
    @NotNull Status status();

    /** The client used when {@code REALTY_REST_MODULE_URL} is unset. */
    static @NotNull ModuleClient disabled() {
        return new ModuleClient() {
            @Override
            public @NotNull Optional<RegionResponse.Dimensions> dimensions(@NotNull UUID worldId,
                                                                           @NotNull String regionId) {
                return Optional.empty();
            }

            @Override
            public @NotNull Map<UUID, String> names(@NotNull Collection<UUID> ids) {
                return Map.of();
            }

            @Override
            public @NotNull NameLookup uuidOf(@NotNull String name) {
                return new NameLookup.Unavailable();
            }

            @Override
            public @NotNull Status status() {
                return Status.DISABLED;
            }
        };
    }
}
```

- [ ] **Step 5: Type `dimensions` in `RegionResponse`**

Replace `@Nullable Object dimensions,` with `@Nullable Dimensions dimensions,` and add inside the record, after `Bid`:

```java
    /**
     * Live WorldGuard geometry from the query-service module. For a cuboid the four
     * points are its footprint corners, so both shapes read the same way.
     */
    public record Dimensions(@NotNull String shape,
                             int minY,
                             int maxY,
                             @NotNull List<Point> points) {
    }

    public record Point(int x, int z) {
    }
```

Update the class Javadoc: `dimensions` is null when the module is disabled or unreachable (drop "until the enrichment client ships"). Make the same wording fix in `json/PlayerRef.java`'s Javadoc.

- [ ] **Step 6: Run tests**

Run: `./gradlew :realty-rest:test --tests "*ModuleClientDisabledTest" --tests "*RegionEndpointTest"`
Expected: PASS (the region test still passes with `null` dimensions).

- [ ] **Step 7: Commit**

```bash
git add realty-rest/src
git commit -m "feat(rest): define the query-service client contract and typed region dimensions"
```

---

### Task 2: `HttpModuleClient` against a fake module

**Files:**
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/module/HttpModuleClient.java`
- Test: `realty-rest/src/test/java/io/github/md5sha256/realty/rest/module/FakeModule.java`
- Test: `realty-rest/src/test/java/io/github/md5sha256/realty/rest/module/HttpModuleClientTest.java`

**Interfaces:**
- Consumes: `ModuleClient`, `NameLookup`, `RegionResponse.Dimensions` (Task 1).
- Produces: `HttpModuleClient(URI baseUrl, String secret, Duration timeout, HttpClient http, ObjectMapper mapper)` and `static ModuleClient from(RestSettings)` which returns `disabled()` when `moduleUrl` is null or blank.

- [ ] **Step 1: Write the fake module**

A Javalin app that speaks the module's wire contract and records what it received.

```java
package io.github.md5sha256.realty.rest.module;

import io.javalin.Javalin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** A stand-in for the query-service module's HTTP surface. */
final class FakeModule {

    static final String SECRET = "hunter2";
    static final UUID WORLD = UUID.fromString("8f4d0000-0000-0000-0000-000000000001");
    static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f64f65c7e1");

    final List<String> receivedBodies = new CopyOnWriteArrayList<>();
    private final long stallMillis;

    FakeModule(long stallMillis) {
        this.stallMillis = stallMillis;
    }

    @NotNull Javalin app() {
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        app.before(ctx -> {
            if (!SECRET.equals(ctx.header("X-Realty-Secret"))) {
                ctx.status(401).json(Map.of("error", "UNAUTHORIZED", "message", "nope"));
                ctx.skipRemainingHandlers();
            }
            if (this.stallMillis > 0) {
                Thread.sleep(this.stallMillis);
            }
        });
        app.get("/health", ctx -> ctx.json(Map.of("status", "ok")));
        app.get("/regions/{worldId}/{regionId}/dimensions", ctx -> {
            if (ctx.pathParam("worldId").equals(WORLD.toString())
                    && ctx.pathParam("regionId").equals("downtown_plot_14")) {
                ctx.result("{\"shape\":\"POLYGONAL\",\"minY\":62,\"maxY\":140,\"points\":["
                        + "{\"x\":104,\"z\":-88},{\"x\":131,\"z\":-88},{\"x\":131,\"z\":-61},{\"x\":104,\"z\":-61}]}")
                        .contentType("application/json");
            } else {
                ctx.status(404).json(Map.of("error", "REGION_NOT_FOUND", "message", "no"));
            }
        });
        app.post("/players/names", ctx -> {
            this.receivedBodies.add(ctx.body());
            StringBuilder players = new StringBuilder();
            for (String id : idsIn(ctx.body())) {
                if (!players.isEmpty()) {
                    players.append(',');
                }
                String name = id.equals(NOTCH.toString()) ? "\"Notch\""
                        : id.equals(BEDROCK.toString()) ? "\".Cool Guy 123\"" : "null";
                players.append("{\"id\":\"").append(id).append("\",\"name\":").append(name).append('}');
            }
            ctx.result("{\"players\":[" + players + "]}").contentType("application/json");
        });
        app.post("/players/uuids", ctx -> {
            this.receivedBodies.add(ctx.body());
            String body = ctx.body();
            String player = body.contains("\"Notch\"")
                    ? "{\"id\":\"" + NOTCH + "\",\"name\":\"Notch\"}"
                    : body.contains("\".Cool Guy 123\"")
                    ? "{\"id\":\"" + BEDROCK + "\",\"name\":\".Cool Guy 123\"}"
                    : "{\"id\":null,\"name\":\"nobody\"}";
            ctx.result("{\"players\":[" + player + "]}").contentType("application/json");
        });
        return app;
    }

    /** Pulls the quoted strings out of {@code {"ids":["…","…"]}} without a JSON library. */
    private static @NotNull List<String> idsIn(@NotNull String body) {
        List<String> ids = new java.util.ArrayList<>();
        int from = body.indexOf('[');
        int to = body.lastIndexOf(']');
        if (from < 0 || to < from) {
            return ids;
        }
        for (String part : body.substring(from + 1, to).split(",")) {
            String trimmed = part.trim();
            if (trimmed.length() >= 2) {
                ids.add(trimmed.substring(1, trimmed.length() - 1));
            }
        }
        return ids;
    }
}
```

Import `java.util.ArrayList` explicitly instead of the inline `java.util.ArrayList` above (project rule).

- [ ] **Step 2: Write the failing tests**

```java
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
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :realty-rest:test --tests "*HttpModuleClientTest"`
Expected: compilation failure — `HttpModuleClient` missing.

- [ ] **Step 4: Write `HttpModuleClient`**

```java
package io.github.md5sha256.realty.rest.module;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.md5sha256.realty.rest.RestSettings;
import io.github.md5sha256.realty.rest.json.RegionResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link ModuleClient} over HTTP. Every failure — connection refused, timeout, a
 * non-2xx status, an unparseable body — becomes an empty result, because the spec's
 * rule is that a request never fails because the game server is offline. The first
 * failure after a success is logged at WARNING and the first success after a
 * failure at INFO, so a flapping module does not flood the log.
 */
public final class HttpModuleClient implements ModuleClient {

    private static final Logger LOGGER = Logger.getLogger(HttpModuleClient.class.getName());
    static final String SECRET_HEADER = "X-Realty-Secret";
    /** The module rejects larger batches; callers never send more than a page of refs anyway. */
    static final int MAX_BATCH = 256;

    private final URI baseUrl;
    private final String secret;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final AtomicBoolean reachable = new AtomicBoolean(true);

    public HttpModuleClient(@NotNull URI baseUrl,
                            @NotNull String secret,
                            @NotNull Duration timeout,
                            @NotNull HttpClient http,
                            @NotNull ObjectMapper mapper) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.secret = Objects.requireNonNull(secret, "secret");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.http = Objects.requireNonNull(http, "http");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** Builds the client the settings describe, or {@link ModuleClient#disabled()} when no URL is set. */
    public static @NotNull ModuleClient from(@NotNull RestSettings settings) {
        String url = settings.moduleUrl();
        String secret = settings.moduleSecret();
        if (url == null || url.isBlank() || secret == null || secret.isBlank()) {
            return ModuleClient.disabled();
        }
        Duration timeout = Duration.ofMillis(settings.moduleTimeoutMs());
        HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).build();
        return new HttpModuleClient(URI.create(url.endsWith("/") ? url.substring(0, url.length() - 1) : url),
                secret, timeout, http, new ObjectMapper());
    }

    @Override
    public @NotNull Optional<RegionResponse.Dimensions> dimensions(@NotNull UUID worldId,
                                                                   @NotNull String regionId) {
        String path = "/regions/" + worldId + "/" + URLEncoder.encode(regionId, StandardCharsets.UTF_8) + "/dimensions";
        JsonNode body = get(path);
        if (body == null || !body.has("shape")) {
            return Optional.empty();
        }
        List<RegionResponse.Point> points = new ArrayList<>();
        for (JsonNode point : body.path("points")) {
            points.add(new RegionResponse.Point(point.path("x").asInt(), point.path("z").asInt()));
        }
        return Optional.of(new RegionResponse.Dimensions(
                body.path("shape").asText(), body.path("minY").asInt(), body.path("maxY").asInt(), points));
    }

    @Override
    public @NotNull Map<UUID, String> names(@NotNull Collection<UUID> ids) {
        List<UUID> distinct = new ArrayList<>(new LinkedHashSet<>(ids));
        if (distinct.isEmpty()) {
            return Map.of();
        }
        if (distinct.size() > MAX_BATCH) {
            distinct = distinct.subList(0, MAX_BATCH);
        }
        JsonNode body = post("/players/names", Map.of("ids", distinct.stream().map(UUID::toString).toList()));
        Map<UUID, String> names = new LinkedHashMap<>();
        if (body == null) {
            return names;
        }
        for (JsonNode player : body.path("players")) {
            JsonNode name = player.path("name");
            if (!name.isNull() && name.isTextual()) {
                names.put(UUID.fromString(player.path("id").asText()), name.asText());
            }
        }
        return names;
    }

    @Override
    public @NotNull NameLookup uuidOf(@NotNull String name) {
        JsonNode body = post("/players/uuids", Map.of("names", List.of(name)));
        if (body == null) {
            return new NameLookup.Unavailable();
        }
        JsonNode player = body.path("players").path(0);
        JsonNode id = player.path("id");
        if (id.isNull() || !id.isTextual()) {
            return new NameLookup.Unknown();
        }
        return new NameLookup.Resolved(UUID.fromString(id.asText()), player.path("name").asText(name));
    }

    @Override
    public @NotNull Status status() {
        return get("/health") == null ? Status.UNREACHABLE : Status.OK;
    }

    private @Nullable JsonNode get(@NotNull String path) {
        return send(HttpRequest.newBuilder(URI.create(this.baseUrl + path)).GET(), path);
    }

    private @Nullable JsonNode post(@NotNull String path, @NotNull Object body) {
        try {
            String json = this.mapper.writeValueAsString(body);
            return send(HttpRequest.newBuilder(URI.create(this.baseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)), path);
        } catch (IOException ex) {
            return failed(path, ex);
        }
    }

    /** {@code null} on any failure, after recording it; a parsed body on 2xx. */
    private @Nullable JsonNode send(@NotNull HttpRequest.Builder request, @NotNull String path) {
        try {
            HttpResponse<String> response = this.http.send(
                    request.header(SECRET_HEADER, this.secret).timeout(this.timeout).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                recovered();
                return null;
            }
            if (response.statusCode() / 100 != 2) {
                return failed(path, new IOException("HTTP " + response.statusCode()));
            }
            recovered();
            return this.mapper.readTree(response.body());
        } catch (IOException ex) {
            return failed(path, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failed(path, ex);
        }
    }

    private @Nullable JsonNode failed(@NotNull String path, @NotNull Exception ex) {
        if (this.reachable.compareAndSet(true, false)) {
            LOGGER.log(Level.WARNING, "query-service module unreachable at " + this.baseUrl + path
                    + "; responses degrade to null geometry and names until it returns", ex);
        }
        return null;
    }

    private void recovered() {
        if (this.reachable.compareAndSet(false, true)) {
            LOGGER.info("query-service module reachable again at " + this.baseUrl);
        }
    }
}
```

Note `send` treats `404` as a clean empty (unknown region) rather than a failure, so an unknown region does not flip the reachability flag or log a warning.

- [ ] **Step 5: Run tests**

Run: `./gradlew :realty-rest:test --tests "*HttpModuleClientTest"`
Expected: 10 tests pass. If `JavalinTest.test` passes `server.port()` as 0, use `server.port()` after start — `JavalinTest` starts on a random port and `Javalin#port()` reports it.

- [ ] **Step 6: Commit**

```bash
git add realty-rest/src
git commit -m "feat(rest): add the HTTP query-service client with degrade-to-empty semantics"
```

---

### Task 3: Wire the client into the server, health, config and main

**Files:**
- Modify: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RealtyRestServer.java` (constructor ~line 95; health route ~line 129; handler construction)
- Modify: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RealtyRestMain.java` (~line 47)
- Modify: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RestConfiguration.java` (`load`, ~line 26–50)
- Modify: `realty-rest/src/test/java/io/github/md5sha256/realty/rest/TestServers.java`
- Test: `realty-rest/src/test/java/io/github/md5sha256/realty/rest/HealthEndpointTest.java`, `RestConfigurationTest.java`

**Interfaces:**
- Produces: `RealtyRestServer(RealtyBackend, Database, RestSettings, ModuleClient)`; the existing three-argument constructor stays and delegates with `ModuleClient.disabled()`. `moduleClient()` accessor. `RegionHandler` and `PlayerRegionsHandler` constructors gain a trailing `ModuleClient` parameter (Tasks 4 and 5 use it; in this task they store it and do nothing else with it).
- Produces in `TestServers`: `static RealtyRestServer withModule(ModuleClient client)` (for-sale region backend, worlds `world`), plus `static ModuleClient stubModule(Map<UUID,String> names, Map<String, RegionResponse.Dimensions> dimensionsByRegionId, Map<String, UUID> uuidsByName)` and `static ModuleClient unreachableModule()`.

- [ ] **Step 1: Write the failing tests**

Add to `HealthEndpointTest`:

```java
    @Test
    void reportsTheModuleAsDisabledWhenNoUrlIsConfigured() {
        JavalinTest.test(TestServers.withHealthyDatabase().javalin(), (server, client) -> {
            Response response = client.get("/v1/health");
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"status\":\"ok\",\"module\":\"disabled\"}", response.body().string());
        });
    }

    @Test
    void anUnreachableModuleIsDegradedNotUnhealthy() {
        JavalinTest.test(TestServers.withModule(TestServers.unreachableModule()).javalin(), (server, client) -> {
            Response response = client.get("/v1/health");
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"status\":\"ok\",\"module\":\"unreachable\"}", response.body().string());
        });
    }

    @Test
    void aReachableModuleReportsOk() {
        ModuleClient stub = TestServers.stubModule(Map.of(), Map.of(), Map.of());
        JavalinTest.test(TestServers.withModule(stub).javalin(), (server, client) -> {
            Assertions.assertEquals("{\"status\":\"ok\",\"module\":\"ok\"}", client.get("/v1/health").body().string());
        });
    }
```

Check the existing failing-database test still expects `{"status":"degraded"}` with `503`; leave that body unchanged.

Add to `RestConfigurationTest` (follow its existing style for building an env `Function<String,String>`):

```java
    @Test
    void aModuleUrlWithoutASecretWarnsAndDisablesEnrichment() {
        Map<String, String> env = new HashMap<>(requiredEnv());
        env.put("REALTY_REST_MODULE_URL", "http://localhost:8123");
        RestConfiguration config = RestConfiguration.load(env::get);
        Assertions.assertEquals("http://localhost:8123", config.rest().moduleUrl());
        Assertions.assertNull(config.rest().moduleSecret());
        Assertions.assertTrue(config.describeRedacted().contains("REALTY_REST_MODULE_SECRET=<unset>"));
    }
```

(`requiredEnv()` — reuse whatever helper the file already has for the three `REALTY_DB_*` variables; if none exists, add a private one returning them.)

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :realty-rest:test --tests "*HealthEndpointTest" --tests "*RestConfigurationTest"`
Expected: compilation failures (`withModule`, `stubModule` missing) and the health body mismatch.

- [ ] **Step 3: Server changes**

In `RealtyRestServer`: add `private final ModuleClient moduleClient;`, the four-argument constructor, and make the three-argument one call `this(backend, database, settings, ModuleClient.disabled())`. Health route:

```java
        this.javalin.get("/v1/health", ctx -> {
            if (databaseReachable()) {
                ctx.status(200).json(Map.of("status", "ok",
                        "module", this.moduleClient.status().name().toLowerCase(Locale.ROOT)));
            } else {
                ctx.status(503).json(Map.of("status", "degraded"));
            }
        });
```

`Map.of` iteration order is unspecified, so for the byte-exact test use a `LinkedHashMap` (`status` then `module`) or a small `record HealthResponse(String status, String module)` in `json/`. Use the record. Pass `this.moduleClient` as the last argument to `new RegionHandler(...)` and `new PlayerRegionsHandler(...)`, and add the parameter + field to both handlers (unused until Tasks 4/5). Add `public @NotNull ModuleClient moduleClient()`.

In `RealtyRestMain` line ~47: `new RealtyRestServer(backend, database, config.rest(), HttpModuleClient.from(config.rest()))`. Log which client was chosen after the resolved-configuration log: `"query-service enrichment: " + (client.status() == ModuleClient.Status.DISABLED ? "disabled (REALTY_REST_MODULE_URL unset or no secret)" : "enabled against " + config.rest().moduleUrl())`.

In `RestConfiguration.load`, after building the settings:

```java
        if (rest.moduleUrl() != null && !rest.moduleUrl().isBlank()
                && (rest.moduleSecret() == null || rest.moduleSecret().isBlank())) {
            LOGGER.warning("REALTY_REST_MODULE_URL is set but REALTY_REST_MODULE_SECRET is not; the module "
                    + "rejects unauthenticated calls, so enrichment is disabled until a secret is set");
        }
```

- [ ] **Step 4: Test harness**

In `TestServers` add:

```java
    static @NotNull RealtyRestServer withModule(@NotNull ModuleClient module) {
        List<RealtyWorldEntity> worlds = List.of(new RealtyWorldEntity(WORLD_ID, "world"));
        FreeholdContractEntity freehold = new FreeholdContractEntity(1, UUID.randomUUID(), null, 25000.0, true);
        RealtyBackend.RegionInfo info = ...; // build exactly as withForSaleRegion() does, but with authorityId = AUTHORITY
        return new RealtyRestServer(regionBackend(info, RegionState.FOR_SALE),
                new StubDatabase(false, worlds), defaultSettings(), module);
    }

    static final UUID WORLD_ID = UUID.fromString("8f4d0000-0000-0000-0000-000000000001");
    static final UUID AUTHORITY = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    static @NotNull ModuleClient stubModule(@NotNull Map<UUID, String> names,
                                            @NotNull Map<String, RegionResponse.Dimensions> dimensionsByRegionId,
                                            @NotNull Map<String, UUID> uuidsByName) {
        return new ModuleClient() {
            @Override
            public @NotNull Optional<RegionResponse.Dimensions> dimensions(@NotNull UUID worldId, @NotNull String regionId) {
                return Optional.ofNullable(dimensionsByRegionId.get(regionId));
            }

            @Override
            public @NotNull Map<UUID, String> names(@NotNull Collection<UUID> ids) {
                Map<UUID, String> resolved = new LinkedHashMap<>();
                for (UUID id : ids) {
                    if (names.containsKey(id)) {
                        resolved.put(id, names.get(id));
                    }
                }
                return resolved;
            }

            @Override
            public @NotNull NameLookup uuidOf(@NotNull String name) {
                UUID id = uuidsByName.get(name);
                return id == null ? new NameLookup.Unknown() : new NameLookup.Resolved(id, name);
            }

            @Override
            public @NotNull Status status() {
                return Status.OK;
            }
        };
    }

    static @NotNull ModuleClient unreachableModule() {
        return new ModuleClient() {
            @Override
            public @NotNull Optional<RegionResponse.Dimensions> dimensions(@NotNull UUID worldId, @NotNull String regionId) {
                return Optional.empty();
            }

            @Override
            public @NotNull Map<UUID, String> names(@NotNull Collection<UUID> ids) {
                return Map.of();
            }

            @Override
            public @NotNull NameLookup uuidOf(@NotNull String name) {
                return new NameLookup.Unavailable();
            }

            @Override
            public @NotNull Status status() {
                return Status.UNREACHABLE;
            }
        };
    }
```

Read `withForSaleRegion()` and reuse its `RegionInfo` construction verbatim, changing only the ids to the named constants (`AUTHORITY` as the authority id, region `downtown_plot_14`, world `WORLD_ID`). Task 4 relies on those exact values.

- [ ] **Step 5: Run tests**

Run: `./gradlew :realty-rest:test`
Expected: everything except the known `PterodactylEggTest` case passes, including `OpenApiConformanceTest` (no new routes).

- [ ] **Step 6: Commit**

```bash
git add realty-rest/src
git commit -m "feat(rest): wire the query-service client into the server and report it in health"
```

---

### Task 4: Enrich `/v1/region` with dimensions and player names

**Files:**
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/module/PlayerNames.java`
- Modify: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RegionHandler.java`
- Test: `realty-rest/src/test/java/io/github/md5sha256/realty/rest/RegionEndpointTest.java`

**Interfaces:**
- Consumes: `ModuleClient`, `RegionResponse.Dimensions`, `TestServers.withModule/stubModule/unreachableModule`, constants `WORLD_ID`, `AUTHORITY`.
- Produces: `PlayerNames.resolve(ModuleClient, Collection<UUID>) -> Map<UUID,String>` (skips nulls, one call) and `PlayerNames.ref(@Nullable UUID, Map<UUID,String>) -> @Nullable PlayerRef`.

- [ ] **Step 1: Write the failing tests**

Add to `RegionEndpointTest`:

```java
    @Test
    void enrichesDimensionsAndNamesFromTheModule() {
        RegionResponse.Dimensions dims = new RegionResponse.Dimensions("CUBOID", 62, 140, List.of(
                new RegionResponse.Point(104, -88), new RegionResponse.Point(131, -88),
                new RegionResponse.Point(131, -61), new RegionResponse.Point(104, -61)));
        ModuleClient module = TestServers.stubModule(
                Map.of(TestServers.AUTHORITY, "DCGovernment"),
                Map.of("downtown_plot_14", dims),
                Map.of());
        JavalinTest.test(TestServers.withModule(module).javalin(), (server, client) -> {
            Response response = client.get("/v1/region?world=world&region=downtown_plot_14");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"authority\":{\"id\":\"" + TestServers.AUTHORITY + "\",\"name\":\"DCGovernment\"}"), body);
            Assertions.assertTrue(body.contains("\"dimensions\":{\"shape\":\"CUBOID\",\"minY\":62,\"maxY\":140,\"points\":[{\"x\":104,\"z\":-88}"), body);
        });
    }

    @Test
    void anUnreachableModuleYieldsNullsAnd200() {
        JavalinTest.test(TestServers.withModule(TestServers.unreachableModule()).javalin(), (server, client) -> {
            Response response = client.get("/v1/region?world=world&region=downtown_plot_14");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"name\":null"), body);
            Assertions.assertTrue(body.contains("\"dimensions\":null"), body);
            Assertions.assertTrue(body.contains("\"price\":25000.0"), "database-sourced data is unaffected: " + body);
        });
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :realty-rest:test --tests "*RegionEndpointTest"`
Expected: the two new tests fail (names null, dimensions null).

- [ ] **Step 3: Write `PlayerNames`**

```java
package io.github.md5sha256.realty.rest.module;

import io.github.md5sha256.realty.rest.json.PlayerRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Names for every player id in a response, fetched in one module call. Handlers
 * collect their ids first and build {@link PlayerRef}s afterwards so a ten-player
 * response costs one round trip, not ten.
 */
public final class PlayerNames {

    private PlayerNames() {
    }

    public static @NotNull Map<UUID, String> resolve(@NotNull ModuleClient module, @NotNull Collection<@Nullable UUID> ids) {
        Set<UUID> distinct = new LinkedHashSet<>();
        for (UUID id : ids) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return module.names(distinct);
    }

    public static @Nullable PlayerRef ref(@Nullable UUID id, @NotNull Map<UUID, String> names) {
        return id == null ? null : new PlayerRef(id.toString(), names.get(id));
    }
}
```

- [ ] **Step 4: Rework `RegionHandler.handle`**

After the 404 check and tag query, collect ids and enrich:

```java
        List<UUID> playerIds = new ArrayList<>();
        if (info.freehold() != null) {
            playerIds.add(info.freehold().titleHolderId());
            playerIds.add(info.freehold().authorityId());
        }
        if (info.leasehold() != null) {
            playerIds.add(info.leasehold().landlordId());
            playerIds.add(info.leasehold().tenantId());
        }
        if (info.highestBid() != null) {
            playerIds.add(info.highestBid().bidderId());
        }
        Map<UUID, String> names = PlayerNames.resolve(this.moduleClient, playerIds);
        RegionResponse.Dimensions dimensions = this.moduleClient.dimensions(worldId, regionParam).orElse(null);

        RegionResponse response = new RegionResponse(
                regionParam, worldRef, state == null ? null : state.name(),
                toFreehold(info.freehold(), info.lastSoldPrice(), names),
                toLeasehold(info.leasehold(), names),
                toAuction(info.auction(), info.highestBid(), names),
                dimensions, tags);
```

Change `toFreehold`, `toLeasehold`, `toAuction` to take `Map<UUID, String> names` and build refs with `PlayerNames.ref(id, names)` instead of `new PlayerRef(id.toString(), null)`. (`authority` and `landlord` are non-null ids; `Objects.requireNonNull(PlayerNames.ref(...))` there keeps the `@NotNull` contract honest.)

- [ ] **Step 5: Run tests**

Run: `./gradlew :realty-rest:test --tests "*RegionEndpointTest" --tests "*Error500Test"`
Expected: all pass, including the pre-existing region tests (names null under the disabled client).

- [ ] **Step 6: Commit**

```bash
git add realty-rest/src
git commit -m "feat(rest): enrich the region payload with live dimensions and player names"
```

---

### Task 5: Player lookup by name, names on `/v1/players/regions`, docs and compose

**Files:**
- Modify: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/PlayerRegionsHandler.java` (`handle`, `resolvePlayerId` ~lines 44–88)
- Modify: `realty-rest/src/main/resources/openapi.yaml` (health schema ~line 11–35; `dimensions` schema ~line 473; error-code list ~line 358)
- Modify: `compose.yml` (module variables, commented, after the CORS comment), `compose.local.yml` (module variables, active), `realty-rest/README.md` (variables table + a short "Enrichment" paragraph)
- Test: `realty-rest/src/test/java/io/github/md5sha256/realty/rest/PlayerRegionsEndpointTest.java`

**Interfaces:**
- Consumes: `ModuleClient.uuidOf`, `NameLookup`, `PlayerNames`, `TestServers.withPlayerHoldings...` (read how it builds its backend; add a `withPlayerHoldingsAndModule(ModuleClient)` variant alongside it that passes the client through).

- [ ] **Step 1: Write the failing tests**

Add to `PlayerRegionsEndpointTest` (keep the existing 502-without-module test; it still holds):

```java
    @Test
    void resolvesAPlayerNameThroughTheModuleAndNamesTheRef() {
        ModuleClient module = TestServers.stubModule(
                Map.of(TestServers.PLAYER_ID, ".Cool Guy 123"), Map.of(), Map.of(".Cool Guy 123", TestServers.PLAYER_ID));
        JavalinTest.test(TestServers.withPlayerHoldingsAndModule(module).javalin(), (server, client) -> {
            for (String encoded : List.of(".Cool%20Guy%20123", ".Cool+Guy+123")) {
                Response response = client.get("/v1/players/regions?player=" + encoded);
                Assertions.assertEquals(200, response.code(), encoded);
                Assertions.assertTrue(response.body().string().contains(
                        "\"player\":{\"id\":\"" + TestServers.PLAYER_ID + "\",\"name\":\".Cool Guy 123\"}"), encoded);
            }
        });
    }

    @Test
    void anUnknownNameIs404() {
        ModuleClient module = TestServers.stubModule(Map.of(), Map.of(), Map.of());
        JavalinTest.test(TestServers.withPlayerHoldingsAndModule(module).javalin(), (server, client) -> {
            Response response = client.get("/v1/players/regions?player=nobody");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("PLAYER_NOT_FOUND"));
        });
    }

    @Test
    void aNameWithAnUnreachableModuleIs502ButAUuidStillWorks() {
        JavalinTest.test(TestServers.withPlayerHoldingsAndModule(TestServers.unreachableModule()).javalin(), (server, client) -> {
            Assertions.assertEquals(502, client.get("/v1/players/regions?player=Notch").code());
            Response byId = client.get("/v1/players/regions?player=" + TestServers.PLAYER_ID);
            Assertions.assertEquals(200, byId.code());
            Assertions.assertTrue(byId.body().string().contains("\"name\":null"));
        });
    }
```

`TestServers.PLAYER_ID` is whatever UUID `withPlayerHoldings()` already uses for the player — promote it to a named constant if it is currently inline.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :realty-rest:test --tests "*PlayerRegionsEndpointTest"`
Expected: compilation failure on `withPlayerHoldingsAndModule` / `PLAYER_ID`, then behavioural failures.

- [ ] **Step 3: Rework `PlayerRegionsHandler`**

Replace the static `resolvePlayerId` with an instance method returning a `PlayerRef`:

```java
    private @NotNull PlayerRef resolvePlayer(@NotNull String param) {
        if (isUuidShaped(param)) {
            UUID id;
            try {
                id = UUID.fromString(param);
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("MALFORMED_UUID", "Query parameter 'player' is not a valid UUID");
            }
            return PlayerNames.ref(id, PlayerNames.resolve(this.moduleClient, List.of(id)));
        }
        return switch (this.moduleClient.uuidOf(param)) {
            case NameLookup.Resolved resolved -> new PlayerRef(resolved.id().toString(), resolved.name());
            case NameLookup.Unknown unknown -> throw ApiException.notFound("PLAYER_NOT_FOUND",
                    "No player named '" + param + "'");
            case NameLookup.Unavailable unavailable -> throw ApiException.badGateway("NAME_LOOKUP_UNAVAILABLE",
                    "Player name lookup requires the query-service module, which is not reachable");
        };
    }
```

In `handle`: `PlayerRef player = resolvePlayer(playerParam); UUID playerId = UUID.fromString(player.id());` and drop the old `new PlayerRef(playerId.toString(), null)` line. `QueryParams.required` already decodes `+` and `%20` (the existing encoding tests prove it); do not decode again.

- [ ] **Step 4: OpenAPI**

- Health `200` schema: add `module: {type: string, enum: [ok, unreachable, disabled]}` and extend the description: an unreachable module is degraded, not unhealthy.
- Replace the `dimensions` schema (currently a nullable free-form object) with a `Dimensions` component: `shape` (enum `CUBOID`, `POLYGONAL`), `minY`, `maxY` (integers), `points` (array of `{x, z}` integers), `nullable: true`, description "null when the query-service module is disabled or unreachable".
- In `/v1/players/regions` responses add `404` with `PLAYER_NOT_FOUND` ("`player` was given as a name the server does not know"), and reword the `502` text to "module not reachable".
- `PlayerRef.name` description: "from the query-service module; null when it is disabled or unreachable".

Run `./gradlew :realty-rest:test --tests "*OpenApiConformanceTest"` — it must still pass (no path changes).

- [ ] **Step 5: Compose and README**

`compose.local.yml`, in the `environment:` block after `REALTY_REST_CORS_ORIGINS`:

```yaml
      # The query-service module runs inside the Paper process on the host. Its
      # shared-secret lives in plugins/Realty/modules/query-service/config.yml and
      # must match REALTY_REST_MODULE_SECRET here.
      REALTY_REST_MODULE_URL: "http://host.docker.internal:8123"
      REALTY_REST_MODULE_SECRET: "change-me"
      REALTY_REST_MODULE_TIMEOUT_MS: "1500"
```

`compose.yml`, after the CORS comment block, commented out in the same style:

```yaml
      # Point at the query-service module inside the game server to enrich
      # responses with live geometry and player names. Both values must match the
      # module's config.yml; unset leaves those fields null.
      # REALTY_REST_MODULE_URL: "http://game-server:8123"
      # REALTY_REST_MODULE_SECRET: "change-me"
      # REALTY_REST_MODULE_TIMEOUT_MS: "1500"
```

**Staging note for `compose.yml`:** the working tree already holds the user's own uncommitted CORS comment in this file. Stage only your hunk: `git show HEAD:compose.yml > /tmp/compose.base && ...` is not available — instead, make your edit, run `git diff compose.yml` and confirm both hunks are present, then stage with `git add -p` equivalent is unavailable non-interactively, so: copy the current file aside, `git checkout HEAD -- compose.yml`, apply ONLY your module block, `git add compose.yml`, then restore the copy (which contains both edits) over the working file. Verify `git diff --cached compose.yml` shows only the module block and `git diff compose.yml` shows only the CORS comment.

`realty-rest/README.md`: ensure the three module variables are in the variables table with the meanings from the spec, and add a paragraph "Enrichment" stating what the module supplies, the degrade rule, and that `?player=<name>` needs it.

- [ ] **Step 6: Run the module's tests**

Run: `./gradlew :realty-rest:test`
Expected: all pass except the known `PterodactylEggTest` case.

- [ ] **Step 7: Commit**

```bash
git add realty-rest/src realty-rest/README.md compose.local.yml compose.yml
git commit -m "feat(rest): resolve players by name through the module and document enrichment"
```

---

## Self-review notes

- **Spec coverage:** enrichment as a post-backend step with identity `nameResolver` untouched (T4/T5); `dimensions` null when unreachable (T1/T4); names null when unreachable, 502 only for name lookup, UUID lookup still works (T5); `%20` and `+` fixtures (T5); health reports module separately, 200 (T3); env vars already parsed, URL-without-secret warns (T3); compose/egg docs (T5; egg already declares the variables). No caching, no module change.
- **Type consistency:** `ModuleClient.dimensions/names/uuidOf/status` (T1) used in T2–T5; `NameLookup.Resolved/Unknown/Unavailable` (T1) in T2/T3/T5; `PlayerNames.resolve/ref` (T4) in T4/T5; `RealtyRestServer` 4-arg constructor (T3) in T3–T5 via `TestServers`.
- **Deliberate choice:** an unknown name is `404 PLAYER_NOT_FOUND`, which the spec does not define (it only defines the 502). A 200 "owns nothing" for a nonexistent player would hide typos; 404 is the honest answer.
