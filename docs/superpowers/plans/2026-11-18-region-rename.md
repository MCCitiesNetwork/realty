# Region Rename Implementation Plan

**Goal:** Rename a registered region and keep everything attached to it: a landlord's manager for their own subregion, a bypass holder or the console for any region.
**Spec:** `docs/superpowers/specs/2026-11-18-region-rename-design.md`

Backend tests extend `AbstractDatabaseTest` and need a running Docker daemon (Testcontainers MariaDB). A `--tests` run that matches nothing still reports success, so check the test count in the report after each run.

## Task 1: Backend rename

**Files:**
- Modify `realty-backend-api/src/main/java/io/github/md5sha256/realty/api/RealtyBackend.java` (after `deleteVacantLeasehold`)
- Modify `realty-backend/src/main/java/io/github/md5sha256/realty/database/mapper/RealtyRegionMapper.java`, `FreeholdHistoryMapper.java`, `LeaseholdHistoryMapper.java`, `AgentHistoryMapper.java`, `RegionTagMapper.java`
- Modify the matching `Maria…Mapper` in `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/`
- Modify `realty-backend/src/main/java/io/github/md5sha256/realty/database/RealtyBackendImpl.java` (after `deleteVacantLeasehold`)
- Test: create `realty-backend/src/test/java/io/github/md5sha256/realty/database/RegionRenameTest.java`

**Interfaces:**

```java
sealed interface RenameRegionResult {
    /** {@code tenantId} is the tenant of the region's lease, if it has one. */
    record Success(@Nullable UUID tenantId) implements RenameRegionResult {}
    record NoRegion() implements RenameRegionResult {}
    record NameTaken() implements RenameRegionResult {}
    record NotAuthorized() implements RenameRegionResult {}
}
@NotNull RenameRegionResult renameRegion(@NotNull String worldGuardRegionId,
                                         @NotNull String newWorldGuardRegionId,
                                         @NotNull UUID worldId,
                                         @NotNull ActorContext ctx);

// RealtyRegionMapper
int renameGuarded(@NotNull String worldGuardRegionId, @NotNull String newWorldGuardRegionId,
                  @NotNull UUID worldId, @Nullable Integer requiredLandlordPartyId);
// FreeholdHistoryMapper, LeaseholdHistoryMapper, AgentHistoryMapper
int renameRegion(@NotNull String worldGuardRegionId, @NotNull String newWorldGuardRegionId,
                 @NotNull UUID worldId);
// RegionTagMapper (deleteByRegionId already exists)
int moveToRegion(@NotNull String worldGuardRegionId, @NotNull String newWorldGuardRegionId);
```

