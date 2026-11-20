# Region Redefine Implementation Plan

**Goal:** Change a registered region's shape and keep everything attached to it: a landlord's manager for their own vacant subregion with the wand, an admin for any region with the wand or a WorldEdit selection.
**Spec:** `docs/superpowers/specs/2026-11-19-region-redefine-design.md`

Backend tests extend `AbstractDatabaseTest` and need a running Docker daemon (Testcontainers MariaDB). A `--tests` run that matches nothing still reports success, so check the test count in the report after each run.

## Task 1: Backend schematic delete

**Files:**
- Modify `realty-backend-api/src/main/java/io/github/md5sha256/realty/api/RealtyBackend.java` (after `getSchematic`)
- Modify `realty-backend/src/main/java/io/github/md5sha256/realty/database/mapper/RealtySchematicMapper.java`
- Modify `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaRealtySchematicMapper.java`
- Modify `realty-backend/src/main/java/io/github/md5sha256/realty/database/RealtyBackendImpl.java` (after `getSchematic`)
- Test: `realty-backend/src/test/java/io/github/md5sha256/realty/database/RealtySchematicBackendTest.java`

**Interfaces:**

```java
// RealtyBackend
/**
 * Deletes the region's captured schematic.
 *
 * @return whether there was one
 */
boolean deleteSchematic(@NotNull String worldGuardRegionId, @NotNull UUID worldId);

// RealtySchematicMapper
/** @return rows deleted */
int deleteByWorldGuardRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId);
```

- [ ] Write the failing tests in `RealtySchematicBackendTest`, registering regions as the existing tests do:
  - `deleteRemovesTheCapture`: register `plot_b`, store `{1}`, `deleteSchematic` returns true, `getSchematic` returns null.
  - `deleteReportsFalseWhenNothingWasCaptured`: register `plot_b`, `deleteSchematic` returns false.
  - `deleteReportsFalseForAnUnregisteredRegion`: `deleteSchematic("nope", WORLD_ID)` returns false.
  - `deleteLeavesTheSameNameInAnotherWorld`: register `plot_b` in `WORLD_ID` and in a second world id, store a schematic in each, delete in `WORLD_ID`; the other world's bytes are still read.
- [ ] Run `./gradlew :realty-backend:test --tests "*RealtySchematicBackendTest"` and expect a compile FAIL: `deleteSchematic` does not exist.
- [ ] Implement:
  - `MariaRealtySchematicMapper.deleteByWorldGuardRegion`, an `@Delete` text block:

    ```sql
    DELETE s FROM RealtySchematic s
    JOIN RealtyRegion r ON r.realtyRegionId = s.realtyRegionId
    WHERE r.worldGuardRegionId = #{worldGuardRegionId}
      AND r.worldId = #{worldId}
    ```
  - `RealtyBackendImpl.deleteSchematic`: open a session as `storeSchematic` does, call the mapper, commit, return `rows > 0`.
- [ ] Run the same command and expect PASS (8 tests).
- [ ] Run `./gradlew build`.
- [ ] Commit `feat(backend): delete a region's captured schematic`.

## Task 2: Redefine events

**Files:**
- Create `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/event/RegionRedefineEvent.java`
- Create `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/event/RegionRedefinedEvent.java`
- Test: create `realty-paper-api/src/test/java/io/github/md5sha256/realty/api/event/RegionRedefineEventsTest.java`

**Interfaces:**

```java
public class RegionRedefineEvent extends RealtyRegionEvent implements Cancellable {
    public RegionRedefineEvent(@NotNull WorldGuardRegion region, @Nullable UUID actorId,
                               @NotNull ProtectedRegion newRegion);
    public @NotNull Optional<UUID> getActorId();
    /** The region that is to replace it: the same id, the new shape, not yet in WorldGuard. */
    public @NotNull ProtectedRegion getNewRegion();
    // isCancelled, setCancelled, getHandlers, getHandlerList as RegionRenameEvent
}

public class RegionRedefinedEvent extends RealtyRegionEvent {
    public RegionRedefinedEvent(@NotNull WorldGuardRegion region, @Nullable UUID actorId,
                                @NotNull ProtectedRegion oldRegion);
    public @NotNull Optional<UUID> getActorId();
    /** The region it replaced, with the old shape. It is no longer in WorldGuard. */
    public @NotNull ProtectedRegion getOldRegion();
}
```

