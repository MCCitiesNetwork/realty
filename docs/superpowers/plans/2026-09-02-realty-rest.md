# Realty REST API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship `realty-rest`, a standalone read-only HTTP service exposing the `/realty info` and `/realty list` payloads, plus the `RealtyWorld` table that lets it resolve world names without the game server.

**Architecture:** A new Gradle subproject runs its own JVM process against the same MariaDB the plugin writes to, reusing `realty-backend`'s MyBatis mappers and entity records verbatim. Realty core gains a small write path maintaining a `RealtyWorld` name table. The service is configured entirely from environment variables and ships as a shadow jar, a Docker image and a Pterodactyl egg.

**Tech Stack:** Java 25, Javalin 6, Jackson 2, MyBatis 3.5.19, MariaDB, JUnit 5, Testcontainers, Gradle (Kotlin DSL), Shadow.

**Spec:** `docs/superpowers/specs/2026-09-02-realty-rest-api-design.md`

**Not in this plan:** the `query-service` module (its own spec and plan) and the enrichment client that consumes it. This plan ships an API that returns bare UUIDs for players and no `dimensions` — useful on its own, exactly as the spec's shipping order describes.

## Global Constraints

- **Java 25.** Toolchain comes from the `realty-conventions` convention plugin. Never set a toolchain directly.
- **No wildcard imports, no static imports.** Every import is an explicit single-class import. Use `Assertions.assertEquals(...)`, never a static-imported `assertEquals(...)`.
- **All SQL in MyBatis mappers uses Java text blocks (`"""`).** Never single-line strings, never `+` concatenation.
- **New migration files must be registered** in `MariaSchemaMigrator.DEFAULT_MIGRATIONS`. A migration file alone does nothing.
- **`realty-rest` never migrates.** It never calls `initializeSchema` and never writes. Only query methods on `RealtyBackend` and read-only mappers.
- **Every identity in a response is `{"id": ..., "name": ...}`** with a nullable `name`. In this plan `name` is always null for players; the field exists so the enrichment plan adds no breaking change.
- **Money is a raw JSON number; durations are integer seconds; timestamps are ISO-8601 UTC.** Never `CurrencyFormatter` or `DurationFormatter` — those are presentation.
- **Handlers accept both `%20` and `+` as a space** in query values.
- **`realty-rest` has no config file** and therefore no `defaults/` reference copy. It is configured from environment variables only.
- **Group is `io.github.md5sha256`, version comes from the convention plugin.** Do not hardcode a version.

## Parallelisation

Four tracks. Tasks within a track are sequential; tracks are independent except where an arrow shows a dependency.

```
Track A (realty-backend)     Task 1 ──────────────┬──> Task 6
  schema + mappers           Task 2 ──────────┐   │
                                              │   │
Track B (realty-paper)       Task 3 ◄─ needs 1│   │
  world write path                            │   │
                                              │   │
Track C (realty-rest core)   Task 4 ──> Task 5 ──> Task 7
  scaffold + server                       │   └──> Task 8 ◄─ needs 2
                                          │
Track D (packaging)          Task 9 ◄─────┘        Task 10 ◄─ needs 6,7,8
```

**Start immediately and concurrently: Task 1, Task 2, Task 4.** They touch disjoint files and share no state.

- **Task 3** needs Task 1 (the table must exist).
- **Task 5** needs Task 4 (the subproject must exist).
- **Task 6** needs Tasks 1 and 5.
- **Task 7** needs Tasks 1 and 5.
- **Task 8** needs Tasks 2 and 5.
- **Task 9** needs Task 5 only, so it runs alongside Tasks 6–8.
- **Task 10** needs Tasks 6, 7 and 8, because the conformance test enumerates every route.

Tasks 6, 7 and 8 are independent of one another and may run in parallel by three workers. They all modify `RealtyRestServer.java`, so each adds exactly one line to the route block noted in its task; expect a trivial merge conflict there and resolve by keeping every line.

---

### Task 1: `RealtyWorld` table, entity and mapper

**Files:**
- Create: `realty-backend/src/main/resources/sql/migrations/V16__realty_worlds.sql`
- Create: `realty-backend-api/src/main/java/io/github/md5sha256/realty/database/entity/RealtyWorldEntity.java`
- Create: `realty-backend/src/main/java/io/github/md5sha256/realty/database/mapper/RealtyWorldMapper.java`
- Create: `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaRealtyWorldMapper.java`
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/MariaSchemaMigrator.java:58` (add to `DEFAULT_MIGRATIONS`)
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/MariaDatabase.java:111` (add `addMapper`)
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/SqlSessionWrapper.java:64` (add accessor)
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/MariaSqlSession.java`
- Test: `realty-backend/src/test/java/io/github/md5sha256/realty/database/RealtyWorldMapperTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `RealtyWorldEntity(UUID worldId, String worldName)` — record in package `io.github.md5sha256.realty.database.entity`.
  - `RealtyWorldMapper` with `void upsert(UUID worldId, String worldName)`, `List<RealtyWorldEntity> selectAll()`, `RealtyWorldEntity selectByName(String worldName)` (nullable), `RealtyWorldEntity selectById(UUID worldId)` (nullable).
  - `SqlSessionWrapper.realtyWorldMapper()` returning `RealtyWorldMapper`.

- [ ] **Step 1: Write the failing test**

Create `realty-backend/src/test/java/io/github/md5sha256/realty/database/RealtyWorldMapperTest.java`.

Note the existing `AbstractDatabaseTest` is package-private in `io.github.md5sha256.realty.database`, so this test must sit in that package.

```java
package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class RealtyWorldMapperTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000001");

    @Test
    void upsertThenSelectByIdReturnsTheWorld() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyWorldMapper().upsert(WORLD_ID, "world_nether");
            RealtyWorldEntity found = session.realtyWorldMapper().selectById(WORLD_ID);
            Assertions.assertNotNull(found);
            Assertions.assertEquals(WORLD_ID, found.worldId());
            Assertions.assertEquals("world_nether", found.worldName());
        }
    }

    @Test
    void upsertTwiceUpdatesTheNameRatherThanInserting() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyWorldMapper().upsert(WORLD_ID, "old_name");
            session.realtyWorldMapper().upsert(WORLD_ID, "new_name");
            List<RealtyWorldEntity> all = session.realtyWorldMapper().selectAll();
            Assertions.assertEquals(1, all.size());
            Assertions.assertEquals("new_name", all.getFirst().worldName());
        }
    }

    @Test
    void selectByNameFindsAWorldWithSpacesInItsName() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyWorldMapper().upsert(WORLD_ID, "My World");
            RealtyWorldEntity found = session.realtyWorldMapper().selectByName("My World");
            Assertions.assertNotNull(found);
            Assertions.assertEquals(WORLD_ID, found.worldId());
        }
    }

    @Test
    void selectByNameReturnsNullForAnUnknownName() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            Assertions.assertNull(session.realtyWorldMapper().selectByName("nope"));
        }
    }
}
```

- [ ] **Step 2: Add `RealtyWorld` to the truncate list so tests do not leak state**

In `AbstractDatabaseTest.truncateTables()`, add `TRUNCATE TABLE RealtyWorld;` to the existing multi-statement SQL block, immediately after `SET FOREIGN_KEY_CHECKS = 0;`.

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :realty-backend:test --tests "*RealtyWorldMapperTest*"`
Expected: FAIL — compilation error, `RealtyWorldEntity` and `realtyWorldMapper()` do not exist.

- [ ] **Step 4: Write the migration**

Create `realty-backend/src/main/resources/sql/migrations/V16__realty_worlds.sql`:

```sql
CREATE TABLE IF NOT EXISTS RealtyWorld
(
    worldId   UUID         NOT NULL PRIMARY KEY,
    worldName VARCHAR(255) NOT NULL
);

CREATE INDEX idx_RealtyWorld_worldName ON RealtyWorld (worldName);
```

The name index is deliberately **not** unique. Bukkit will not load two worlds with the same name simultaneously, but a world can be renamed and its old name later reused by another world; a unique constraint would turn that into a failing write on the plugin's main-thread event path. Lookup by name resolves duplicates by taking the first row, and the plugin's write path keeps the table converged.

`UUID` is a native MariaDB column type here, matching `RealtyRegion.worldId` in `V1__maria_initial_schema.sql`. The globally registered `UUIDAsBin16Handler` (see `MariaDatabase:93`) handles conversion, so mappers take a `java.util.UUID` directly with no per-mapper type handler.

- [ ] **Step 5: Register the migration**

In `MariaSchemaMigrator.java`, add a final entry to `DEFAULT_MIGRATIONS` after the version 15 line:

```java
            new MigrationStep(15, "repair null extension counts", "V15__repair_null_extension_counts.sql"),
            new MigrationStep(16, "realty worlds", "V16__realty_worlds.sql")
    );
```

- [ ] **Step 6: Write the entity record**

Create `realty-backend-api/src/main/java/io/github/md5sha256/realty/database/entity/RealtyWorldEntity.java`:

```java
package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Internal entity record mapping to the {@code RealtyWorld} DDL table.
 *
 * <p>Maps a world's UUID to its Bukkit name. Written by Realty core; read by the
 * REST API so it can resolve a world name without the game server running.</p>
 *
 * @param worldId   UUID of the world
 * @param worldName The world's Bukkit name, which is its folder name on disk and
 *                  may contain spaces
 */
public record RealtyWorldEntity(
        @NotNull UUID worldId,
        @NotNull String worldName
) {
}
```

- [ ] **Step 7: Write the base mapper interface**

Create `realty-backend/src/main/java/io/github/md5sha256/realty/database/mapper/RealtyWorldMapper.java`:

```java
package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Mapper for the world UUID to world name lookup table.
 *
 * @see RealtyWorldEntity
 */
public interface RealtyWorldMapper {

    void upsert(@NotNull UUID worldId, @NotNull String worldName);

    @NotNull List<RealtyWorldEntity> selectAll();

    @Nullable RealtyWorldEntity selectById(@NotNull UUID worldId);

    @Nullable RealtyWorldEntity selectByName(@NotNull String worldName);

}
```

- [ ] **Step 8: Write the MariaDB mapper**

Create `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaRealtyWorldMapper.java`:

```java
package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import io.github.md5sha256.realty.database.mapper.RealtyWorldMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface MariaRealtyWorldMapper extends RealtyWorldMapper {

    @Override
    @Insert("""
            INSERT INTO RealtyWorld (worldId, worldName)
            VALUES (#{worldId}, #{worldName})
            ON DUPLICATE KEY UPDATE worldName = #{worldName}
            """)
    void upsert(@Param("worldId") @NotNull UUID worldId,
                @Param("worldName") @NotNull String worldName);

    @Override
    @Select("""
            SELECT worldId, worldName
            FROM RealtyWorld
            ORDER BY worldName
            """)
    @ConstructorArgs({
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "worldName", javaType = String.class)
    })
    @NotNull List<RealtyWorldEntity> selectAll();

    @Override
    @Select("""
            SELECT worldId, worldName
            FROM RealtyWorld
            WHERE worldId = #{worldId}
            """)
    @ConstructorArgs({
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "worldName", javaType = String.class)
    })
    @Nullable RealtyWorldEntity selectById(@Param("worldId") @NotNull UUID worldId);

    @Override
    @Select("""
            SELECT worldId, worldName
            FROM RealtyWorld
            WHERE worldName = #{worldName}
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "worldName", javaType = String.class)
    })
    @Nullable RealtyWorldEntity selectByName(@Param("worldName") @NotNull String worldName);

}
```

- [ ] **Step 9: Register the mapper with MyBatis**

In `MariaDatabase.java`, after line 111 (`configuration.addMapper(MariaSearchMapper.class);`):

```java
        configuration.addMapper(MariaRealtyWorldMapper.class);
```

Add the matching import alongside the other `maria.mapper` imports.

- [ ] **Step 10: Expose the mapper on the session wrapper**

In `SqlSessionWrapper.java`, after the `searchMapper()` declaration:

```java
    @NotNull RealtyWorldMapper realtyWorldMapper();
```

In `MariaSqlSession.java`, add the matching accessor following the pattern of the existing ones:

```java
    @Override
    public @NotNull RealtyWorldMapper realtyWorldMapper() {
        return this.session.getMapper(MariaRealtyWorldMapper.class);
    }
```

Add both imports explicitly.

- [ ] **Step 11: Run the test to verify it passes**

Run: `./gradlew :realty-backend:test --tests "*RealtyWorldMapperTest*"`
Expected: PASS, 4 tests.

- [ ] **Step 12: Commit**

```bash
git add realty-backend-api/src/main/java/io/github/md5sha256/realty/database/entity/RealtyWorldEntity.java \
        realty-backend/src/main/resources/sql/migrations/V16__realty_worlds.sql \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/mapper/RealtyWorldMapper.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaRealtyWorldMapper.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/MariaSchemaMigrator.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/MariaDatabase.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/SqlSessionWrapper.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/MariaSqlSession.java \
        realty-backend/src/test/java/io/github/md5sha256/realty/database/RealtyWorldMapperTest.java \
        realty-backend/src/test/java/io/github/md5sha256/realty/database/AbstractDatabaseTest.java
git commit -m "feat(backend): add RealtyWorld world-name lookup table"
```

---

### Task 2: Rented-regions-with-end-date query

Removes the N+1 that `ListCommand` performs today, so the HTTP handler in Task 8 does not inherit it.

**Files:**
- Create: `realty-backend-api/src/main/java/io/github/md5sha256/realty/database/entity/RentedRegionView.java`
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/mapper/LeaseholdContractMapper.java`
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaLeaseholdContractMapper.java`
- Test: `realty-backend/src/test/java/io/github/md5sha256/realty/database/RentedRegionViewTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `RentedRegionView(String worldGuardRegionId, UUID worldId, LocalDateTime endDate)` with `endDate` nullable, and `LeaseholdContractMapper.selectRentedRegionsWithEndDate(UUID tenantId, int limit, int offset)` returning `List<RentedRegionView>`.

- [ ] **Step 1: Read the existing mapper to match its conventions**

Open `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaLeaseholdContractMapper.java` and find the existing query that selects rented regions for a tenant. The new query is that query plus a join to the leasehold end date. Match its exact join structure and column aliases rather than inventing new ones.

- [ ] **Step 2: Write the failing test**

Create `realty-backend/src/test/java/io/github/md5sha256/realty/database/RentedRegionViewTest.java`:

```java
package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.RentedRegionView;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

class RentedRegionViewTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000002");
    private static final UUID TENANT = UUID.fromString("3a1c88f0-0000-0000-0000-000000000001");
    private static final UUID LANDLORD = UUID.fromString("3a1c88f0-0000-0000-0000-000000000002");

    @Test
    void returnsOneRowPerRentedRegionCarryingItsEndDate() {
        LocalDateTime end = LocalDateTime.of(2026, 10, 1, 12, 0, 0);
        logic.createLeasehold("plot_rented", WORLD_ID, LANDLORD, 100.0, 604800L, null);
        logic.setTenant("plot_rented", WORLD_ID, TENANT);
        logic.setLeaseholdEndDate("plot_rented", WORLD_ID, end);

        try (SqlSessionWrapper session = database.openSession(true)) {
            List<RentedRegionView> rows = session.leaseholdContractMapper()
                    .selectRentedRegionsWithEndDate(TENANT, 10, 0);
            Assertions.assertEquals(1, rows.size());
            Assertions.assertEquals("plot_rented", rows.getFirst().worldGuardRegionId());
            Assertions.assertEquals(end, rows.getFirst().endDate());
        }
    }

    @Test
    void toleratesANullEndDate() {
        logic.createLeasehold("plot_no_end", WORLD_ID, LANDLORD, 100.0, 604800L, null);
        logic.setTenant("plot_no_end", WORLD_ID, TENANT);

        try (SqlSessionWrapper session = database.openSession(true)) {
            List<RentedRegionView> rows = session.leaseholdContractMapper()
                    .selectRentedRegionsWithEndDate(TENANT, 10, 0);
            Assertions.assertEquals(1, rows.size());
            Assertions.assertNull(rows.getFirst().endDate());
        }
    }

    @Test
    void returnsNothingForATenantWhoRentsNothing() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            List<RentedRegionView> rows = session.leaseholdContractMapper()
                    .selectRentedRegionsWithEndDate(UUID.randomUUID(), 10, 0);
            Assertions.assertTrue(rows.isEmpty());
        }
    }
}
```

The exact `logic.*` setup calls must match the real signatures on `RealtyBackend`. Read `RealtyBackend.java` for `createLeasehold`, `setTenant` and whichever method sets an end date, and adjust the three setup lines to the real signatures. If no public method sets an end date, insert it directly with a `session.leaseholdContractMapper()` call or raw SQL in the test.

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :realty-backend:test --tests "*RentedRegionViewTest*"`
Expected: FAIL — `RentedRegionView` and `selectRentedRegionsWithEndDate` do not exist.

- [ ] **Step 4: Write the projection record**

Create `realty-backend-api/src/main/java/io/github/md5sha256/realty/database/entity/RentedRegionView.java`:

```java
package io.github.md5sha256.realty.database.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Projection entity pairing a rented region with its leasehold end date, so a
 * caller listing a tenant's rented regions does not need one lookup per region.
 *
 * @param worldGuardRegionId The WorldGuard region identifier
 * @param worldId            The world UUID
 * @param endDate            When the lease ends, or {@code null} for a lease with no end date
 */
public record RentedRegionView(
        @NotNull String worldGuardRegionId,
        @NotNull UUID worldId,
        @Nullable LocalDateTime endDate
) {
}
```

- [ ] **Step 5: Add the method to the base mapper**

In `LeaseholdContractMapper.java`:

```java
    @NotNull List<RentedRegionView> selectRentedRegionsWithEndDate(@NotNull UUID tenantId,
                                                                   int limit,
                                                                   int offset);
```

Add explicit imports for `RentedRegionView` and `java.util.List` if not already present.

- [ ] **Step 6: Implement it in the MariaDB mapper**

In `MariaLeaseholdContractMapper.java`, adjusting join aliases to match the existing queries in that file:

```java
    @Override
    @Select("""
            SELECT rr.worldGuardRegionId, rr.worldId, lc.endDate
            FROM RealtyRegion rr
            INNER JOIN Contract c
                ON c.realtyRegionId = rr.realtyRegionId
                AND c.contractType = 'leasehold'
            INNER JOIN LeaseholdContract lc
                ON lc.leaseholdContractId = c.contractId
            WHERE lc.tenantId = #{tenantId}
            ORDER BY rr.worldGuardRegionId
            LIMIT #{limit} OFFSET #{offset}
            """)
    @ConstructorArgs({
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "endDate", javaType = LocalDateTime.class)
    })
    @NotNull List<RentedRegionView> selectRentedRegionsWithEndDate(
            @Param("tenantId") @NotNull UUID tenantId,
            @Param("limit") int limit,
            @Param("offset") int offset);
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew :realty-backend:test --tests "*RentedRegionViewTest*"`
Expected: PASS, 3 tests.

- [ ] **Step 8: Commit**

```bash
git add realty-backend-api/src/main/java/io/github/md5sha256/realty/database/entity/RentedRegionView.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/mapper/LeaseholdContractMapper.java \
        realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaLeaseholdContractMapper.java \
        realty-backend/src/test/java/io/github/md5sha256/realty/database/RentedRegionViewTest.java
git commit -m "feat(backend): add rented-regions-with-end-date projection query"
```

**Do not change `ListCommand` to use this.** The command keeps its current behaviour; adopting the query there is a separate, deliberate change.

---

### Task 3: Realty core maintains the world table

**Depends on Task 1.**

**Files:**
- Create: `realty-paper/src/main/java/io/github/md5sha256/realty/listener/WorldRegistrar.java`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java`
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/listener/WorldRegistrarTest.java`

**Interfaces:**
- Consumes: `SqlSessionWrapper.realtyWorldMapper()` and `RealtyWorldMapper.upsert` from Task 1.
- Produces: `WorldRegistrar` implementing `org.bukkit.event.Listener`, with a public `void syncAll(Collection<World> worlds)` used at enable and testable without a running server.

- [ ] **Step 1: Write the failing test**

Create `realty-paper/src/test/java/io/github/md5sha256/realty/listener/WorldRegistrarTest.java`. This test never touches Bukkit — it asserts that `syncAll` upserts one row per input, using a hand-written fake mapper.

```java
package io.github.md5sha256.realty.listener;

import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import io.github.md5sha256.realty.database.mapper.RealtyWorldMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class WorldRegistrarTest {

    private static final class RecordingMapper implements RealtyWorldMapper {

        private final Map<UUID, String> rows = new LinkedHashMap<>();

        @Override
        public void upsert(UUID worldId, String worldName) {
            this.rows.put(worldId, worldName);
        }

        @Override
        public List<RealtyWorldEntity> selectAll() {
            List<RealtyWorldEntity> all = new ArrayList<>();
            this.rows.forEach((id, name) -> all.add(new RealtyWorldEntity(id, name)));
            return all;
        }

        @Override
        public RealtyWorldEntity selectById(UUID worldId) {
            String name = this.rows.get(worldId);
            return name == null ? null : new RealtyWorldEntity(worldId, name);
        }

        @Override
        public RealtyWorldEntity selectByName(String worldName) {
            return null;
        }
    }

    @Test
    void syncAllUpsertsEveryWorld() {
        RecordingMapper mapper = new RecordingMapper();
        UUID overworld = UUID.randomUUID();
        UUID nether = UUID.randomUUID();

        WorldRegistrar.syncAll(mapper, Map.of(overworld, "world", nether, "world_nether"));

        Assertions.assertEquals(2, mapper.selectAll().size());
        Assertions.assertEquals("world", mapper.selectById(overworld).worldName());
        Assertions.assertEquals("world_nether", mapper.selectById(nether).worldName());
    }

    @Test
    void syncAllOverwritesARenamedWorld() {
        RecordingMapper mapper = new RecordingMapper();
        UUID overworld = UUID.randomUUID();

        WorldRegistrar.syncAll(mapper, Map.of(overworld, "old_name"));
        WorldRegistrar.syncAll(mapper, Map.of(overworld, "new_name"));

        Assertions.assertEquals(1, mapper.selectAll().size());
        Assertions.assertEquals("new_name", mapper.selectById(overworld).worldName());
    }

    @Test
    void syncAllAcceptsAWorldNameContainingSpaces() {
        RecordingMapper mapper = new RecordingMapper();
        UUID id = UUID.randomUUID();

        WorldRegistrar.syncAll(mapper, Map.of(id, "My World"));

        Assertions.assertEquals("My World", mapper.selectById(id).worldName());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :realty-paper:test --tests "*WorldRegistrarTest*"`
Expected: FAIL — `WorldRegistrar` does not exist.

- [ ] **Step 3: Write the listener**

Create `realty-paper/src/main/java/io/github/md5sha256/realty/listener/WorldRegistrar.java`:

```java
package io.github.md5sha256.realty.listener;

import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.mapper.RealtyWorldMapper;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Keeps the {@code RealtyWorld} table in step with the worlds Bukkit has loaded.
 *
 * <p>The REST API runs in a separate process and cannot ask Bukkit for a world's
 * name, so core projects the mapping into the database. Worlds are a bounded,
 * effectively immutable set and Bukkit fires load events for them, which is why
 * this projection can stay correct where one for WorldGuard geometry could not.</p>
 *
 * <p>Unloading a world does not delete its row. A region in an unloaded world is
 * still a region the API must be able to name.</p>
 */
public final class WorldRegistrar implements Listener {

    private final Database database;
    private final Executor databaseExecutor;

    public WorldRegistrar(@NotNull Database database, @NotNull Executor databaseExecutor) {
        this.database = database;
        this.databaseExecutor = databaseExecutor;
    }

    /**
     * Upserts every entry of {@code worlds}. Package-visible and static so it can be
     * tested without a running server.
     */
    static void syncAll(@NotNull RealtyWorldMapper mapper, @NotNull Map<UUID, String> worlds) {
        worlds.forEach(mapper::upsert);
    }

    /**
     * Reads the world list on the calling (main) thread, then writes off it.
     */
    public void syncLoadedWorlds(@NotNull Iterable<World> worlds) {
        Map<UUID, String> snapshot = new LinkedHashMap<>();
        for (World world : worlds) {
            snapshot.put(world.getUID(), world.getName());
        }
        write(snapshot);
    }

    @EventHandler
    public void onWorldLoad(@NotNull WorldLoadEvent event) {
        World world = event.getWorld();
        write(Map.of(world.getUID(), world.getName()));
    }

    private void write(@NotNull Map<UUID, String> snapshot) {
        if (snapshot.isEmpty()) {
            return;
        }
        this.databaseExecutor.execute(() -> {
            try (SqlSessionWrapper session = this.database.openSession(true)) {
                syncAll(session.realtyWorldMapper(), snapshot);
            }
        });
    }

}
```

Note there is no `WorldUnloadEvent` handler. An unloaded world's regions still exist in the database and must still be nameable, so rows are never removed. The spec's mention of unload events refers to keeping the table converged, which load-time upserts achieve on their own.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :realty-paper:test --tests "*WorldRegistrarTest*"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Wire it into the plugin**

In `Realty.java`, in `onEnable` where other listeners are registered, construct the registrar with the same `Database` and async executor the plugin already holds, register it as a listener, then seed it:

```java
        WorldRegistrar worldRegistrar = new WorldRegistrar(mariaDatabase, this.databaseExecutor);
        getServer().getPluginManager().registerEvents(worldRegistrar, this);
        worldRegistrar.syncLoadedWorlds(getServer().getWorlds());
```

Use whatever the surrounding code calls the database instance and the async executor — read the neighbouring lines rather than assuming these names. Add the import explicitly.

- [ ] **Step 6: Verify the plugin still builds**

Run: `./gradlew :realty-paper:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add realty-paper/src/main/java/io/github/md5sha256/realty/listener/WorldRegistrar.java \
        realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java \
        realty-paper/src/test/java/io/github/md5sha256/realty/listener/WorldRegistrarTest.java
git commit -m "feat(paper): project loaded worlds into the RealtyWorld table"
```

---

### Task 4: `realty-rest` subproject and environment configuration