- [ ] Write the failing tests in `RegionRenameTest`, with the constants and the `vacantLease` and `rentedLease` helpers of `SubregionDeleteTest` (region ids prefixed `region_rename_`):
  - `landlordManagerRenamesALeasehold`: rename as the landlord, expect `Success(null)`; `getLeaseholdContract(newName)` is not null and `getLeaseholdContract(oldName)` is null.
  - `rentedLeaseholdReportsItsTenant`: expect `Success(TENANT)`.
  - `historyMovesToTheNewName`: insert one row through each of `freeholdHistoryMapper`, `leaseholdHistoryMapper` and `agentHistoryMapper` for the old name; after the rename `countHistory` for the new name is 1 and for the old name is 0, for each.
  - `historyOfTheSameNameInAnotherWorldStays`: a history row with the old name and a second world id still counts 1 under the old name in that world.
  - `tagsMoveToTheNewName`: two tags on the old name; after the rename `selectTagIdsByRegionId(newName)` has both and `selectTagIdsByRegionId(oldName)` is empty.
  - `aTagTheNewNameAlreadyHasLeavesOneRow`: tag `shop` on both names before the rename; after it the new name has `shop` once and the old name has none.
  - `signsSchematicAndFeesFollowTheRegion`: register a sign, a schematic and a region fee before the rename; each is read back for the new name.
  - `saleFeeKeepsTheOldName`: a `SaleFee` row written with the old name (through `saleFeeMapper`) still reads the old name.
  - `takenNameInTheSameWorldIsRefused`: a second region holds the new name: `NameTaken`, and both regions keep their names.
  - `sameNameInAnotherWorldIsAllowed`: `Success`.
  - `nonManagerIsRefused`: `AS_STRANGER`: `NotAuthorized`, the lease is still read by the old name.
  - `freeholdNeedsTheBypass`: a freehold as its title holder without bypass: `NotAuthorized`; as `AS_STAFF`: `Success`.
  - `consoleRenamesAnyRegion`: `ActorContext.console()`: `Success`.
  - `unknownRegionIsNoRegion`.
  - `landlordChangedAfterTheReadRenamesNothing`: call `realtyRegionMapper().renameGuarded` with a party id that is not the landlord's; expect 0 rows.
  - `nothingKeepsTheOldName` (the completeness test): create a freehold with a title holder; add a freehold history, a leasehold history and an agent history row, two tags, a sign, a schematic, a region fee, an offer and an auction with a bid, all for that region. Rename as `AS_STAFF`. Read `information_schema.COLUMNS` through `database.openSession().session().getConnection()` for `TABLE_SCHEMA = DATABASE() AND COLUMN_NAME IN ('worldGuardRegionId', 'regionName')`. Assert the result names at least `RealtyRegion`, `FreeholdHistory`, `LeaseholdHistory`, `AgentHistory` and `RegionTag`, so a broken query cannot pass. For each `TABLE_NAME.COLUMN_NAME` not in the allow-list `Set.of("SaleFee.regionName", "PendingRefund.regionName")`, run `SELECT COUNT(*) FROM <table> WHERE <column> = ?` with the old name and assert 0, naming the table in the failure message.
- [ ] Run `./gradlew :realty-backend:test --tests "*RegionRenameTest"` and expect a compile FAIL: `renameRegion` does not exist.
- [ ] Implement:
  - `MariaRealtyRegionMapper.renameGuarded`, an `@Update` text block:

    ```sql
    UPDATE RealtyRegion rr
    SET rr.worldGuardRegionId = #{newWorldGuardRegionId}
    WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
    AND rr.worldId = #{worldId}
    AND (#{requiredLandlordPartyId} IS NULL OR EXISTS (
        SELECT 1
        FROM Contract c
        INNER JOIN LeaseholdContract lc ON lc.leaseholdContractId = c.contractId
        WHERE c.realtyRegionId = rr.realtyRegionId
        AND c.contractType = 'leasehold'
        AND lc.landlordPartyId = #{requiredLandlordPartyId}))
    ```
  - `renameRegion` on each of the three `Maria…HistoryMapper`s: `UPDATE <Table> SET worldGuardRegionId = #{newWorldGuardRegionId} WHERE worldGuardRegionId = #{worldGuardRegionId} AND worldId = #{worldId}`, for `FreeholdHistory`, `LeaseholdHistory` and `AgentHistory`.
  - `MariaRegionTagMapper.moveToRegion`: `UPDATE IGNORE RegionTag SET worldGuardRegionId = #{newWorldGuardRegionId} WHERE worldGuardRegionId = #{worldGuardRegionId}`.
  - `RealtyBackendImpl.renameRegion`, in one `database.openSession()`:
    1. `realtyRegionMapper().selectByWorldGuardRegion(old, worldId)` null → `NoRegion`.
    2. `selectByWorldGuardRegion(new, worldId)` not null → `NameTaken`.
    3. `lease = leaseholdContractMapper().selectByRegion(old, worldId)`. When `!ctx.bypass()`: `lease == null` or `!ctx.mayManage(lease.landlord())` → `NotAuthorized`. `requiredLandlordPartyId = ctx.bypass() ? null : namedPartyId(wrapper, lease.landlord())`. Then `endRead(wrapper)`.
    4. `renameGuarded(...)` inside `try`/`catch (PersistenceException)`. The catch returns `NameTaken` when the cause is a `java.sql.SQLIntegrityConstraintViolationException` and rethrows otherwise. On 0 rows: roll back, read the region again, and return `NoRegion` if it is gone, else `NotAuthorized`.
    5. The three history `renameRegion` calls, `moveToRegion`, then `regionTagMapper().deleteByRegionId(old)`.
    6. Commit and return `Success(lease == null ? null : lease.tenantId())`.
