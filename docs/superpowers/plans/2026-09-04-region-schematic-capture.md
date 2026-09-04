# Region Schematic Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an operator capture a WorldGuard region's blocks as a Sponge Schematic v3, store it in MariaDB, and serve the raw bytes over `realty-rest` for a TypeScript frontend to render.

**Architecture:** A new `/realty schematic capture <region> [--force]` command in `realty-paper` copies the region's bounds into a WorldEdit `BlockArrayClipboard` on the main thread and writes Sponge v3 bytes; `realty-backend` persists them as a `LONGBLOB` in a new `RealtySchematic` table keyed by `realtyRegionId`; `realty-rest` serves them at `GET /v1/region/schematic?world=&region=`.

**Tech Stack:** Java 25, Gradle (multi-module), MyBatis + MariaDB, Incendo Cloud commands, WorldEdit clipboard API (already on the classpath), Javalin, JUnit 5, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-04-region-schematic-capture-design.md`

## Global Constraints

- **No new build dependency.** WorldEdit 7.3.18 resolves transitively through the existing `compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.18")` in `realty-paper/build.gradle.kts`. Do not add a FAWE or WorldEdit coordinate.
- **SQL in mappers uses Java text blocks (`"""`).** Never single-line strings or `+` concatenation.
- **No wildcard imports, no static imports.** Every import is an explicit single class. Use `Assertions.assertEquals(...)`, not a static-imported `assertEquals(...)`.
- **No fully-qualified class names inline** — use import statements.
- **Migrations must be registered.** A new `V17__*.sql` file is inert until added to `MariaSchemaMigrator.DEFAULT_MIGRATIONS`.
- **Permissions must be declared.** Every new permission node goes in `realty-paper/src/main/resources/paper-plugin.yml`.
- **Command-facing mapper methods take `(String worldGuardRegionId, UUID worldId)`** and JOIN through `RealtyRegion` internally — never make the caller resolve `realtyRegionId` first.
- **Bukkit permission checks (`hasPermission`) run on the main thread**, never inside an async callback.
- **Durations rendered for players use `DurationFormatter`**, never `Duration.toString()`.
- **Column type for UUIDs is `UUID`**, matching `V16__realty_worlds.sql` — not `BINARY(16)`.
- **Permission node shape is `realty.command.<group>.<sub>`**, e.g. `realty.command.set.price`.
- Build/test commands: `./gradlew :realty-backend:test`, `./gradlew :realty-paper:test`, `./gradlew :realty-rest:test`, `./gradlew build`.
- `realty-backend` tests use Testcontainers and need a working Docker daemon.

---

## File Structure

**`realty-backend-api`**
- Create `src/main/java/io/github/md5sha256/realty/database/entity/RealtySchematicEntity.java` — the row record.

**`realty-backend`**
- Create `src/main/resources/sql/migrations/V17__realty_schematics.sql` — the DDL.
- Modify `src/main/java/io/github/md5sha256/realty/database/maria/MariaSchemaMigrator.java` — register V17.
- Create `src/main/java/io/github/md5sha256/realty/database/mapper/RealtySchematicMapper.java` — signatures only.
- Create `src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaRealtySchematicMapper.java` — the SQL.
- Modify `src/main/java/io/github/md5sha256/realty/database/SqlSessionWrapper.java` — accessor.
- Modify `src/main/java/io/github/md5sha256/realty/database/maria/MariaSqlSession.java` — accessor impl.
- Create `src/test/java/io/github/md5sha256/realty/database/RealtySchematicMapperTest.java`.

**`realty-backend-api` / `realty-backend` (backend surface)**
- Modify `realty-backend-api/src/main/java/io/github/md5sha256/realty/api/RealtyBackend.java` — two methods.
- Modify `realty-backend/src/main/java/io/github/md5sha256/realty/database/RealtyBackendImpl.java` — implement them.

**`realty-paper`**
- Create `src/main/java/io/github/md5sha256/realty/schematic/RegionSchematicWriter.java` — bounds → Sponge v3 bytes. Pure WorldEdit, no Bukkit command plumbing, so it is unit-testable on its own.
- Create `src/main/java/io/github/md5sha256/realty/schematic/CaptureCooldown.java` — the in-memory per-region cooldown. Split out so cooldown logic is testable without Bukkit.
- Create `src/main/java/io/github/md5sha256/realty/command/SchematicCommandGroup.java` — the command bean.
- Modify `src/main/java/io/github/md5sha256/realty/settings/Settings.java` — new cooldown key.
- Modify `src/main/resources/config.yml` — new key + regenerate `defaults/`.
- Modify `src/main/java/io/github/md5sha256/realty/localisation/MessageKeys.java` and `src/main/resources/messages.yml` — new message keys.
- Modify `src/main/resources/paper-plugin.yml` — two permissions.
- Modify `src/main/java/io/github/md5sha256/realty/Realty.java` — register the bean.
- Create `src/test/java/io/github/md5sha256/realty/schematic/CaptureCooldownTest.java`.

**`realty-rest`**
- Create `src/main/java/io/github/md5sha256/realty/rest/RegionSchematicHandler.java`.
- Modify `src/main/java/io/github/md5sha256/realty/rest/RealtyRestServer.java` — route + `ROUTES`.
- Modify `src/main/java/io/github/md5sha256/realty/rest/SchemaVersionCheck.java` — bump to 17.
- Modify `src/main/resources/openapi.yaml` — document the path.
- Create `src/test/java/io/github/md5sha256/realty/rest/RegionSchematicEndpointTest.java`.

---

### Task 1: Schema, entity and mapper

**Files:**
- Create: `realty-backend-api/src/main/java/io/github/md5sha256/realty/database/entity/RealtySchematicEntity.java`
- Create: `realty-backend/src/main/resources/sql/migrations/V17__realty_schematics.sql`
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/MariaSchemaMigrator.java:57`
- Create: `realty-backend/src/main/java/io/github/md5sha256/realty/database/mapper/RealtySchematicMapper.java`
- Create: `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaRealtySchematicMapper.java`
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/SqlSessionWrapper.java`
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/MariaSqlSession.java`
- Test: `realty-backend/src/test/java/io/github/md5sha256/realty/database/RealtySchematicMapperTest.java`

**Interfaces:**
- Consumes: nothing (first task).
- Produces:
  - `RealtySchematicEntity(int realtyRegionId, byte[] data, LocalDateTime capturedAt, UUID capturedBy)`
  - `RealtySchematicMapper.upsert(String worldGuardRegionId, UUID worldId, byte[] data, LocalDateTime capturedAt, UUID capturedBy)` returning `int` (rows affected; `0` means the region is not registered)
  - `RealtySchematicMapper.selectByWorldGuardRegion(String worldGuardRegionId, UUID worldId)` returning `@Nullable RealtySchematicEntity`
  - `SqlSessionWrapper.realtySchematicMapper()`

- [ ] **Step 1: Write the failing test**

Create `realty-backend/src/test/java/io/github/md5sha256/realty/database/RealtySchematicMapperTest.java`.

Note `AbstractDatabaseTest` is package-private in `io.github.md5sha256.realty.database`, exposes `protected static Database database`, and truncates tables between tests. A schematic row needs a registered region first, so each test registers one via `realtyRegionMapper()`.

```java
package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.RealtySchematicEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

class RealtySchematicMapperTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000aa");
    private static final UUID CAPTURED_BY = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000bb");
    private static final LocalDateTime CAPTURED_AT = LocalDateTime.of(2026, 9, 4, 12, 30, 0);

    @Test
    void upsertThenSelectReturnsTheStoredBytes() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyRegionMapper().registerWorldGuardRegion("plot_a", WORLD_ID);

            int rows = session.realtySchematicMapper()
                    .upsert("plot_a", WORLD_ID, new byte[]{1, 2, 3}, CAPTURED_AT, CAPTURED_BY);
            Assertions.assertEquals(1, rows);

            RealtySchematicEntity found =
                    session.realtySchematicMapper().selectByWorldGuardRegion("plot_a", WORLD_ID);
            Assertions.assertNotNull(found);
            Assertions.assertArrayEquals(new byte[]{1, 2, 3}, found.data());
            Assertions.assertEquals(CAPTURED_AT, found.capturedAt());
            Assertions.assertEquals(CAPTURED_BY, found.capturedBy());
        }
    }

    @Test
    void upsertTwiceReplacesTheSchematicRatherThanInserting() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyRegionMapper().registerWorldGuardRegion("plot_a", WORLD_ID);
            session.realtySchematicMapper()
                    .upsert("plot_a", WORLD_ID, new byte[]{1}, CAPTURED_AT, CAPTURED_BY);
            session.realtySchematicMapper()
                    .upsert("plot_a", WORLD_ID, new byte[]{9, 9}, CAPTURED_AT.plusHours(1), CAPTURED_BY);

            RealtySchematicEntity found =
                    session.realtySchematicMapper().selectByWorldGuardRegion("plot_a", WORLD_ID);
            Assertions.assertNotNull(found);
            Assertions.assertArrayEquals(new byte[]{9, 9}, found.data());
            Assertions.assertEquals(CAPTURED_AT.plusHours(1), found.capturedAt());
        }
    }

    @Test
    void selectReturnsNullWhenTheRegionHasNoSchematic() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyRegionMapper().registerWorldGuardRegion("plot_a", WORLD_ID);
            Assertions.assertNull(
                    session.realtySchematicMapper().selectByWorldGuardRegion("plot_a", WORLD_ID));
        }
    }

    @Test
    void upsertAffectsNoRowsForAnUnregisteredRegion() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            int rows = session.realtySchematicMapper()
                    .upsert("never_registered", WORLD_ID, new byte[]{1}, CAPTURED_AT, CAPTURED_BY);
            Assertions.assertEquals(0, rows);
        }
    }

    @Test
    void aRegionInAnotherWorldDoesNotShareASchematic() {
        UUID otherWorld = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000cc");
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyRegionMapper().registerWorldGuardRegion("plot_a", WORLD_ID);
            session.realtyRegionMapper().registerWorldGuardRegion("plot_a", otherWorld);
            session.realtySchematicMapper()
                    .upsert("plot_a", WORLD_ID, new byte[]{7}, CAPTURED_AT, CAPTURED_BY);

            Assertions.assertNull(
                    session.realtySchematicMapper().selectByWorldGuardRegion("plot_a", otherWorld));
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :realty-backend:test --tests "*RealtySchematicMapperTest*"`
Expected: FAIL — compilation error, `RealtySchematicEntity` and `realtySchematicMapper()` do not exist.

- [ ] **Step 3: Create the entity record**

`realty-backend-api/src/main/java/io/github/md5sha256/realty/database/entity/RealtySchematicEntity.java`:

```java
package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal entity record mapping to the {@code RealtySchematic} DDL table.
 *
 * <p>One row per region: a re-capture replaces the previous schematic rather than
 * adding a version, so there is no history to page through.</p>
 *
 * @param realtyRegionId The {@code RealtyRegion} this schematic was captured from
 * @param data           Sponge Schematic v3 bytes, as written by WorldEdit
 * @param capturedAt     When the capture ran
 * @param capturedBy     Who ran the capture
 */
public record RealtySchematicEntity(
        int realtyRegionId,
        byte @NotNull [] data,
        @NotNull LocalDateTime capturedAt,
        @NotNull UUID capturedBy
) {
}
```

- [ ] **Step 4: Write the migration**

`realty-backend/src/main/resources/sql/migrations/V17__realty_schematics.sql`:

```sql
CREATE TABLE IF NOT EXISTS RealtySchematic
(
    realtyRegionId INT      NOT NULL PRIMARY KEY,
    data           LONGBLOB NOT NULL,
    capturedAt     DATETIME NOT NULL,
    capturedBy     UUID     NOT NULL
);
```

- [ ] **Step 5: Register the migration**

In `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/MariaSchemaMigrator.java`, add a trailing entry to `DEFAULT_MIGRATIONS` (add a comma after the V16 line):

```java
            new MigrationStep(16, "realty worlds", "V16__realty_worlds.sql"),
            new MigrationStep(17, "region schematics", "V17__realty_schematics.sql")
```

- [ ] **Step 6: Write the base mapper interface**

`realty-backend/src/main/java/io/github/md5sha256/realty/database/mapper/RealtySchematicMapper.java`:

```java
package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.RealtySchematicEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base mapper interface for the {@code RealtySchematic} table. SQL annotations are
 * provided by database-specific sub-interfaces.
 *
 * <p>Both methods are addressed by WorldGuard region plus world and join through
 * {@code RealtyRegion} internally, so callers never resolve a {@code realtyRegionId}
 * first.</p>
 *
 * @see RealtySchematicEntity
 */
public interface RealtySchematicMapper {

    /**
     * Stores {@code data} as the region's schematic, replacing any previous one.
     *
     * @return rows affected; {@code 0} when no such region is registered
     */
    int upsert(@NotNull String worldGuardRegionId,
               @NotNull UUID worldId,
               byte @NotNull [] data,
               @NotNull LocalDateTime capturedAt,
               @NotNull UUID capturedBy);

    @Nullable RealtySchematicEntity selectByWorldGuardRegion(@NotNull String worldGuardRegionId,
                                                            @NotNull UUID worldId);

}
```

- [ ] **Step 7: Write the MariaDB mapper**

`realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaRealtySchematicMapper.java`.

The insert selects `realtyRegionId` from `RealtyRegion` rather than taking it as a parameter, which is what makes an unregistered region affect zero rows instead of inserting an orphan:

```java
package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.RealtySchematicEntity;
import io.github.md5sha256.realty.database.mapper.RealtySchematicMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

public interface MariaRealtySchematicMapper extends RealtySchematicMapper {

    @Override
    @Insert("""
            INSERT INTO RealtySchematic (realtyRegionId, data, capturedAt, capturedBy)
            SELECT r.realtyRegionId, #{data}, #{capturedAt}, #{capturedBy}
            FROM RealtyRegion r
            WHERE r.worldGuardRegionId = #{worldGuardRegionId}
              AND r.worldId = #{worldId}
            ON DUPLICATE KEY UPDATE data = #{data},
                                    capturedAt = #{capturedAt},
                                    capturedBy = #{capturedBy}
            """)
    int upsert(@Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
               @Param("worldId") @NotNull UUID worldId,
               @Param("data") byte @NotNull [] data,
               @Param("capturedAt") @NotNull LocalDateTime capturedAt,
               @Param("capturedBy") @NotNull UUID capturedBy);

    @Override
    @Select("""
            SELECT s.realtyRegionId, s.data, s.capturedAt, s.capturedBy
            FROM RealtySchematic s
            JOIN RealtyRegion r ON r.realtyRegionId = s.realtyRegionId
            WHERE r.worldGuardRegionId = #{worldGuardRegionId}
              AND r.worldId = #{worldId}
            """)
    @ConstructorArgs({
            @Arg(column = "realtyRegionId", javaType = int.class),
            @Arg(column = "data", javaType = byte[].class),
            @Arg(column = "capturedAt", javaType = LocalDateTime.class),
            @Arg(column = "capturedBy", javaType = UUID.class)
    })
    @Nullable RealtySchematicEntity selectByWorldGuardRegion(
            @Param("worldGuardRegionId") @NotNull String worldGuardRegionId,
            @Param("worldId") @NotNull UUID worldId);

}
```

- [ ] **Step 8: Wire the mapper into the session**

In `SqlSessionWrapper.java`, add the import `io.github.md5sha256.realty.database.mapper.RealtySchematicMapper` and, next to `realtyWorldMapper()`:

```java
    @NotNull RealtySchematicMapper realtySchematicMapper();
```

In `MariaSqlSession.java`, add imports for `RealtySchematicMapper` and `MariaRealtySchematicMapper`, then:

```java
    @Override
    public @NotNull RealtySchematicMapper realtySchematicMapper() {
        return session.getMapper(MariaRealtySchematicMapper.class);
    }
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `./gradlew :realty-backend:test --tests "*RealtySchematicMapperTest*"`
Expected: PASS, 5 tests.

If `upsertAffectsNoRowsForAnUnregisteredRegion` fails, the `INSERT ... SELECT` was likely rewritten to `INSERT ... VALUES`; restore the `SELECT` form.

- [ ] **Step 10: Run the whole backend suite**

Run: `./gradlew :realty-backend:test`
Expected: PASS. `MariaSchemaMigratorTest` exercises the migration list, so a mis-registered V17 surfaces here.

- [ ] **Step 11: Commit**

```bash
git add realty-backend-api/src/main/java/io/github/md5sha256/realty/database/entity/RealtySchematicEntity.java \
        realty-backend/src/main/resources/sql/migrations/V17__realty_schematics.sql \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/MariaSchemaMigrator.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/mapper/RealtySchematicMapper.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaRealtySchematicMapper.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/SqlSessionWrapper.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/MariaSqlSession.java \
        realty-backend/src/test/java/io/github/md5sha256/realty/database/RealtySchematicMapperTest.java
git commit -m "feat(backend): store one Sponge v3 schematic per region"
```

---

### Task 2: Backend API surface

**Files:**
- Modify: `realty-backend-api/src/main/java/io/github/md5sha256/realty/api/RealtyBackend.java`
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/RealtyBackendImpl.java`
- Test: `realty-backend/src/test/java/io/github/md5sha256/realty/database/RealtySchematicBackendTest.java`

**Interfaces:**
- Consumes: `RealtySchematicMapper` and `SqlSessionWrapper.realtySchematicMapper()` from Task 1.
- Produces:
  - `RealtyBackend.storeSchematic(String worldGuardRegionId, UUID worldId, byte[] data, UUID capturedBy)` returning `boolean` (`false` when the region is not registered)
  - `RealtyBackend.getSchematic(String worldGuardRegionId, UUID worldId)` returning `byte @Nullable []`

- [ ] **Step 1: Write the failing test**

Create `realty-backend/src/test/java/io/github/md5sha256/realty/database/RealtySchematicBackendTest.java`. `AbstractDatabaseTest` exposes `protected static RealtyBackend logic`.

```java
package io.github.md5sha256.realty.database;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class RealtySchematicBackendTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000dd");
    private static final UUID CAPTURED_BY = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000ee");

    @Test
    void storeThenGetReturnsTheBytes() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyRegionMapper().registerWorldGuardRegion("plot_b", WORLD_ID);
        }
        Assertions.assertTrue(logic.storeSchematic("plot_b", WORLD_ID, new byte[]{4, 5}, CAPTURED_BY));
        Assertions.assertArrayEquals(new byte[]{4, 5}, logic.getSchematic("plot_b", WORLD_ID));
    }

    @Test
    void getReturnsNullWhenNothingWasCaptured() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyRegionMapper().registerWorldGuardRegion("plot_b", WORLD_ID);
        }
        Assertions.assertNull(logic.getSchematic("plot_b", WORLD_ID));
    }

    @Test
    void storeReportsFailureForAnUnregisteredRegion() {
        Assertions.assertFalse(logic.storeSchematic("nope", WORLD_ID, new byte[]{1}, CAPTURED_BY));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :realty-backend:test --tests "*RealtySchematicBackendTest*"`
Expected: FAIL — `storeSchematic` / `getSchematic` are not defined on `RealtyBackend`.

- [ ] **Step 3: Declare the methods on the interface**

In `RealtyBackend.java`, add the imports it needs if absent (`org.jetbrains.annotations.Nullable`, `java.util.UUID`) and declare:

```java
    /**
     * Stores {@code data} as the region's schematic, replacing any previous capture.
     *
     * @return {@code false} when no such region is registered, so nothing was stored
     */
    boolean storeSchematic(@NotNull String worldGuardRegionId,
                           @NotNull UUID worldId,
                           byte @NotNull [] data,
                           @NotNull UUID capturedBy);

    /**
     * The region's most recent schematic, or {@code null} if it has never been captured.
     */
    byte @Nullable [] getSchematic(@NotNull String worldGuardRegionId, @NotNull UUID worldId);
```

- [ ] **Step 4: Implement them**

In `RealtyBackendImpl.java`, add imports for `RealtySchematicEntity` and `java.time.LocalDateTime` if absent, then implement following the file's existing session-handling style:

```java
    @Override
    public boolean storeSchematic(@NotNull String worldGuardRegionId,
                                  @NotNull UUID worldId,
                                  byte @NotNull [] data,
                                  @NotNull UUID capturedBy) {
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            return session.realtySchematicMapper()
                    .upsert(worldGuardRegionId, worldId, data, LocalDateTime.now(), capturedBy) > 0;
        }
    }

    @Override
    public byte @Nullable [] getSchematic(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            RealtySchematicEntity entity =
                    session.realtySchematicMapper().selectByWorldGuardRegion(worldGuardRegionId, worldId);
            return entity == null ? null : entity.data();
        }
    }