Each class's Javadoc says what `RegionRenameEvent`'s does, in this design's terms: the redefine event fires after the database checks and the geometry checks, so a redefine that passes it goes ahead; `RegionRedefinedEvent` is what says it happened.

- [ ] Write the failing tests, following `RegionRenameEventsTest` with `REGION = new WorldGuardRegion(null, null)` and a `ProtectedCuboidRegion` as the new and old region:
  - `theRedefineEventCarriesTheNewRegionAndCanBeCancelled`
  - `theRedefinedEventCarriesTheOldRegion`
  - `aConsoleRedefineHasNoActor`
- [ ] Run `./gradlew :realty-paper-api:test --tests "*RegionRedefineEventsTest"` and expect a compile FAIL: the classes do not exist.
- [ ] Implement both classes as `RegionRenameEvent` and `RegionRenamedEvent` are written, with their own `HandlerList`.
- [ ] Run the same command and expect PASS (3 tests).
- [ ] Run `./gradlew build`.
- [ ] Commit `feat(api): add the region redefine events`.

## Task 3: Geometry and the WorldGuard swap

**Files:**
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/command/util/SubregionSelectionValidator.java`
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/command/SubregionDialog.java` (the one `overlappingSibling` call)
- Create `realty-paper/src/main/java/io/github/md5sha256/realty/api/WorldGuardRegionRedefine.java`
- Test: create `realty-paper/src/test/java/io/github/md5sha256/realty/command/util/SubregionSelectionValidatorTest.java`
- Test: create `realty-paper/src/test/java/io/github/md5sha256/realty/api/WorldGuardRegionRedefineTest.java`

**Interfaces:**

```java
// SubregionSelectionValidator
/** As before, but {@code excludeId} (the region being redefined) is never its own sibling. */
public static @Nullable ProtectedRegion overlappingSibling(@NotNull Region selection,
        @NotNull ProtectedRegion parent, @NotNull RegionManager regionManager,
        @Nullable String excludeId);
/** The region as a WorldEdit selection: a cuboid or a polygon; empty for any other shape. */
public static @NotNull Optional<Region> asSelection(@NotNull ProtectedRegion region);
/**
 * The first WorldGuard child of {@code old} that does not lie fully inside {@code replacement}.
 * A child of a shape {@link #asSelection} cannot read counts as outside.
 */
public static @NotNull Optional<ProtectedRegion> firstChildOutside(@NotNull ProtectedRegion old,
        @NotNull ProtectedRegion replacement, @NotNull RegionManager regionManager);

// WorldGuardRegionRedefine
public WorldGuardRegionRedefine(@NotNull RegionContainer regionContainer, @NotNull Logger logger);

/** A cuboid or polygon region with {@code id} and the selection's shape; empty for any other selection. */
public static @NotNull Optional<ProtectedRegion> fromSelection(@NotNull String id, @NotNull Region selection);
/** Same class, same Y range, and the same corners (cuboid) or the same points in order (polygon). */
public static boolean sameShape(@NotNull ProtectedRegion a, @NotNull ProtectedRegion b);

public sealed interface Problem {
    record TooSmall(long volume, int minVolume) implements Problem {}
    record OutsideParent(@NotNull String parentId) implements Problem {}
    record OverlapsSibling(@NotNull String siblingId) implements Problem {}
    record ChildOutside(@NotNull String childId) implements Problem {}
    record NoRegionManager() implements Problem {}
}
/**
 * Rules 6 and 7 of the spec. {@code subregion} turns on the volume, parent and sibling checks.
 * Main thread.
 */
public @NotNull Optional<Problem> check(@NotNull WorldGuardRegion region, @NotNull Region selection,
        @NotNull ProtectedRegion replacement, boolean subregion, int minVolume);

public sealed interface Outcome {
    record Redefined(@NotNull WorldGuardRegion region) implements Outcome {}
    record RedefinedSaveFailed(@NotNull WorldGuardRegion region, @NotNull String error) implements Outcome {}
}
/** Copies the old region's settings onto {@code replacement}, swaps it in, re-points the children, saves. Main thread. */
public @NotNull Outcome redefine(@NotNull WorldGuardRegion region, @NotNull ProtectedRegion replacement);
```