- [ ] Run the same command and expect PASS.
- [ ] Run `./gradlew build`.
- [ ] Commit `feat(backend): rename a region and move its name-keyed rows`.

## Task 2: Paper API module additions

**Files:**
- Modify `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/SignCache.java`
- Modify `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/RegionProfileService.java`
- Create `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/event/RegionRenameEvent.java` and `RegionRenamedEvent.java`
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/api/SignCacheTest.java` and `SignProfileNamesTest.java`

**Interfaces:**

```java
// SignCache
/** Moves the region's signs to its new name. */
public void renameRegion(@NotNull String worldGuardRegionId, @NotNull String newWorldGuardRegionId,
                         @NotNull UUID worldId);

// RegionProfileService
/** Whether a grouped flag or sign profile lists {@code regionName}. */
public boolean hasGroupedProfile(@NotNull String regionName);

// events, both extend RealtyRegionEvent
public RegionRenameEvent(@NotNull WorldGuardRegion region, @Nullable UUID actorId, @NotNull String newName); // Cancellable
public @NotNull String getNewName();
public @Nullable UUID getActorId();   // null for the console

public RegionRenamedEvent(@NotNull WorldGuardRegion region, @Nullable UUID actorId, @NotNull String oldName);
public @NotNull String getOldName();
public @Nullable UUID getActorId();
```

- [ ] Write the failing tests:
  - `SignCacheTest.renamedRegionKeepsItsSigns`: `put` two signs for `old`; `renameRegion("old", "new", WORLD)`; `getSignsByRegion("new", WORLD)` has both positions, `getSignsByRegion("old", WORLD)` is empty, and `get(...)` for each position returns an entry whose `worldGuardRegionId()` is `new` with the same `realtyRegionId()`.
  - `SignCacheTest.renameLeavesOtherRegionsAlone`: a sign of a second region is unchanged.
  - `SignProfileNamesTest.groupedProfileNamesAreReported`: after `addGroupedFlagProfile(Set.of("plot-a"), …)`, `hasGroupedProfile("plot-a")` is true and `hasGroupedProfile("plot-b")` is false; the same after `addGroupedSignProfile`.
- [ ] Run `./gradlew :realty-paper:test --tests "*SignCacheTest" --tests "*SignProfileNamesTest"` and expect a compile FAIL: the methods do not exist.
- [ ] Implement:
  - `renameRegion`: remove the old `RegionKey` from `regionToSigns`; for each position, replace the `cache` entry with `new SignCacheEntry(entry.realtyRegionId(), newId, entry.regionWorldId())` and add the position under the new key.
  - `hasGroupedProfile`: `groupedFlagProfiles.containsKey(regionName) || groupedSignProfiles.containsKey(regionName)`.
  - The two events, written as `RegionDeleteEvent` and `RegionDeletedEvent` are, with the extra field.
- [ ] Run the same command and expect PASS.
- [ ] Run `./gradlew build`.
- [ ] Commit `feat(api): rename events, sign cache rename and grouped profile lookup`.

## Task 3: The WorldGuard swap and capture state

**Files:**
- Create `realty-paper/src/main/java/io/github/md5sha256/realty/api/WorldGuardRegionRename.java`
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/schematic/CaptureRegistry.java` and `CaptureCooldown.java`
- Test: create `realty-paper/src/test/java/io/github/md5sha256/realty/api/WorldGuardRegionRenameTest.java`; extend `realty-paper/src/test/java/io/github/md5sha256/realty/schematic/CaptureCooldownTest.java` and create `CaptureRegistryTest.java` beside it

**Interfaces:**