```

Check how neighbouring methods in this file obtain a session — if they use a shared helper rather than `this.database.openSession(true)` directly, follow that instead.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :realty-backend:test --tests "*RealtySchematicBackendTest*"`
Expected: PASS, 3 tests.

- [ ] **Step 6: Build the modules that implement the interface**

Run: `./gradlew :realty-backend:test :realty-rest:compileJava :realty-paper:compileJava`
Expected: PASS. `RealtyBackend` is implemented by proxies in `realty-rest` tests, which tolerate new methods, but a real implementor elsewhere would fail to compile here.

- [ ] **Step 7: Commit**

```bash
git add realty-backend-api/src/main/java/io/github/md5sha256/realty/api/RealtyBackend.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/RealtyBackendImpl.java \
        realty-backend/src/test/java/io/github/md5sha256/realty/database/RealtySchematicBackendTest.java
git commit -m "feat(backend): expose schematic store and fetch on RealtyBackend"
```

---

### Task 3: Capture cooldown

**Files:**
- Create: `realty-paper/src/main/java/io/github/md5sha256/realty/schematic/CaptureCooldown.java`
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/schematic/CaptureCooldownTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `new CaptureCooldown(Supplier<Instant> clock)`
  - `CaptureCooldown.remaining(String worldGuardRegionId, UUID worldId, Duration cooldown)` returning `@Nullable Duration` — `null` when a capture is allowed now, otherwise the time left
  - `CaptureCooldown.record(String worldGuardRegionId, UUID worldId)`