- [ ] Write the failing tests.

  `SubregionSelectionValidatorTest`, with `TestRegionManagers.inMemory()` (make the class and `inMemory` public, or copy the three-line factory into a test helper in `command/util`):
  - `aRegionIsNotItsOwnSibling`: parent with two children `a` and `b`; a selection over `a` with `excludeId = "a"` returns null; with `null` returns `a`.
  - `aSiblingIsStillFoundWhenOneIsExcluded`: a selection over `b` with `excludeId = "a"` returns `b`.
  - `asSelectionReadsCuboidsAndPolygons`: a cuboid gives a `CuboidRegion` with the same corners; a polygon a `Polygonal2DRegion` with the same points and Y range; a `GlobalProtectedRegion` gives empty.
  - `firstChildOutsideFindsAChildLeftOutside`: a freehold 0..100 with children inside 10..20 and 80..90; a replacement 0..50 gives the second child.
  - `firstChildOutsideIsEmptyWhenAllFit`: a replacement 0..100 gives empty, for one cuboid and one polygon child.

  `WorldGuardRegionRedefineTest`, set up as `WorldGuardRegionRenameTest` (mocked `BukkitAdapter`, `regionContainer.get` returning an in-memory manager):
  - `aCuboidBecomesAPolygonAndKeepsItsSettings`: owners and members (players and groups), a custom flag, priority 7 and a parent are kept; the manager's region under the same id is the replacement and a `ProtectedPolygonalRegion`.
  - `aPolygonBecomesACuboid`.
  - `childrenPointAtTheReplacement`: `assertSame(replacement, child.getParent())`.
  - `theSpatialIndexSeesTheNewShape`: after growing 0..20 to 0..40, `getApplicableRegions(BlockVector3.at(30, 15, 30))` contains the region; after shrinking, a point only in the old shape does not.
  - `aFailedSaveStillRedefinesAndReportsTheError`: as the rename's save test.
  - `sameShapeComparesCornersAndPoints`: equal cuboids true; a cuboid one block taller false; a polygon with the same points in another order false; a cuboid and a polygon with the same footprint false.
  - `fromSelectionBuildsOnlyCuboidsAndPolygons`: a `CuboidRegion` and a `Polygonal2DRegion` each give the matching region with the given id; an `EllipsoidRegion` gives empty.
  - `checkRefusesASubregionOutsideItsParent`, `checkRefusesASubregionOverASibling`, `checkRefusesASubregionBelowTheMinimum`, `checkRefusesAFreeholdLeavingAChildOutside`, `checkPassesAValidShape`, `checkIgnoresSubregionRulesForATopLevelRegion` (a top-level region smaller than the minimum passes with `subregion = false`).
- [ ] Run `./gradlew :realty-paper:test --tests "*SubregionSelectionValidatorTest" --tests "*WorldGuardRegionRedefineTest"` and expect a compile FAIL.
- [ ] Implement:
  - `overlappingSibling`: skip a sibling whose id equals `excludeId`. Pass `null` from `SubregionDialog`.
  - `asSelection`: `ProtectedCuboidRegion` → `new CuboidRegion(min, max)`; `ProtectedPolygonalRegion` → `new Polygonal2DRegion(null, points, minY, maxY)`.
  - `firstChildOutside`: for each region in `regionManager.getRegions().values()` whose `getParent() == old` (identity), return it when `asSelection` is empty or `!regionIsFullyContainedByParent(selection, replacement)`.
  - `fromSelection`: as `CreateCommand.createProtectedRegion`, without the bounding-box fallback.
  - `check`: no manager → `NoRegionManager`. When `subregion`: `blockVolume(selection) < minVolume` → `TooSmall`; `!regionIsFullyContainedByParent(selection, parent)` → `OutsideParent`; `overlappingSibling(selection, parent, manager, region id)` → `OverlapsSibling`. Then `firstChildOutside` → `ChildOutside`.
  - `redefine`: `replacement.copyFrom(old)`; `addRegion(replacement)`; for each region whose `getParent() == old` (identity), `setParent(replacement)`; `save()`, catching `StorageException` into `RedefinedSaveFailed` with a WARNING log. A `CircularInheritanceException` becomes an `IllegalStateException`, as in the rename.