```java
public final class WorldGuardRegionRename {
    public WorldGuardRegionRename(@NotNull RegionContainer regionContainer, @NotNull Logger logger);

    public sealed interface Outcome {
        /** {@code saveError} is set when WorldGuard could not save; the swap still stands. */
        record Renamed(@NotNull WorldGuardRegion region, @Nullable String saveError) implements Outcome {}
        record NameTaken() implements Outcome {}
    }

    /** Whether Realty can rebuild this region under another id: a cuboid or a polygon. */
    public static boolean isSupported(@NotNull ProtectedRegion region);
    /** Whether no region with {@code newId} exists in the region's world. Main thread. */
    public boolean isFree(@NotNull WorldGuardRegion region, @NotNull String newId);
    /** Replaces {@code region} with a copy under {@code newId} and saves. Main thread. */
    public @NotNull Outcome rename(@NotNull WorldGuardRegion region, @NotNull String newId);
}

// CaptureRegistry
public boolean isInFlight(@NotNull String worldGuardRegionId, @NotNull UUID worldId);
// CaptureCooldown
public void rename(@NotNull String worldGuardRegionId, @NotNull String newWorldGuardRegionId, @NotNull UUID worldId);
```

- [ ] Write the failing tests. `WorldGuardRegionRenameTest` builds a real manager, `new RegionManager(new MemoryRegionDatabase(), new ChunkHashTable.Factory(), new SimpleFlagRegistry())`, and returns it from a mocked `RegionContainer.get(any())`, with `BukkitAdapter` mocked statically as `RealtyPaperApiImplTest` does:
  - `cuboidKeepsItsShapeAndSettings`: a cuboid with an owner player, an owner group, a member player, a member group, `Flags.GREETING` set, a custom `StringFlag` registered in the registry and set, priority 7 and a parent. After `rename`, expect `Renamed` with a null `saveError`; the new region has the same minimum and maximum points, owners, members, flag map, priority and parent; `hasRegion(oldId)` is false.
  - `polygonKeepsItsPointsAndHeight`: a `ProtectedPolygonalRegion` with three points; the new region is polygonal with the same points and Y range.
  - `childrenPointAtTheNewRegion`: a child whose parent is the old region has the new region as its parent afterwards, and still exists.
  - `takenIdChangesNothing`: a region with the new id exists: `NameTaken`, and the old region is still in the manager.
  - `isFreeReadsTheManager` and `globalRegionIsNotSupported` (a `GlobalProtectedRegion`).
  - `CaptureRegistry`: `isInFlight` is true between `begin` and `finish` and false otherwise.
  - `CaptureCooldown`: after a recorded capture of `old` and `rename("old", "new", WORLD)`, `new` is on cooldown and `old` is not.
- [ ] Run `./gradlew :realty-paper:test --tests "*WorldGuardRegionRenameTest" --tests "*Capture*"` and expect a compile FAIL: `WorldGuardRegionRename` does not exist.
- [ ] Implement `rename`:
  1. `RegionManager manager = regionContainer.get(BukkitAdapter.adapt(region.world()))`; a null manager or `manager.hasRegion(newId)` → `NameTaken`.
  2. Build the copy: for a `ProtectedPolygonalRegion`, `new ProtectedPolygonalRegion(newId, old.isTransient(), old.getPoints(), old.getMinimumPoint().y(), old.getMaximumPoint().y())`; otherwise `new ProtectedCuboidRegion(newId, old.isTransient(), old.getMinimumPoint(), old.getMaximumPoint())`.
  3. `copy.copyFrom(old)` (it throws `CircularInheritanceException` only for a parent cycle, which a copy of a valid region cannot have; wrap it in an `IllegalStateException`).
  4. `manager.addRegion(copy)`; for each region in `manager.getRegions().values()` whose `getParent()` is `old`, `setParent(copy)`; `manager.removeRegion(old.getId())`.
  5. `manager.save()`; on `StorageException`, log a WARNING and return `Renamed(newRegion, message)`.
- [ ] Run the same command and expect PASS.
- [ ] Run `./gradlew build`.
- [ ] Commit `feat(paper): swap a WorldGuard region for a copy under a new id`.

## Task 4: Paper API renameRegion

