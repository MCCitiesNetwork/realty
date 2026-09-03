# Query-Service Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the `query-service` Realty module: a secured, localhost-bound HTTP endpoint inside the game server that answers for live WorldGuard region geometry and player name/UUID lookups, plus an in-process `PlayerNameService` on the Paper API.

**Architecture:** A new adapter subproject `realty-paper-adapters/query-service` extending `SimplePluginModule<Realty>`, embedding Javalin (relocated) and reading a `config.yml` with a regenerated `defaults/` copy. Geometry is read live from WorldGuard via a main-thread hop with a timeout; names go through Realty's existing `SquirrelIdUsernameResolver`, surfaced to the module (and everyone else) through a new `PlayerNameService` interface in `realty-paper-api`.

**Tech Stack:** Java 25, Gradle (Kotlin DSL, Shadow 9.3.1), Javalin 6.4.0 + Jackson 2.18.2 (same as `realty-rest`), Paper API 26.1.2, WorldGuard 7.0.18, `plugin-infrastructure` module system, JUnit 5, `javalin-testtools`.

**Spec:** `docs/superpowers/specs/2026-09-02-realty-query-service-design.md` (companion: `2026-09-02-realty-rest-api-design.md`)

## Global Constraints

- Java 25 toolchain via `realty-conventions`; Paper API `26.1.2.build.74-stable`; WorldGuard `7.0.18`; `plugin-infrastructure:1.0.0-SNAPSHOT` as `compileOnly` in the adapter (it is `implementation`, not `api`, in `realty-paper`).
- Javalin `6.4.0`, Jackson `2.18.2`, `javalin-testtools 6.4.0` — identical to `realty-rest`.
- **No wildcard or static imports.** `Assertions.assertEquals(...)`, never static-imported.
- **Never fully-qualify a class inline; import it.**
- Every operator config ships `defaults/default-config.yml`, rewritten on **every** start, never read back, and it must itself parse.
- Module jar must ship `module-manifest.yml` at the jar root with `expected-plugin-class: io.github.md5sha256.realty.Realty`.
- `reloadable: true` requires overriding `reload()`; the default is a no-op.
- The module registers **no commands** and has **no write path**.
- Empty `shared-secret` → HTTP server not started, `WARNING` logged naming the reason.
- Default `bind-host` `127.0.0.1`, `port` `8123`, `request-timeout-ms` `1000`.
- Missing/wrong secret → `401`. Unknown region → `404`. Main-thread timeout → `504`.
- Commit messages end with the attribution lines the session was given; never an email.
- Run all Gradle commands from the repo root with `./gradlew` (Git Bash) or `.\gradlew.bat` (PowerShell).

---

## File Structure

**New subproject `realty-paper-adapters/query-service`** (package `io.github.md5sha256.realty.adapter.query`):

| File | Responsibility |
|---|---|
| `build.gradle.kts` | Deps, shadow relocation of Javalin/Jackson/Jetty/Kotlin under `...adapter.query.libraries`, `slf4j-api` left to Paper |
| `src/main/resources/module-manifest.yml` | Module manifest |
| `src/main/resources/config.yml` | Bundled default config (also the source of the reference copy) |
| `QueryServiceConfig.java` | Reads `config.yml`, writes reference copy; pure `from(YamlConfiguration)` for tests |
| `RegionDimensions.java` | Record `{shape,minY,maxY,points}` + `fromProtectedRegion` (pure geometry mapping) |
| `RegionDimensionsSource.java` | Interface: `(worldId, regionId) -> CompletableFuture<Optional<RegionDimensions>>` |
| `MainThreadDimensionsSource.java` | Implementation: main-thread hop into WorldGuard, timeout applied by caller |
| `ApiException.java` / `json/ErrorResponse.java` | Same error shape as `realty-rest` |
| `QueryServiceServer.java` | Builds Javalin, secret filter, routes, exception mapping; `start()`/`stop()` |
| `DimensionsHandler.java` | `GET /regions/{worldId}/{regionId}/dimensions` |
| `PlayerNamesHandler.java` | `GET /players/{uuid}/name`, `POST /players/names`, `POST /players/uuids` |
| `QueryServiceModule.java` | `SimplePluginModule<Realty>`: lifecycle + reload |
| tests mirroring each of the above |

**Modified in existing projects:**

| File | Change |
|---|---|
| `realty-paper-api/.../api/PlayerNameService.java` | **New** interface |
| `realty-paper-api/.../api/RealtyPaperApi.java` | Add `playerNameService()` accessor |
| `realty-paper/.../util/SquirrelIdUsernameResolver.java` | Add `getUuid(String)` reverse lookup |
| `realty-paper/.../util/SquirrelIdPlayerNameService.java` | **New** `PlayerNameService` implementation |
| `realty-paper/.../api/RealtyPaperApiImpl.java` | Hold and expose the service |
| `realty-paper/.../Realty.java` | Construct service, pass to impl, register with `ServicesManager` |
| `realty-paper/build.gradle.kts` | Stage `query-service.jar` into `run/plugins/Realty/modules` |
| `settings.gradle.kts` | `include("realty-paper-adapters:query-service")` |
| `README.md`, `CLAUDE.md` | Document the module |

Note: `settings.gradle.kts` currently has an uncommitted change commenting out `realty-areashop-importer`. Leave that line as you find it; only add the new include.

---

### Task 1: `PlayerNameService` on the Paper API

**Files:**
- Create: `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/PlayerNameService.java`
- Modify: `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/RealtyPaperApi.java` (add one method next to `setSafeBlockPredicate`, ~line 31)
- Create: `realty-paper/src/main/java/io/github/md5sha256/realty/util/SquirrelIdPlayerNameService.java`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/util/SquirrelIdUsernameResolver.java`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/api/RealtyPaperApiImpl.java` (constructor ~line 65, `setSafeBlockPredicate` ~line 86)
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java` (~lines 258, 305, 322)
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/util/SquirrelIdPlayerNameServiceTest.java`

**Interfaces:**
- Produces: `io.github.md5sha256.realty.api.PlayerNameService` with
  `CompletableFuture<Optional<String>> nameOf(UUID)`,
  `CompletableFuture<Optional<UUID>> uuidOf(String)`, and default batch
  `CompletableFuture<Map<UUID, Optional<String>>> namesOf(Collection<UUID>)`,
  `CompletableFuture<Map<String, Optional<UUID>>> uuidsOf(Collection<String>)`.
- Produces: `RealtyPaperApi.playerNameService()` returning it. Task 6 calls `plugin.paperApi().playerNameService()`.

- [ ] **Step 1: Write the interface**

```java
package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves player identities in both directions using the server's own knowledge first.
 *
 * <p>Prefers the local usercache, which holds the right name for anyone who has joined — including
 * Bedrock/Floodgate players whose {@code .}-prefixed names Mojang cannot resolve — and only then
 * falls back to a Mojang-backed lookup. Callers should resolve this rather than hand-rolling
 * {@code Bukkit.getOfflinePlayer(uuid).getName()}.</p>
 *
 * <p>Every method is safe to call from any thread and never completes exceptionally: an identity
 * that cannot be resolved completes with {@link Optional#empty()}.</p>
 */
public interface PlayerNameService {

    @NotNull CompletableFuture<Optional<String>> nameOf(@NotNull UUID id);

    @NotNull CompletableFuture<Optional<UUID>> uuidOf(@NotNull String name);

    /**
     * Resolves many ids at once. The returned map contains every requested id, in request order,
     * so a caller can distinguish "no name" from "not asked".
     */
    default @NotNull CompletableFuture<Map<UUID, Optional<String>>> namesOf(
            @NotNull Collection<UUID> ids) {
        Map<UUID, CompletableFuture<Optional<String>>> pending = new LinkedHashMap<>();
        for (UUID id : ids) {
            pending.computeIfAbsent(id, this::nameOf);
        }
        return CompletableFuture.allOf(pending.values().toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    Map<UUID, Optional<String>> resolved = new LinkedHashMap<>();
                    pending.forEach((id, future) -> resolved.put(id, future.join()));
                    return resolved;
                });
    }

    /** Reverse of {@link #namesOf}; same ordering and completeness guarantees. */
    default @NotNull CompletableFuture<Map<String, Optional<UUID>>> uuidsOf(
            @NotNull Collection<String> names) {
        Map<String, CompletableFuture<Optional<UUID>>> pending = new LinkedHashMap<>();
        for (String name : names) {
            pending.computeIfAbsent(name, this::uuidOf);
        }
        return CompletableFuture.allOf(pending.values().toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    Map<String, Optional<UUID>> resolved = new LinkedHashMap<>();
                    pending.forEach((name, future) -> resolved.put(name, future.join()));
                    return resolved;
                });
    }
}
```