**Files:**
- Modify: `settings.gradle.kts`
- Create: `realty-rest/build.gradle.kts`
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RestSettings.java`
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RestConfiguration.java`
- Test: `realty-rest/src/test/java/io/github/md5sha256/realty/rest/RestConfigurationTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `RestSettings(String host, int port, int maxPageSize, String moduleUrl, String moduleSecret, int moduleTimeoutMs)` — record, `moduleUrl` and `moduleSecret` nullable.
  - `RestConfiguration.load(Function<String, String> env)` returning a `RestConfiguration(DatabaseSettings database, RestSettings rest)` record, throwing `IllegalStateException` naming the missing variable.

- [ ] **Step 1: Register the subproject**

In `settings.gradle.kts`, after the `realty-paper` line:

```kotlin
include("realty-rest")
```

- [ ] **Step 2: Write the build file**

Create `realty-rest/build.gradle.kts`:

```kotlin
plugins {
    `realty-conventions`
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":realty-backend"))
    implementation("io.javalin:javalin:6.4.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    compileOnly("org.jetbrains:annotations:26.0.2-1")

    testImplementation("org.testcontainers:testcontainers-mariadb:2.0.1")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
}

tasks.shadowJar {
    archiveBaseName.set("realty-rest")
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "io.github.md5sha256.realty.rest.RealtyRestMain"
    }
    mergeServiceFiles()
}
```

Check how the Shadow plugin is declared in `realty-paper/build.gradle.kts` and match that exact plugin id and version mechanism — if it comes from the root `plugins` block or a version catalog, use the same route rather than the id above.

- [ ] **Step 3: Write the failing test**

Create `realty-rest/src/test/java/io/github/md5sha256/realty/rest/RestConfigurationTest.java`:

```java
package io.github.md5sha256.realty.rest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class RestConfigurationTest {

    private static Map<String, String> validEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("REALTY_DB_URL", "mariadb://localhost:3306/realty");
        env.put("REALTY_DB_USERNAME", "realty");
        env.put("REALTY_DB_PASSWORD", "secret");
        return env;
    }

    @Test
    void loadsDefaultsWhenOptionalVariablesAreAbsent() {
        RestConfiguration config = RestConfiguration.load(validEnv()::get);
        Assertions.assertEquals("0.0.0.0", config.rest().host());
        Assertions.assertEquals(8080, config.rest().port());
        Assertions.assertEquals(100, config.rest().maxPageSize());
        Assertions.assertNull(config.rest().moduleUrl());
        Assertions.assertEquals(1500, config.rest().moduleTimeoutMs());
    }

    @Test
    void readsTheDatabaseSettings() {
        RestConfiguration config = RestConfiguration.load(validEnv()::get);
        Assertions.assertEquals("mariadb://localhost:3306/realty", config.database().url());
        Assertions.assertEquals("realty", config.database().username());
    }

    @Test
    void overridesDefaultsFromTheEnvironment() {
        Map<String, String> env = validEnv();
        env.put("REALTY_REST_PORT", "9000");
        env.put("REALTY_REST_MAX_PAGE_SIZE", "25");
        RestConfiguration config = RestConfiguration.load(env::get);
        Assertions.assertEquals(9000, config.rest().port());
        Assertions.assertEquals(25, config.rest().maxPageSize());
    }

    @Test
    void failsNamingTheMissingRequiredVariable() {
        Map<String, String> env = validEnv();
        env.remove("REALTY_DB_PASSWORD");
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> RestConfiguration.load(env::get));
        Assertions.assertTrue(thrown.getMessage().contains("REALTY_DB_PASSWORD"),
                "message should name the missing variable, was: " + thrown.getMessage());
    }

    @Test
    void failsNamingAVariableThatIsNotAnInteger() {
        Map<String, String> env = validEnv();
        env.put("REALTY_REST_PORT", "not-a-number");
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> RestConfiguration.load(env::get));
        Assertions.assertTrue(thrown.getMessage().contains("REALTY_REST_PORT"),
                "message should name the offending variable, was: " + thrown.getMessage());
    }

    @Test
    void redactsThePasswordWhenDescribed() {
        RestConfiguration config = RestConfiguration.load(validEnv()::get);
        String described = config.describeRedacted();
        Assertions.assertFalse(described.contains("secret"), "password must not appear: " + described);
        Assertions.assertTrue(described.contains("REALTY_DB_URL"));
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :realty-rest:test`
Expected: FAIL — `RestConfiguration` does not exist.

- [ ] **Step 5: Write the settings record**

Create `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RestSettings.java`:

```java
package io.github.md5sha256.realty.rest;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * HTTP-side settings, read from the environment.
 *
 * @param moduleUrl       base URL of the query-service module, or {@code null} to
 *                        disable enrichment entirely
 * @param moduleSecret    shared secret sent to the module, or {@code null}
 * @param moduleTimeoutMs per-call timeout before a module-sourced field degrades to null
 */
public record RestSettings(
        @NotNull String host,
        int port,
        int maxPageSize,
        @Nullable String moduleUrl,
        @Nullable String moduleSecret,
        int moduleTimeoutMs
) {
}
```

- [ ] **Step 6: Write the configuration loader**

Create `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RestConfiguration.java`:

```java
package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.DatabaseSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * The service's entire configuration, resolved from environment variables.
 *
 * <p>There is deliberately no config file: the deployment targets are Docker and
 * Pterodactyl, where the panel owns the values and the filesystem is ephemeral.
 * Discoverability is served by {@link #describeRedacted()} being logged at startup
 * and by failures naming the exact variable at fault.</p>
 */
public record RestConfiguration(
        @NotNull DatabaseSettings database,
        @NotNull RestSettings rest
) {

    public static @NotNull RestConfiguration load(@NotNull Function<String, String> env) {
        DatabaseSettings database = new DatabaseSettings(
                required(env, "REALTY_DB_URL"),
                required(env, "REALTY_DB_USERNAME"),
                required(env, "REALTY_DB_PASSWORD"));
        RestSettings rest = new RestSettings(
                optional(env, "REALTY_REST_HOST", "0.0.0.0"),
                integer(env, "REALTY_REST_PORT", 8080),
                integer(env, "REALTY_REST_MAX_PAGE_SIZE", 100),
                env.apply("REALTY_REST_MODULE_URL"),
                env.apply("REALTY_REST_MODULE_SECRET"),
                integer(env, "REALTY_REST_MODULE_TIMEOUT_MS", 1500));
        return new RestConfiguration(database, rest);
    }

    /**
     * Every resolved setting, with secrets replaced. Logged at startup so the
     * running configuration is always visible.
     */
    public @NotNull String describeRedacted() {
        return """
                REALTY_DB_URL=%s
                REALTY_DB_USERNAME=%s
                REALTY_DB_PASSWORD=%s
                REALTY_REST_HOST=%s
                REALTY_REST_PORT=%d
                REALTY_REST_MAX_PAGE_SIZE=%d
                REALTY_REST_MODULE_URL=%s
                REALTY_REST_MODULE_SECRET=%s
                REALTY_REST_MODULE_TIMEOUT_MS=%d"""
                .formatted(this.database.url(),
                        this.database.username(),
                        "<redacted>",
                        this.rest.host(),
                        this.rest.port(),
                        this.rest.maxPageSize(),
                        this.rest.moduleUrl() == null ? "<unset>" : this.rest.moduleUrl(),
                        this.rest.moduleSecret() == null ? "<unset>" : "<redacted>",
                        this.rest.moduleTimeoutMs());
    }

    private static @NotNull String required(@NotNull Function<String, String> env,
                                            @NotNull String key) {
        String value = env.apply(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable " + key + " is not set");
        }
        return value;
    }

    private static @NotNull String optional(@NotNull Function<String, String> env,
                                            @NotNull String key,
                                            @NotNull String fallback) {
        String value = env.apply(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int integer(@NotNull Function<String, String> env,
                               @NotNull String key,
                               int fallback) {
        String value = env.apply(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Environment variable " + key + " must be an integer, was: " + value, ex);
        }
    }

}
```

`@Nullable` is imported for the record component annotations in `RestSettings`, not for this file — remove the import here if your IDE flags it as unused.

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew :realty-rest:test`
Expected: PASS, 6 tests.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts realty-rest/
git commit -m "feat(rest): scaffold realty-rest with environment configuration"
```

---

### Task 5: Javalin bootstrap, health endpoint and error mapping

**Depends on Task 4.**

**Files:**
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RealtyRestMain.java`
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RealtyRestServer.java`
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/ApiException.java`
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/json/ErrorResponse.java`
- Test: `realty-rest/src/test/java/io/github/md5sha256/realty/rest/HealthEndpointTest.java`

**Interfaces:**
- Consumes: `RestConfiguration`, `RestSettings` from Task 4.
- Produces:
  - `RealtyRestServer(RealtyBackend backend, Database database, RestSettings settings)` with `void start()` and `void stop()`, and `io.javalin.Javalin javalin()` exposing the configured instance for tests.
  - `ApiException(int status, String code, String message)` extending `RuntimeException`, with static factories `notFound(String code, String message)`, `badRequest(String code, String message)`, `badGateway(String code, String message)`.
  - `ErrorResponse(String error, String message)` record.
  - A `ROUTES` constant on `RealtyRestServer`: `List<String>` of every registered path, used by Task 9's conformance test. Each later task appends its path.

- [ ] **Step 1: Write the failing test**

Create `realty-rest/src/test/java/io/github/md5sha256/realty/rest/HealthEndpointTest.java`:

```java
package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class HealthEndpointTest {

    @Test
    void healthReturnsOkWhenTheDatabaseAnswers() throws IOException {
        RealtyRestServer server = TestServers.withHealthyDatabase();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/health");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"status\":\"ok\""));
        });
    }

    @Test
    void healthReturns503WhenTheDatabaseIsUnreachable() {
        RealtyRestServer server = TestServers.withFailingDatabase();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/health");
            Assertions.assertEquals(503, response.code());
        });
    }

    @Test
    void anUnknownPathReturnsAJsonErrorBody() {
        RealtyRestServer server = TestServers.withHealthyDatabase();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/nope");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("\"error\""));
        });
    }
}
```

Add `testImplementation("io.javalin:javalin-testtools:6.4.0")` to `realty-rest/build.gradle.kts`.

Also create `realty-rest/src/test/java/io/github/md5sha256/realty/rest/TestServers.java`, a small helper building a `RealtyRestServer` over a stubbed `RealtyBackend` and a `Database` whose `openSession` either works or throws. Write it as a plain class with two static factory methods returning `RealtyRestServer`; stub only the methods the endpoint under test actually calls, throwing `UnsupportedOperationException` from the rest.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :realty-rest:test --tests "*HealthEndpointTest*"`
Expected: FAIL — `RealtyRestServer` does not exist.