**Files:**
- Modify `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/RealtyPaperApi.java`
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/api/RealtyPaperApiImpl.java` (after `cancelSubregionDelete`; constructor)
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java` (build `WorldGuardRegionRename` beside `WorldGuardRegionRemoval`; pass it, `captureRegistry` and one shared `CaptureCooldown` to `RealtyPaperApiImpl`; the schematic command takes the same `CaptureCooldown` instance)
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/api/RealtyPaperApiImplTest.java` (both places that construct `RealtyPaperApiImpl`)

**Interfaces:**

```java
sealed interface RenameRegionResult {
    record Renamed(@NotNull WorldGuardRegion region, @NotNull String oldName,
                   @Nullable UUID tenantId, boolean leftGroupedProfile) implements RenameRegionResult {}
    /** Renamed, but WorldGuard could not save; it retries on its next save. */
    record WorldGuardSaveError(@NotNull Renamed renamed, @NotNull String error) implements RenameRegionResult {}
    record InvalidName() implements RenameRegionResult {}
    record SameName() implements RenameRegionResult {}
    record NameTaken() implements RenameRegionResult {}
    record NotRegistered() implements RenameRegionResult {}
    record NotAuthorized() implements RenameRegionResult {}
    record UnsupportedRegion() implements RenameRegionResult {}
    record CaptureRunning() implements RenameRegionResult {}
    record Error(@NotNull String message) implements RenameRegionResult {}
}
@NotNull CompletableFuture<RenameRegionResult> renameRegion(@NotNull WorldGuardRegion region,
                                                            @NotNull String newName,
                                                            @NotNull ActorContext ctx);
```

- [ ] Write the failing tests in a nested `renameRegion` class. Give it a real `RegionManager` as in Task 3, holding `protectedRegion`:
  - `invalidNameIsRefused` (`"bad name!"`), `sameNameIsRefused` (the region's id in upper case), `takenNameIsRefused` (the manager holds the new id), `captureInFlightIsRefused` (`captureRegistry.begin` first), `subregionWithoutParentIsRefusedWithoutBypass`, `parentThatIsNotAFreeholdIsRefused` (stub `getFreeholdContract` to null). Each expects its result case and `verify(realtyApi, never()).renameRegion(any(), any(), any(), any())`.
  - `landlordRenamesASubregion`: the region has a parent, the freehold is stubbed, the backend returns `Success(TENANT)`: expect `Renamed` whose region id is the lower-cased new name, `oldName` is `REGION_ID`, `tenantId` is the tenant; the manager has the new id and not the old one; `signCache.getSignsByRegion(newName, WORLD_ID)` holds a sign put under the old name.
  - `bypassRenamesARegionWithoutAParent`: `ActorContext.player(id, true)` and no parent: `Renamed`, and `getFreeholdContract` is never called.
  - `backendRefusalsAreMapped`: `NoRegion` → `NotRegistered`, `NameTaken` → `NameTaken`, `NotAuthorized` → `NotAuthorized`; the manager still holds the old id.
  - `collisionAtTheSwapReversesTheDatabase`: the backend stub adds a region with the new id to the manager when it is called, then returns `Success`: expect `NameTaken` and `verify(realtyApi).renameRegion(eq(newName), eq(REGION_ID), eq(WORLD_ID), eq(ActorContext.console()))`.
  - `groupedProfileOnTheOldNameIsReported`: `regionProfileService.addGroupedFlagProfile(Set.of(REGION_ID), …)` first: `Renamed.leftGroupedProfile()` is true.
  - `backendFailureIsAnError`: the backend throws: `Error`.
- [ ] Run `./gradlew :realty-paper:test --tests "*RealtyPaperApiImplTest"` and expect a compile FAIL: `renameRegion` does not exist.
- [ ] Implement, with a private `VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9-]+$")`:
  - Main thread: pattern → `InvalidName`; `newId = newName.toLowerCase(Locale.ROOT)`; equal to the region id → `SameName`; `!WorldGuardRegionRename.isSupported` → `UnsupportedRegion`; `!regionRename.isFree(region, newId)` → `NameTaken`; `captureRegistry.isInFlight` → `CaptureRunning`; `!ctx.bypass()` and `parentId(region).isEmpty()` → `NotAuthorized`. Read `leftGroupedProfile = regionProfileService.hasGroupedProfile(oldId)`.
  - Wrap the rest in `serializeByRegion` for the smaller of the two ids, and inside it `serializeByRegion` for the larger (`String.compareTo`).
  - `dbExec`: when not bypassing, `!isFreehold(parentId, worldId)` → `NotAuthorized`; else `realtyApi.renameRegion(oldId, newId, worldId, ctx)`. On `Success` also read `realtyApi.getRegionWithState(newId, worldId)` for the redraw.
  - `mainThreadExec`, on `Success`: `regionRename.rename(region, newId)`.
    - `NameTaken`: on `dbExec`, `realtyApi.renameRegion(newId, oldId, worldId, ActorContext.console())`; a `Success` gives `NameTaken`; anything else logs SEVERE with both names and gives `Error`.
    - `Renamed`: `signCache.renameRegion`, `captureCooldown.rename`, then, when the state was read, `regionProfileService.applyFlags(newRegion, state, placeholders)` and `signTextApplicator.updateLoadedSigns(world, newId, state, placeholders)`. Return `Renamed`, wrapped in `WorldGuardSaveError` when `saveError` is set.
  - `exceptionally`: log through `logger` at SEVERE and return `Error`.
