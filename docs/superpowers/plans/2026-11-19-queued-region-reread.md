# Queued Region Re-read Implementation Plan

**Goal:** A queued API operation acts on the region as WorldGuard has it when its turn comes, not as its caller looked it up.
**Spec:** `docs/superpowers/specs/2026-11-19-queued-region-reread-design.md`

All tests are in `realty-paper/src/test/java/io/github/md5sha256/realty/api/RealtyPaperApiImplTest.java`, in a new `@Nested class QueuedRegion`, with a real in-memory manager from `TestRegionManagers.inMemory()` returned by `regionContainer.get(any())`, as the `RedefineRegion` group does.

## Task 1: The queue re-reads the region

**Files:**
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/api/RealtyPaperApiImpl.java` (beside `serializeByRegion`)
- Test: `RealtyPaperApiImplTest`

**Interfaces:**

```java
<T> @NotNull CompletableFuture<T> serializeRegion(@NotNull WorldGuardRegion region,
        @NotNull Supplier<T> gone, @NotNull Function<WorldGuardRegion, CompletableFuture<T>> operation);
@NotNull Optional<WorldGuardRegion> current(@NotNull WorldGuardRegion region);
```

Both package-private; `serializeByRegion` becomes package-private too, so a test can hold the queue.

- [ ] Write the failing tests in `QueuedRegion`:
  - `theOperationGetsTheRegionWorldGuardHasWhenItsTurnComes`: `CompletableFuture<String> gate = new CompletableFuture<>()`; `api.serializeByRegion(REGION_ID, WORLD_ID, () -> gate)`; then `CompletableFuture<ProtectedRegion> seen = api.serializeRegion(wgRegion, () -> null, now -> CompletableFuture.completedFuture(now.region()))`; replace the region in the manager with a new `ProtectedCuboidRegion` of the same id; `gate.complete("done")`; `assertSame(replacement, seen.join())`.
  - `aRegionGoneWhenItsTurnComesGivesGone`: the same with `regionManager.removeRegion(REGION_ID)` before completing the gate, and `gone` returning a marker object: the result is the marker and the operation never ran (an `AtomicBoolean` it would set stays false).
  - `aWorldWithoutARegionManagerPassesTheRegionAsGiven`: `regionContainer.get(any())` returns null; `serializeRegion(wgRegion, ...)` gives `wgRegion.region()`.
  - `currentReadsTheManager`: `current(wgRegion)` is the manager's object for the id, and empty once removed.
- [ ] Run `./gradlew :realty-paper:test --tests "*RealtyPaperApiImplTest*QueuedRegion*"` and expect a compile FAIL: `serializeRegion` does not exist.
- [ ] Implement:
  - `current`: `RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(region.world()))`; null manager → `Optional.of(region)`; else `Optional.ofNullable(manager.getRegion(id)).map(now -> new WorldGuardRegion(now, region.world()))`.
  - `serializeRegion`: `serializeByRegion(id, worldId, () -> onMainThread(() -> current(region)).thenCompose(now -> now.map(operation).orElseGet(() -> done(gone.get()))))`, where a private `onMainThread(Supplier<U>)` runs the supplier inline when `server.isPrimaryThread()` and otherwise with `CompletableFuture.supplyAsync(supplier, executorState.mainThreadExec())`.
- [ ] Run the same command and expect PASS (4 tests).
- [ ] Run `./gradlew :realty-paper:build`.
- [ ] Commit `feat(api): re-read a queued operation's region when its turn comes`.

## Task 2: Every queued operation uses the re-read region

**Files:**
- Modify `RealtyPaperApiImpl.java`: `buy`, `rent`, `unrent`, `extend`, `terminate`, `payBid`, `payOffer`, `setTitleHolder`, `setTenant`, `setLandlord`, `renameRegion`, `redefineRegion`
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/api/WorldGuardRegionRedefine.java` (delete `current`)
- Modify `realty-paper/src/test/java/io/github/md5sha256/realty/api/WorldGuardRegionRedefineTest.java` (delete `currentReadsTheRegionWorldGuardHasNow`)
- Test: `RealtyPaperApiImplTest`

- [ ] Write the failing tests in `QueuedRegion`:
  - `aBuyQueuedBehindARedefineOwnsTheRegionWorldGuardHas`: hold the queue with a gate as in Task 1; queue `api.buy(wgRegion, BUYER_CTX, false)` with the backend stubbed as in the existing buy tests (`executeBuy` gives `RESERVED`, the economy transfer succeeds); replace the region in the manager; complete the gate; the manager's region's owners contain `BUYER_ID`.
  - `aRenameQueuedBehindARedefineKeepsTheNewShape`: hold the queue on `REGION_ID`; queue `api.renameRegion(wgRegion, "renamed-plot", staff)` with `renameRegion` stubbed to `Success(Optional.empty())`; replace the region with a cuboid 0..40; complete the gate; the manager's `renamed-plot` has maximum point 40,40,40.
- [ ] Run `./gradlew :realty-paper:test --tests "*RealtyPaperApiImplTest*QueuedRegion*"` and expect FAIL: the buyer owns the old object; the renamed region has the old shape.
- [ ] Implement, for each operation in the file list: replace `serializeByRegion(regionId, worldId, () -> ...)` with `serializeRegion(region, () -> <gone result from the spec's table>, current -> ...)`, and inside the operation use `current` wherever it used `region`. In `renameRegion`, keep both queues and wrap the innermost operation in `onMainThread(() -> current(region))`, returning `NotRegistered` when empty. In `redefineRegion`, replace the `worldGuardRegionRedefine.current(queued)` re-read in `redefineInWorldGuard` with `serializeRegion`, then delete `WorldGuardRegionRedefine.current` and its test.
- [ ] Run the same command and expect PASS, then `./gradlew :realty-paper:test` and expect the whole suite green, including the `RedefineRegion` group's stale-region and removed-region tests. Where an existing test's in-memory manager lacks `protectedRegion`, the region is now gone when the operation runs: add it to that manager in the group's set-up.
- [ ] Run `./gradlew build`.
- [ ] Commit `fix(api): act on the region WorldGuard has when a queued operation runs`.