- [ ] **Step 2: Add the accessor to `RealtyPaperApi`**

Directly after `void setSafeBlockPredicate(@NotNull Predicate<Block> predicate);`:

```java
    /**
     * Player name and UUID resolution backed by the server's usercache and Realty's profile cache.
     * Modules and other plugins should use this rather than {@code Bukkit.getOfflinePlayer}.
     */
    @NotNull PlayerNameService playerNameService();
```

- [ ] **Step 3: Write the failing test for the implementation**

The implementation is built over two functions so the test needs no Bukkit. `SquirrelIdUsernameResolver.getUsername` returns the UUID *string* when it cannot resolve; the service must turn that into `Optional.empty()`.

```java
package io.github.md5sha256.realty.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

class SquirrelIdPlayerNameServiceTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f64f65c7e1");

    private static SquirrelIdPlayerNameService service() {
        return new SquirrelIdPlayerNameService(
                id -> CompletableFuture.completedFuture(
                        id.equals(NOTCH) ? "Notch"
                                : id.equals(BEDROCK) ? ".Cool Guy 123"
                                : id.toString()),
                name -> CompletableFuture.completedFuture(
                        name.equals("Notch") ? Optional.of(NOTCH)
                                : name.equals(".Cool Guy 123") ? Optional.of(BEDROCK)
                                : Optional.empty()));
    }

    @Test
    void resolvesAJavaEditionName() {
        Assertions.assertEquals(Optional.of("Notch"), service().nameOf(NOTCH).join());
    }

    @Test
    void resolvesABedrockNameWithSpaces() {
        Assertions.assertEquals(Optional.of(".Cool Guy 123"), service().nameOf(BEDROCK).join());
    }

    @Test
    void anUnresolvableUuidIsEmptyRatherThanTheUuidString() {
        UUID unknown = UUID.randomUUID();
        Assertions.assertEquals(Optional.empty(), service().nameOf(unknown).join());
    }

    @Test
    void reverseLookupWorksForBothNameForms() {
        Assertions.assertEquals(Optional.of(NOTCH), service().uuidOf("Notch").join());
        Assertions.assertEquals(Optional.of(BEDROCK), service().uuidOf(".Cool Guy 123").join());
        Assertions.assertEquals(Optional.empty(), service().uuidOf("nobody").join());
    }

    @Test
    void aFailedLookupCompletesEmptyNotExceptionally() {
        SquirrelIdPlayerNameService failing = new SquirrelIdPlayerNameService(
                id -> CompletableFuture.failedFuture(new IllegalStateException("boom")),
                name -> CompletableFuture.failedFuture(new IllegalStateException("boom")));
        Assertions.assertEquals(Optional.empty(), failing.nameOf(NOTCH).join());
        Assertions.assertEquals(Optional.empty(), failing.uuidOf("Notch").join());
    }

    @Test
    void batchLookupKeepsEveryRequestedIdInOrder() {
        UUID unknown = UUID.randomUUID();
        Map<UUID, Optional<String>> names = service().namesOf(List.of(BEDROCK, unknown, NOTCH)).join();
        Assertions.assertEquals(List.of(BEDROCK, unknown, NOTCH), List.copyOf(names.keySet()));
        Assertions.assertEquals(Optional.empty(), names.get(unknown));
        Assertions.assertEquals(Optional.of("Notch"), names.get(NOTCH));
    }
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `./gradlew :realty-paper:test --tests "io.github.md5sha256.realty.util.SquirrelIdPlayerNameServiceTest"`
Expected: compilation failure — `SquirrelIdPlayerNameService` does not exist.

- [ ] **Step 5: Add the reverse lookup to `SquirrelIdUsernameResolver`**

Add imports `org.bukkit.OfflinePlayer` and keep the rest. Add after `getUsername`:

```java
    /**
     * Name → UUID. Mirrors {@link #getUsername}: the local usercache first, which is the only place
     * a Floodgate name such as {@code .Cool Guy 123} can be found, then the profile service.
     * Completes empty, never exceptionally, when nothing knows the name.
     */
    @NotNull
    public CompletableFuture<Optional<UUID>> getUuid(@NotNull String name) {
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached.getUniqueId()));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                Profile profile = this.service.findByName(name);
                return Optional.ofNullable(profile).map(Profile::getUniqueId);
            } catch (Exception ex) {
                return Optional.<UUID>empty();
            }
        }, this.asyncExecutor);
    }
```

- [ ] **Step 6: Write the implementation**

```java
package io.github.md5sha256.realty.util;