A `Supplier<Instant>` clock is injected so the test does not sleep.

- [ ] **Step 1: Write the failing test**

`realty-paper/src/test/java/io/github/md5sha256/realty/schematic/CaptureCooldownTest.java`:

```java
package io.github.md5sha256.realty.schematic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class CaptureCooldownTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000ff");
    private static final Duration COOLDOWN = Duration.ofMinutes(10);

    @Test
    void aRegionNeverCapturedIsAllowedImmediately() {
        CaptureCooldown cooldown = new CaptureCooldown(Instant::now);
        Assertions.assertNull(cooldown.remaining("plot_a", WORLD_ID, COOLDOWN));
    }

    @Test
    void aRecentCaptureReportsTheTimeRemaining() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-04T12:00:00Z"));
        CaptureCooldown cooldown = new CaptureCooldown(now::get);

        cooldown.record("plot_a", WORLD_ID);
        now.set(Instant.parse("2026-09-04T12:04:00Z"));

        Assertions.assertEquals(Duration.ofMinutes(6), cooldown.remaining("plot_a", WORLD_ID, COOLDOWN));
    }

    @Test
    void theCooldownExpiresExactlyAtItsDuration() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-04T12:00:00Z"));
        CaptureCooldown cooldown = new CaptureCooldown(now::get);

        cooldown.record("plot_a", WORLD_ID);
        now.set(Instant.parse("2026-09-04T12:10:00Z"));

        Assertions.assertNull(cooldown.remaining("plot_a", WORLD_ID, COOLDOWN));
    }

    @Test
    void eachRegionCoolsDownIndependently() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-04T12:00:00Z"));
        CaptureCooldown cooldown = new CaptureCooldown(now::get);

        cooldown.record("plot_a", WORLD_ID);

        Assertions.assertNotNull(cooldown.remaining("plot_a", WORLD_ID, COOLDOWN));
        Assertions.assertNull(cooldown.remaining("plot_b", WORLD_ID, COOLDOWN));
    }

    @Test
    void theSameRegionNameInAnotherWorldCoolsDownIndependently() {
        UUID otherWorld = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000011");
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-04T12:00:00Z"));
        CaptureCooldown cooldown = new CaptureCooldown(now::get);

        cooldown.record("plot_a", WORLD_ID);

        Assertions.assertNull(cooldown.remaining("plot_a", otherWorld, COOLDOWN));
    }

    @Test
    void aZeroCooldownAlwaysAllowsCapture() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-04T12:00:00Z"));
        CaptureCooldown cooldown = new CaptureCooldown(now::get);

        cooldown.record("plot_a", WORLD_ID);

        Assertions.assertNull(cooldown.remaining("plot_a", WORLD_ID, Duration.ZERO));
    }

    @Test
    void recordingAgainRestartsTheCooldown() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-04T12:00:00Z"));
        CaptureCooldown cooldown = new CaptureCooldown(now::get);

        cooldown.record("plot_a", WORLD_ID);
        now.set(Instant.parse("2026-09-04T12:09:00Z"));
        cooldown.record("plot_a", WORLD_ID);

        Assertions.assertEquals(Duration.ofMinutes(10), cooldown.remaining("plot_a", WORLD_ID, COOLDOWN));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :realty-paper:test --tests "*CaptureCooldownTest*"`
Expected: FAIL — `CaptureCooldown` does not exist.

- [ ] **Step 3: Implement the cooldown**

`realty-paper/src/main/java/io/github/md5sha256/realty/schematic/CaptureCooldown.java`:

```java
package io.github.md5sha256.realty.schematic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Per-region capture rate limiting, held in memory only: it is a courtesy against
 * repeated expensive captures, not an audited limit, so it resets on restart rather
 * than costing a table and a write per attempt.
 *
 * <p>The clock is injected so the cooldown can be tested without sleeping.</p>
 */
public final class CaptureCooldown {

    private final Map<Key, Instant> lastCapture = new ConcurrentHashMap<>();
    private final Supplier<Instant> clock;

    public CaptureCooldown(@NotNull Supplier<Instant> clock) {
        this.clock = clock;
    }

    /**
     * How much longer the region must wait, or {@code null} if it may be captured now.
     */
    public @Nullable Duration remaining(@NotNull String worldGuardRegionId,
                                        @NotNull UUID worldId,
                                        @NotNull Duration cooldown) {
        if (cooldown.isZero() || cooldown.isNegative()) {
            return null;
        }
        Instant last = this.lastCapture.get(new Key(worldGuardRegionId, worldId));
        if (last == null) {
            return null;
        }
        Duration elapsed = Duration.between(last, this.clock.get());
        if (elapsed.compareTo(cooldown) >= 0) {
            return null;
        }
        return cooldown.minus(elapsed);
    }

    public void record(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        this.lastCapture.put(new Key(worldGuardRegionId, worldId), this.clock.get());
    }

    private record Key(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :realty-paper:test --tests "*CaptureCooldownTest*"`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add realty-paper/src/main/java/io/github/md5sha256/realty/schematic/CaptureCooldown.java \
        realty-paper/src/test/java/io/github/md5sha256/realty/schematic/CaptureCooldownTest.java
git commit -m "feat(paper): add in-memory per-region capture cooldown"
```

---

### Task 4: Schematic writer

**Files:**
- Create: `realty-paper/src/main/java/io/github/md5sha256/realty/schematic/RegionSchematicWriter.java`
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/schematic/RegionSchematicWriterTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `RegionSchematicWriter.write(com.sk89q.worldedit.world.World weWorld, com.sk89q.worldedit.regions.Region weRegion)` returning `byte[]`, throwing `java.io.IOException` / `com.sk89q.worldedit.WorldEditException`.

This class holds no Bukkit types — the caller adapts Bukkit and WorldGuard objects into WorldEdit ones. That keeps it unit-testable, since WorldEdit's `BlockArrayClipboard` and clipboard writer work without a running server.

- [ ] **Step 1: Write the failing test**

The test writes a clipboard-backed region rather than a live world, so it needs no server. It asserts the bytes are a gzipped NBT document (Sponge v3 is gzipped NBT, magic bytes `0x1f 0x8b`) and that WorldEdit can read them back.

`realty-paper/src/test/java/io/github/md5sha256/realty/schematic/RegionSchematicWriterTest.java`:

```java
package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

class RegionSchematicWriterTest {

    private static BlockArrayClipboard filledClipboard() throws Exception {
        CuboidRegion region = new CuboidRegion(BlockVector3.ZERO, BlockVector3.at(2, 1, 2));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setBlock(BlockVector3.at(1, 1, 1), BlockTypes.STONE.getDefaultState());
        return clipboard;
    }

    @Test
    void writesGzippedSpongeV3Bytes() throws Exception {
        byte[] bytes = RegionSchematicWriter.writeClipboard(filledClipboard());

        Assertions.assertTrue(bytes.length > 0, "expected a non-empty schematic");
        // Sponge v3 is gzipped NBT; 0x1f8b is the gzip magic number.
        Assertions.assertEquals((byte) 0x1f, bytes[0]);
        Assertions.assertEquals((byte) 0x8b, bytes[1]);
    }