- [ ] Run the same command and expect PASS (5 + 13 tests).
- [ ] Run `./gradlew build`.
- [ ] Commit `feat(paper): check and swap a region's shape in WorldGuard`.

## Task 4: Paper API redefineRegion

**Files:**
- Modify `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/RealtyPaperApi.java` (a `// --- Redefine ---` section after `// --- Rename ---`)
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/api/RealtyPaperApiImpl.java` (constructor; a `// --- Redefine ---` section after the rename)
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java` (the `new RealtyPaperApiImpl` call)
- Modify the five `new RealtyPaperApiImpl(` calls in `realty-paper/src/test/java/io/github/md5sha256/realty/api/RealtyPaperApiImplTest.java`
- Test: a new `@Nested class RedefineRegion` in `RealtyPaperApiImplTest`

**Interfaces:** `RedefineRegionResult` and `redefineRegion` exactly as in the spec's "Paper API" section. The Javadoc states rules 1 to 8, that the selection must be in the region's world (a selection whose `getWorld()` is set and names another world is `WrongWorld`), the event order, and "Call it on the main thread".

Constructor: after `WorldGuardRegionRename worldGuardRegionRename`, add `@NotNull WorldGuardRegionRedefine worldGuardRegionRedefine`; after `LongSupplier terminationNoticeSeconds`, add `@NotNull IntSupplier subregionMinVolume`. `Realty` passes `new WorldGuardRegionRedefine(regionContainer, getLogger())` and `() -> this.settings.get().subregionMinVolume()`.

- [ ] Write the failing tests in `RedefineRegion`, set up as `RenameRegion` (a parent `parent_plot` 0..200 holding the 0..100 `protectedRegion`, a real in-memory manager, events not cancelled). `AS_LANDLORD = ActorContext.player(LANDLORD_ID, false)`, `AS_STAFF = ActorContext.player(BUYER_ID, true)`, a min-volume supplier of 20, and a selection helper `cuboid(x1, y1, z1, x2, y2, z2)`. A vacant lease is `LeaseholdContractEntity` with `LANDLORD` and `tenantId = null`; `getFreeholdContract("parent_plot", WORLD_ID)` returns a freehold so the region is a subregion.
  - `unsupportedRegion`, `unsupportedSelection` (an `EllipsoidRegion`), `wrongWorld` (a `CuboidRegion` whose world mock is named otherwise), `sameShape`, `captureRunning`: each returns its result, calls neither `getLeaseholdContract` nor `deleteSchematic`, and leaves the manager's region as `protectedRegion`.
  - `notRegistered`: both contract reads null.
  - `landlordRedefinesAVacantSubregion`: a selection 0..50; `Redefined` with `oldVolume` 101³, `newVolume` 51³, `notify` empty, `captureRemoved` as `deleteSchematic` returned; the manager's region has the new corners; `verify(events).fireSync(any(RegionRedefineEvent.class))` before the swap and `RegionRedefinedEvent` after, using `inOrder`; `deleteSchematic` is called after the swap.
  - `landlordIsRefusedARentedSubregion`: `Occupied`, no event, no swap.
  - `strangerIsRefused`: `ActorContext.player(TENANT_ID, false)`: `NotAuthorized`.
  - `landlordIsRefusedAFreehold`: no parent, a freehold contract: `NotAuthorized`.
  - `staffRedefinesARentedSubregionAndTheTenantIsNamed`: `notify` is `Optional.of(TENANT_ID)`.
  - `staffRedefinesAFreeholdAndTheTitleHolderIsNamed`: a top-level region with a freehold with `TITLE_HOLDER_ID`.
  - `subregionOutsideItsParent`: a selection 150..250 → `OutsideParent("parent_plot")`.
  - `subregionOverASibling`: a sibling at 120..130 under the parent; a selection 0..125 → `OverlapsSibling`.
  - `subregionTooSmall`: a 2×2×2 selection → `TooSmall(8, 20)`.
  - `freeholdLeavingAChildOutside`: → `ChildOutside`.
  - `cancelledEventChangesNothing`: `events.fireSync(any(RegionRedefineEvent.class))` returns false: `Cancelled`, no swap, no `deleteSchematic`.
  - `aFailedCaptureDeleteStillRedefines`: `deleteSchematic` throws: `Redefined` with `captureRemoved` false.
- [ ] Run `./gradlew :realty-paper:test --tests "*RealtyPaperApiImplTest*RedefineRegion*"` and expect a compile FAIL: `redefineRegion` does not exist.
- [ ] Implement `RealtyPaperApiImpl.redefineRegion`:
  1. On the calling thread, in order: `!WorldGuardRegionRename.isSupported(region.region())` → `UnsupportedRegion`; `fromSelection(regionId, selection)` empty → `UnsupportedSelection`; `selection.getWorld() != null` and its name differs from `region.world().getName()` → `WrongWorld`; `sameShape` → `SameShape`; `captureRegistry.isInFlight` → `CaptureRunning`. Read `parentId(region)`.
  2. `serializeByRegion(regionId, worldId, ...)`: on `dbExec`, a private `checkRedefine(regionId, worldId, parentId, ctx)` returning a private `record RedefineCheck(@NotNull Optional<RedefineRegionResult> refusal, boolean subregion, @NotNull Optional<UUID> notify)`:
     `lease = getLeaseholdContract`, `freehold = getFreeholdContract`; both null → `NotRegistered`; `subregion = lease != null && parentId.isPresent() && isFreehold(parentId.get(), worldId)`; without bypass, `!subregion || !ctx.mayManage(lease.landlord())` → `NotAuthorized`, then `lease.tenantId() != null` → `Occupied`. `notify` is the lease's tenant, else the freehold's title holder.
  3. `thenComposeAsync` on `mainThreadExec`: a refusal ends it. Else `worldGuardRegionRedefine.check(region, selection, replacement, check.subregion(), subregionMinVolume.getAsInt())`; a `Problem` maps to its result (`NoRegionManager` → `Error("WorldGuard has no region manager for " + world name)`). Fire `RegionRedefineEvent(region, ctx.player(), replacement)`; cancelled → `Cancelled`. Take `oldVolume` from `asSelection(old)` and `newVolume` from the selection, both with `blockVolume`. Call `redefine`.
  4. `thenApplyAsync` on `dbExec`: `deleteSchematic`, catching `RuntimeException` into a WARNING log and `false`.
  5. `thenApplyAsync` on `mainThreadExec`: fire `RegionRedefinedEvent(newRegion, ctx.player(), old)`; return `Redefined`, wrapped in `WorldGuardSaveError` for `RedefinedSaveFailed`.
  6. `exceptionally`: log SEVERE with the region id, return `Error(message)`, as the rename does.
- [ ] Run the same command and expect PASS (18 tests).
- [ ] Run `./gradlew build`.
- [ ] Commit `feat(api): redefine a region's shape`.

## Task 5: Admin command, messages and replies

**Files:**
- Create `realty-paper/src/main/java/io/github/md5sha256/realty/command/RedefineReplies.java`
- Create `realty-paper/src/main/java/io/github/md5sha256/realty/command/RedefineCommand.java`
- Create `realty-paper/src/main/java/io/github/md5sha256/realty/command/util/WorldEditSelections.java`
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/command/CreateCommand.java` (use `WorldEditSelections.of` in place of its private `getSelection`)
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/localisation/MessageKeys.java` (after the `RENAME_` keys, and `NOTIFICATION_REGION_REDEFINED` after `NOTIFICATION_REGION_RENAMED`)
- Modify `realty-paper/src/main/resources/messages.yml` (a `redefine:` section after `rename:`; `region-redefined` under `notification:`)
- Modify `realty-paper/src/main/resources/paper-plugin.yml` (after `realty.command.rename.others`)
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java` (register after `RenameCommand`)
- Test: create `realty-paper/src/test/java/io/github/md5sha256/realty/command/RedefineMessagesTest.java`
- Test: create `realty-paper/src/test/java/io/github/md5sha256/realty/command/RedefineRepliesTest.java`

**Interfaces:**

```java
/** Replies to a redefine and notifies the tenant or title holder. Shared by both redefine commands. */
public record RedefineReplies(@NotNull MessageContainer messages, @NotNull RealtyEventDispatch events) {
    /** {@code actorId} is empty for the console; nobody is notified of their own redefine. */
    public void report(@NotNull CommandSender sender, @NotNull Optional<UUID> actorId,
                       @NotNull String regionId, @NotNull RealtyPaperApi.RedefineRegionResult result);
    public void error(@NotNull CommandSender sender, @NotNull Throwable error);
}

public record RedefineCommand(@NotNull RealtyPaperApi api, @NotNull ActorContexts actors,
                              @NotNull ExecutorState executorState, @NotNull MessageContainer messages,
                              @NotNull RedefineReplies replies) implements CustomCommandBean.Single {}
```

Message keys and placeholders are those in the spec's "Messages" table, as `REDEFINE_SUCCESS = "redefine.success"` and so on. Wording follows the `rename:` section, each with a `# <placeholders>` comment.

Permission:

```yaml
  realty.command.redefine:
    description: Allows redefining any registered region with a WorldEdit selection
    default: op
```

- [ ] Write the failing tests:
  - `RedefineMessagesTest`: the `SubregionDeleteMessagesTest` pattern over every `REDEFINE_` key and `NOTIFICATION_REGION_REDEFINED`.
  - `RedefineRepliesTest`, with a Mockito `CommandSender` and `RealtyEventDispatch` and the shipped messages: `Redefined` sends `success` and, when `captureRemoved`, `capture-removed`; with a `notify` that is not the actor it fires one `RealtyNotificationEvent` to that player; with `notify` equal to the actor it fires none; `WorldGuardSaveError` sends `success` then `worldguard-save-error`; `TooSmall(8, 20)` sends `too-small` with both numbers; `Cancelled` sends `common.action-cancelled`.
- [ ] Run `./gradlew :realty-paper:test --tests "*RedefineMessagesTest" --tests "*RedefineRepliesTest"` and expect a compile FAIL.
- [ ] Implement:
  - The keys, the messages and the permission node.
  - `RedefineReplies.report`: an exhaustive `switch` over the result, one key per case, as `RenameCommand.report`. The notification is fired with `events.fireSync(new RealtyNotificationEvent(List.of(id), NOTIFICATION_REGION_REDEFINED, message, region))`.
  - `RedefineCommand`: `literal("redefine")`, `permission("realty.command.redefine")`, `required("region", WorldGuardRegionParser.worldGuardRegion())`. A non-player gets `common.players-only`. Read the selection as `CreateCommand.getSelection` does (move that method to a public static `WorldEditSelections.of(Player)` in `command/util` and use it from both); null → `redefine.no-selection`. Then build the actor with `actors.forRegion(player, true, region)` on `dbExec`, call `api.redefineRegion` on `mainThreadExec`, report with `RedefineReplies`, and end with `exceptionally(replies::error)`.
  - Register `new RedefineCommand(paperApi, this.actorContexts, executorState, messageContainer, redefineReplies)` in `Realty`, with one `RedefineReplies` made beside it for Task 6 to share.
- [ ] Run the same command and expect PASS.
- [ ] Run `./gradlew build`.
- [ ] Commit `feat(command): add /realty redefine for admins`.

## Task 6: Wand redefine for landlords

**Files:**
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/command/SubregionDialog.java`
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/command/SubregionCommandGroup.java`
- Modify `realty-paper/src/main/resources/paper-plugin.yml` (after `realty.command.subregion.delete.others`)
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java` (pass `RedefineReplies` and `ActorContexts` to `SubregionDialog`)
- Test: create `realty-paper/src/test/java/io/github/md5sha256/realty/command/SubregionRedefineHeightTest.java`

**Interfaces:**

```java
// SubregionDialog
/** Starts a wand redefine of {@code target}: the height dialog, then the confirm dialog. Main thread. */
public void openRedefine(@NotNull Player player, @NotNull WorldGuardRegion target);
/** The height slider's start: the selection's height once set, else the target's, else 16; clamped to 1..max. */
static int initialHeight(@NotNull WandSelection selection, @Nullable ProtectedRegion target, int maxHeight);
```

Permissions:

```yaml
  realty.command.subregion.redefine:
    description: Allows redefining a vacant subregion whose landlord you manage, with the wand
    default: op
  realty.command.subregion.redefine.others:
    description: Allows redefining any subregion with the wand, rented or not
    default: op
```

- [ ] Write the failing test `SubregionRedefineHeightTest` for `initialHeight`: no height set and no target gives 16; no height set and a target with Y 64..73 gives 10; a height already set wins over the target; a result above `maxHeight` is clamped.
- [ ] Run `./gradlew :realty-paper:test --tests "*SubregionRedefineHeightTest"` and expect a compile FAIL.
- [ ] Implement:
  - `SubregionDialog` keeps a `ConcurrentHashMap<UUID, WorldGuardRegion> redefineTargets`. `openRedefine` checks the wand selection is complete (else `subregion.selection-incomplete`) and in the target's world (else `redefine.wrong-world`), records the target and shows the height dialog. The height dialog uses `initialHeight` and, when a target is recorded, the title "Step 1 of 2: Height". Continue goes to `showRedefineConfirm` when a target is recorded, else to `open` as now. Clear also removes the target.
  - `showRedefineConfirm`: a `DialogType.confirmation` titled "Step 2 of 2: Confirm", body "Redefine <region>: <old> blocks to <new> blocks." from `blockVolume`, with Confirm (green) and Cancel. Confirm removes the target, builds the actor with `actors.forRegion(player, player.hasPermission("realty.command.subregion.redefine.others"), target)` on `dbExec`, calls `api.redefineRegion(target, selection.toRegion(), actor)` on `mainThreadExec`, clears the wand selection when the result is `Redefined` or `WorldGuardSaveError`, and reports through `RedefineReplies`. Cancel removes the target.
  - `SubregionCommandGroup`: `base.literal("redefine").permission("realty.command.subregion.redefine").required("region", WorldGuardRegionParser.worldGuardRegion())`, player-only, calling `dialog.openRedefine(player, region)`. Add the node to the class Javadoc list.
- [ ] Run the same command and expect PASS (4 tests).
- [ ] Run `./gradlew build`.
- [ ] The dialogs can only be checked on a live server; see Task 7.
- [ ] Commit `feat(command): redefine a subregion with the wand`.

## Task 7: Live-server checks

No code. On a test server with this build, WorldGuard, WorldEdit and a landlord account holding `realty.command.subregion.wand`, `realty.command.subregion.confirm` and `realty.command.subregion.redefine`:

1. As the landlord, mark a new footprint for a vacant wand-made subregion and run `/realty subregion redefine <region>`. The height dialog says "Step 1 of 2" and starts at the region's height. Confirm; `/rg info <region>` shows the new corners and the same owners, members and flags.
2. As the landlord, try a rented subregion: `redefine.occupied`, and nothing changes.
3. As an op, redefine the rented subregion with the wand: it changes, and the tenant gets `notification.region-redefined`.
4. Try a footprint crossing the parent's edge (`outside-parent`) and one over a sibling (`overlaps-sibling`).
5. As an op, select a smaller freehold with WorldEdit that leaves out a subregion and run `/realty redefine <freehold>`: `child-outside`. Select a larger one: it changes, and `/rg info` on each subregion still names the freehold as parent.
6. `/realty redefine` with a WorldEdit polygon selection (`//sel poly`).
7. Capture a region, redefine it: the reply has `capture-removed`, and the web explorer shows the new outline and no preview.
8. `/realty redefine` with no WorldEdit selection: `redefine.no-selection`.

Record the results in the stack's last PR description.

## Notes from the build

- `Redefined` names the player to tell `occupantId`, not `notify` as the spec has it: a record component may not be called `notify`, because `Object.notify()` exists.
- `RealtyPaperApiImplTest` builds `RealtyPaperApiImpl` in four places, not five.
- The message tests load `messages.yml` through a new test helper, `ShippedMessages`, instead of copying the loader again.
- Added after Task 4 (its own commit on the API layer): the database step runs in the region's queue, so a redefine waiting behind another one on the same region would still hold the region object the first one replaced, and would leave the children pointing at a region no longer in WorldGuard. The main-thread step now reads the region from WorldGuard again (`WorldGuardRegionRedefine.current`); a region removed meanwhile is `NotRegistered`.
- After review, simplified on top of the plan: the redefine target is passed through the dialogs instead of kept in a per-player map; the wand's rectangle selection carries its world, so the API's `WrongWorld` check covers the wand too; `WorldGuardRegionRedefine.check` returns the API's results directly and its outcome is one record with an optional save error; rename and redefine share one swap step (`WorldGuardRegionRename.takePlace`); `SubregionSelectionValidator.toProtectedRegion` is the one selection-to-region builder, used by the create paths too; and `overlappingSibling` skips siblings whose bounding boxes do not meet the selection.