import io.github.md5sha256.realty.api.PlayerNameService;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * {@link PlayerNameService} over {@link SquirrelIdUsernameResolver}.
 *
 * <p>Built over two functions rather than the resolver itself so the mapping rules can be tested
 * without a running server: the resolver answers an unknown UUID with the UUID's own string, and
 * this class is where that becomes {@link Optional#empty()}.</p>
 */
public final class SquirrelIdPlayerNameService implements PlayerNameService {

    private final Function<UUID, CompletableFuture<String>> nameLookup;
    private final Function<String, CompletableFuture<Optional<UUID>>> uuidLookup;

    public SquirrelIdPlayerNameService(@NotNull SquirrelIdUsernameResolver resolver) {
        this(resolver::getUsername, resolver::getUuid);
    }

    SquirrelIdPlayerNameService(@NotNull Function<UUID, CompletableFuture<String>> nameLookup,
                                @NotNull Function<String, CompletableFuture<Optional<UUID>>> uuidLookup) {
        this.nameLookup = Objects.requireNonNull(nameLookup, "nameLookup");
        this.uuidLookup = Objects.requireNonNull(uuidLookup, "uuidLookup");
    }

    @Override
    public @NotNull CompletableFuture<Optional<String>> nameOf(@NotNull UUID id) {
        return this.nameLookup.apply(id)
                .thenApply(name -> name == null || name.isEmpty() || name.equals(id.toString())
                        ? Optional.<String>empty()
                        : Optional.of(name))
                .exceptionally(ex -> Optional.empty());
    }

    @Override
    public @NotNull CompletableFuture<Optional<UUID>> uuidOf(@NotNull String name) {
        return this.uuidLookup.apply(name)
                .exceptionally(ex -> Optional.empty());
    }
}
```

- [ ] **Step 7: Wire it through `RealtyPaperApiImpl` and `Realty`**

In `RealtyPaperApiImpl`: add a constructor parameter `@NotNull PlayerNameService playerNameService` (last), store it in a `private final PlayerNameService playerNameService;`, and add:

```java
    @Override
    public @NotNull PlayerNameService playerNameService() {
        return this.playerNameService;
    }
```

In `Realty.onEnable`, directly after `this.nameResolver = new SquirrelIdUsernameResolver(...)` succeeds (after the try/catch), add a field `private PlayerNameService playerNameService;` and:

```java
        this.playerNameService = new SquirrelIdPlayerNameService(this.nameResolver);
```

Pass `this.playerNameService` as the new last argument of `new RealtyPaperApiImpl(...)`. Next to the two existing `ServicesManager.register` calls add:

```java
        getServer().getServicesManager()
                .register(PlayerNameService.class, this.playerNameService, this, ServicePriority.Normal);
```

Add imports for `PlayerNameService` and `SquirrelIdPlayerNameService` in both files. Do not migrate the eight existing `getOfflinePlayer` call sites — out of scope per spec.

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew :realty-paper-api:compileJava :realty-paper:test --tests "io.github.md5sha256.realty.util.SquirrelIdPlayerNameServiceTest"`
Expected: BUILD SUCCESSFUL, 6 tests pass. Also run `./gradlew :realty-paper:compileJava` and every other subproject that implements `RealtyPaperApi` compiles (grep for `implements RealtyPaperApi` — only `RealtyPaperApiImpl` should).

- [ ] **Step 9: Commit**

```bash
git add realty-paper-api realty-paper/src
git commit -m "feat(api): add PlayerNameService with name and UUID lookup in both directions"
```

---

### Task 2: Subproject skeleton and config

**Files:**
- Modify: `settings.gradle.kts`
- Create: `realty-paper-adapters/query-service/build.gradle.kts`
- Create: `realty-paper-adapters/query-service/src/main/resources/module-manifest.yml`
- Create: `realty-paper-adapters/query-service/src/main/resources/config.yml`
- Create: `realty-paper-adapters/query-service/src/main/java/io/github/md5sha256/realty/adapter/query/QueryServiceConfig.java`
- Test: `realty-paper-adapters/query-service/src/test/java/io/github/md5sha256/realty/adapter/query/QueryServiceConfigTest.java`

**Interfaces:**
- Produces: `QueryServiceConfig` with `bindHost()`, `port()`, `sharedSecret()` (never null, may be blank), `requestTimeout()` (`Duration`), `httpEnabled()` (`!sharedSecret().isBlank()`), `static read(Path)`, `static from(YamlConfiguration)`, `static writeReferenceCopy(Path)`, constants `CONFIG_FILE`, `DEFAULTS_DIR`, `REFERENCE_FILE`.

- [ ] **Step 1: Register the subproject**

Add to `settings.gradle.kts` after the `player-notifications-adapter` include:

```kotlin
include("realty-paper-adapters:query-service")
```

- [ ] **Step 2: Write `build.gradle.kts`**

Javalin, Jackson, Jetty and Kotlin are relocated: the module loads in a `URLClassLoader` whose parent chain reaches Paper's plugin class loaders, which can resolve classes from *other* plugins' jars, so an unrelocated Javalin here could collide with a different version another plugin bundles. `slf4j-api` is deliberately **not** shaded: Paper provides it (bound to Log4j), and a relocated copy would have no binding and log nothing.

```kotlin
plugins {
    `java-library`
    `realty-conventions`
    id("com.gradleup.shadow") version "9.3.1"
}

dependencies {
    compileOnly(project(":realty-paper"))
    compileOnly(project(":realty-paper-api"))
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.18") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("org.jetbrains:annotations:26.0.2-1")
    compileOnly("com.minecraftcitiesnetwork:plugin-infrastructure:1.0.0-SNAPSHOT")
    // Paper ships slf4j-api bound to Log4j; shading our own copy would leave Javalin logging
    // into a binding-less void.
    compileOnly("org.slf4j:slf4j-api:2.0.16")

    implementation("io.javalin:javalin:6.4.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    testImplementation(project(":realty-paper-api"))
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    testImplementation("com.sk89q.worldguard:worldguard-bukkit:7.0.18") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    testImplementation("io.javalin:javalin-testtools:6.4.0")
    testImplementation("org.slf4j:slf4j-simple:2.0.16")
}

tasks.shadowJar {
    archiveBaseName.set("query-service")
    archiveClassifier.set("")
    val base = "io.github.md5sha256.realty.adapter.query.libraries"
    relocate("io.javalin", "$base.io.javalin")
    relocate("org.eclipse.jetty", "$base.org.eclipse.jetty")
    relocate("com.fasterxml.jackson", "$base.com.fasterxml.jackson")
    relocate("kotlin", "$base.kotlin")
    relocate("org.intellij", "$base.org.intellij")
    relocate("org.jetbrains", "$base.org.jetbrains")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
```

- [ ] **Step 3: Write the manifest and bundled config**

`module-manifest.yml`:

```yaml
module-name: query-service
entry-class: io.github.md5sha256.realty.adapter.query.QueryServiceModule
author: md5sha256
expected-plugin-class: io.github.md5sha256.realty.Realty
reloadable: true
```

`config.yml`:

```yaml
# query-service
#
# A private HTTP endpoint inside the game server that answers for the two things Realty's
# database does not hold: live WorldGuard region geometry, and player names. It exists for
# realty-rest, which runs as a separate process; it is not a second public API.
#
# Every request must carry the shared secret in the X-Realty-Secret header. Leaving it empty
# does NOT run the endpoint open — it disables the endpoint entirely, and realty-rest degrades
# to returning null geometry and null names.
shared-secret: ""

# Interface to bind. Localhost by default, so a same-host realty-rest can reach it and nothing
# else can. Set to 0.0.0.0 only if realty-rest runs on another host or in another container,
# and front it with a reverse proxy if that hop crosses a network you do not control.
bind-host: "127.0.0.1"
port: 8123

# WorldGuard's region data may only be read on the main thread, so a geometry request waits
# for a main-thread tick. If one does not come within this many milliseconds the request fails
# with 504 rather than holding an HTTP worker while the server is stalled.
request-timeout-ms: 1000
```

- [ ] **Step 4: Write the failing config test**

```java
package io.github.md5sha256.realty.adapter.query;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

class QueryServiceConfigTest {

    private static QueryServiceConfig parse(String yaml) {
        return QueryServiceConfig.from(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    private static Path reference(Path dataFolder) {
        return dataFolder.resolve(QueryServiceConfig.DEFAULTS_DIR)
                .resolve(QueryServiceConfig.REFERENCE_FILE);
    }

    @Test
    void readsEveryKey() {
        QueryServiceConfig config = parse("""
                shared-secret: "hunter2"
                bind-host: "0.0.0.0"
                port: 9000
                request-timeout-ms: 250
                """);
        Assertions.assertEquals("hunter2", config.sharedSecret());
        Assertions.assertEquals("0.0.0.0", config.bindHost());
        Assertions.assertEquals(9000, config.port());
        Assertions.assertEquals(Duration.ofMillis(250), config.requestTimeout());
        Assertions.assertTrue(config.httpEnabled());
    }

    @Test
    void absentKeysTakeTheSpecDefaults() {
        QueryServiceConfig config = parse("# nothing\n");
        Assertions.assertEquals("", config.sharedSecret());
        Assertions.assertEquals("127.0.0.1", config.bindHost());
        Assertions.assertEquals(8123, config.port());
        Assertions.assertEquals(Duration.ofMillis(1000), config.requestTimeout());
    }

    @Test
    void aBlankSecretDisablesHttp() {
        Assertions.assertFalse(parse("shared-secret: \"\"\n").httpEnabled());
        Assertions.assertFalse(parse("shared-secret: \"   \"\n").httpEnabled());
        Assertions.assertFalse(parse("# absent\n").httpEnabled());
    }

    @Test
    void aFirstStartWritesTheLiveFileAndTheReferenceCopy(@TempDir Path dataFolder) {
        QueryServiceConfig config = QueryServiceConfig.read(dataFolder);
        Assertions.assertTrue(Files.isRegularFile(dataFolder.resolve(QueryServiceConfig.CONFIG_FILE)));
        Assertions.assertTrue(Files.isRegularFile(reference(dataFolder)));
        Assertions.assertFalse(config.httpEnabled(), "the shipped default has no secret");
        Assertions.assertEquals(8123, config.port());
    }

    @Test
    void aLaterStartLeavesTheOperatorsFileAlone(@TempDir Path dataFolder) throws IOException {
        QueryServiceConfig.read(dataFolder);
        Path live = dataFolder.resolve(QueryServiceConfig.CONFIG_FILE);
        Files.writeString(live, "shared-secret: \"s\"\nport: 1\n", StandardCharsets.UTF_8);

        QueryServiceConfig config = QueryServiceConfig.read(dataFolder);

        Assertions.assertEquals(1, config.port());
        Assertions.assertEquals("shared-secret: \"s\"\nport: 1\n",
                Files.readString(live, StandardCharsets.UTF_8));
    }

    @Test
    void aStaleReferenceCopyIsOverwrittenOnEveryStartAndParses(@TempDir Path dataFolder) throws IOException {
        QueryServiceConfig.read(dataFolder);
        Files.writeString(reference(dataFolder), "# left over\n", StandardCharsets.UTF_8);

        QueryServiceConfig.read(dataFolder);

        String refreshed = Files.readString(reference(dataFolder), StandardCharsets.UTF_8);
        Assertions.assertFalse(refreshed.contains("left over"));
        QueryServiceConfig parsed = parse(refreshed);
        Assertions.assertEquals(8123, parsed.port());
        Assertions.assertEquals("127.0.0.1", parsed.bindHost());
    }
}
```

- [ ] **Step 5: Run it to verify it fails**

Run: `./gradlew :realty-paper-adapters:query-service:test`
Expected: compilation failure — `QueryServiceConfig` does not exist.

- [ ] **Step 6: Write `QueryServiceConfig`**

```java
package io.github.md5sha256.realty.adapter.query;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;

/**
 * Reads the module's {@code config.yml}.
 *
 * <p>Kept out of {@link QueryServiceModule} so it can be tested directly: the module extends
 * {@code SimplePluginModule}, and reaching it from a test would drag {@code plugin-infrastructure} —
 * a {@code compileOnly} dependency — onto the test classpath.</p>
 */
public final class QueryServiceConfig {

    static final String CONFIG_FILE = "config.yml";
    /** See the project config rules: every operator config ships a regenerated reference copy. */
    static final String DEFAULTS_DIR = "defaults";
    static final String REFERENCE_FILE = "default-config.yml";

    private static final String SHARED_SECRET = "shared-secret";
    private static final String BIND_HOST = "bind-host";
    private static final String PORT = "port";
    private static final String REQUEST_TIMEOUT_MS = "request-timeout-ms";

    private final String sharedSecret;
    private final String bindHost;
    private final int port;
    private final Duration requestTimeout;

    QueryServiceConfig(@NotNull String sharedSecret,
                       @NotNull String bindHost,
                       int port,
                       @NotNull Duration requestTimeout) {
        this.sharedSecret = sharedSecret;
        this.bindHost = bindHost;
        this.port = port;
        this.requestTimeout = requestTimeout;
    }

    /** The shared secret; blank when unset. Blank means the HTTP server is not started. */
    public @NotNull String sharedSecret() {
        return this.sharedSecret;
    }

    public @NotNull String bindHost() {
        return this.bindHost;
    }

    public int port() {
        return this.port;
    }

    /** Cap on a main-thread round trip before a geometry request fails with 504. */
    public @NotNull Duration requestTimeout() {
        return this.requestTimeout;
    }

    /**
     * Whether the HTTP server runs at all. An empty secret fails closed rather than running open:
     * {@code realty-rest} degrades to nulls, which is safe, unlike an unauthenticated query port.
     */
    public boolean httpEnabled() {
        return !this.sharedSecret.isBlank();
    }

    /**
     * Reads the operator's {@code config.yml}, writing the bundled default there first if they have
     * none, and refreshing the reference copy beside it either way.
     */
    public static @NotNull QueryServiceConfig read(@NotNull Path dataFolder) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Path file = dataFolder.resolve(CONFIG_FILE);
        try {
            Files.createDirectories(dataFolder);
            if (!Files.exists(file)) {
                copyBundled(file);
            }
            writeReferenceCopy(dataFolder);
            try (Reader reader = Files.newBufferedReader(file)) {
                return from(YamlConfiguration.loadConfiguration(reader));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read " + CONFIG_FILE, ex);
        }
    }

    static @NotNull QueryServiceConfig from(@NotNull YamlConfiguration config) {
        Objects.requireNonNull(config, "config");
        return new QueryServiceConfig(
                config.getString(SHARED_SECRET, ""),
                config.getString(BIND_HOST, "127.0.0.1"),
                config.getInt(PORT, 8123),
                Duration.ofMillis(config.getLong(REQUEST_TIMEOUT_MS, 1000L)));
    }

    /**
     * Writes {@code defaults/default-config.yml}, overwriting any previous copy. Rewritten on every
     * start so it always shows what a current file looks like; never read back.
     */
    public static void writeReferenceCopy(@NotNull Path dataFolder) throws IOException {
        Path defaults = dataFolder.resolve(DEFAULTS_DIR);
        Files.createDirectories(defaults);
        copyBundled(defaults.resolve(REFERENCE_FILE));
    }

    private static void copyBundled(@NotNull Path target) throws IOException {
        try (InputStream bundled = QueryServiceConfig.class.getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {
            if (bundled == null) {
                throw new IllegalStateException("query-service jar is missing its bundled " + CONFIG_FILE);
            }
            Files.copy(bundled, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
```

Note `QueryServiceModule` is referenced in Javadoc only; it arrives in Task 6. Javadoc `{@link}` to a missing class is a warning, not an error — or use `{@code QueryServiceModule}` until then.

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :realty-paper-adapters:query-service:test`
Expected: BUILD SUCCESSFUL, 6 tests pass.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts realty-paper-adapters/query-service
git commit -m "feat(query-service): add module skeleton and config with reference copy"
```

---

### Task 3: Region geometry — `RegionDimensions` and the main-thread source

**Files:**
- Create: `.../adapter/query/RegionDimensions.java`
- Create: `.../adapter/query/RegionDimensionsSource.java`
- Create: `.../adapter/query/MainThreadDimensionsSource.java`
- Test: `.../adapter/query/RegionDimensionsTest.java`

**Interfaces:**
- Produces: `record RegionDimensions(String shape, int minY, int maxY, List<Point> points)` with nested `record Point(int x, int z)` and `static RegionDimensions fromProtectedRegion(ProtectedRegion)`.
- Produces: `interface RegionDimensionsSource { CompletableFuture<Optional<RegionDimensions>> dimensions(UUID worldId, String regionId); }`. Task 4's handler consumes it; Task 6 constructs `MainThreadDimensionsSource(Executor mainThreadExec)`.

- [ ] **Step 1: Write the failing shape test**

WorldGuard's region classes are plain objects and need no server, so the test uses them directly.

```java
package io.github.md5sha256.realty.adapter.query;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class RegionDimensionsTest {

    @Test
    void aCuboidYieldsFourFootprintCorners() {
        ProtectedCuboidRegion cuboid = new ProtectedCuboidRegion("plot",
                BlockVector3.at(104, 62, -88), BlockVector3.at(131, 140, -61));

        RegionDimensions dims = RegionDimensions.fromProtectedRegion(cuboid);

        Assertions.assertEquals("CUBOID", dims.shape());
        Assertions.assertEquals(62, dims.minY());
        Assertions.assertEquals(140, dims.maxY());
        Assertions.assertEquals(List.of(
                new RegionDimensions.Point(104, -88),
                new RegionDimensions.Point(131, -88),
                new RegionDimensions.Point(131, -61),
                new RegionDimensions.Point(104, -61)), dims.points());
    }

    @Test
    void aPolygonKeepsItsPointsInOrder() {
        ProtectedPolygonalRegion polygon = new ProtectedPolygonalRegion("tri",
                List.of(BlockVector2.at(0, 0), BlockVector2.at(10, 0), BlockVector2.at(5, 8)),
                10, 20);

        RegionDimensions dims = RegionDimensions.fromProtectedRegion(polygon);

        Assertions.assertEquals("POLYGONAL", dims.shape());
        Assertions.assertEquals(10, dims.minY());
        Assertions.assertEquals(20, dims.maxY());
        Assertions.assertEquals(List.of(
                new RegionDimensions.Point(0, 0),
                new RegionDimensions.Point(10, 0),
                new RegionDimensions.Point(5, 8)), dims.points());
    }

    @Test
    void cornersOfAnUnorderedCuboidAreNormalised() {
        // WorldGuard normalises min/max itself; pin that we read those, not the constructor args.
        ProtectedCuboidRegion cuboid = new ProtectedCuboidRegion("plot",
                BlockVector3.at(131, 140, -61), BlockVector3.at(104, 62, -88));
        RegionDimensions dims = RegionDimensions.fromProtectedRegion(cuboid);
        Assertions.assertEquals(new RegionDimensions.Point(104, -88), dims.points().get(0));
        Assertions.assertEquals(62, dims.minY());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :realty-paper-adapters:query-service:test --tests "*RegionDimensionsTest"`
Expected: compilation failure.

- [ ] **Step 3: Write `RegionDimensions`**

```java
package io.github.md5sha256.realty.adapter.query;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A region's footprint and vertical bounds. For a cuboid the points are the four corners of its
 * footprint so a consumer can treat both shapes uniformly. Area and volume are derivable and
 * deliberately not sent.
 */
public record RegionDimensions(@NotNull String shape,
                               int minY,
                               int maxY,
                               @NotNull List<Point> points) {

    public record Point(int x, int z) {
    }

    /** Must be called on the main thread: {@link ProtectedRegion} is not thread-safe. */
    public static @NotNull RegionDimensions fromProtectedRegion(@NotNull ProtectedRegion region) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        if (region.getType() == RegionType.CUBOID) {
            return new RegionDimensions("CUBOID", min.y(), max.y(), List.of(
                    new Point(min.x(), min.z()),
                    new Point(max.x(), min.z()),
                    new Point(max.x(), max.z()),
                    new Point(min.x(), max.z())));
        }
        List<Point> points = region.getPoints().stream()
                .map(p -> new Point(p.x(), p.z()))
                .toList();
        return new RegionDimensions("POLYGONAL", min.y(), max.y(), points);
    }
}
```

If `BlockVector3.y()` does not compile against WorldEdit 7.4.x on this classpath, use `getY()`/`getX()`/`getZ()` (and `BlockVector2.getX()`/`getZ()`); both accessor families exist in 7.3+, the compiler will tell you which is present.

- [ ] **Step 4: Write the source interface and main-thread implementation**

`RegionDimensionsSource.java`:

```java
package io.github.md5sha256.realty.adapter.query;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Answers for a region's live geometry; empty when no such world or region exists. */
@FunctionalInterface
public interface RegionDimensionsSource {

    @NotNull CompletableFuture<Optional<RegionDimensions>> dimensions(@NotNull UUID worldId,
                                                                      @NotNull String regionId);
}
```

`MainThreadDimensionsSource.java`:

```java
package io.github.md5sha256.realty.adapter.query;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Reads WorldGuard on the main thread. The measurement is a handful of O(1) field reads; the thread
 * hop is the cost. The caller applies the timeout, because it owns the request.
 */
public final class MainThreadDimensionsSource implements RegionDimensionsSource {

    private final Executor mainThread;

    public MainThreadDimensionsSource(@NotNull Executor mainThread) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
    }

    @Override
    public @NotNull CompletableFuture<Optional<RegionDimensions>> dimensions(@NotNull UUID worldId,
                                                                             @NotNull String regionId) {
        return CompletableFuture.supplyAsync(() -> readOnMainThread(worldId, regionId), this.mainThread);
    }

    private static @NotNull Optional<RegionDimensions> readOnMainThread(@NotNull UUID worldId,
                                                                         @NotNull String regionId) {
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            return Optional.empty();
        }
        RegionManager manager = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(world));
        if (manager == null) {
            return Optional.empty();
        }
        ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) {
            return Optional.empty();
        }
        return Optional.of(RegionDimensions.fromProtectedRegion(region));
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :realty-paper-adapters:query-service:test --tests "*RegionDimensionsTest"`
Expected: 3 tests pass.

- [ ] **Step 6: Commit**

```bash
git add realty-paper-adapters/query-service
git commit -m "feat(query-service): map WorldGuard regions to a uniform dimensions payload"
```

---

### Task 4: HTTP server, secret filter, and the dimensions route

**Files:**
- Create: `.../adapter/query/ApiException.java`
- Create: `.../adapter/query/json/ErrorResponse.java`
- Create: `.../adapter/query/QueryServiceServer.java`
- Create: `.../adapter/query/DimensionsHandler.java`
- Test: `.../adapter/query/TestServers.java`
- Test: `.../adapter/query/AuthenticationTest.java`
- Test: `.../adapter/query/DimensionsEndpointTest.java`

**Interfaces:**
- Consumes: `RegionDimensionsSource`, `RegionDimensions` (Task 3); `PlayerNameService` (Task 1).
- Produces: `QueryServiceServer(String secret, Duration requestTimeout, RegionDimensionsSource, PlayerNameService)` with `javalin()`, `start(String host, int port)`, `stop()`, constant `SECRET_HEADER = "X-Realty-Secret"`, and `List<String> ROUTES`. Task 5 adds player routes to `registerRoutes()`; Task 6 constructs it.

- [ ] **Step 1: Write the error types**

`ApiException.java` (same shape as `realty-rest`'s, plus `504`):

```java
package io.github.md5sha256.realty.adapter.query;

import org.jetbrains.annotations.NotNull;

/** A failure with a status code and a stable machine-readable code, rendered as {@code ErrorResponse}. */
public final class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    public ApiException(int status, @NotNull String code, @NotNull String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static @NotNull ApiException badRequest(@NotNull String code, @NotNull String message) {
        return new ApiException(400, code, message);
    }

    public static @NotNull ApiException unauthorized() {
        return new ApiException(401, "UNAUTHORIZED", "Missing or wrong X-Realty-Secret header");
    }

    public static @NotNull ApiException notFound(@NotNull String code, @NotNull String message) {
        return new ApiException(404, code, message);
    }

    public static @NotNull ApiException gatewayTimeout(@NotNull String message) {
        return new ApiException(504, "MAIN_THREAD_TIMEOUT", message);
    }

    public int status() {
        return this.status;
    }

    public @NotNull String code() {
        return this.code;
    }
}
```

`json/ErrorResponse.java`:

```java
package io.github.md5sha256.realty.adapter.query.json;

import org.jetbrains.annotations.NotNull;

public record ErrorResponse(@NotNull String error, @NotNull String message) {
}
```

- [ ] **Step 2: Write the test harness and failing tests**

`TestServers.java`:

```java
package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.api.PlayerNameService;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class TestServers {

    static final String SECRET = "hunter2";
    static final UUID WORLD = UUID.fromString("8f4d0000-0000-0000-0000-000000000001");
    static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f64f65c7e1");

    private TestServers() {
    }

    static @NotNull RegionDimensions plot14() {
        return new RegionDimensions("POLYGONAL", 62, 140, List.of(
                new RegionDimensions.Point(104, -88), new RegionDimensions.Point(131, -88),
                new RegionDimensions.Point(131, -61), new RegionDimensions.Point(104, -61)));
    }

    /** Knows exactly one region, {@code downtown_plot_14} in {@link #WORLD}. */
    static @NotNull RegionDimensionsSource oneRegion() {
        return (worldId, regionId) -> CompletableFuture.completedFuture(
                worldId.equals(WORLD) && regionId.equals("downtown_plot_14")
                        ? Optional.of(plot14()) : Optional.empty());
    }

    /** A source whose main thread never ticks: the future never completes. */
    static @NotNull RegionDimensionsSource stalledMainThread() {
        return (worldId, regionId) -> new CompletableFuture<>();
    }

    static @NotNull PlayerNameService twoPlayers() {
        Map<UUID, String> names = Map.of(NOTCH, "Notch", BEDROCK, ".Cool Guy 123");
        return new PlayerNameService() {
            @Override
            public @NotNull CompletableFuture<Optional<String>> nameOf(@NotNull UUID id) {
                return CompletableFuture.completedFuture(Optional.ofNullable(names.get(id)));
            }

            @Override
            public @NotNull CompletableFuture<Optional<UUID>> uuidOf(@NotNull String name) {
                return CompletableFuture.completedFuture(names.entrySet().stream()
                        .filter(e -> e.getValue().equals(name))
                        .map(Map.Entry::getKey)
                        .findFirst());
            }
        };
    }

    static @NotNull QueryServiceServer standard() {
        return new QueryServiceServer(SECRET, Duration.ofSeconds(5), oneRegion(), twoPlayers());
    }

    static @NotNull QueryServiceServer withStalledMainThread(@NotNull Duration timeout) {
        return new QueryServiceServer(SECRET, timeout, stalledMainThread(), twoPlayers());
    }
}
```

`AuthenticationTest.java`:

```java
package io.github.md5sha256.realty.adapter.query;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AuthenticationTest {

    private static final String PATH = "/regions/" + TestServers.WORLD + "/downtown_plot_14/dimensions";

    @Test
    void aMissingSecretIs401() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get(PATH);
            Assertions.assertEquals(401, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"UNAUTHORIZED\""));
        });
    }

    @Test
    void aWrongSecretIs401() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get(PATH, req -> req.header(QueryServiceServer.SECRET_HEADER, "nope"));
            Assertions.assertEquals(401, response.code());
        });
    }

    @Test
    void anUnknownRouteStillRequiresTheSecret() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Assertions.assertEquals(401, client.get("/nothing-here").code());
        });
    }

    @Test
    void healthAnswersWithTheSecret() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get("/health",
                    req -> req.header(QueryServiceServer.SECRET_HEADER, TestServers.SECRET));
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"status\":\"ok\""));
        });
    }
}
```

`DimensionsEndpointTest.java`:

```java
package io.github.md5sha256.realty.adapter.query;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class DimensionsEndpointTest {

    private static okhttp3.Request.Builder auth(okhttp3.Request.Builder req) {
        return req.header(QueryServiceServer.SECRET_HEADER, TestServers.SECRET);
    }

    @Test
    void returnsShapeBoundsAndPoints() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get(
                    "/regions/" + TestServers.WORLD + "/downtown_plot_14/dimensions",
                    DimensionsEndpointTest::auth);
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertEquals(
                    "{\"shape\":\"POLYGONAL\",\"minY\":62,\"maxY\":140,\"points\":["
                            + "{\"x\":104,\"z\":-88},{\"x\":131,\"z\":-88},"
                            + "{\"x\":131,\"z\":-61},{\"x\":104,\"z\":-61}]}",
                    body);
        });
    }

    @Test
    void anUnknownRegionIs404() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get(
                    "/regions/" + TestServers.WORLD + "/plot_9/dimensions", DimensionsEndpointTest::auth);
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"REGION_NOT_FOUND\""));
        });
    }

    @Test
    void aMalformedWorldIdIs400() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get("/regions/not-a-uuid/plot/dimensions", DimensionsEndpointTest::auth);
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"INVALID_WORLD_ID\""));
        });
    }

    @Test
    void aStalledMainThreadIs504NotAHang() {
        QueryServiceServer server = TestServers.withStalledMainThread(Duration.ofMillis(200));
        JavalinTest.test(server.javalin(), (s, client) -> {
            long started = System.nanoTime();
            Response response = client.get(
                    "/regions/" + TestServers.WORLD + "/downtown_plot_14/dimensions",
                    DimensionsEndpointTest::auth);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            Assertions.assertEquals(504, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"MAIN_THREAD_TIMEOUT\""));
            Assertions.assertTrue(elapsedMs < 5_000, "took " + elapsedMs + "ms");
        });
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :realty-paper-adapters:query-service:test --tests "*AuthenticationTest" --tests "*DimensionsEndpointTest"`
Expected: compilation failure — `QueryServiceServer` does not exist.

- [ ] **Step 4: Write `DimensionsHandler`**

```java
package io.github.md5sha256.realty.adapter.query;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/** {@code GET /regions/{worldId}/{regionId}/dimensions}. */
final class DimensionsHandler {

    private final RegionDimensionsSource source;
    private final Duration timeout;

    DimensionsHandler(@NotNull RegionDimensionsSource source, @NotNull Duration timeout) {
        this.source = Objects.requireNonNull(source, "source");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    void handle(@NotNull Context ctx) {
        UUID worldId;
        try {
            worldId = UUID.fromString(ctx.pathParam("worldId"));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_WORLD_ID",
                    "worldId must be a UUID: " + ctx.pathParam("worldId"));
        }
        String regionId = ctx.pathParam("regionId");
        Optional<RegionDimensions> dims;
        try {
            dims = this.source.dimensions(worldId, regionId)
                    .orTimeout(this.timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof TimeoutException) {
                throw ApiException.gatewayTimeout(
                        "Main thread did not answer within " + this.timeout.toMillis() + "ms");
            }
            throw ex;
        }
        ctx.json(dims.orElseThrow(() -> ApiException.notFound("REGION_NOT_FOUND",
                "No region '" + regionId + "' in world " + worldId)));
    }
}
```

Replace the inline `java.util.concurrent.TimeUnit` with an import (`import java.util.concurrent.TimeUnit;`) — the project forbids inline qualified names.

- [ ] **Step 5: Write `QueryServiceServer`**

```java
package io.github.md5sha256.realty.adapter.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.md5sha256.realty.adapter.query.json.ErrorResponse;
import io.github.md5sha256.realty.api.PlayerNameService;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.jetbrains.annotations.NotNull;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
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
            "/regions/{worldId}/{regionId}/dimensions");

    private static final Logger LOGGER = Logger.getLogger(QueryServiceServer.class.getName());
    private static final String HANDLED_ATTRIBUTE = "realty.handled";

    private final byte[] secret;
    private final Duration requestTimeout;
    private final RegionDimensionsSource dimensions;
    private final PlayerNameService names;
    private final Javalin javalin;

    public QueryServiceServer(@NotNull String secret,
                              @NotNull Duration requestTimeout,
                              @NotNull RegionDimensionsSource dimensions,
                              @NotNull PlayerNameService names) {
        if (secret.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.dimensions = Objects.requireNonNull(dimensions, "dimensions");
        this.names = Objects.requireNonNull(names, "names");
        this.javalin = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(new ObjectMapper(), false));
            config.showJavalinBanner = false;
        });
        registerRoutes();
    }

    private void registerRoutes() {
        this.javalin.before(ctx -> {
            String presented = ctx.header(SECRET_HEADER);
            if (presented == null || !MessageDigest.isEqual(
                    presented.getBytes(StandardCharsets.UTF_8), this.secret)) {
                throw ApiException.unauthorized();
            }
        });

        this.javalin.get("/health", ctx -> ctx.json(Map.of("status", "ok")));

        DimensionsHandler dimensionsHandler = new DimensionsHandler(this.dimensions, this.requestTimeout);
        this.javalin.get("/regions/{worldId}/{regionId}/dimensions", dimensionsHandler::handle);

        this.javalin.exception(ApiException.class, (ex, ctx) -> {
            ctx.attribute(HANDLED_ATTRIBUTE, true);
            ctx.status(ex.status()).json(new ErrorResponse(ex.code(), ex.getMessage()));
        });
        this.javalin.exception(Exception.class, (ex, ctx) -> {
            ctx.attribute(HANDLED_ATTRIBUTE, true);
            LOGGER.log(Level.SEVERE, "Unhandled failure serving " + ctx.path(), ex);
            ctx.status(500).json(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
        });
        this.javalin.error(404, ctx -> {
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
```

Task 5 adds the `PlayerNamesHandler` registration and its three paths to `ROUTES`; `this.names` is stored now so the constructor signature does not change between tasks.

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :realty-paper-adapters:query-service:test`
Expected: all tests pass (config 6, dimensions 3, auth 4, endpoint 4). If the secret check for `/nothing-here` returns 404 instead of 401, Javalin's `before` filter did not run for unmatched routes: switch the filter to `this.javalin.beforeMatched(...)` plus an explicit `this.javalin.before("/*", ...)` — the `before(Handler)` form with no path matches all requests in Javalin 6 and is expected to work.

- [ ] **Step 7: Commit**

```bash
git add realty-paper-adapters/query-service
git commit -m "feat(query-service): serve region dimensions over a secret-gated HTTP endpoint"
```

---

### Task 5: Player name routes

**Files:**
- Create: `.../adapter/query/PlayerNamesHandler.java`
- Create: `.../adapter/query/json/PlayerName.java`
- Create: `.../adapter/query/json/NamesRequest.java`
- Create: `.../adapter/query/json/UuidsRequest.java`
- Modify: `.../adapter/query/QueryServiceServer.java` (`ROUTES`, `registerRoutes`)
- Test: `.../adapter/query/PlayerNamesEndpointTest.java`

**Interfaces:**
- Consumes: `PlayerNameService` (Task 1), `QueryServiceServer` (Task 4).
- Produces the wire contract `realty-rest`'s client (a later cycle) will call:
  - `GET /players/{uuid}/name` → `{"id":"...","name":"Notch"|null}`
  - `POST /players/names` body `{"ids":["..."]}` → `{"players":[{"id","name"}]}` in request order, nulls kept
  - `POST /players/uuids` body `{"names":["..."]}` → `{"players":[{"id"|null,"name"}]}` in request order

- [ ] **Step 1: Write the failing tests**

```java
package io.github.md5sha256.realty.adapter.query;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class PlayerNamesEndpointTest {

    private static okhttp3.Request.Builder auth(okhttp3.Request.Builder req) {
        return req.header(QueryServiceServer.SECRET_HEADER, TestServers.SECRET);
    }

    @Test
    void singleLookupResolvesAJavaEditionName() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get("/players/" + TestServers.NOTCH + "/name",
                    PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals(
                    "{\"id\":\"" + TestServers.NOTCH + "\",\"name\":\"Notch\"}", response.body().string());
        });
    }

    @Test
    void singleLookupOfAnUnknownUuidIsNullNameNot404() {
        UUID unknown = UUID.randomUUID();
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get("/players/" + unknown + "/name", PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"id\":\"" + unknown + "\",\"name\":null}", response.body().string());
        });
    }

    @Test
    void singleLookupWithAMalformedUuidIs400() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get("/players/steve/name", PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"INVALID_UUID\""));
        });
    }

    @Test
    void batchNamesKeepsOrderAndNullsForUnknownIds() {
        UUID unknown = UUID.randomUUID();
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.post("/players/names",
                    "{\"ids\":[\"" + TestServers.BEDROCK + "\",\"" + unknown + "\",\"" + TestServers.NOTCH + "\"]}",
                    PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"players\":["
                            + "{\"id\":\"" + TestServers.BEDROCK + "\",\"name\":\".Cool Guy 123\"},"
                            + "{\"id\":\"" + unknown + "\",\"name\":null},"
                            + "{\"id\":\"" + TestServers.NOTCH + "\",\"name\":\"Notch\"}]}",
                    response.body().string());
        });
    }

    @Test
    void batchUuidsResolvesBedrockNamesWithSpaces() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.post("/players/uuids",
                    "{\"names\":[\".Cool Guy 123\",\"nobody\",\"Notch\"]}", PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"players\":["
                            + "{\"id\":\"" + TestServers.BEDROCK + "\",\"name\":\".Cool Guy 123\"},"
                            + "{\"id\":null,\"name\":\"nobody\"},"
                            + "{\"id\":\"" + TestServers.NOTCH + "\",\"name\":\"Notch\"}]}",
                    response.body().string());
        });
    }

    @Test
    void batchWithAMalformedBodyIs400() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.post("/players/names", "{\"ids\":[\"steve\"]}", PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(400, response.code());
            Response notJson = client.post("/players/uuids", "nope", PlayerNamesEndpointTest::auth);
            Assertions.assertEquals(400, notJson.code());
        });
    }

    @Test
    void batchRequiresTheSecretToo() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Assertions.assertEquals(401, client.post("/players/names", "{\"ids\":[]}").code());
        });
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :realty-paper-adapters:query-service:test --tests "*PlayerNamesEndpointTest"`
Expected: 404s / compilation failures — the routes and handler do not exist.

- [ ] **Step 3: Write the JSON records**

`json/PlayerName.java`:

```java
package io.github.md5sha256.realty.adapter.query.json;