- [ ] **Step 3: Write the error types**

Create `ApiException.java`:

```java
package io.github.md5sha256.realty.rest;

import org.jetbrains.annotations.NotNull;

/**
 * An error with a chosen HTTP status and a stable machine-readable code.
 *
 * <p>Never thrown for an unexpected failure — those become a generic 500 whose
 * detail goes to the log, never to the client.</p>
 */
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

    public static @NotNull ApiException notFound(@NotNull String code, @NotNull String message) {
        return new ApiException(404, code, message);
    }

    public static @NotNull ApiException badGateway(@NotNull String code, @NotNull String message) {
        return new ApiException(502, code, message);
    }

    public int status() {
        return this.status;
    }

    public @NotNull String code() {
        return this.code;
    }

}
```

Create `json/ErrorResponse.java`:

```java
package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;

public record ErrorResponse(@NotNull String error, @NotNull String message) {
}
```

- [ ] **Step 4: Write the server**

Create `RealtyRestServer.java`:

```java
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
            "/v1/health"
    );

    private final RealtyBackend backend;
    private final Database database;
    private final RestSettings settings;
    private final Javalin javalin;

    public RealtyRestServer(@NotNull RealtyBackend backend,
                            @NotNull Database database,
                            @NotNull RestSettings settings) {
        this.backend = backend;
        this.database = database;
        this.settings = settings;
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

        // Task 6 registers /v1/worlds here.
        // Task 7 registers /v1/regions here.
        // Task 8 registers /v1/players/regions here.

        this.javalin.exception(ApiException.class, (ex, ctx) ->
                ctx.status(ex.status()).json(new ErrorResponse(ex.code(), ex.getMessage())));

        this.javalin.exception(Exception.class, (ex, ctx) -> {
            LOGGER.log(Level.SEVERE, "Unhandled failure serving " + ctx.path(), ex);
            ctx.status(500).json(new ErrorResponse("INTERNAL_ERROR",
                    "An unexpected error occurred"));
        });

        this.javalin.error(404, ctx -> {
            if (ctx.result() == null || ctx.result().isBlank()) {
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

    public void start() {
        this.javalin.start(this.settings.host(), this.settings.port());
    }

    public void stop() {
        this.javalin.stop();
    }

}
```

- [ ] **Step 5: Write the entry point**

Create `RealtyRestMain.java`:

```java
package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.RealtyBackendImpl;
import io.github.md5sha256.realty.database.maria.MariaDatabase;
import org.jetbrains.annotations.NotNull;

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
```

This never calls `initializeSchema` and never migrates. The `SchemaVersionCheck` call is added in the next step.

- [ ] **Step 6: Write the failing schema-version test**

Create `realty-rest/src/test/java/io/github/md5sha256/realty/rest/SchemaVersionCheckTest.java`:

```java
package io.github.md5sha256.realty.rest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SchemaVersionCheckTest {

    @Test
    void acceptsTheExactExpectedVersion() {
        Assertions.assertDoesNotThrow(
                () -> SchemaVersionCheck.verify(SchemaVersionCheck.EXPECTED_VERSION));
    }

    @Test
    void acceptsAnOlderDatabase() {
        Assertions.assertDoesNotThrow(
                () -> SchemaVersionCheck.verify(SchemaVersionCheck.EXPECTED_VERSION - 1));
    }

    @Test
    void refusesANewerDatabaseNamingBothVersions() {
        int newer = SchemaVersionCheck.EXPECTED_VERSION + 1;
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> SchemaVersionCheck.verify(newer));
        Assertions.assertTrue(thrown.getMessage().contains(String.valueOf(newer)));
        Assertions.assertTrue(thrown.getMessage()
                .contains(String.valueOf(SchemaVersionCheck.EXPECTED_VERSION)));
    }
}
```

An older database is accepted deliberately. The API reads a subset of columns, so a database behind this build is missing at most a table the API tolerates being absent; a database *ahead* of it may have changed the meaning of a column this build reads, which is the unsafe direction.

- [ ] **Step 7: Run it to verify it fails**

Run: `./gradlew :realty-rest:test --tests "*SchemaVersionCheckTest*"`
Expected: FAIL — `SchemaVersionCheck` does not exist.

- [ ] **Step 8: Write the check**

Create `realty-rest/src/main/java/io/github/md5sha256/realty/rest/SchemaVersionCheck.java`:

```java
package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.DatabaseSettings;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Refuses to start against a database migrated past what this build understands.
 *
 * <p>A service that silently serves columns it misreads is worse than one that does
 * not start: the failure here is immediate and names the real cause, which is a
 * plugin upgraded ahead of the API.</p>
 */
public final class SchemaVersionCheck {

    /**
     * The highest migration version this build was written against. Bump this
     * deliberately when a migration lands that the API must understand.
     */
    public static final int EXPECTED_VERSION = 16;

    private static final String SELECT_VERSION = """
            SELECT COALESCE(MAX(version), 0) FROM schema_version
            """;

    private SchemaVersionCheck() {
    }

    public static void verify(int appliedVersion) {
        if (appliedVersion > EXPECTED_VERSION) {
            throw new IllegalStateException(
                    "Database schema version " + appliedVersion + " is newer than this build "
                            + "understands (" + EXPECTED_VERSION + "). Upgrade realty-rest to match "
                            + "the Realty plugin before starting it.");
        }
    }

    public static int readAppliedVersion(@NotNull DatabaseSettings settings) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:" + settings.url(), settings.username(), settings.password());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SELECT_VERSION)) {
            rs.next();
            return rs.getInt(1);
        }
    }

}
```

`MariaDatabase` prepends `jdbc:` to `settings.url()` — see `MariaDatabase.jdbcUrl` — so this does the same rather than assuming the URL already carries the prefix.

- [ ] **Step 9: Call it from the entry point**

In `RealtyRestMain.main`, after logging the configuration and before constructing `MariaDatabase`:

```java
        try {
            SchemaVersionCheck.verify(SchemaVersionCheck.readAppliedVersion(config.database()));
        } catch (SQLException | IllegalStateException ex) {
            LOGGER.severe(ex.getMessage());
            System.exit(1);
            return;
        }
```

Add the `java.sql.SQLException` import.

- [ ] **Step 10: Run the tests to verify they pass**

Run: `./gradlew :realty-rest:test --tests "*HealthEndpointTest*" --tests "*SchemaVersionCheckTest*"`
Expected: PASS, 6 tests.

- [ ] **Step 11: Commit**

```bash
git add realty-rest/
git commit -m "feat(rest): add Javalin bootstrap, health endpoint and schema version gate"
```

---

### Task 6: `GET /v1/worlds`

**Depends on Tasks 1 and 5.**