- [ ] Run the same command and expect PASS.
- [ ] Run `./gradlew build`.
- [ ] Commit `feat(api): rename a region`.

## Task 5: Command, permissions and messages

**Files:**
- Create `realty-paper/src/main/java/io/github/md5sha256/realty/command/RenameCommand.java`
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java` (register it after `DeleteCommand`)
- Modify `realty-paper/src/main/resources/paper-plugin.yml`, `messages.yml`, and `localisation/MessageKeys.java`
- Modify `realty-paper-adapters/player-notifications-adapter/src/main/java/io/github/md5sha256/realty/adapter/playernotifs/RealtyCategory.java` and `src/main/resources/titles.yml` (claim `notification.region-renamed` in the leases category, as `notification.subregion-deleted` is)
- Test: create `realty-paper/src/test/java/io/github/md5sha256/realty/command/RenameMessagesTest.java`

**Interfaces:**

```java
public record RenameCommand(@NotNull RealtyPaperApi api, @NotNull ActorContexts actors,
                            @NotNull ExecutorState executorState, @NotNull MessageContainer messages,
                            @NotNull RealtyEventDispatch events) implements CustomCommandBean
```

`MessageKeys`: `RENAME_SUCCESS`, `RENAME_LEFT_GROUPED_PROFILE`, `RENAME_INVALID_NAME`, `RENAME_SAME_NAME`, `RENAME_NAME_TAKEN`, `RENAME_NOT_REGISTERED`, `RENAME_NOT_AUTHORIZED`, `RENAME_UNSUPPORTED_REGION`, `RENAME_CAPTURE_RUNNING`, `RENAME_WORLDGUARD_SAVE_ERROR`, `RENAME_ERROR` (values `rename.success` and so on), and `NOTIFICATION_REGION_RENAMED = "notification.region-renamed"`.

- [ ] Write the failing test `RenameMessagesTest`, a copy of `SubregionDeleteMessagesTest` with the twelve constants above in its `@ValueSource`.
- [ ] Run `./gradlew :realty-paper:test --tests "*RenameMessagesTest"` and expect a compile FAIL: the constants do not exist.
- [ ] Implement:
  - The command: `builder.literal("rename").permission("realty.command.rename").required("region", WorldGuardRegionParser.worldGuardRegion()).required("name", StringParser.stringParser())`.
  - The handler: fire `RegionRenameEvent(region, actorIdOrNull, name)`; a cancelled event replies `COMMON_ACTION_CANCELLED`. A player's actor is `actors.forRegion(player, player.hasPermission("realty.command.rename.others"), region)` built on `dbExec`; any other sender uses `ActorContext.console()`. Then `api.renameRegion(region, name, actor)` composed on `mainThreadExec`, and the reply on `mainThreadExec`.
  - The reply is an exhaustive `switch`, one key per case. On `Renamed` (and on the `Renamed` inside `WorldGuardSaveError`): send `RENAME_SUCCESS` with `<old>` and `<region>`; send `RENAME_LEFT_GROUPED_PROFILE` when the flag is set; fire `RegionRenamedEvent(renamed.region(), actorIdOrNull, renamed.oldName())`; when `tenantId` is set and is not the actor, fire `RealtyNotificationEvent(List.of(tenantId), NOTIFICATION_REGION_RENAMED, message, renamed.region())` as `SubregionCommandGroup.tellTenantOfRemoval` does. `WorldGuardSaveError` then also sends `RENAME_WORLDGUARD_SAVE_ERROR`.
  - End the chain with `exceptionally`, replying `RENAME_ERROR`.
  - `messages.yml`: a `rename:` block with the eleven keys and `notification.region-renamed`, each with a comment naming its placeholders, in the file's colour vocabulary.
  - `paper-plugin.yml`: `realty.command.rename` ("Allows renaming a subregion whose landlord you manage") and `realty.command.rename.others` ("Allows renaming any other registered region"), both `default: op`.
- [ ] Run the same command and expect PASS. Also run `./gradlew :realty-paper-adapters:player-notifications-adapter:test`, which checks that every notification key is claimed.
- [ ] Run `./gradlew build`.
- [ ] Live-server checks (by hand on a test server; these cannot be covered by a unit test):
  1. Wand-create a subregion with a sign. As its landlord run `/realty rename <it> <new>`: the reply names both, the sign redraws with the new name, `/rg info <new>` shows the same flags, owners and parent, and `/rg info <old>` finds nothing.
  2. Rent it from a second account, add that account's tenant membership, and rename again: the tenant gets the notification, can still build, and `/realty info <new>` shows the tenant.
  3. As an admin rename a freehold that has subregions: `/rg info` on each subregion shows the new parent, and `/realty history <new>` shows the earlier sales.
  4. Try a taken name, a name with a space, and the same name: each gets its own refusal.
  5. A third account without management gets `not-authorized` on the subregion and on a standalone leasehold.
  6. Run the rename from the console.
  7. Restart the server: the region, its flags and its sign are still there under the new name.
  8. Open the web explorer: the region is at the new address and the old address returns 404.
- [ ] Commit `feat(command): /realty rename`.

There is no `CLAUDE.md` in this repository to update. The API additions are a minor version; this stack does not change the version, which is set when the release is cut.

## Notes from the build

Changes made while executing this plan, recorded here so the plan matches the code:

- Task 1 adds `renamingOntoATakenNameFailsOnTheUniqueKey`. It pins the exception the rename reads as "name taken" when another transaction takes the name after the read.
- Task 2: `SignCacheTest` is in `realty-paper-api/src/test`, not `realty-paper`. The grouped profile lookup has its own `GroupedProfileLookupTest` there, because `SignProfileNamesTest` is about names in sign text. The two events have a `RegionRenameEventsTest`.
- Task 3 adds `aFailedSaveStillRenamesAndReportsTheError`, with a region store that refuses to save.
- Task 4 does not call `regionProfileService.applyFlags` after the swap. That method clears every flag before it sets the profile's, so it would remove the hand-set flags the copy had just carried over. Only the signs are redrawn. The spec's Step 4 and Known limitations record this.
- Task 4 redraws signs in `region.world()`; it does not look the world up through the server.
- Task 5 puts `notification.region-renamed` in the adapter's leases category with the title "Rented region renamed".
- `./gradlew build` on the development machine fails two `StaticSiteTest` cases in `realty-rest` with and without this work, and the web explorer's `npmTest` fails in some full builds and passes when run alone. Neither is touched by the rename.
- After the plan, a simplify pass put the region name rule in `WorldGuardRegion.isValidName` and removed duplication.
- After review, two further changes were made; the spec's "Changes after review" section describes them. One keys region tags by world (migration V24). The other moves the subregion check into the rename statement, has the API fire the rename events after its checks, and replaces nullable return types with `Optional` and a separate save-failed outcome.