import org.jetbrains.annotations.Nullable;

/** One identity. Either side may be null when unresolved; neither is omitted. */
public record PlayerName(@Nullable String id, @Nullable String name) {
}
```

`json/NamesRequest.java`:

```java
package io.github.md5sha256.realty.adapter.query.json;

import java.util.List;

public record NamesRequest(List<String> ids) {
}
```

`json/UuidsRequest.java`:

```java
package io.github.md5sha256.realty.adapter.query.json;

import java.util.List;

public record UuidsRequest(List<String> names) {
}
```

- [ ] **Step 4: Write `PlayerNamesHandler`**

```java
package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.adapter.query.json.NamesRequest;
import io.github.md5sha256.realty.adapter.query.json.PlayerName;
import io.github.md5sha256.realty.adapter.query.json.UuidsRequest;
import io.github.md5sha256.realty.api.PlayerNameService;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The player routes. Batch forms exist because a ten-region response resolved one name at a
 * time would turn an N+1 removed from SQL into an N+1 over the network. Bodies rather than query
 * strings, because a Floodgate name such as {@code .Cool Guy 123} is not reliably URL-safe.
 */
final class PlayerNamesHandler {

    private final PlayerNameService names;

    PlayerNamesHandler(@NotNull PlayerNameService names) {
        this.names = Objects.requireNonNull(names, "names");
    }