**Files:**
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/json/WorldRef.java`
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/WorldLookup.java`
- Modify: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RealtyRestServer.java` (add the route and its `ROUTES` entry)
- Test: `realty-rest/src/test/java/io/github/md5sha256/realty/rest/WorldsEndpointTest.java`

**Interfaces:**
- Consumes: `RealtyWorldMapper` and `RealtyWorldEntity` from Task 1; `RealtyRestServer`, `ApiException` from Task 5.
- Produces:
  - `WorldRef(String id, String name)` record — the `{id, name}` identity shape for worlds. `name` nullable.
  - `WorldLookup(Database database)` with `List<WorldRef> all()`, `UUID resolve(String worldNameOrUuid)` throwing `ApiException.notFound` for an unknown name, and `WorldRef refFor(UUID worldId)`.

- [ ] **Step 1: Write the failing test**

Create `realty-rest/src/test/java/io/github/md5sha256/realty/rest/WorldsEndpointTest.java`:

```java
package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorldsEndpointTest {

    @Test
    void listsEveryKnownWorld() {
        RealtyRestServer server = TestServers.withWorlds();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/worlds");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"name\":\"world\""));
            Assertions.assertTrue(body.contains("\"name\":\"My World\""));
        });
    }

    @Test
    void returnsAnEmptyArrayWhenNoWorldsAreKnown() {
        RealtyRestServer server = TestServers.withNoWorlds();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/worlds");
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("[]", response.body().string().trim());
        });
    }
}
```

Add `withWorlds()` and `withNoWorlds()` factories to `TestServers`, backing the stub `Database` with a fixed list containing a world named `world` and one named `My World`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :realty-rest:test --tests "*WorldsEndpointTest*"`
Expected: FAIL — route not registered, 404.

- [ ] **Step 3: Write the world identity record**

Create `json/WorldRef.java`:

```java
package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A world identity. The UUID is always present and is the stable identifier; the
 * name is nullable because a region may reference a world the table has never seen.
 */
public record WorldRef(@NotNull String id, @Nullable String name) {
}
```

- [ ] **Step 4: Write the lookup**

Create `WorldLookup.java`:

```java
package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import io.github.md5sha256.realty.rest.json.WorldRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Resolves worlds by name or UUID against the {@code RealtyWorld} table.
 *
 * <p>Reads the database on every call. Worlds are a handful of rows and the query
 * is a primary-key or indexed lookup; caching is not worth the staleness until
 * something measures a need.</p>
 */
public final class WorldLookup {

    private final Database database;

    public WorldLookup(@NotNull Database database) {
        this.database = database;
    }

    public @NotNull List<WorldRef> all() {
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            List<WorldRef> refs = new ArrayList<>();
            for (RealtyWorldEntity entity : session.realtyWorldMapper().selectAll()) {
                refs.add(new WorldRef(entity.worldId().toString(), entity.worldName()));
            }
            return refs;
        }
    }

    /**
     * Accepts a world UUID or a world name. A UUID is returned as-is without a
     * database round trip, so a lookup by UUID works even for a world the table
     * has never seen.
     */
    public @NotNull UUID resolve(@NotNull String worldNameOrUuid) {
        try {
            return UUID.fromString(worldNameOrUuid);
        } catch (IllegalArgumentException ignored) {
            // Not a UUID, so treat it as a name.
        }
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            RealtyWorldEntity entity = session.realtyWorldMapper().selectByName(worldNameOrUuid);
            if (entity == null) {
                throw ApiException.notFound("WORLD_NOT_FOUND",
                        "No world named '" + worldNameOrUuid + "'");
            }
            return entity.worldId();
        }
    }

    public @NotNull WorldRef refFor(@NotNull UUID worldId) {
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            RealtyWorldEntity entity = session.realtyWorldMapper().selectById(worldId);
            return new WorldRef(worldId.toString(), entity == null ? null : entity.worldName());
        }
    }

}
```

- [ ] **Step 5: Register the route**

In `RealtyRestServer.registerRoutes()`, replace the `// Task 6 registers` comment with:

```java
        WorldLookup worldLookup = new WorldLookup(this.database);
        this.javalin.get("/v1/worlds", ctx -> ctx.json(worldLookup.all()));
```

Hoist `worldLookup` to a field if Tasks 7 and 8 also need it — they do, so make it a private final field assigned in the constructor before `registerRoutes()` is called, and expose it via a package-private `worldLookup()` accessor.

Add `"/v1/worlds"` to the `ROUTES` list.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :realty-rest:test --tests "*WorldsEndpointTest*"`
Expected: PASS, 2 tests.

- [ ] **Step 7: Commit**

```bash
git add realty-rest/
git commit -m "feat(rest): add GET /v1/worlds"
```

---

### Task 7: `GET /v1/regions`

**Depends on Tasks 1 and 5.** Uses `WorldLookup` from Task 6; if Task 6 has not landed yet, create `WorldLookup` exactly as specified there — the two tasks define it identically.

**Files:**
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/json/PlayerRef.java`
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/json/RegionResponse.java`
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RegionHandler.java`
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/QueryParams.java`
- Modify: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RealtyRestServer.java`
- Test: `realty-rest/src/test/java/io/github/md5sha256/realty/rest/RegionEndpointTest.java`
- Test: `realty-rest/src/test/java/io/github/md5sha256/realty/rest/QueryParamsTest.java`

**Interfaces:**
- Consumes: `RealtyBackend.getRegionInfo`, `getRegionState`, `SqlSessionWrapper.regionTagMapper()`, `WorldLookup`.
- Produces:
  - `PlayerRef(String id, String name)` record, `name` nullable — always null in this plan.
  - `RegionResponse` record, shaped exactly as the spec's example.
  - `QueryParams.required(Context ctx, String name)` and `QueryParams.plusAwareDecode(String raw)`.

- [ ] **Step 1: Write the failing encoding test**

Create `realty-rest/src/test/java/io/github/md5sha256/realty/rest/QueryParamsTest.java`:

```java
package io.github.md5sha256.realty.rest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class QueryParamsTest {

    @Test
    void decodesPercentEncodedSpaces() {
        Assertions.assertEquals("My World", QueryParams.plusAwareDecode("My%20World"));
    }

    @Test
    void decodesPlusAsASpace() {
        Assertions.assertEquals("My World", QueryParams.plusAwareDecode("My+World"));
    }

    @Test
    void decodesAFloodgateNameWithADotPrefixAndSpaces() {
        Assertions.assertEquals(".Cool Guy 123", QueryParams.plusAwareDecode(".Cool%20Guy%20123"));
        Assertions.assertEquals(".Cool Guy 123", QueryParams.plusAwareDecode(".Cool+Guy+123"));
    }

    @Test
    void leavesAPercentSignInAWorldNameIntact() {
        Assertions.assertEquals("100%", QueryParams.plusAwareDecode("100%25"));
    }

    @Test
    void leavesAnOrdinaryNameUnchanged() {
        Assertions.assertEquals("world_nether", QueryParams.plusAwareDecode("world_nether"));
    }
}
```

- [ ] **Step 2: Write the failing endpoint test**

Create `realty-rest/src/test/java/io/github/md5sha256/realty/rest/RegionEndpointTest.java`:

```java
package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RegionEndpointTest {

    @Test
    void returnsAForSaleFreeholdRegion() {
        RealtyRestServer server = TestServers.withForSaleRegion();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/regions?world=world&region=downtown_plot_14");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"worldGuardRegionId\":\"downtown_plot_14\""));
            Assertions.assertTrue(body.contains("\"price\":25000.0"));
            Assertions.assertTrue(body.contains("\"leasehold\":null"));
            Assertions.assertTrue(body.contains("\"dimensions\":null"));
        });
    }

    @Test
    void resolvesAWorldNameContainingASpace() {
        RealtyRestServer server = TestServers.withRegionInWorldNamedMyWorld();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Assertions.assertEquals(200,
                    client.get("/v1/regions?world=My%20World&region=plot_1").code());
            Assertions.assertEquals(200,
                    client.get("/v1/regions?world=My+World&region=plot_1").code());
        });
    }

    @Test
    void returns404ForAnUnknownWorldName() {
        RealtyRestServer server = TestServers.withNoWorlds();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/regions?world=nope&region=plot_1");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("WORLD_NOT_FOUND"));
        });
    }

    @Test
    void returns404ForAnUnknownRegion() {
        RealtyRestServer server = TestServers.withWorldsButNoRegions();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/regions?world=world&region=nope");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("REGION_NOT_FOUND"));
        });
    }

    @Test
    void returns400WhenTheRegionParameterIsMissing() {
        RealtyRestServer server = TestServers.withWorlds();
        JavalinTest.test(server.javalin(), (jsonServer, client) ->
                Assertions.assertEquals(400, client.get("/v1/regions?world=world").code()));
    }

    @Test
    void playerIdentitiesCarryANullNameUntilEnrichmentShips() {
        RealtyRestServer server = TestServers.withForSaleRegion();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/regions?world=world&region=downtown_plot_14")
                    .body().string();
            Assertions.assertTrue(body.contains("\"name\":null"));
        });
    }
}
```

Add the named factories to `TestServers`, each stubbing `RealtyBackend.getRegionInfo`/`getRegionState` with fixed entity records.

- [ ] **Step 3: Run both tests to verify they fail**

Run: `./gradlew :realty-rest:test --tests "*QueryParamsTest*" --tests "*RegionEndpointTest*"`
Expected: FAIL — `QueryParams` does not exist; route not registered.

- [ ] **Step 4: Write the query parameter helper**

Create `QueryParams.java`:

```java
package io.github.md5sha256.realty.rest;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Query parameter reading, with the decoding rules the API's names demand.
 *
 * <p>Neither kind of name this API accepts is URL-safe. A world name is a folder
 * name and may contain spaces or a percent sign; a Floodgate player name is an
 * Xbox gamertag behind a {@code .} prefix and may contain spaces. Clients differ
 * on whether they encode a space as {@code %20} or {@code +}, so both are
 * accepted.</p>
 */
public final class QueryParams {

    private QueryParams() {
    }

    public static @NotNull String required(@NotNull Context ctx, @NotNull String name) {
        String raw = ctx.queryParam(name);
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("MISSING_PARAMETER",
                    "Query parameter '" + name + "' is required");
        }
        return raw;
    }

    /**
     * Decodes a raw query value, treating {@code +} as a space.
     *
     * <p>Javalin has already percent-decoded the value, so this only has to undo a
     * {@code +}. Decoding twice would corrupt a name legitimately containing a
     * percent sign, so the value is not run through {@link URLDecoder} again.</p>
     */
    public static @NotNull String plusAwareDecode(@NotNull String raw) {
        String percentDecoded = URLDecoder.decode(raw.replace("+", "%20"), StandardCharsets.UTF_8);
        return percentDecoded;
    }

}
```

**Verify this against real Javalin behaviour before trusting it.** Run the `QueryParamsTest` cases and one live request through `JavalinTest` with `?world=100%25`. If Javalin already decodes `+` to a space, `plusAwareDecode` becomes an identity function on values read via `ctx.queryParam` and should be applied only to raw query strings. Adjust the implementation so all five `QueryParamsTest` cases and the live `100%` case pass; the tests define the contract, not this sketch.

- [ ] **Step 5: Write the response records**

Create `json/PlayerRef.java`:

```java
package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A player identity. The UUID is always present; the name is null until the
 * query-service enrichment client ships, and null thereafter whenever the module
 * is unreachable.
 */
public record PlayerRef(@NotNull String id, @Nullable String name) {
}
```

Create `json/RegionResponse.java`. The nested records live in the same file because they change together and are never used apart:

```java
package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The region payload, mirroring what {@code /realty info} renders.
 *
 * <p>{@code dimensions} is always null until the query-service enrichment client
 * ships, and null thereafter whenever the module is unreachable. It is deliberately
 * present-and-null rather than absent, so adding it later is not a breaking change.</p>
 */
public record RegionResponse(
        @NotNull String worldGuardRegionId,
        @NotNull WorldRef world,
        @Nullable String state,
        @Nullable Freehold freehold,
        @Nullable Leasehold leasehold,
        @Nullable Auction auction,
        @Nullable Object dimensions,
        @NotNull List<String> tags
) {

    /**
     * @param price a null price means the region is not currently for sale, which is
     *              how {@code InfoCommand} distinguishes its for-sale and sold renderings
     */
    public record Freehold(
            @Nullable PlayerRef titleHolder,
            @NotNull PlayerRef authority,
            @Nullable Double price,
            @Nullable Double lastSoldPrice
    ) {
    }

    /**
     * @param maxExtensions null means unlimited extensions
     */
    public record Leasehold(
            @NotNull PlayerRef landlord,
            @Nullable PlayerRef tenant,
            double price,
            long durationSeconds,
            @Nullable String startDate,
            @Nullable String endDate,
            @Nullable Integer extensionsUsed,
            @Nullable Integer maxExtensions
    ) {
    }

    public record Auction(
            @Nullable String endDate,
            @Nullable Bid highestBid
    ) {
    }

    public record Bid(
            @NotNull PlayerRef bidder,
            double amount
    ) {
    }

}
```

`dimensions` is typed `Object` only so this plan can always pass null; the enrichment plan replaces it with a real `DimensionsResponse` record. That is a deliberate, temporary loosening — do not add any other `Object`-typed field.

- [ ] **Step 6: Write the handler and register the route**

Create `RegionHandler.java` mapping `RealtyBackend.RegionInfo` and `RegionState` onto `RegionResponse`, reading tags via `session.regionTagMapper().selectTagIdsByRegionId(regionId)`. Throw `ApiException.notFound("REGION_NOT_FOUND", ...)` when `getRegionState` returns null and `RegionInfo` has no freehold, leasehold or auction. Format every `LocalDateTime` as ISO-8601 UTC using the same formatter as `RealtyRestMain.formatIso` — extract that to a shared `IsoDates.format(LocalDateTime)` utility and have both call it, rather than duplicating the conversion.

Register in `RealtyRestServer.registerRoutes()`, replacing the `// Task 7 registers` comment:

```java
        RegionHandler regionHandler = new RegionHandler(this.backend, this.database, this.worldLookup);
        this.javalin.get("/v1/regions", regionHandler::handle);
```

Add `"/v1/regions"` to `ROUTES`.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :realty-rest:test --tests "*QueryParamsTest*" --tests "*RegionEndpointTest*"`
Expected: PASS, 11 tests.

- [ ] **Step 8: Commit**

```bash
git add realty-rest/
git commit -m "feat(rest): add GET /v1/regions with name-based world lookup"
```

---

### Task 8: `GET /v1/players/regions`

**Depends on Tasks 2 and 5.**

**Files:**
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/json/PlayerRegionsResponse.java`
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/PlayerRegionsHandler.java`
- Modify: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RealtyRestServer.java`
- Test: `realty-rest/src/test/java/io/github/md5sha256/realty/rest/PlayerRegionsEndpointTest.java`

**Interfaces:**
- Consumes: `RealtyBackend.listRegions`, `listOwnedRegions`, `listRentedRegions`; `LeaseholdContractMapper.selectRentedRegionsWithEndDate` and `RentedRegionView` from Task 2; `WorldLookup.refFor`, `PlayerRef`, `QueryParams`.
- Produces: `PlayerRegionsResponse(PlayerRef player, int page, int pageSize, int totalCount, int totalPages, List<RegionRef> owned, List<RegionRef> landlord, List<RentedRef> rented, List<Object> regions)` where `regions` is null unless a single category was requested, and the three category lists are null when it was.

- [ ] **Step 1: Write the failing test**

Create `realty-rest/src/test/java/io/github/md5sha256/realty/rest/PlayerRegionsEndpointTest.java`:

```java
package io.github.md5sha256.realty.rest;

import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PlayerRegionsEndpointTest {

    private static final String UUID_PARAM = "3a1c88f0-0000-0000-0000-000000000001";

    @Test
    void returnsThreeCategoriesForCategoryAll() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/regions?player=" + UUID_PARAM);
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"owned\""));
            Assertions.assertTrue(body.contains("\"landlord\""));
            Assertions.assertTrue(body.contains("\"rented\""));
        });
    }

    @Test
    void rentedEntriesCarryEndDateAndSecondsRemaining() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/players/regions?player=" + UUID_PARAM).body().string();
            Assertions.assertTrue(body.contains("\"secondsRemaining\""));
            Assertions.assertTrue(body.contains("\"endDate\""));
        });
    }

    @Test
    void aPlayerWhoOwnsNothingIs200WithZeroTotal() {
        RealtyRestServer server = TestServers.withEmptyPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/regions?player=" + UUID_PARAM);
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"totalCount\":0"));
        });
    }

    @Test
    void returns400ForAMalformedUuid() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/regions?player=not-a-uuid");
            Assertions.assertEquals(400, response.code());
        });
    }

    @Test
    void returns502WhenLookingUpByNameWithNoModuleConfigured() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            Response response = client.get("/v1/players/regions?player=Notch");
            Assertions.assertEquals(502, response.code());
            Assertions.assertTrue(response.body().string().contains("NAME_LOOKUP_UNAVAILABLE"));
        });
    }

    @Test
    void clampsPageSizeToTheConfiguredMaximum() {
        RealtyRestServer server = TestServers.withPlayerHoldingsAndMaxPageSize(10);
        JavalinTest.test(server.javalin(), (jsonServer, client) -> {
            String body = client.get("/v1/players/regions?player=" + UUID_PARAM + "&pageSize=500")
                    .body().string();
            Assertions.assertTrue(body.contains("\"pageSize\":10"));
        });
    }

    @Test
    void returns400ForAPageBelowOne() {
        RealtyRestServer server = TestServers.withPlayerHoldings();
        JavalinTest.test(server.javalin(), (jsonServer, client) ->
                Assertions.assertEquals(400,
                        client.get("/v1/players/regions?player=" + UUID_PARAM + "&page=0").code()));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :realty-rest:test --tests "*PlayerRegionsEndpointTest*"`
Expected: FAIL — route not registered.

- [ ] **Step 3: Write the response records**

Create `json/PlayerRegionsResponse.java`, with nested `RegionRef(String worldGuardRegionId, WorldRef world)` and `RentedRef(String worldGuardRegionId, WorldRef world, String endDate, Long secondsRemaining)`. Annotate the class with `@JsonInclude(JsonInclude.Include.NON_NULL)` so the unused category lists are omitted rather than serialised as null — a caller asking for one category should not receive three null keys.

- [ ] **Step 4: Write the handler**

Create `PlayerRegionsHandler.java`. It must:

- Read `player` via `QueryParams.required`, decode it, and parse as a UUID. If it is not a UUID, throw `ApiException.badGateway("NAME_LOOKUP_UNAVAILABLE", "Player name lookup requires the query-service module")` — until the enrichment plan ships, no name can be resolved, and `502` is the spec's status for exactly that condition. A malformed *UUID-shaped* value still yields `400`; the discriminator is whether the value parses as a UUID, and anything that does not is treated as a name.
- Read `category` (default `all`), `page` (default 1, reject `< 1` with `400`), and `pageSize` (default 10, clamp to `settings.maxPageSize()`).
- For `all`, call `backend.listRegions(uuid, pageSize, (page - 1) * pageSize)` and map the three lists.
- For `owned`/`rented`, call the single-category methods and populate `regions`.
- Build every rented entry from `selectRentedRegionsWithEndDate`, computing `secondsRemaining` as `Duration.between(LocalDateTime.now(ZoneOffset.UTC), endDate).toSeconds()`, clamped at zero, and null when `endDate` is null. **Do not** call `getLeaseholdContract` per region.
- Compute `totalPages` as `(totalCount + pageSize - 1) / pageSize`, and return page 1 with empty lists rather than an error when `totalCount` is zero.

Register in `RealtyRestServer.registerRoutes()`, replacing the `// Task 8 registers` comment:

```java
        PlayerRegionsHandler playerRegionsHandler =
                new PlayerRegionsHandler(this.backend, this.database, this.worldLookup, this.settings);
        this.javalin.get("/v1/players/regions", playerRegionsHandler::handle);
```

Add `"/v1/players/regions"` to `ROUTES`.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :realty-rest:test --tests "*PlayerRegionsEndpointTest*"`
Expected: PASS, 7 tests.

- [ ] **Step 6: Commit**

```bash
git add realty-rest/
git commit -m "feat(rest): add GET /v1/players/regions"
```

---

### Task 9: OpenAPI document, docs endpoints and the conformance test

**Depends on Tasks 6, 7 and 8** — the conformance test enumerates every route.

**Files:**
- Create: `realty-rest/src/main/resources/openapi.yaml`
- Create: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/OpenApiRoutes.java`
- Modify: `realty-rest/src/main/java/io/github/md5sha256/realty/rest/RealtyRestServer.java`
- Test: `realty-rest/src/test/java/io/github/md5sha256/realty/rest/OpenApiConformanceTest.java`

**Interfaces:**
- Consumes: `RealtyRestServer.ROUTES`.
- Produces: `OpenApiRoutes.documentedPaths()` returning `Set<String>` parsed from the bundled `openapi.yaml`.

- [ ] **Step 1: Write the failing conformance test**

```java
package io.github.md5sha256.realty.rest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

class OpenApiConformanceTest {

    @Test
    void everyRegisteredRouteIsDocumented() {
        Set<String> documented = OpenApiRoutes.documentedPaths();
        Set<String> undocumented = new TreeSet<>(RealtyRestServer.ROUTES);
        undocumented.removeAll(documented);
        Assertions.assertTrue(undocumented.isEmpty(),
                "routes missing from openapi.yaml: " + undocumented);
    }

    @Test
    void everyDocumentedPathIsRegistered() {
        Set<String> registered = new HashSet<>(RealtyRestServer.ROUTES);
        Set<String> unimplemented = new TreeSet<>(OpenApiRoutes.documentedPaths());
        unimplemented.removeAll(registered);
        Assertions.assertTrue(unimplemented.isEmpty(),
                "documented paths with no route: " + unimplemented);
    }
}
```

The two directions are separate tests deliberately: one failure message tells you a route needs documenting, the other tells you the document describes something that does not exist.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :realty-rest:test --tests "*OpenApiConformanceTest*"`
Expected: FAIL — `OpenApiRoutes` does not exist.

- [ ] **Step 3: Write the OpenAPI document**

Create `realty-rest/src/main/resources/openapi.yaml` describing exactly four paths — `/v1/health`, `/v1/worlds`, `/v1/regions`, `/v1/players/regions` — with full schemas for every response record built in Tasks 6–8, every status code from the spec's table, and descriptions that state the two documented quirks: that `all` pages the three categories against one shared offset, and that `dimensions` and player `name` are null without the query-service module.

Add the `openapi.yaml` path list to the document under `paths:` using the exact same strings as `RealtyRestServer.ROUTES`.

- [ ] **Step 4: Write the parser and serve the document**

`OpenApiRoutes.documentedPaths()` reads `/openapi.yaml` from the classpath and returns the keys under `paths:`. Parse it with a minimal hand-rolled scan — collect lines matching `^  (/\S+):$` — rather than adding a YAML dependency for one job.

Serve it, replacing nothing and adding to `registerRoutes()`:

```java
        this.javalin.get("/v1/openapi.yaml", ctx -> ctx.contentType("application/yaml")
                .result(OpenApiRoutes.rawDocument()));
```

Add a `/v1/openapi.json` route converting the parsed document, and `/v1/docs` returning a small static Swagger UI page loading `/v1/openapi.yaml`. Add all three paths to `ROUTES` **and** to `openapi.yaml`, or the conformance test fails — which is the point.

- [ ] **Step 5: Run the whole suite**

Run: `./gradlew :realty-rest:test`
Expected: PASS, all tests.

- [ ] **Step 6: Commit**

```bash
git add realty-rest/
git commit -m "feat(rest): publish spec-first OpenAPI document with conformance test"
```

---

### Task 10: Docker, compose, Pterodactyl egg and README

**Depends on Tasks 6, 7, 8** (the image must run a complete service).

**Files:**
- Create: `realty-rest/Dockerfile`
- Create: `compose.yml`
- Create: `realty-rest/pterodactyl-egg.json`
- Create: `realty-rest/README.md`

- [ ] **Step 1: Build the shadow jar and run it against the dev database**

```bash
./gradlew :realty-rest:shadowJar
./gradlew startDevDb
REALTY_DB_URL="mariadb://localhost:3306/realty" \
REALTY_DB_USERNAME=realty REALTY_DB_PASSWORD=realty \
java -jar realty-rest/build/libs/realty-rest-all.jar
```

Expected: the service logs its redacted configuration and binds port 8080. In another shell, `curl -s localhost:8080/v1/health` returns `{"status":"ok"}` and `curl -s localhost:8080/v1/worlds` returns `[]` or the dev worlds. Stop it with Ctrl-C.

- [ ] **Step 2: Write the Dockerfile**

Multi-stage: a build stage running `./gradlew :realty-rest:shadowJar`, and a runtime stage on a JRE 25 base copying only the jar. Create and switch to a non-root user, `EXPOSE 8080`, and declare:

```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s \
  CMD wget -qO- http://127.0.0.1:8080/v1/health || exit 1
```

Ensure whatever tool the healthcheck uses actually exists in the chosen base image; install it or switch to a base that has it.

- [ ] **Step 3: Write compose.yml**

At the repository root, bringing up `mariadb:11.7` and the API together. This is a **separate file** from `compose.dev.yml`, which stays exactly as it is — that one exists only for `./gradlew runServer`. Give the API service `depends_on` with `condition: service_healthy` against the database, and pass configuration as `environment` entries.

- [ ] **Step 4: Write the Pterodactyl egg**

`realty-rest/pterodactyl-egg.json` declaring each variable from the Global Constraints table as a panel variable with its default, description and validation rules (`REALTY_REST_PORT` as `integer|between:1,65535`, the two required database variables as `required|string`), and `java -jar realty-rest-all.jar` as the startup command.

- [ ] **Step 5: Write the README**

`realty-rest/README.md` documenting the full environment variable table, all three deployment routes, and one worked `curl` example per endpoint — including one with a space-containing world name showing the `%20` form, since that is the case a reader will otherwise get wrong.

- [ ] **Step 6: Verify the image builds and serves**

```bash
docker build -t realty-rest:dev -f realty-rest/Dockerfile .
docker compose -f compose.yml up -d
curl -s localhost:8080/v1/health
docker compose -f compose.yml down
```

Expected: `{"status":"ok"}`.

- [ ] **Step 7: Commit**

```bash
git add realty-rest/Dockerfile realty-rest/pterodactyl-egg.json realty-rest/README.md compose.yml
git commit -m "build(rest): add Docker, compose and Pterodactyl packaging"
```

---

## Final verification

- [ ] Run the full build: `./gradlew build`
- [ ] Confirm `./gradlew :realty-rest:test` passes every test.
- [ ] Confirm `git grep -n "import static" realty-rest/` returns nothing.
- [ ] Confirm `git grep -n "import .*\.\*;" realty-rest/` returns nothing.
- [ ] Confirm `realty-rest` never calls `initializeSchema`: `git grep -n "initializeSchema" realty-rest/` returns nothing.
- [ ] Confirm the plugin still enables against the dev database: `./gradlew runServer`, then `/realty info` in game on a known region, and check the `RealtyWorld` table has a row per loaded world.