    @Test
    void theBytesReadBackAsAClipboardOfTheSameSize() throws Exception {
        byte[] bytes = RegionSchematicWriter.writeClipboard(filledClipboard());

        try (ClipboardReader reader = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC
                .getReader(new ByteArrayInputStream(bytes))) {
            Clipboard read = reader.read();
            Assertions.assertEquals(BlockVector3.at(3, 2, 3), read.getDimensions());
            Assertions.assertEquals(BlockTypes.STONE,
                    read.getBlock(read.getMinimumPoint().add(1, 1, 1)).getBlockType());
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :realty-paper:test --tests "*RegionSchematicWriterTest*"`
Expected: FAIL — `RegionSchematicWriter` does not exist.

If it instead fails on a missing WorldEdit class at test runtime, add `testImplementation("com.sk89q.worldguard:worldguard-bukkit:7.0.18")`'s transitive WorldEdit to the test classpath — that dependency is already declared in `realty-paper/build.gradle.kts:39`, so this should not arise.

- [ ] **Step 3: Implement the writer**

`realty-paper/src/main/java/io/github/md5sha256/realty/schematic/RegionSchematicWriter.java`:

```java
package io.github.md5sha256.realty.schematic;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Copies a region's blocks into a clipboard and serialises it as Sponge Schematic v3.
 *
 * <p>Deliberately free of Bukkit and WorldGuard types: the caller adapts those into
 * WorldEdit's {@link World} and {@link Region} first, which leaves this testable
 * without a running server.</p>
 *
 * <p>Written against the WorldEdit API rather than FastAsyncWorldEdit's, so one
 * implementation serves either install — FAWE provides the same classes.</p>
 */
public final class RegionSchematicWriter {

    private RegionSchematicWriter() {
    }

    /**
     * Copies {@code region} out of {@code world} and returns its Sponge v3 bytes.
     *
     * <p>Reads live world state, so it must be called on the main thread.</p>
     */
    public static byte @NotNull [] write(@NotNull World world, @NotNull Region region)
            throws IOException, WorldEditException {
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        try (EditSession session = WorldEdit.getInstance().newEditSession(world)) {
            ForwardExtentCopy copy = new ForwardExtentCopy(
                    session, region, clipboard, region.getMinimumPoint());
            copy.setCopyingEntities(false);
            Operations.complete(copy);
        }
        return writeClipboard(clipboard);
    }

    /**
     * Serialises an already-populated clipboard. Split out so the serialisation half
     * can be tested without a world.
     */
    public static byte @NotNull [] writeClipboard(@NotNull Clipboard clipboard) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(out)) {
            writer.write(clipboard);
        }
        return out.toByteArray();
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :realty-paper:test --tests "*RegionSchematicWriterTest*"`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add realty-paper/src/main/java/io/github/md5sha256/realty/schematic/RegionSchematicWriter.java \
        realty-paper/src/test/java/io/github/md5sha256/realty/schematic/RegionSchematicWriterTest.java
git commit -m "feat(paper): write a region's blocks as Sponge v3 schematic bytes"
```

---

### Task 5: Settings key and messages

**Files:**
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/settings/Settings.java`
- Modify: `realty-paper/src/main/resources/config.yml`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/localisation/MessageKeys.java`
- Modify: `realty-paper/src/main/resources/messages.yml`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `Settings.schematicCaptureCooldownSeconds()` returning `long`
  - `MessageKeys.SCHEMATIC_CAPTURED`, `SCHEMATIC_COOLDOWN`, `SCHEMATIC_FAILED`, `SCHEMATIC_NOT_REGISTERED`, `SCHEMATIC_FORCE_NO_PERMISSION`

- [ ] **Step 1: Add the settings key**

In `Settings.java`, add a component to the record after `terminationNoticeSeconds`:

```java
        @Setting("schematic-capture-cooldown-seconds") long schematicCaptureCooldownSeconds,
```

and, in the compact constructor alongside the other defaults:

```java
        if (schematicCaptureCooldownSeconds < 0) {
            schematicCaptureCooldownSeconds = 300;
        }
```

A zero is left as zero deliberately — it is how an operator disables the cooldown. Only a negative is corrected.

- [ ] **Step 2: Add the key to the shipped config**

In `realty-paper/src/main/resources/config.yml`, next to the other duration settings:

```yaml
# How long a region must wait between /realty schematic capture runs.
# 0 disables the cooldown. Bypassed by --force (realty.command.schematic.capture.force).
schematic-capture-cooldown-seconds: 300
```

The `defaults/default-config.yml` reference copy is rewritten on every start by `copyResourceTemplate` in `onLoad`, so it needs no separate edit — but confirm that is still how core seeds it before assuming.

- [ ] **Step 3: Add the message keys**

In `MessageKeys.java`, alongside the other groups:

```java
    public static final String SCHEMATIC_CAPTURED = "schematic.captured";
    public static final String SCHEMATIC_COOLDOWN = "schematic.cooldown";
    public static final String SCHEMATIC_FAILED = "schematic.failed";
    public static final String SCHEMATIC_NOT_REGISTERED = "schematic.not-registered";
    public static final String SCHEMATIC_FORCE_NO_PERMISSION = "schematic.force-no-permission";
```

- [ ] **Step 4: Add the messages**

In `realty-paper/src/main/resources/messages.yml`, add a `schematic:` block in the file's existing alphabetical position, matching the surrounding indentation (four spaces, as in the `set:` block):

```yaml
schematic:
    captured: <prefix> Captured a schematic of <region> (<size> bytes).
    cooldown: <prefix> <region> was captured recently. Try again in <remaining>.
    failed: '<prefix> Failed to capture <region>: <error>'
    not-registered: <prefix> <region> is not registered with Realty.
    force-no-permission: <prefix> You do not have permission to bypass the capture cooldown.
```

- [ ] **Step 5: Verify the module still compiles and its tests pass**

Run: `./gradlew :realty-paper:test`
Expected: PASS. `Settings` is a Configurate record, so a malformed `config.yml` surfaces at runtime rather than here — Step 6 covers that.

- [ ] **Step 6: Verify the shipped config still parses**

Run: `./gradlew :realty-paper:processResources` then confirm the new key is present in the staged resource:

```bash
grep -n "schematic-capture-cooldown-seconds" realty-paper/build/resources/main/config.yml
```

Expected: one match. A reference copy that would not start documents a lie, so this must hold.

- [ ] **Step 7: Commit**

```bash
git add realty-paper/src/main/java/io/github/md5sha256/realty/settings/Settings.java \
        realty-paper/src/main/resources/config.yml \
        realty-paper/src/main/java/io/github/md5sha256/realty/localisation/MessageKeys.java \
        realty-paper/src/main/resources/messages.yml
git commit -m "feat(paper): add schematic capture cooldown setting and messages"
```

---

### Task 6: The capture command

**Files:**
- Create: `realty-paper/src/main/java/io/github/md5sha256/realty/command/SchematicCommandGroup.java`
- Modify: `realty-paper/src/main/resources/paper-plugin.yml`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java:817-887`

**Interfaces:**
- Consumes: `CaptureCooldown` (Task 3), `RegionSchematicWriter` (Task 4), `Settings.schematicCaptureCooldownSeconds()` and the `MessageKeys.SCHEMATIC_*` constants (Task 5), `RealtyBackend.storeSchematic` (Task 2).
- Produces: `SchematicCommandGroup`, a `CustomCommandBean` registering `/realty schematic capture`.

- [ ] **Step 1: Read the surrounding conventions**

Before writing, read `realty-paper/src/main/java/io/github/md5sha256/realty/command/SetCommandGroup.java` in full for the `CustomCommandBean` shape, and `AuctionCommandGroup.java` for how a group threads `RealtyPaperApi`, `Settings` and `MessageContainer` through a record. Read `command/util/WorldGuardRegionResolver.java` for how `<region>` resolves (explicit argument, else the player's location).

Check how an existing command declares a flag in Cloud (`grep -rn "flag(" realty-paper/src/main/java/io/github/md5sha256/realty/command/`). If none does, `builder.flag(manager.flagBuilder("force").build())` is the Cloud 2.0 form; adapt to whatever the codebase already uses.

- [ ] **Step 2: Write the command group**

`realty-paper/src/main/java/io/github/md5sha256/realty/command/SchematicCommandGroup.java`. The essential ordering, which the reviewer should check first:

1. Resolve region and sender.
2. If `--force` was passed and the sender is a `Player` lacking `realty.command.schematic.capture.force`, send `SCHEMATIC_FORCE_NO_PERMISSION` and stop — **before** the cooldown check, so an unauthorised `--force` is refused rather than silently ignored.
3. If `--force` was not passed, ask `CaptureCooldown.remaining(...)`; if non-null, send `SCHEMATIC_COOLDOWN` with the remaining time formatted by `DurationFormatter` and stop.
4. Capture on the main thread via `RegionSchematicWriter.write(...)`, adapting the Bukkit world with `BukkitAdapter.adapt(world)` and the WorldGuard region with the existing conversion used elsewhere in this package (see `SubregionState` / `CreateCommand` for the `ProtectedRegion` → WorldEdit `Region` pattern; a cuboid becomes `new CuboidRegion(weWorld, region.getMinimumPoint(), region.getMaximumPoint())`).
5. Persist via `api.backend().storeSchematic(...)` — if it returns `false`, send `SCHEMATIC_NOT_REGISTERED` and do **not** record the cooldown.
6. On success, `cooldown.record(...)` and send `SCHEMATIC_CAPTURED` with the region and byte count.
7. Wrap the capture in a `try`/`catch (IOException | WorldEditException e)` and send `SCHEMATIC_FAILED` with the message, logging the throwable.

The permission checks are Bukkit `hasPermission` calls and must stay on the main thread — do not move them inside an async callback. Console senders (non-`Player`) are trusted, matching `SetCommandGroup.authorizeLeaseholdSet`.

Register the subcommand with `.permission("realty.command.schematic.capture")`, an optional `region` argument via `WorldGuardRegionResolver.worldGuardRegionResolver()`, and the `force` flag.

- [ ] **Step 3: Declare the permissions**

In `realty-paper/src/main/resources/paper-plugin.yml`, in the permissions block:

```yaml
  realty.command.schematic.capture:
    description: Allows using /realty schematic capture
    default: op
  realty.command.schematic.capture.force:
    description: Allows bypassing the /realty schematic capture cooldown with --force
    default: op
```

- [ ] **Step 4: Register the command bean**

In `Realty.java`, add the import and a new entry in the `List.of(...)` at line 817, following the `new SetCommandGroup(paperApi, messageContainer, this.eventDispatch)` form. The group needs the settings reference the other groups receive (`this.settings`, an `AtomicReference<Settings>`) plus a `CaptureCooldown` instance constructed with `Instant::now`.

- [ ] **Step 5: Compile**

Run: `./gradlew :realty-paper:compileJava`
Expected: PASS.

- [ ] **Step 6: Run the paper test suite**

Run: `./gradlew :realty-paper:test`
Expected: PASS.

- [ ] **Step 7: Verify in a running server**

Run: `./gradlew runServer`

In the server console: create or pick a WorldGuard region registered with Realty, then run `realty schematic capture <region>`. Expect the captured message with a non-zero byte count. Run it again immediately and expect the cooldown message with a remaining time. Run `realty schematic capture <region> --force` and expect it to succeed.

Confirm the row landed:

```bash
docker compose -f compose.dev.yml exec -T mariadb \
  mariadb -urealty -prealty realty -e "SELECT realtyRegionId, LENGTH(data), capturedAt FROM RealtySchematic;"
```

Expected: one row with a non-zero length.

- [ ] **Step 8: Commit**

```bash
git add realty-paper/src/main/java/io/github/md5sha256/realty/command/SchematicCommandGroup.java \
        realty-paper/src/main/resources/paper-plugin.yml \
        realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java
git commit -m "feat(paper): add /realty schematic capture with a per-region cooldown"
```

---

### Task 7: REST endpoint

**Files:**
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RegionSchematicHandler.java`
- Modify: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RealtyRestServer.java:59-78` (ROUTES) and its `registerRoutes`
- Modify: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/SchemaVersionCheck.java:36`
- Modify: `realty-rest/src/main/resources/openapi.yaml`
- Modify: `realty-rest/src/test/java/io/github/md5sha256/realty/rest/TestServers.java`
- Test: `realty-rest/src/test/java/io/github/md5sha256/realty/rest/RegionSchematicEndpointTest.java`

**Interfaces:**
- Consumes: `RealtyBackend.getSchematic(String, UUID)` (Task 2).
- Produces: `GET /v1/region/schematic?world=&region=`.

- [ ] **Step 1: Bump the expected schema version**

`SchemaVersionCheck.java:36`: change `EXPECTED_VERSION = 16` to `17`. Update the surrounding javadoc, which currently explains the V16 (`RealtyWorld`) rationale, to also name V17.

This is required, not incidental: the service refuses to boot on any other version, so shipping V17 without this bump takes the API down.

- [ ] **Step 2: Write the failing test**

`realty-rest/src/test/java/io/github/md5sha256/realty/rest/RegionSchematicEndpointTest.java`. Follow the `TestServers` proxy-stub pattern — read `TestServers.withTags` for the `InvocationHandler` shape, and add a `withSchematic` factory there:

```java
package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RegionSchematicEndpointTest {

    @Test
    void returnsTheRawBytesWithAnOctetStreamContentType() {
        RealtyRestServer server = TestServers.withSchematic(new byte[]{1, 2, 3});
        JavalinTest.test(server.javalin(), (app, client) -> {
            Response response = client.get("/v1/region/schematic?world=world&region=plot_a");
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("application/octet-stream", response.header("Content-Type"));
            Assertions.assertArrayEquals(new byte[]{1, 2, 3}, response.body().bytes());
        });
    }

    @Test
    void returns404WhenTheRegionHasNoSchematic() {
        RealtyRestServer server = TestServers.withSchematic(null);
        JavalinTest.test(server.javalin(), (app, client) -> {
            Response response = client.get("/v1/region/schematic?world=world&region=plot_a");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("SCHEMATIC_NOT_FOUND"));
        });
    }

    @Test
    void requiresBothWorldAndRegionParams() {
        RealtyRestServer server = TestServers.withSchematic(new byte[]{1});
        JavalinTest.test(server.javalin(), (app, client) -> {
            Assertions.assertEquals(400, client.get("/v1/region/schematic?world=world").code());
            Assertions.assertEquals(400, client.get("/v1/region/schematic?region=plot_a").code());
        });
    }

    @Test
    void returns404ForAnUnknownWorld() {
        RealtyRestServer server = TestServers.withSchematic(new byte[]{1});
        JavalinTest.test(server.javalin(), (app, client) -> {
            Response response = client.get("/v1/region/schematic?world=nosuchworld&region=plot_a");
            Assertions.assertEquals(404, response.code());
        });
    }
}
```

Add to `TestServers` a factory whose backend stubs only `getSchematic`, and whose `StubDatabase` knows the world `world` (copy how `withWorlds()` seeds `RealtyWorldEntity` rows so `WorldLookup.resolve("world")` succeeds):

```java
    static @NotNull RealtyRestServer withSchematic(byte @Nullable [] schematic) {
        List<RealtyWorldEntity> worlds = List.of(new RealtyWorldEntity(
                UUID.fromString("8f4d1c2e-0000-0000-0000-000000000099"), "world"));
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getSchematic" -> schematic;
            default -> throw new UnsupportedOperationException(
                    "RealtyBackend#" + method.getName() + " is not stubbed for this test");
        };
        RealtyBackend backend = (RealtyBackend) Proxy.newProxyInstance(
                RealtyBackend.class.getClassLoader(),
                new Class<?>[]{RealtyBackend.class},
                handler);
        return new RealtyRestServer(backend, new StubDatabase(false, worlds), defaultSettings());
    }
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :realty-rest:test --tests "*RegionSchematicEndpointTest*"`
Expected: FAIL — the route 404s with an unknown-path body, and `withSchematic` does not exist.

- [ ] **Step 4: Write the handler**

`realty-rest/src/main/java/io/github/md5sha256/realty/rest/RegionSchematicHandler.java`:

```java
package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * {@code GET /v1/region/schematic?world=...&region=...} -- the raw Sponge Schematic v3
 * bytes captured by {@code /realty schematic capture}, for a browser-side renderer.
 *
 * <p>Served as bytes rather than JSON deliberately: the frontend schematic renderers
 * read an {@code ArrayBuffer} directly, so base64-wrapping it in JSON would cost a
 * third more bytes and a decode step for nothing.</p>
 */
final class RegionSchematicHandler {

    private final RealtyBackend backend;
    private final WorldLookup worldLookup;

    RegionSchematicHandler(@NotNull RealtyBackend backend, @NotNull WorldLookup worldLookup) {
        this.backend = backend;
        this.worldLookup = worldLookup;
    }

    void handle(@NotNull Context ctx) {
        String worldParam = QueryParams.required(ctx, "world");
        String regionParam = QueryParams.required(ctx, "region");

        UUID worldId = this.worldLookup.resolve(worldParam);
        byte[] schematic = this.backend.getSchematic(regionParam, worldId);

        if (schematic == null) {
            throw ApiException.notFound("SCHEMATIC_NOT_FOUND",
                    "No schematic captured for region '" + regionParam + "' in world '" + worldParam + "'");
        }

        ctx.contentType("application/octet-stream");
        ctx.result(schematic);
    }
}
```

Check `ApiException.notFound`'s exact signature and `WorldLookup.resolve`'s behaviour for an unknown world before finalising — `resolve` is expected to throw the not-found `ApiException` itself, which is what makes the unknown-world test pass with no extra branch here.

- [ ] **Step 5: Register the route**

In `RealtyRestServer.java`, add `"/v1/region/schematic"` to the `ROUTES` list (line 59-78 block, next to `"/v1/region/members"`), construct the handler alongside the others, and register it in `registerRoutes`:

```java
        routes.get("/v1/region/schematic", regionSchematicHandler::handle);
```

- [ ] **Step 6: Document the endpoint**

In `realty-rest/src/main/resources/openapi.yaml`, add a `/v1/region/schematic` path beside `/v1/region/members`, following the existing style for the `world` and `region` query parameters:

```yaml
  /v1/region/schematic:
    get:
      summary: The region's captured schematic
      description: >
        Raw Sponge Schematic v3 bytes for the region, as captured in game by
        /realty schematic capture. Returns 404 if the region has never been captured.
      parameters:
        - name: world
          in: query
          required: true
          schema:
            type: string
        - name: region
          in: query
          required: true
          schema:
            type: string
      responses:
        '200':
          description: The schematic
          content:
            application/octet-stream:
              schema:
                type: string
                format: binary
        '400':
          description: A required query parameter is missing
        '404':
          description: No such region, or no schematic captured for it
```

Match the surrounding indentation and the exact response-object style already used in this file — copy from the `/v1/region/members` entry rather than trusting the sketch above.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :realty-rest:test --tests "*RegionSchematicEndpointTest*"`
Expected: PASS, 4 tests.

- [ ] **Step 8: Run the whole REST suite**

Run: `./gradlew :realty-rest:test`
Expected: PASS. `OpenApiConformanceTest` asserts routes and documentation match in both directions, so a missing `openapi.yaml` entry (or a documented path with no route) fails here.

- [ ] **Step 9: Commit**

```bash
git add realty-rest/src/main/java/io/github/md5sha256/realty/rest/RegionSchematicHandler.java \
        realty-rest/src/main/java/io/github/md5sha256/realty/rest/RealtyRestServer.java \
        realty-rest/src/main/java/io/github/md5sha256/realty/rest/SchemaVersionCheck.java \
        realty-rest/src/main/resources/openapi.yaml \
        realty-rest/src/test/java/io/github/md5sha256/realty/rest/TestServers.java \
        realty-rest/src/test/java/io/github/md5sha256/realty/rest/RegionSchematicEndpointTest.java
git commit -m "feat(rest): serve a region's captured schematic bytes"
```

---

### Task 8: Whole-build verification and documentation

**Files:**
- Modify: `CLAUDE.md` (schematic capture note)
- Modify: `realty-rest/README.md` if it enumerates endpoints

- [ ] **Step 1: Run the full build**

Run: `./gradlew build`
Expected: PASS across every module. This is the first point the whole graph is checked together.

- [ ] **Step 2: Document the feature**

In `CLAUDE.md`, add a short note under *Domain Patterns* — the capture is on-demand, one row per region, WorldEdit's clipboard API is used (no new dependency, FAWE-compatible), and `realty-rest`'s `EXPECTED_VERSION` must be bumped with every migration.

If `realty-rest/README.md` lists endpoints, add `/v1/region/schematic` there in the same style.

- [ ] **Step 3: Verify the docs claim nothing untrue**

Re-read what you wrote against the code as it now stands. Every file path, permission node, and setting name must exist.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md realty-rest/README.md
git commit -m "docs: describe region schematic capture"
```

---

## Self-Review

**Spec coverage:**

| Spec requirement | Task |
|---|---|
| WorldEdit clipboard API, no new dependency | 4 (Global Constraints restate it) |
| Live main-thread capture | 4, 6 |
| `/realty schematic capture <region> [--force]` | 6 |
| In-memory per-region cooldown from `Settings` | 3, 5, 6 |
| `--force` bypasses cooldown only; gated by its own permission; refused before the cooldown check | 6 (step 2 ordering), 3 |
| Permissions in `paper-plugin.yml`, `realty.command.*` shape | 6 |
| `RealtySchematic` table keyed on `realtyRegionId`, `UUID` column type | 1 |
| V17 registered in `DEFAULT_MIGRATIONS` | 1 |
| Mapper split (base interface + Maria impl), registered on both session types | 1 |
| Mapper methods take `(worldGuardRegionId, worldId)` and JOIN | 1 |
| Two `RealtyBackend` methods | 2 |
| `GET /v1/region/schematic?world=&region=` returning octet-stream | 7 |
| `404 SCHEMATIC_NOT_FOUND` vs `404 REGION_NOT_FOUND` | 7 |
| Route added to `ROUTES` + `openapi.yaml` | 7 |
| `EXPECTED_VERSION` bumped to 17 | 7 |
| Tests: mapper round-trip, replace-on-recapture, cooldown, REST 200/404 | 1, 2, 3, 7 |

No spec requirement is unassigned.

**Placeholder scan:** no TBDs. Tasks 6 and 7 direct the implementer to read specific neighbouring files before finalising (Cloud's flag API and `ApiException`/`WorldLookup` signatures) rather than guessing at an API this plan has not verified line-by-line — that is a deliberate instruction to check, not a deferred decision, and the surrounding behaviour is fully specified either way.

**Type consistency:** `storeSchematic`/`getSchematic` are named identically in Tasks 2, 6 and 7. `CaptureCooldown.remaining`/`record` match between Tasks 3 and 6. `RealtySchematicEntity`'s four components are identical in Task 1's entity, mapper `@ConstructorArgs`, and DDL column order. `upsert` returns `int` in Task 1 and is compared `> 0` in Task 2.