    void single(@NotNull Context ctx) {
        UUID id = parseUuid(ctx.pathParam("uuid"));
        Optional<String> name = this.names.nameOf(id).join();
        ctx.json(new PlayerName(id.toString(), name.orElse(null)));
    }

    void names(@NotNull Context ctx) {
        NamesRequest request = body(ctx, NamesRequest.class);
        if (request.ids() == null) {
            throw ApiException.badRequest("INVALID_BODY", "Body must be {\"ids\":[...]}");
        }
        List<UUID> ids = new ArrayList<>(request.ids().size());
        for (String raw : request.ids()) {
            ids.add(parseUuid(raw));
        }
        Map<UUID, Optional<String>> resolved = this.names.namesOf(ids).join();
        List<PlayerName> players = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            players.add(new PlayerName(id.toString(), resolved.get(id).orElse(null)));
        }
        ctx.json(Map.of("players", players));
    }

    void uuids(@NotNull Context ctx) {
        UuidsRequest request = body(ctx, UuidsRequest.class);
        if (request.names() == null) {
            throw ApiException.badRequest("INVALID_BODY", "Body must be {\"names\":[...]}");
        }
        Map<String, Optional<UUID>> resolved = this.names.uuidsOf(request.names()).join();
        List<PlayerName> players = new ArrayList<>(request.names().size());
        for (String name : request.names()) {
            players.add(new PlayerName(resolved.get(name).map(UUID::toString).orElse(null), name));
        }
        ctx.json(Map.of("players", players));
    }

    private static <T> @NotNull T body(@NotNull Context ctx, @NotNull Class<T> type) {
        try {
            return ctx.bodyAsClass(type);
        } catch (RuntimeException ex) {
            throw ApiException.badRequest("INVALID_BODY", "Body is not valid JSON for this route");
        }
    }

    private static @NotNull UUID parseUuid(@NotNull String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_UUID", "Not a UUID: " + raw);
        }
    }
}
```

`Map.of("players", players)` serialises as `{"players":[...]}`; the name lookups are already async-safe, so `join()` on a Javalin worker thread is acceptable here (no main-thread hop is involved, per the spec's *Threading* section).

- [ ] **Step 5: Register the routes**

In `QueryServiceServer.ROUTES` add, after the dimensions path:

```java
            "/players/{uuid}/name",
            "/players/names",
            "/players/uuids");
```

In `registerRoutes()`, after the dimensions route:

```java
        PlayerNamesHandler playerNames = new PlayerNamesHandler(this.names);
        this.javalin.get("/players/{uuid}/name", playerNames::single);
        this.javalin.post("/players/names", playerNames::names);
        this.javalin.post("/players/uuids", playerNames::uuids);
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :realty-paper-adapters:query-service:test`
Expected: all pass. If Jackson serialises `Map.of("players", ...)` fine but record field order differs, the exact-string assertions will show it; records serialise in declaration order with Jackson 2.18, so `{"id","name"}` is expected.

- [ ] **Step 7: Commit**

```bash
git add realty-paper-adapters/query-service
git commit -m "feat(query-service): add single and batch player name and UUID routes"
```

---

### Task 6: The module lifecycle, reload, and dev staging

**Files:**
- Create: `.../adapter/query/QueryServiceModule.java`
- Modify: `realty-paper/build.gradle.kts` (`runServer` block, ~lines 202–235)
- Test: `.../adapter/query/RouteSurfaceTest.java`

**Interfaces:**
- Consumes: `QueryServiceConfig` (Task 2), `MainThreadDimensionsSource` (Task 3), `QueryServiceServer` (Task 4/5), `Realty.executorState().mainThreadExec()`, `Realty.paperApi().playerNameService()` (Task 1).

The module itself needs a live Bukkit server and is verified by the smoke test in Task 7. What *is* unit-testable is that the route surface documented in the README matches what the server registers.

- [ ] **Step 1: Write the failing route-surface test**

`ROUTES` is the list the README documents. The test proves every declared path is actually served (no `NOT_FOUND` body) and that the catch-all still fires for an undeclared one, without reaching into Javalin internals — the same hand-list approach `realty-rest`'s `OpenApiConformanceTest` takes.

```java
package io.github.md5sha256.realty.adapter.query;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RouteSurfaceTest {

    private static okhttp3.Request.Builder auth(okhttp3.Request.Builder req) {
        return req.header(QueryServiceServer.SECRET_HEADER, TestServers.SECRET);
    }

    @Test
    void everyDeclaredRouteIsServed() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            for (String route : QueryServiceServer.ROUTES) {
                String path = route
                        .replace("{worldId}", TestServers.WORLD.toString())
                        .replace("{regionId}", "downtown_plot_14")
                        .replace("{uuid}", TestServers.NOTCH.toString());
                Response response = route.startsWith("/players/names") || route.startsWith("/players/uuids")
                        ? client.post(path, "{\"ids\":[],\"names\":[]}", RouteSurfaceTest::auth)
                        : client.get(path, RouteSurfaceTest::auth);
                String body = response.body().string();
                Assertions.assertFalse(body.contains("\"error\":\"NOT_FOUND\""),
                        route + " is declared in ROUTES but not registered: " + body);
                Assertions.assertNotEquals(405, response.code(), route + " registered with the wrong method");
            }
        });
    }

    @Test
    void anUndeclaredRouteFallsThroughToNotFound() {
        JavalinTest.test(TestServers.standard().javalin(), (server, client) -> {
            Response response = client.get("/regions", RouteSurfaceTest::auth);
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\":\"NOT_FOUND\""));
        });
    }
}
```

Jackson ignores the extra field in the shared POST body only if the mapper is configured to; if `INVALID_BODY` comes back for the batch routes, add `.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)` to the `ObjectMapper` in `QueryServiceServer` — the internal client will never send unknown fields, so this is harmless and matches `realty-rest`'s lenient default.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :realty-paper-adapters:query-service:test --tests "*RouteSurfaceTest"`
Expected: pass or fail depending on whether the accessor compiles; if it passes immediately that is fine — its job is to guard future drift. Do not skip it.

- [ ] **Step 3: Write `QueryServiceModule`**

```java
package io.github.md5sha256.realty.adapter.query;

import com.minecraftcitiesnetwork.pluginInfrastructure.modules.SimplePluginModule;
import io.github.md5sha256.realty.Realty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Runs the private query endpoint {@code realty-rest} calls for live geometry and player names.
 *
 * <p>Registers no commands: modules start after commands are registered and Paper accepts no new
 * Brigadier commands at that point. Has no write path.</p>
 */
public final class QueryServiceModule extends SimplePluginModule<Realty> {

    private @Nullable QueryServiceServer server;

    @Override
    public void initialize(@NotNull Realty plugin, @NotNull Path dataFolder) {
        super.initialize(plugin, dataFolder);
        start(plugin, QueryServiceConfig.read(dataFolder));
    }

    @Override
    public void shutdown(@NotNull Realty plugin) {
        stop();
        super.shutdown(plugin);
    }

    /**
     * A reload re-reads {@code config.yml} and restarts the server on the new settings.
     * {@code reloadable: true} in the manifest does nothing by itself; this override is what makes
     * {@code /realty module reload query-service} take effect.
     */
    @Override
    public @NotNull CompletableFuture<Void> reload(@NotNull Realty plugin) {
        stop();
        start(plugin, QueryServiceConfig.read(dataFolder()));
        return CompletableFuture.completedFuture(null);
    }

    private void start(@NotNull Realty plugin, @NotNull QueryServiceConfig config) {
        Logger log = plugin.getLogger();
        if (!config.httpEnabled()) {
            log.warning("query-service: shared-secret is empty in " + dataFolder().resolve("config.yml")
                    + ", so the query endpoint is NOT running. realty-rest will serve null geometry and "
                    + "null player names until a secret is set here and matched in REALTY_REST_MODULE_SECRET.");
            return;
        }
        QueryServiceServer created = new QueryServiceServer(
                config.sharedSecret(),
                config.requestTimeout(),
                new MainThreadDimensionsSource(plugin.executorState().mainThreadExec()),
                plugin.paperApi().playerNameService());
        created.start(config.bindHost(), config.port());
        this.server = created;
        log.info("query-service listening on http://" + config.bindHost() + ":" + config.port());
    }

    private void stop() {
        QueryServiceServer running = this.server;
        this.server = null;
        if (running != null) {
            running.stop();
        }
    }
}
```

`ModuleLifecycleManager` logs a `RuntimeException` from `initialize` as severe and unloads the module, so a port already in use surfaces as a clear startup failure rather than a silent no-op; nothing is registered before the server is constructed, so there is nothing to leak on that path.

- [ ] **Step 4: Stage the jar in `runServer`**

In `realty-paper/build.gradle.kts` inside the `runServer` block, after the `playerNotificationsAdapterJar` declaration:

```kotlin
        // The REST query seam. Staged so the module/realty-rest pair can be smoke-tested locally.
        val queryServiceJar = project(":realty-paper-adapters:query-service")
                .tasks.named("shadowJar", AbstractArchiveTask::class).flatMap { it.archiveFile }
```

Add `queryServiceJar` to the `inputs.files(...)` call, and in `doFirst` after the player-notifications copy:

```kotlin
            queryServiceJar.get().asFile.copyTo(moduleDir.resolve("query-service.jar"), overwrite = true)
```

- [ ] **Step 5: Build everything**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. Then inspect the shaded jar:

```bash
unzip -l realty-paper-adapters/query-service/build/libs/query-service-*.jar | grep -E "module-manifest.yml|config.yml|libraries/io/javalin/Javalin.class|org/slf4j" 
```

Expected: the manifest and `config.yml` at the root, Javalin under `io/github/md5sha256/realty/adapter/query/libraries/`, and **no** `org/slf4j` entries.

- [ ] **Step 6: Commit**

```bash
git add realty-paper-adapters/query-service realty-paper/build.gradle.kts
git commit -m "feat(query-service): add the module lifecycle with reload, stage it for runServer"
```

---

### Task 7: Documentation and smoke test

**Files:**
- Modify: `README.md` (module table ~line 63, and a new subsection after the essentials-adapter one)
- Modify: `CLAUDE.md` (module list in *Project Overview*, and *Modules* section)

- [ ] **Step 1: README**

Add a table row:

```markdown
| `realty-paper-adapters/query-service` | Private HTTP endpoint serving live WorldGuard geometry and player names to `realty-rest` |
```

And a subsection after the essentials-adapter paragraph:

```markdown
### query-service

`realty-rest` runs outside the game server and can only read MariaDB, which holds neither
WorldGuard geometry nor player names. `query-service` answers for both from inside the server over a
private, secret-gated HTTP endpoint. Its `config.yml` (`plugins/Realty/modules/query-service/config.yml`):

| Key | Default | Meaning |
|---|---|---|
| `shared-secret` | *(empty)* | Required in every request's `X-Realty-Secret` header. **Empty disables the endpoint** rather than running it open; set the same value in `realty-rest`'s `REALTY_REST_MODULE_SECRET`. |
| `bind-host` | `127.0.0.1` | Localhost by default. Widen only if `realty-rest` runs on another host, and put a reverse proxy (with TLS) in front if that crosses a network you do not control. |
| `port` | `8123` | |
| `request-timeout-ms` | `1000` | Geometry is read on the main thread; a request that cannot get a tick within this budget returns `504`. |

Routes (all require the secret; unversioned because both sides ship from this repository):

| Route | Answers |
|---|---|
| `GET /health` | `{"status":"ok"}` |
| `GET /regions/{worldId}/{regionId}/dimensions` | `shape` (`CUBOID`/`POLYGONAL`), `minY`, `maxY`, ordered footprint `points` — read live, never cached. `404` if WorldGuard has no such region. |
| `GET /players/{uuid}/name` | `{"id","name"}`, `name` null when unknown |
| `POST /players/names` `{"ids":[…]}` | `{"players":[{"id","name"}]}` in request order, unknowns kept with null `name` |
| `POST /players/uuids` `{"names":[…]}` | `{"players":[{"id","name"}]}` in request order, unknowns kept with null `id`. A body, not a query string, because Floodgate names like `.Cool Guy 123` are not URL-safe. |

Names come from the server's own usercache first, so Bedrock/Floodgate players resolve; Mojang is
only consulted for a UUID the server has never seen. The same lookups are available in-process to
other plugins as the `PlayerNameService` Bukkit service.

`/realty module reload query-service` re-reads the config and restarts the endpoint.
```

- [ ] **Step 2: CLAUDE.md**

In *Project Overview*'s adapters bullet, add `realty-paper-adapters/query-service` to the list of notification-adjacent modules (it is not a delivery module — say so). In *Modules*, add:

```markdown
`query-service` is the in-server half of the REST seam (spec:
`docs/superpowers/specs/2026-09-02-realty-query-service-design.md`). It embeds Javalin, **relocated**
under `io.github.md5sha256.realty.adapter.query.libraries` because the module class loader's parent
chain can resolve classes from other plugins' jars; `slf4j-api` is deliberately not shaded so it binds
to Paper's Log4j. Geometry is read live on the main thread via `MainThreadDimensionsSource` (there is
no WorldGuard lifecycle event to keep a projection correct); names go through
`RealtyPaperApi.playerNameService()`, the `PlayerNameService` in `realty-paper-api` that wraps
`SquirrelIdUsernameResolver`. An empty `shared-secret` leaves the server unstarted and logs a
`WARNING`. It overrides `reload()`; `startModules()`'s "no delivery module" warning does not count it.
```

Also update the sentence "`runServer` still stages all three into `run/plugins/Realty/modules`" to "all four".

- [ ] **Step 3: Smoke test on the dev server**

Run: `./gradlew runServer` (Docker must be up). After the server reports `Plugin enabled successfully`:

1. Confirm the log shows the `WARNING` about the empty secret and that `run/plugins/Realty/modules/query-service/defaults/default-config.yml` exists.
2. Stop the server. Set `shared-secret: "dev"` in `run/plugins/Realty/modules/query-service/config.yml`. Start again. Confirm `query-service listening on http://127.0.0.1:8123`.
3. From another terminal:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8123/health              # 401
curl -s -H "X-Realty-Secret: dev" http://127.0.0.1:8123/health                     # {"status":"ok"}
curl -s -H "X-Realty-Secret: dev" -X POST -d '{"ids":["069a79f4-44e9-4726-a5be-fca90e38aaf5"]}' http://127.0.0.1:8123/players/names
```

4. In game, create a region (`/rg define smoke`) and hit `/regions/<world uuid>/smoke/dimensions` with the secret — expect `CUBOID` with four points. The world UUID is in the `RealtyWorld` table or `/v1/worlds` on a running `realty-rest`.
5. Run `/realty module reload query-service` and confirm the listening line logs again.

Record the outcomes (pass/fail per step) in the commit message body if anything deviated.

- [ ] **Step 4: Full verification**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, every subproject's tests green.

- [ ] **Step 5: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "docs: document the query-service module and PlayerNameService"
```

---

## Self-review notes

- **Spec coverage:** config + reference copy (T2); dimensions route, cuboid→4 corners, 404 (T3/T4); secret, 401, localhost default (T2/T4); batch names/uuids, nulls kept, body not query string, Bedrock fixtures (T5); main-thread hop + 504 (T3/T4); `PlayerNameService` in `realty-paper-api`, one implementation two doors, no call-site migration (T1); lifecycle start-in-initialize / stop-in-shutdown awaiting Jetty, `reload()` override, no commands (T6); tests listed in the spec's *Testing* section each map to a test file above. `/health` is an addition the companion REST spec's health endpoint needs in order to report module reachability; it is documented in T7.
- **Known limitation, stated deliberately:** WorldGuard permits `/` in region IDs and a path segment cannot carry one unencoded. The spec chose path segments for this internal seam; a caller with such an ID must percent-encode it, and Javalin's default does not match `%2F` inside a segment. Revisit if a real server has such IDs.
- **Type consistency:** `RegionDimensionsSource.dimensions(UUID, String)` in T3/T4/T6; `QueryServiceServer(String, Duration, RegionDimensionsSource, PlayerNameService)` in T4/T5/T6; `PlayerNameService.nameOf/uuidOf/namesOf/uuidsOf` in T1/T5.
